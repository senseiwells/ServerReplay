package me.senseiwells.replay.player

import com.google.common.hash.Hashing
import com.replaymod.replaystudio.io.ReplayOutputStream
import com.replaymod.replaystudio.lib.viaversion.api.protocol.packet.State
import com.replaymod.replaystudio.lib.viaversion.api.protocol.version.ProtocolVersion
import com.replaymod.replaystudio.protocol.Packet
import com.replaymod.replaystudio.protocol.PacketTypeRegistry
import com.replaymod.replaystudio.replay.ReplayFile
import com.replaymod.replaystudio.replay.ReplayMetaData
import com.replaymod.replaystudio.replay.ZipReplayFile
import com.replaymod.replaystudio.studio.ReplayStudio
import io.netty.buffer.Unpooled
import me.senseiwells.replay.ServerReplay
import me.senseiwells.replay.config.ReplayConfig
import me.senseiwells.replay.rejoin.RejoinedReplayPlayer
import net.minecraft.DetectedVersion
import net.minecraft.SharedConstants
import net.minecraft.network.ConnectionProtocol
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.protocol.PacketFlow
import net.minecraft.network.protocol.game.*
import net.minecraft.network.protocol.login.ClientboundGameProfilePacket
import net.minecraft.network.protocol.login.ClientboundLoginCompressionPacket
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.network.ServerGamePacketListenerImpl
import java.io.IOException
import java.net.URL
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readBytes
import kotlin.io.path.writeBytes
import com.github.steveice10.netty.buffer.Unpooled as ReplayUnpooled
import net.minecraft.network.protocol.Packet as MinecraftPacket

class PlayerRecorder internal constructor(
    player: ServerPlayer,
    private val recordings: Path
) {
    private val executor: ExecutorService

    private val connection: ServerGamePacketListenerImpl
    private val server: MinecraftServer
    private val state: PlayerState

    private val replay: ReplayFile
    private val output: ReplayOutputStream
    private val meta: ReplayMetaData

    private val start: Long

    private var protocol = ConnectionProtocol.LOGIN
    private var last = 0L

    private var packId = 0

    val player: ServerPlayer
        get() = this.connection.player

    init {
        this.executor = Executors.newSingleThreadExecutor()

        this.connection = player.connection
        this.server = player.server

        val out = this.recordings.resolve("replay").toFile()
        this.replay = ZipReplayFile(ReplayStudio(), out)

        this.output = this.replay.writePacketData()
        this.meta = this.createNewMeta()

        this.start = System.currentTimeMillis()

        this.state = PlayerState(this)

        this.saveMeta()
    }

    fun record(outgoing: MinecraftPacket<*>) {
        if (outgoing is ClientboundLoginCompressionPacket) {
            return
        }

        // Protocol may be mutated in #onPacket, we need current state
        val id = this.protocol.getPacketId(PacketFlow.CLIENTBOUND, outgoing)
        val state = this.protocolAsState()

        if (this.prePacket(outgoing)) {
            return
        }

        val buf = FriendlyByteBuf(Unpooled.buffer())
        val saved = try {
            outgoing.write(buf)
            val wrapped = ReplayUnpooled.wrappedBuffer(buf.array(), buf.arrayOffset(), buf.readableBytes())

            val version = ProtocolVersion.getProtocol(SharedConstants.getProtocolVersion())
            val registry = PacketTypeRegistry.get(version, state)

            Packet(registry, id, wrapped)
        } finally {
            buf.release()
        }

        val timestamp = this.getRecordingTimeMS()
        this.last = timestamp

        this.executor.execute {
            try {
                this.output.write(timestamp, saved)
            } catch (e: IOException) {
                ServerReplay.logger.error("Failed to write packet", e)
            }
        }

        this.postPacket(outgoing)
    }

    // THIS SHOULD ONLY BE CALLED WHEN STARTING
    // REPLAY AFTER THE PLAYER HAS LOGGED IN!
    fun start() {
        RejoinedReplayPlayer.rejoin(this.player, this)
    }

    @JvmOverloads
    fun stop(save: Boolean = true) {
        PlayerRecorders.remove(this.connection.player)
        if (save) {
            this.meta.duration = this.last.toInt()
            this.saveMeta()
        }

        this.close(save)
    }

    fun tick() {
        this.state.tick()
    }

    fun getRecordingTimeMS(): Long {
        return System.currentTimeMillis() - this.start
    }

    private fun prePacket(packet: MinecraftPacket<*>): Boolean {
        when (packet) {
            is ClientboundGameProfilePacket -> {
                // The player has now logged in
                this.protocol = ConnectionProtocol.PLAY
            }
            is ClientboundAddPlayerPacket -> {
                val uuids = this.meta.players.toMutableSet()
                uuids.add(packet.playerId.toString())
                this.meta.players = uuids.toTypedArray()
                this.saveMeta()
            }
            is ClientboundBundlePacket -> {
                for (sub in packet.subPackets()) {
                    this.record(sub)
                }
                return true
            }
            is ClientboundResourcePackPacket -> {
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
                val uuid = this.player.uuid
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
        val player = this.player
        this.record(ClientboundAddPlayerPacket(player))
        val tracked = player.entityData.nonDefaultValues
        if (tracked != null) {
            this.record(ClientboundSetEntityDataPacket(player.id, tracked))
        }
    }

    private fun close(save: Boolean) {
        this.executor.execute {
            try {
                val path = this.recording()
                this.output.close()

                if (save) {
                    this.replay.saveTo(path.toFile())
                }
                this.replay.close()
                ServerReplay.logger.info("Successfully closed replay${if (save) " and saved to $path" else ""}")
            } catch (exception: Exception) {
                ServerReplay.logger.error("Failed to write replay", exception)
            }
        }

        this.executor.shutdown()
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
            ConnectionProtocol.LOGIN -> State.LOGIN
            else -> throw IllegalStateException("Expected connection protocol to be 'PLAY' or 'LOGIN'")
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

    private fun downloadAndRecordResourcePack(packet: ClientboundResourcePackPacket): Boolean {
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
        this.record(ClientboundResourcePackPacket(
            "replay://${requestId}",
            "",
            packet.isRequired,
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