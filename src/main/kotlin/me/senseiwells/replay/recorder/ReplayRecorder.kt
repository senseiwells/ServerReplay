package me.senseiwells.replay.recorder

import com.google.common.hash.Hashing
import com.mojang.authlib.GameProfile
import com.replaymod.replaystudio.io.ReplayOutputStream
import com.replaymod.replaystudio.lib.viaversion.api.protocol.packet.State
import com.replaymod.replaystudio.lib.viaversion.api.protocol.version.ProtocolVersion
import com.replaymod.replaystudio.protocol.Packet
import com.replaymod.replaystudio.protocol.PacketTypeRegistry
import com.replaymod.replaystudio.replay.ReplayMetaData
import io.netty.buffer.Unpooled
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import me.senseiwells.replay.ServerReplay
import me.senseiwells.replay.config.ReplayConfig
import me.senseiwells.replay.util.DebugPacketData
import me.senseiwells.replay.util.FileUtils
import me.senseiwells.replay.util.ReplayOptimizerUtils
import me.senseiwells.replay.util.SizedZipReplayFile
import net.minecraft.DetectedVersion
import net.minecraft.SharedConstants
import net.minecraft.network.ConnectionProtocol
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.protocol.PacketFlow
import net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket
import net.minecraft.network.protocol.game.ClientboundBundlePacket
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket
import net.minecraft.network.protocol.game.ClientboundRespawnPacket
import net.minecraft.network.protocol.login.ClientboundGameProfilePacket
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
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

