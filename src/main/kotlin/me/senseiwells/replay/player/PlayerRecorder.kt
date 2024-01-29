package me.senseiwells.replay.player

import com.google.common.hash.Hashing
import com.mojang.authlib.GameProfile
import com.replaymod.replaystudio.io.ReplayOutputStream
import com.replaymod.replaystudio.lib.viaversion.api.protocol.packet.State
import com.replaymod.replaystudio.lib.viaversion.api.protocol.version.ProtocolVersion
import com.replaymod.replaystudio.protocol.Packet
import com.replaymod.replaystudio.protocol.PacketTypeRegistry
import com.replaymod.replaystudio.replay.ReplayMetaData
import io.netty.buffer.Unpooled
import me.senseiwells.replay.ServerReplay
import me.senseiwells.replay.config.ReplayConfig
import me.senseiwells.replay.rejoin.RejoinedReplayPlayer
import me.senseiwells.replay.util.FileUtils
import me.senseiwells.replay.util.SizedZipReplayFile
import net.minecraft.DetectedVersion
import net.minecraft.SharedConstants
import net.minecraft.network.ConnectionProtocol
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.protocol.PacketFlow
import net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket
import net.minecraft.network.protocol.game.*
import net.minecraft.network.protocol.login.ClientboundGameProfilePacket
import net.minecraft.network.protocol.login.ClientboundLoginCompressionPacket
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.EntityType
import org.jetbrains.annotations.ApiStatus.Internal
import java.io.IOException
import java.net.URL
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.io.path.*
import com.github.steveice10.netty.buffer.Unpooled as ReplayUnpooled
import net.minecraft.network.protocol.Packet as MinecraftPacket

