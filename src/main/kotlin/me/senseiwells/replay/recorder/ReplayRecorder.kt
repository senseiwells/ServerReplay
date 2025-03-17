package me.senseiwells.replay.recorder

import com.mojang.authlib.GameProfile
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import me.senseiwells.replay.ServerReplay
import me.senseiwells.replay.api.network.RecordablePayload
import me.senseiwells.replay.chunk.ChunkRecorder
import me.senseiwells.replay.config.ReplayConfig
import me.senseiwells.replay.player.PlayerRecorder
import me.senseiwells.replay.saver.ReplaySaver
import me.senseiwells.replay.saver.ReplaySaver.Companion.broadcastToOpsAndConsole
import me.senseiwells.replay.util.DebugPacketData
import me.senseiwells.replay.util.FileUtils
import me.senseiwells.replay.util.ReplayOptimizerUtils
import me.senseiwells.replay.util.getDebugName
import net.minecraft.network.ConnectionProtocol
import net.minecraft.network.ProtocolInfo
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket
import net.minecraft.network.protocol.configuration.ConfigurationProtocols
import net.minecraft.network.protocol.game.ClientboundBundlePacket
import net.minecraft.network.protocol.game.GameProtocols
import net.minecraft.network.protocol.login.ClientboundLoginFinishedPacket
import net.minecraft.network.protocol.login.LoginProtocols
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.phys.Vec2
import net.minecraft.world.phys.Vec3
import org.apache.commons.lang3.builder.StandardToStringStyle
import org.apache.commons.lang3.builder.ToStringBuilder
import org.jetbrains.annotations.ApiStatus.Internal
import java.nio.file.Path
import java.util.*
import java.util.concurrent.CompletableFuture
import kotlin.io.path.pathString
import kotlin.time.Duration.Companion.milliseconds

/**
 * This is the abstract class representing a replay recorder.
 *
 * This class is responsible for starting, stopping, and saving
 * the replay files as well as recording all the packets.
 *
 * @param server The [MinecraftServer] instance.
 * @param profile The profile of the player being recorded.
 * @see PlayerRecorder
 * @see ChunkRecorder
 */
