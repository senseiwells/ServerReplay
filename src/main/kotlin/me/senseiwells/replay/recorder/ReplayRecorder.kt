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
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToStream
import me.senseiwells.replay.ServerReplay
import me.senseiwells.replay.config.ReplayConfig
import me.senseiwells.replay.util.*
import net.minecraft.DetectedVersion
import net.minecraft.SharedConstants
import net.minecraft.network.ConnectionProtocol
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.PacketFlow
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket
import net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket
import net.minecraft.network.protocol.game.ClientboundBundlePacket
import net.minecraft.network.protocol.login.ClientboundGameProfilePacket
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.EntityType
import org.apache.commons.lang3.builder.StandardToStringStyle
import org.apache.commons.lang3.builder.ToStringBuilder
import org.jetbrains.annotations.ApiStatus.Internal
import java.io.IOException
import java.net.URL
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.io.path.*
import kotlin.math.max
import kotlin.time.Duration.Companion.milliseconds
import com.github.steveice10.netty.buffer.Unpooled as ReplayUnpooled
import net.minecraft.network.protocol.Packet as MinecraftPacket

abstract class ReplayRecorder(
    protected val server: MinecraftServer,
    protected val profile: GameProfile,
    private val recordings: Path
) {
    private val packets by lazy { Object2ObjectOpenHashMap<String, DebugPacketData>() }
    private val executor: ExecutorService

    private val replay: SizedZipReplayFile
    private val output: ReplayOutputStream
    private val meta: ReplayMetaData
    private val date: String

    private var start: Long = 0

    private var protocol = ConnectionProtocol.LOGIN
    private var lastPacket = 0L

    private var lastCompressedSize = 0L
    private var lastRawSize = 0L
    private var startTimeOfLastSize = System.currentTimeMillis() - 1
    private var endTimeOfLastSize = System.currentTimeMillis()
    private var currentSizeFuture: CompletableFuture<Long>? = null
    private var isCheckingSize = false

    private var nextFileCheckTime = System.currentTimeMillis() + DEFAULT_FILE_CHECK_TIME_MS

    private var packId = 0

    private var started = false

    private var ignore = false

    val location: Path

    val stopped: Boolean
        get() = this.executor.isShutdown
    val recordingPlayerUUID: UUID
        get() = this.profile.id

    abstract val level: ServerLevel

    init {
        this.executor = Executors.newSingleThreadExecutor()

        this.date = DateUtils.getFormattedDate()
        this.location = FileUtils.findNextAvailable(this.recordings.resolve(this.date))
        this.replay = SizedZipReplayFile(out = this.location.toFile())

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
        val safe = this.server.isSameThread
        if (ServerReplay.config.debug && !safe) {
            ServerReplay.logger.warn("Trying to record packet off-thread ${outgoing.getDebugName()}")
        }

        if (safe && ReplayOptimizerUtils.optimisePackets(this, outgoing)) {
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
                val type = outgoing.getDebugName()
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
        this.calculateAndCheckFileSize()
        this.checkDuration()
    }

    fun tryStart(restart: Boolean = false): Boolean {
        if (this.started) {
            throw IllegalStateException("Cannot start recording after already started!")
        }
        if (this.start()) {
            this.logStart(restart)
            return true
        }
        return false
    }

    @JvmOverloads
    fun logStart(restart: Boolean = false) {
        this.broadcastToOpsAndConsole("${if (restart) "Restarted" else "Started"} replay for ${this.getName()}")
    }

    @JvmOverloads
    fun stop(save: Boolean = true): CompletableFuture<Long> {
        if (this.stopped) {
            return CompletableFuture.failedFuture(IllegalStateException("Cannot stop replay after already stopped"))
        }

        if (ServerReplay.config.debug) {
            this.broadcastToOpsAndConsole("Replay ${this.getName()} Debug Packet Data:\n${this.getDebugPacketData()}")
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

    fun getCompressedRecordingSize(force: Boolean = false): CompletableFuture<Long> {
        val current = this.currentSizeFuture
        if (current != null) {
            return current
        }

        if (!force && !this.shouldRecalculateFileSize()) {
            return CompletableFuture.completedFuture(this.lastCompressedSize)
        }

        // This will block the executor thread from recording packets
        // until it has duplicated all of its files (so we can access them async)
        val future = CompletableFuture.supplyAsync {
            val recordingTime = this.getTotalRecordingTime()
            this.startTimeOfLastSize = System.currentTimeMillis()
            val compressed = this.replay.getCompressedFileSize(this.executor)
            // Update our check if this is called elsewhere
            this.server.execute {
                this.checkFileSize(compressed, this.lastCompressedSize, this.endTimeOfLastSize, recordingTime)
            }
            this.lastRawSize = this.getRawRecordingSize()
            this.lastCompressedSize = compressed
            this.endTimeOfLastSize = System.currentTimeMillis()
            this.currentSizeFuture = null
            compressed
        }
        this.currentSizeFuture = future
        return future
    }

    fun getStatusWithSize(): CompletableFuture<String> {
        val builder = ToStringBuilder(this, StandardToStringStyle().apply {
            fieldSeparator = ", "
            fieldNameValueSeparator = " = "
            isUseClassName = false
            isUseIdentityHashCode = false
        })
        val seconds = this.getTotalRecordingTime() / 1000
        val hours = seconds / 3600
        val minutes = seconds % 3600 / 60
        val secs = seconds % 60
        val time = "%02d:%02d:%02d".format(hours, minutes, secs)
        builder.append("name", this.getName())
        builder.append("time", time)

        this.appendToStatus(builder)

        builder.append("raw_size", FileUtils.formatSize(this.getRawRecordingSize()))
        if (ServerReplay.config.includeCompressedReplaySizeInStatus) {
            val compressed = this.getCompressedRecordingSize()
            return compressed.thenApply {
                "${builder.append("compressed_size", FileUtils.formatSize(it))}"
            }
        }
        return CompletableFuture.completedFuture(builder.toString())
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

    protected open fun appendToStatus(builder: ToStringBuilder) {
        val duration = this.nextFileCheckTime - System.currentTimeMillis()
        if (duration > 0) {
            builder.append("next_size_check", duration.milliseconds.toString())
        }
    }

    protected open fun addMetadata(map: MutableMap<String, JsonElement>) {
        map["name"] = JsonPrimitive(this.getName())
        map["settings"] = ReplayConfig.toJson(ServerReplay.config)
        map["location"] = JsonPrimitive(this.location.pathString)
        map["time"] = JsonPrimitive(System.currentTimeMillis())

        map["start_of_last_file_check"] = JsonPrimitive(this.startTimeOfLastSize)
        map["end_of_last_file_check"] = JsonPrimitive(this.endTimeOfLastSize)
        map["last_raw_size"] = JsonPrimitive(this.lastRawSize)
        map["last_compressed_size"] = JsonPrimitive(this.lastCompressedSize)
        map["next_file_check"] = JsonPrimitive(this.nextFileCheckTime)
    }

    abstract fun getName(): String

    protected abstract fun start(): Boolean

    protected abstract fun restart(): Boolean

    protected abstract fun closed(future: CompletableFuture<Long>)

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

    protected open fun postPacket(packet: MinecraftPacket<*>) {

    }

    private fun checkDuration() {
        val maxDuration = ServerReplay.config.maxDuration
        if (!maxDuration.isPositive()) {
            return
        }

        if (this.getTimestamp().milliseconds > maxDuration) {
            this.stop(true)
            this.broadcastToOpsAndConsole(
                "Stopped recording replay for ${this.getName()}, past duration limit ${maxDuration}!"
            )
            if (ServerReplay.config.restartAfterMaxDuration && this.canContinueRecording()) {
                this.restart()
            }
        }
    }

    private fun shouldRecalculateFileSize(): Boolean {
        val increase = this.getRawRecordingSize() / this.lastRawSize.toDouble()
        // We've recorded an extra 10% of our previous raw size
        if (increase > 1.1) {
            if (ServerReplay.config.debug) {
                ServerReplay.logger.info("Recalculating file size, file ratio: $increase")
            }
            return true
        }

        val now = System.currentTimeMillis()
        val lastTimeTaken = this.endTimeOfLastSize - this.startTimeOfLastSize
        if (this.endTimeOfLastSize + lastTimeTaken * 0.75 > now) {
            // It's been a while since we last recalculated
            if (ServerReplay.config.debug) {
                ServerReplay.logger.info("Recalculating file size, last check was at ${this.endTimeOfLastSize}ms")
            }
            return true
        }
        return false
    }

    private fun calculateAndCheckFileSize() {
        val maxFileSize = ServerReplay.config.maxFileSize
        if (maxFileSize.bytes <= 0 || this.isCheckingSize) {
            return
        }

        if (System.currentTimeMillis() < this.nextFileCheckTime) {
            val increase = this.getRawRecordingSize() / this.lastRawSize.toDouble()
            // If there's a very significant raw increase, then we should probably check
            if (increase < 1.4 || this.getTotalRecordingTime() < DEFAULT_FILE_CHECK_TIME_MS) {
                return
            }
        }

        // We don't want to do multiple concurrent checks, one is enough
        this.isCheckingSize = true
        this.getCompressedRecordingSize(true).thenRunAsync({
            this.isCheckingSize = false
            // We implicitly call #checkFileSize by compressing the file
        }, this.server)
    }

    private fun checkFileSize(
        compressed: Long,
        previousCompressed: Long,
        previousEndTime: Long,
        totalRecordingTime: Long
    ) {
        val maxFileSize = ServerReplay.config.maxFileSize
        if (maxFileSize.bytes <= 0) {
            return
        }

        if (compressed > maxFileSize.bytes) {
            this.stop(true)
            this.broadcastToOpsAndConsole(
                "Stopped recording replay for ${this.getName()}, over max file size ${maxFileSize.raw}!"
            )
            if (ServerReplay.config.restartAfterMaxFileSize && this.canContinueRecording()) {
                this.restart()
            }
        } else {
            // The bytes per ms for the entire recording duration
            val lDelta = compressed / totalRecordingTime.toDouble()
            // The bytes per ms since the previous compression time
            val sDelta = (compressed - previousCompressed) / (this.startTimeOfLastSize - previousEndTime).toDouble()
            val remaining = maxFileSize.bytes - compressed

            // We average out the deltas and multiply by 1.5 to account for fluctuations
            val estimatedDelta = (lDelta + sDelta) * 0.75

            val estimatedTime = max((remaining / estimatedDelta).toLong(), DEFAULT_FILE_CHECK_TIME_MS)
            this.nextFileCheckTime = this.startTimeOfLastSize + estimatedTime
            if (ServerReplay.config.debug) {
                val timeUntilNextCheck = (this.nextFileCheckTime - System.currentTimeMillis()).milliseconds.toString()
                ServerReplay.logger.info(
                    "Checked compress filesize to be ${FileUtils.formatSize(compressed)}, checking next file size in $timeUntilNextCheck"
                )
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

                var additional = ""
                if (save) {
                    this.broadcastToOpsAndConsole("Starting to save replay ${this.getName()}, please do not stop the server!")

                    this.replay.saveTo(path.toFile())
                    size = path.fileSize()
                    additional = " and saved to $path, compressed to ${FileUtils.formatSize(size)}"
                }

                try {
                    val caches = this.location.parent.resolve(this.location.name + ".cache")
                    @OptIn(ExperimentalPathApi::class)
                    caches.deleteRecursively()
                } catch (e: IOException) {
                    ServerReplay.logger.error("Failed to delete caches", e)
                }

                this.replay.close()
                this.broadcastToOpsAndConsole("Successfully closed replay ${this.getName()}$additional")
            } catch (exception: Exception) {
                val message = "Failed to write replay ${this.getName()}"
                this.broadcastToOps(message)
                ServerReplay.logger.error(message, exception)
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

            this.replay.write(ENTRY_SERVER_REPLAY_META).use {
                val json = HashMap<String, JsonElement>()
                this.addMetadata(json)
                @OptIn(ExperimentalSerializationApi::class)
                Json.encodeToStream(json, it)
            }
        }
    }

    private fun recording(): Path {
        return this.location.parent.resolve(this.location.name + ".mcpr")
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
                if (!this.writeResourcePack(bytes, packet.hash, requestId)) {
                    ServerReplay.logger.error("Resource pack hashes do not match! Pack '${packet.url}' will not be loaded...")
                }
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
        if (expectedHash == "" || expectedHash == packHash) {
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

    private fun broadcastToOps(message: String) {
        if (!ServerReplay.config.notifyAdminsOfStatus) {
            return
        }
        this.server.execute {
            val players = this.server.playerList.players
            val component = Component.literal(message)
            for (player in players) {
                if (this.server.playerList.isOp(player.gameProfile)) {
                    player.sendSystemMessage(component)
                }
            }
        }
    }

    private fun broadcastToOpsAndConsole(message: String) {
        this.broadcastToOps(message)
        ServerReplay.logger.info(message)
    }

    companion object {
        private const val ENTRY_SERVER_REPLAY_META = "server_replay_meta.json"
        private const val DEFAULT_FILE_CHECK_TIME_MS = 30_000L

        private fun MinecraftPacket<*>.getDebugName(): String {
            return if (this is ClientboundCustomPayloadPacket) {
                "CustomPayload(${this.payload.id()})"
            } else {
                this::class.java.simpleName
            }
        }
    }
}