abstract class ReplayRecorder(
    protected val server: MinecraftServer,
    protected val profile: GameProfile,
    private val recordings: Path
) {
    private val packets by lazy { Object2ObjectOpenHashMap<Class<*>, DebugPacketData>() }
    private val executor: ExecutorService

    private val replay: SizedZipReplayFile
    private val output: ReplayOutputStream
    private val meta: ReplayMetaData

    private var start: Long = 0

    private var protocol = ConnectionProtocol.LOGIN
    private var lastPacket = 0L
    private var lastSizeCheck = System.currentTimeMillis()

    private var packId = 0

    private var started = false

    private var ignore = false

    val stopped: Boolean
        get() = this.executor.isShutdown
    val recordingPlayerUUID: UUID
        get() = this.profile.id

    abstract val level: ServerLevel

    init {
        this.executor = Executors.newSingleThreadExecutor()

        val out = this.recordings.resolve("replay").toFile()
        this.replay = SizedZipReplayFile(out)

        this.output = this.replay.writePacketData()
        this.meta = this.createNewMeta()

        this.saveMeta()
    }

    fun record(outgoing: MinecraftPacket<*>) {
        if (!this.started) {
            throw IllegalStateException("Cannot record packets if recorder not started")
        }
        if (this.ignore || this.stopped) {
            return
        }
        if (ReplayOptimizerUtils.optimisePackets(this, outgoing)) {
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

            if (ServerReplay.config.debug) {
                val type = outgoing::class.java
                this.packets.getOrPut(type) { DebugPacketData(type, 0, 0) }.increment(buf.readableBytes())
            }

            val wrapped = ReplayUnpooled.wrappedBuffer(buf.array(), buf.arrayOffset(), buf.readableBytes())

            val version = ProtocolVersion.getProtocol(SharedConstants.getProtocolVersion())
            val registry = PacketTypeRegistry.get(version, state)

            Packet(registry, id, wrapped)
        } finally {
            buf.release()
        }

        val timestamp = this.getTimestamp()
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

    fun tryStart(log: Boolean = true): Boolean {
        if (this.started) {
            throw IllegalStateException("Cannot start recording after already started!")
        }
        if (this.start()) {
            if (log) {
                this.logStart()
            }
            return true
        }
        return false
    }

    fun logStart() {
        ServerReplay.logger.info("Started replay for ${this.getName()}")
    }

    @JvmOverloads
    fun stop(save: Boolean = true): CompletableFuture<Long> {
        if (this.stopped) {
            return CompletableFuture.failedFuture(IllegalStateException("Cannot stop replay after already stopped"))
        }

        if (ServerReplay.config.debug) {
            ServerReplay.logger.info("Replay ${this.getName()} Debug Packet Data:\n${this.getDebugPacketData()}")
        }

        // We only save if the player has actually logged in...
        val future = this.close(save && this.protocol == ConnectionProtocol.PLAY)
        this.closed(future)
        return future
    }

    fun getTotalRecordingTime(): Long {
        return System.currentTimeMillis() - this.start
    }

    fun getRawRecordingSize(): Long {
        return this.replay.getRawFileSize()
    }

    fun getCompressedRecordingSize(): CompletableFuture<Long> {
        return CompletableFuture.supplyAsync({ this.replay.getCompressedFileSize() }, this.executor)
    }

    @Internal
    fun getDebugPacketData(): String {
        return this.packets.values
            .sortedByDescending { it.size }
            .joinToString(separator = "\n", transform = DebugPacketData::format)
    }

    @Internal
    fun afterLogin() {
        this.started = true
        this.start = System.currentTimeMillis()

        // We will not have recorded this, so we need to do it manually.
        this.record(ClientboundGameProfilePacket(this.profile))

        this.protocol = ConnectionProtocol.CONFIGURATION
    }

    @Internal
    fun afterConfigure() {
        this.protocol = ConnectionProtocol.PLAY
    }

    protected fun ignore(block: () -> Unit) {
        val previous = this.ignore
        try {
            this.ignore = true
            block()
        } finally {
            this.ignore = previous
        }
    }

    open fun getTimestamp(): Long {
        return this.getTotalRecordingTime()
    }

    abstract fun getName(): String

    protected abstract fun start(): Boolean

    protected abstract fun restart(): Boolean

    protected abstract fun closed(future: CompletableFuture<Long>)

    protected abstract fun spawnPlayer()

    protected abstract fun canContinueRecording(): Boolean

    protected open fun canRecordPacket(packet: MinecraftPacket<*>): Boolean {
        return true
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
        return !this.canRecordPacket(packet)
    }

    private fun postPacket(packet: MinecraftPacket<*>) {
        when (packet) {
            is ClientboundRespawnPacket -> {
                this.spawnPlayer()
            }
            is ClientboundPlayerInfoUpdatePacket -> {
                val uuid = this.recordingPlayerUUID
                for (entry in packet.newEntries()) {
                    if (uuid == entry.profileId) {
                        this.spawnPlayer()
                        break
                    }
                }
            }
        }
    }

    private fun checkFileSize() {
        val maxFileSize = ServerReplay.config.maxFileSize
        if (maxFileSize.bytes <= 0) {
            return
        }
        val time = System.currentTimeMillis()
        if (time - this.lastSizeCheck < 30_000) {
            return
        }
        this.lastSizeCheck = time
        this.getCompressedRecordingSize().thenAccept { compressed ->
            if (compressed > maxFileSize.bytes) {
                ServerReplay.logger.info(
                    "Stopped recording replay for ${this.getName()}, over max file size ${maxFileSize.raw}!"
                )
                this.stop(true).thenAcceptAsync({ size ->
                    ServerReplay.logger.info(
                        "Saved last replay for ${this.getName()}, compressed to file size of ${FileUtils.formatSize(size)}"
                    )
                    if (ServerReplay.config.restartAfterMaxFileSize && this.canContinueRecording()) {
                        if (this.restart()) {
                            ServerReplay.logger.info("Restarted recording for ${this.getName()}")
                        } else {
                            ServerReplay.logger.info("Failed to restart recording for ${this.getName()}")
                        }
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
        meta.serverName = ServerReplay.config.worldName
        meta.customServerName = ServerReplay.config.serverName
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
        val path = ReplayConfig.root.resolve("packs").resolve(pathHash)

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