abstract class ReplayRecorder(
    val server: MinecraftServer,
    val profile: GameProfile,
    provider: (ReplayRecorder) -> ReplaySaver
) {
    private val packets by lazy { Object2ObjectOpenHashMap<String, DebugPacketData>() }

    private var start: Long = 0

    private var protocol: ProtocolInfo<*> = LoginProtocols.CLIENTBOUND
    private var lastPacket = 0L

    internal var started = false
        private set

    private var ignore = false

    @Suppress("LeakingThis")
    protected val saver = provider.invoke(this)

    /**
     * The directory at which all the temporary replay
     * files will be stored.
     * This also determines the final location of the replay file.
     */
    val location: Path
        get() = this.saver.path

    /**
     * Whether the replay recorder has stopped and
     * is no longer recording any packets.
     */
    val stopped: Boolean
        get() = this.saver.closed

    /**
     * Whether the recorder is currently paused
     */
    open val paused: Boolean
        get() = false

    /**
     * The [UUID] of the player the recording is of.
     */
    val recordingPlayerUUID: UUID
        get() = this.profile.id

    /**
     * The level that the replay recording is currently in.
     */
    abstract val level: ServerLevel

    /**
     * The current position of the recorder.
     */
    abstract val position: Vec3

    /**
     * The current rotation of the recorder.
     */
    abstract val rotation: Vec2

    /**
     * This records an outgoing clientbound packet to the
     * replay file.
     *
     * This method will throw an exception if the recorder
     * has not started recording yet.
     *
     * This method **is** thread-safe; however, it should be noted
     * that any packet optimizations cannot be done if called off
     * the main thread, therefore only calling this method on
     * the main thread is preferable.
     *
     * @param outgoing The outgoing [Packet].
     */
    open fun record(outgoing: Packet<*>) {
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

        if (ReplayOptimizerUtils.shouldIgnorePacket(this, outgoing)) {
            return
        }

        if (outgoing is ClientboundBundlePacket) {
            for (sub in outgoing.subPackets()) {
                this.record(sub)
            }
            return
        }
        if (this.saver.prePacketRecord(outgoing)) {
            return
        }
        if (!this.canRecordPacket(outgoing)) {
            return
        }

        val protocol = this.protocol
        val timestamp = this.getTimestamp()
        this.lastPacket = timestamp

        this.saver.writePacket(outgoing, protocol, timestamp, !safe).thenApply { bytes ->
            if (ServerReplay.config.debug && bytes != null) {
                val type = outgoing.getDebugName()
                this.packets.getOrPut(type) { DebugPacketData(type, 0, 0) }.increment(bytes)
            }
        }

        this.saver.postPacketRecord(outgoing)
        this.checkDuration()
    }

    /**
     * This tries to start this replay recorder and returns
     * whether it was successful in doing so.
     *
     * @param restart Whether this is restarting a previous recording, `false` by default.
     * @return `true` if the recording started successfully `false` otherwise.
     */
    fun start(restart: Boolean = false): Boolean {
        if (!this.started && this.initialize()) {
            this.logStart(restart)
            return true
        }

        return false
    }

    /**
     * Logs that the replay has started/restarted to console and operators.
     *
     * @param restart Whether the log should state `"restarted"` or `"started"`.
     */
    @JvmOverloads
    fun logStart(restart: Boolean = false) {
        this.saver.broadcastToOpsAndConsole("${if (restart) "Restarted" else "Started"} replay for ${this.getName()}")
        ServerReplay.warnDeprecatedConfig(this)
    }

    /**
     * Stops the replay recorder and returns a future which will be completed
     * when the file has completed saving or closing.
     *
     * A failed future will be returned if the replay is already stopped.
     *
     * @param save Whether the recorded replay should be saved to disk, `true` by default.
     * @return A future which will be completed after the recording has finished saving or
     *     closing, this completes with the file size of the final compressed replay in bytes.
     */
    @JvmOverloads
    fun stop(save: Boolean = true): CompletableFuture<Long> {
        if (this.stopped) {
            return CompletableFuture.failedFuture(IllegalStateException("Cannot stop replay after already stopped"))
        }

        if (ServerReplay.config.debug) {
            this.saver.broadcastToOpsAndConsole("Replay ${this.getName()} Debug Packet Data:\n${this.getDebugPacketData()}")
        }

        // We only save if the player has actually logged in...
        val future = this.saver.close(this.lastPacket.toInt(), save && this.protocol.id() == ConnectionProtocol.PLAY)
        this.onClosing(future)
        return future
    }

    /**
     * Adds a marker to the replay file which can be viewed in ReplayMod.
     *
     * @param name The name of the marker, null for unnamed.
     * @param position The marked position.
     * @param rotation The marked rotation.
     * @param timestamp The timestamp of the marker (milliseconds).
     */
    @JvmOverloads
    fun addMarker(
        name: String? = null,
        position: Vec3 = this.position,
        rotation: Vec2 = this.rotation,
        timestamp: Int = this.getTimestamp().toInt()
    ) {
        this.saver.writeMarker(name, position, rotation, timestamp)
    }

    /**
     * This returns the total amount of time (in milliseconds) that
     * has elapsed since the recording has started, this does not
     * account for any pauses.
     *
     * @return The total amount of time (in milliseconds) that has
     *     elapsed since the start of the recording.
     */
    fun getTotalRecordingTime(): Long {
        return System.currentTimeMillis() - this.start
    }

    /**
     * This returns the raw (uncompressed) file size of the replay in bytes.
     *
     * @return The raw file size of the replay in bytes.
     */
    fun getRawRecordingSize(): Long {
        return this.saver.getRawRecordingSize()
    }

    /**
     * This returns a future which will provide the compressed file
     * size of the replay in bytes.
     *
     * Be careful when calling this function - to calculate the compressed
     * file size, we must zip the entire raw replay which can be very
     * expensive.
     *
     * This will not always be accurate since if you do not force compress,
     * then it may return the last compressed size if it predicts that
     * the current size is likely very similar to the last size it calculated.
     * Further, these futures may take extremely long to complete (can be tens
     * of minutes, depending on the raw file size), and by the time the compression
     * is complete the replay size may have already changed significantly.
     *
     * @param force Whether to force compress (which yields a more up-to-date value), `false` by default.
     * @return A future which will complete after the compression is complete, providing the
     *     compressed file size in bytes.
     * @see getRawRecordingSize
     */
    @Deprecated("Getting the compressed recording size is computationally expensive")
    fun getCompressedRecordingSize(force: Boolean = false): CompletableFuture<Long> {
        @Suppress("DEPRECATION")
        return this.saver.getCompressedRecordingSize(force)
    }

    /**
     * This creates a future which will provide the status of the
     * replay recorder as a formatted string.
     * The status may include the compressed file size which is
     * this method provides a future, see [getCompressedRecordingSize].
     *
     * @return A future that will provide the status of the replay recorder.
     */
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
            @Suppress("DEPRECATION")
            val compressed = this.getCompressedRecordingSize()
            return compressed.thenApply {
                "${builder.append("compressed_size", FileUtils.formatSize(it))}"
            }
        }
        return CompletableFuture.completedFuture(builder.toString())
    }

    /**
     * This gets the current timestamp (in milliseconds) of the replay recording.
     *
     * By default, this is the same as [getTotalRecordingTime] however this
     * may be overridden to account for pauses in the replay.
     *
     * @return The timestamp of the recording (in milliseconds).
     */
    open fun getTimestamp(): Long {
        return this.getTotalRecordingTime()
    }

    /**
     * Returns whether a given player should be hidden from the player tab list.
     *
     * @return Whether the player should be hidden
     */
    open fun shouldHidePlayerFromTabList(player: ServerPlayer): Boolean {
        return false
    }

    /**
     * This allows you to add any additional metadata which will be
     * saved in the replay file.
     *
     * @param map The JSON metadata map which can be mutated.
     */
    open fun addMetadata(map: MutableMap<String, Any>) {
        map["name"] = this.getName()
        map["version"] = ServerReplay.version
        map["settings"] = ReplayConfig.toJson(ServerReplay.config.copy(replayServerIp = "hidden"))
        map["location"] = this.location.pathString
        map["time"] = System.currentTimeMillis()
        map["mods"] = ServerReplay.getLoadedMods()
    }

    protected fun spawnPlayer(player: ServerPlayer, packets: Collection<Packet<*>>) {
        this.saver.writePlayer(player, packets)
    }

    /**
     * This appends any additional data to the status.
     *
     * @param builder The [ToStringBuilder] which is used to build the status.
     * @see getStatusWithSize
     */
    protected open fun appendToStatus(builder: ToStringBuilder) {

    }

    /**
     * This method tries to restart the replay recorder by creating
     * a new instance of itself.
     *
     * @return Whether it successfully restarted.
     */
    abstract fun restart(): Boolean

    /**
     * This gets the name of the replay recording.
     *
     * @return The name of the replay recording.
     */
    abstract fun getName(): String

    /**
     * This gets the viewing command for this replay for after it's saved.
     *
     * @return The command to view this replay.
     */
    abstract fun getViewingCommand(): String

    /**
     * This starts the replay recording, note this is **not** called
     * to start a replay if a player is being recorded from the login phase.
     *
     * This method should just simulate
     */
    protected abstract fun initialize(): Boolean

    /**
     * This gets called when the replay is closing.
     *
     * @param future The future that will complete once the replay has closed.
     */
    protected abstract fun onClosing(future: CompletableFuture<Long>)

    /**
     * Determines whether a given packet is able to be recorded.
     *
     * @param packet The packet that is going to be recorded.
     * @return Whether this recorded should record it.
     */
    protected open fun canRecordPacket(packet: Packet<*>): Boolean {
        if (packet is ClientboundCustomPayloadPacket) {
            val payload = packet.payload
            if (payload is RecordablePayload && !payload.shouldRecord()) {
                return false
            }
        }
        return true
    }

    /**
     * Calling this ignores any packets that would've been
     * recorded by this recorder inside the [block] function.
     *
     * @param block The function to call while ignoring packets.
     */
    fun ignore(block: () -> Unit) {
        val previous = this.ignore
        try {
            this.ignore = true
            block()
        } finally {
            this.ignore = previous
        }
    }

    @Internal
    abstract fun takeSnapshot()

    @Internal
    fun tick() {
        this.saver.tick()
    }

    /**
     * This method formats all the debug packet data
     * into a string.
     *
     * @return The formatted debug packet data.
     */
    @Internal
    fun getDebugPacketData(): String {
        return this.packets.values
            .sortedByDescending { it.size }
            .joinToString(separator = "\n", transform = DebugPacketData::format)
    }

    /**
     * This method should be called after the player that is being
     * recorded has logged in.
     * This will mark the replay recorder as being started and will
     * change the replay recording phase into `CONFIGURATION`.
     */
    @Internal
    fun afterLogin() {
        if (!this.started) {
            this.started = true
            this.start = System.currentTimeMillis()
        }

        this.protocol = LoginProtocols.CLIENTBOUND
        // We will not have recorded this, so we need to do it manually.
        this.record(ClientboundLoginFinishedPacket(this.profile))

        this.protocol = ConfigurationProtocols.CLIENTBOUND
    }

    /**
     * This method should be called after the player has finished
     * their configuration phase, and this will mark the player
     * as playing the game - actually in the Minecraft world.
     */
    @Internal
    fun afterConfigure() {
        this.protocol = GameProtocols.CLIENTBOUND_TEMPLATE.bind(RegistryFriendlyByteBuf.decorator(this.server.registryAccess()))
    }

    private fun checkDuration() {
        val maxDuration = ServerReplay.config.maxDuration
        if (!maxDuration.isPositive()) {
            return
        }

        if (this.getTimestamp().milliseconds > maxDuration) {
            this.stop(true)
            this.saver.broadcastToOpsAndConsole(
                "Stopped recording replay for ${this.getName()}, past duration limit ${maxDuration}!"
            )
            if (ServerReplay.config.restartAfterMaxDuration) {
                this.restart()
            }
        }
    }
}