class PlayerRecorder internal constructor(
    private val server: MinecraftServer,
    private val profile: GameProfile,
    private val recordings: Path
) {
    private val executor: ExecutorService

    private val state: PlayerState

    private val replay: SizedZipReplayFile
    private val output: ReplayOutputStream
    private val meta: ReplayMetaData

    private val start: Long

    private var protocol = ConnectionProtocol.LOGIN
    private var lastPacket = 0L
    private var lastSizeCheck = System.currentTimeMillis()

    private var packId = 0

    private var started = false

    val player: ServerPlayer?
        get() = this.server.playerList.getPlayer(this.playerUUID)
    val playerUUID: UUID
        get() = this.profile.id
    val playerName: String
        get() = this.profile.name
    val stopped: Boolean
        get() = this.executor.isShutdown

    init {
        this.executor = Executors.newSingleThreadExecutor()

        val out = this.recordings.resolve("replay").toFile()
        this.replay = SizedZipReplayFile(out)

        this.output = this.replay.writePacketData()
        this.meta = this.createNewMeta()

        this.start = System.currentTimeMillis()

        this.state = PlayerState(this)

        this.saveMeta()
    }

    fun record(outgoing: MinecraftPacket<*>) {
        if (!this.started) {
            throw IllegalStateException("Cannot record packets if recorder not started")
        }
        if (this.stopped) {
            return
        }

        if (outgoing is ClientboundLoginCompressionPacket) {
            return
        }

        if (this.prePacket(outgoing)) {
            return
        }

        val buf = FriendlyByteBuf(Unpooled.buffer())
        val saved = try {
            val id = this.protocol.codec(PacketFlow.CLIENTBOUND).packetId(outgoing)
            val state = this.protocolAsState()

            outgoing.write(buf)
            val wrapped = ReplayUnpooled.wrappedBuffer(buf.array(), buf.arrayOffset(), buf.readableBytes())

            val version = ProtocolVersion.getProtocol(SharedConstants.getProtocolVersion())
            val registry = PacketTypeRegistry.get(version, state)

            Packet(registry, id, wrapped)
        } finally {
            buf.release()
        }

        val timestamp = this.getRecordingTimeMS()
        this.lastPacket = timestamp

        this.executor.execute {
            try {
                this.output.write(timestamp, saved)
            } catch (e: IOException) {
                ServerReplay.logger.error("Failed to write packet", e)
            }
        }

        this.postPacket(outgoing)
        this.checkFileSize()
    }

    // THIS SHOULD ONLY BE CALLED WHEN STARTING
    // REPLAY AFTER THE PLAYER HAS LOGGED IN!
    fun start(log: Boolean = false): Boolean {
        if (this.started) {
            throw IllegalStateException("Cannot start recording after already started!")
        }
        val player = this.player ?: return false
        if (log) {
            ServerReplay.logger.info("Started to record player '${player.scoreboardName}'")
        }
        RejoinedReplayPlayer.rejoin(player, this)
        return true
    }

    @JvmOverloads
    fun stop(save: Boolean = true): CompletableFuture<Long> {
        if (this.stopped) {
            return CompletableFuture.failedFuture(IllegalStateException("Cannot stop replay after already stopped"))
        }

        // We only save if the player has actually logged in...
        val future = this.close(save && this.protocol == ConnectionProtocol.PLAY)
        PlayerRecorders.close(this.server, this.playerUUID, future)
        return future
    }

    fun getRecordingTimeMS(): Long {
        return System.currentTimeMillis() - this.start
    }

    fun getRawRecordingSize(): Long {
        return this.replay.getRawFileSize()
    }

    fun getCompressedRecordingSize(): CompletableFuture<Long> {
        return CompletableFuture.supplyAsync({ this.replay.getCompressedFileSize() }, this.executor)
    }

    @Internal
    fun tick(player: ServerPlayer) {
        this.state.tick(player)
    }

    @Internal
    fun afterLogin() {
        this.started = true
        // We will not have recorded this, so we need to do it manually.
        this.record(ClientboundGameProfilePacket(this.profile))

        this.protocol = ConnectionProtocol.CONFIGURATION
    }

    @Internal
    fun afterConfigure() {
        this.protocol = ConnectionProtocol.PLAY
    }

    private fun prePacket(packet: MinecraftPacket<*>): Boolean {
        when (packet) {
            is ClientboundAddEntityPacket -> {
                if (packet.type == EntityType.PLAYER) {
                    val uuids = this.meta.players.toMutableSet()
                    uuids.add(packet.uuid.toString())
                    this.meta.players = uuids.toTypedArray()
                    this.saveMeta()
                }
            }
            is ClientboundBundlePacket -> {
                for (sub in packet.subPackets()) {
                    this.record(sub)
                }
                return true
            }
            is ClientboundResourcePackPushPacket -> {
                return this.downloadAndRecordResourcePack(packet)
            }
        }
        return false
    }

    private fun postPacket(packet: MinecraftPacket<*>) {
        when (packet) {
            is ClientboundRespawnPacket -> {
                this.spawnPlayer()
            }
            is ClientboundPlayerInfoUpdatePacket -> {
                val uuid = this.playerUUID
                for (entry in packet.newEntries()) {
                    if (uuid == entry.profileId) {
                        this.spawnPlayer()
                        break
                    }
                }
            }
        }
    }

    private fun spawnPlayer() {
        val player = this.player ?: throw IllegalStateException("Tried spawning player before player exists")
        this.record(ClientboundAddEntityPacket(player))
        val tracked = player.entityData.nonDefaultValues
        if (tracked != null) {
            this.record(ClientboundSetEntityDataPacket(player.id, tracked))
        }
    }

    private fun checkFileSize() {
        if (ReplayConfig.maxFileSize <= 0) {
            return
        }
        val time = System.currentTimeMillis()
        if (time - this.lastSizeCheck < 30_000) {
            return
        }
        this.lastSizeCheck = time
        this.getCompressedRecordingSize().thenAccept { compressed ->
            if (compressed > ReplayConfig.maxFileSize) {
                ServerReplay.logger.info(
                    "Stopped recording '${this.playerName}', over max file size ${ReplayConfig.maxFileSizeString}!"
                )
                this.stop(true).thenAcceptAsync({ size ->
                    ServerReplay.logger.info(
                        "Saved last replay for '${this.playerName}', compressed to file size of ${FileUtils.formatSize(size)}"
                    )
                    if (ReplayConfig.restartAfterMaxFileSize && this.player != null) {
                        val recorder = PlayerRecorders.create(this.server, this.profile)
                        recorder.start(false)
                        ServerReplay.logger.info(
                            "Restarted recording for '${this.playerName}'"
                        )
                    }
                }, this.server)
            }
        }
    }

    private fun close(save: Boolean): CompletableFuture<Long> {
        if (save) {
            this.meta.duration = this.lastPacket.toInt()
            this.saveMeta()
        }
        val future = CompletableFuture.supplyAsync({
            var size = 0L
            try {
                val path = this.recording()
                this.output.close()

                if (save) {
                    this.replay.saveTo(path.toFile())
                    size = path.fileSize()
                }
                this.replay.close()
                ServerReplay.logger.info("Successfully closed replay${if (save) " and saved to $path" else ""}")
            } catch (exception: Exception) {
                ServerReplay.logger.error("Failed to write replay", exception)
                throw exception
            }
            size
        }, this.executor)

        this.executor.shutdown()
        return future
    }

    private fun saveMeta() {
        val version = ProtocolVersion.getProtocol(SharedConstants.getProtocolVersion())
        val registry = PacketTypeRegistry.get(version, State.LOGIN)

        this.executor.execute {
            this.replay.writeMetaData(registry, this.meta)
        }
    }

    private fun recording(): Path {
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd--HH-mm-ss")
        val date = LocalDateTime.now().format(formatter)
        return this.recordings.resolve("$date.mcpr")
    }

    private fun protocolAsState(): State {
        return when (this.protocol) {
            ConnectionProtocol.PLAY -> State.PLAY
            ConnectionProtocol.CONFIGURATION -> State.CONFIGURATION
            ConnectionProtocol.LOGIN -> State.LOGIN
            else -> throw IllegalStateException("Expected connection protocol to be 'PLAY', 'CONFIGURATION' or 'LOGIN'")
        }
    }

    private fun createNewMeta(): ReplayMetaData {
        val meta = ReplayMetaData()
        meta.isSingleplayer = false
        meta.serverName = ReplayConfig.worldName
        meta.customServerName = ReplayConfig.serverName
        meta.generator = "ServerReplay v${ServerReplay.version}"
        meta.date = System.currentTimeMillis()
        meta.mcVersion = DetectedVersion.BUILT_IN.name
        return meta
    }

    private fun downloadAndRecordResourcePack(packet: ClientboundResourcePackPushPacket): Boolean {
        if (packet.url.startsWith("replay://")) {
            return false
        }
        @Suppress("DEPRECATION")
        val pathHash = Hashing.sha1().hashString(packet.url, StandardCharsets.UTF_8).toString()
        val path = ReplayConfig.root().resolve("packs").resolve(pathHash)

        val requestId = this.packId++
        if (!path.exists() || !this.writeResourcePack(path.readBytes(), packet.hash, requestId)) {
            CompletableFuture.runAsync {
                path.parent.createDirectories()
                val bytes = URL(packet.url).openStream().readAllBytes()
                path.writeBytes(bytes)
                this.writeResourcePack(bytes, packet.hash, requestId)
            }.exceptionally {
                ServerReplay.logger.error("Failed to download resource pack", it)
                null
            }
        }
        this.record(ClientboundResourcePackPushPacket(
            packet.id,
            "replay://${requestId}",
            "",
            packet.required,
            packet.prompt
        ))
        return true
    }

    private fun writeResourcePack(bytes: ByteArray, expectedHash: String, id: Int): Boolean {
        @Suppress("DEPRECATION")
        val packHash = Hashing.sha1().hashBytes(bytes).toString()
        if (expectedHash == packHash) {
            this.executor.execute {
                try {
                    val index = this.replay.resourcePackIndex ?: HashMap()
                    val write = !index.containsValue(packHash)
                    index[id] = packHash
                    this.replay.writeResourcePackIndex(index)
                    if (write) {
                        this.replay.writeResourcePack(packHash).use {
                            it.write(bytes)
                        }
                    }
                } catch (e: IOException) {
                    ServerReplay.logger.warn("Failed to write resource pack", e)
                }
            }
            return true
        }
        return false
    }
}