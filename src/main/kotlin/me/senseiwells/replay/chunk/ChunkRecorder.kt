package me.senseiwells.replay.chunk

import it.unimi.dsi.fastutil.ints.IntArraySet
import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import me.senseiwells.replay.ServerReplay
import me.senseiwells.replay.api.ServerReplayPluginManager
import me.senseiwells.replay.compat.polymer.PolymerPacketPatcher
import me.senseiwells.replay.mixin.chunk.WitherBossAccessor
import me.senseiwells.replay.mixin.rejoin.ChunkMapAccessor
import me.senseiwells.replay.player.PlayerRecorder
import me.senseiwells.replay.recorder.ChunkSender
import me.senseiwells.replay.recorder.ChunkSender.WrappedTrackedEntity
import me.senseiwells.replay.recorder.ReplayRecorder
import me.senseiwells.replay.rejoin.RejoinedReplayPlayer
import me.senseiwells.replay.util.ClientboundAddEntityPacket
import net.minecraft.core.UUIDUtil
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientboundSetChunkCacheRadiusPacket
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket
import net.minecraft.server.level.ClientInformation
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.boss.wither.WitherBoss
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.chunk.LevelChunk
import net.minecraft.world.level.levelgen.Heightmap
import net.minecraft.world.phys.Vec2
import net.minecraft.world.phys.Vec3
import org.apache.commons.lang3.builder.ToStringBuilder
import org.jetbrains.annotations.ApiStatus.Internal
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.function.Consumer
import kotlin.io.path.nameWithoutExtension

/**
 * An implementation of [ReplayRecorder] for recording chunk areas.
 *
 * This can be created when running the `/replay` command.
 *
 * @param chunks The [ChunkArea] to record.
 * @param recorderName The name of the [ChunkRecorder].
 * @param recordings The chunks recordings directory.
 * @see PlayerRecorder
 * @see ChunkRecorder
 */
class ChunkRecorder internal constructor(
    val chunks: ChunkArea,
    val recorderName: String,
    recordings: Path
): ReplayRecorder(chunks.level.server, PROFILE, recordings), ChunkSender {
    private val dummy by lazy {
        val player = ServerPlayer(this.server, this.chunks.level, PROFILE, ClientInformation.createDefault())
        ChunkGamePacketPacketListener(this, player)
        player
    }

    private val loadedChunks = LongOpenHashSet()
    private val sentChunks = LongOpenHashSet()

    private val recordables = HashSet<ChunkRecordable>()

    private var totalPausedTime: Long = 0
    private var lastPaused: Long = 0

    /**
     * The level that the chunk recording is currently in.
     */
    override val level: ServerLevel
        get() = this.chunks.level

    /**
     * The current position of the recorder.
     */
    override val position: Vec3
        get() = this.dummy.position()

    /**
     * The current rotation of the recorder.
     */
    override val rotation: Vec2
        get() = Vec2.ZERO

    override fun record(outgoing: Packet<*>) {
        super.record(PolymerPacketPatcher.replace(this.dummy.connection, outgoing))
    }

    /**
     * This gets the name of the replay recording.
     *
     * @return The name of the replay recording.
     */
    override fun getName(): String {
        return this.recorderName
    }

    /**
     * This starts the replay recording, it sends all the chunk and
     * entity packets as if a player were logging into the server.
     *
     * This method should just simulate
     */
    override fun initialize(): Boolean {
        val center = this.getCenterChunk()
        // Load the chunk
        this.level.getChunk(center.x, center.z)

        val x = center.middleBlockX
        val z = center.middleBlockZ
        val y = this.level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z)
        this.dummy.setPosRaw(x.toDouble(), y + 10.0, z.toDouble())
        this.dummy.setServerLevel(this.level)
        this.dummy.isInvisible = true

        RejoinedReplayPlayer.rejoin(this.dummy, this)
        this.spawnPlayer()
        this.sendChunksAndEntities()
        ServerReplayPluginManager.startReplay(this)

        val chunks = this.level.chunkSource.chunkMap as ChunkMapAccessor
        for (pos in this.chunks) {
            val holder = chunks.getTickingChunk(pos.toLong())
            if (holder != null) {
                (holder as ChunkRecordable).addRecorder(this)
            }
        }

        return true
    }

    /**
     * This method tries to restart the replay recorder by creating
     * a new instance of itself.
     *
     * @return Whether it successfully restarted.
     */
    override fun restart(): Boolean {
        val recorder = ChunkRecorders.create(this.chunks, this.recorderName)
        return recorder.start(true)
    }

    /**
     * This gets called when the replay is closing. It removes all [ChunkRecordable]s
     * and updates the [ChunkRecorders] manager.
     *
     * @param future The future that will complete once the replay has closed.
     */
    override fun onClosing(future: CompletableFuture<Long>) {
        for (recordable in ArrayList(this.recordables)) {
            recordable.removeRecorder(this)
        }

        if (this.recordables.isNotEmpty()) {
            ServerReplay.logger.warn("Failed to unlink all chunk recordables")
        }

        ChunkRecorders.close(this.server, this, future)
    }

    /**
     * This gets the viewing command for this replay for after it's saved.
     *
     * @return The command to view this replay.
     */
    override fun getViewingCommand(): String {
        return "/replay view chunks \"${this.recorderName}\" \"${this.location.nameWithoutExtension}\""
    }

    /**
     * This gets the current timestamp (in milliseconds) of the replay recording.
     * This subtracts the amount of time paused from the total recording time.
     *
     * @return The timestamp of the recording (in milliseconds).
     */
    override fun getTimestamp(): Long {
        return super.getTimestamp() - this.totalPausedTime - this.getCurrentPause()
    }

    /**
     * Returns whether a given player should be hidden from the player tab list.
     *
     * @return Whether the player should be hidden
     */
    override fun shouldHidePlayerFromTabList(player: ServerPlayer): Boolean {
        return this.dummy == player
    }

    /**
     * This appends any additional data to the status.
     *
     * @param builder The [ToStringBuilder] which is used to build the status.
     * @see getStatusWithSize
     */
    override fun appendToStatus(builder: ToStringBuilder) {
        builder.append("chunks_world", this.chunks.level.dimension().location())
        builder.append("chunks_from", this.chunks.from)
        builder.append("chunks_to", this.chunks.to)
    }

    /**
     * This allows you to add any additional metadata which will be
     * saved in the replay file.
     *
     * @param map The JSON metadata map which can be mutated.
     */
    override fun addMetadata(map: MutableMap<String, JsonElement>) {
        super.addMetadata(map)
        map["chunks_world"] = JsonPrimitive(this.chunks.level.dimension().location().toString())
        map["chunks_from"] = JsonPrimitive(this.chunks.from.toString())
        map["chunks_to"] = JsonPrimitive(this.chunks.to.toString())
        map["paused_time"] = JsonPrimitive(this.totalPausedTime)
    }

    /**
     * This gets the center chunk position of the chunk recording.
     *
     * @return The center most chunk position.
     */
    override fun getCenterChunk(): ChunkPos {
        return this.chunks.center
    }

    /**
     * This will iterate over every chunk position that is going
     * to be sent, each chunk position will be accepted into the
     * [consumer].
     *
     * @param consumer The consumer that will accept the given chunks positions.
     */
    override fun forEachChunk(consumer: Consumer<ChunkPos>) {
        val radius = ServerReplay.config.chunkRecorderLoadRadius
        if (radius < 0) {
            this.chunks.forEach(consumer)
            return
        }

        ChunkPos.rangeClosed(this.chunks.center, radius + 1).filter {
            this.chunks.contains(this.level.dimension(), it)
        }.forEach(consumer)
    }

    /**
     * This determines whether a given [entity] should be tracked.
     *
     * @param entity The entity to check.
     * @param range The entity's tracking range.
     * @return Whether the entity should be tracked.
     */
    override fun shouldTrackEntity(entity: Entity, range: Double): Boolean {
        return this.chunks.contains(entity.level().dimension(), entity.chunkPosition())
    }

    /**
     * This records a packet.
     *
     * @param packet The packet to be recorded.
     */
    override fun sendChunkPacket(packet: Packet<*>) {
        this.record(packet)
    }

    /**
     * This is called when [shouldTrackEntity] returns `true`,
     * this should be used to send any additional packets for this entity.
     *
     * @param tracked The [WrappedTrackedEntity].
     */
    override fun addTrackedEntity(tracked: WrappedTrackedEntity) {
        (tracked.tracked as ChunkRecordable).addRecorder(this)
    }

    /**
     * This gets the view distance of the chunk area.
     *
     * @return The view distance of the chunk area.
     */
    override fun getViewDistance(): Int {
        return this.chunks.viewDistance
    }

    override fun onChunkSent(chunk: LevelChunk) {
        this.sentChunks.add(chunk.pos.toLong())
    }

    /**
     * Determines whether a given packet is able to be recorded.
     *
     * @param packet The packet that is going to be recorded.
     * @return Whether this recorded should record it.
     */
    override fun canRecordPacket(packet: Packet<*>): Boolean {
        // If the server view-distance changes we do not want to update
        // the client - this will cut the view distance in the replay
        if (packet is ClientboundSetChunkCacheRadiusPacket) {
            return packet.radius == this.getViewDistance()
        }
        return super.canRecordPacket(packet)
    }

    /**
     * This gets the dummy chunk recording player.
     *
     * **This is *not* a real player, and many operations on this instance
     * may cause crashes, be very careful with how you use this.**
     *
     * @return The dummy chunk recording player.
     */
    fun getDummyPlayer(): ServerPlayer {
        return this.dummy
    }

    @Internal
    fun addRecordable(recordable: ChunkRecordable) {
        this.recordables.add(recordable)
    }

    @Internal
    fun removeRecordable(recordable: ChunkRecordable) {
        this.recordables.remove(recordable)
    }

    @Internal
    fun onEntityTracked(entity: Entity) {
        if (entity is WitherBoss) {
            val recordable = ((entity as WitherBossAccessor).bossEvent as ChunkRecordable)
            recordable.addRecorder(this)
        }
    }

    @Internal
    fun onEntityUntracked(entity: Entity) {
        if (entity is WitherBoss) {
            val recordable = ((entity as WitherBossAccessor).bossEvent as ChunkRecordable)
            recordable.removeRecorder(this)
        }
    }

    @Internal
    fun onChunkLoaded(chunk: LevelChunk) {
        if (!this.chunks.contains(chunk.level.dimension(), chunk.pos)) {
            ServerReplay.logger.error("Tried to load chunk out of bounds!")
            return
        }

        this.resume()
        this.loadedChunks.add(chunk.pos.toLong())

        if (!this.sentChunks.contains(chunk.pos.toLong())) {
            this.sendChunk(this.level.chunkSource.chunkMap, chunk, IntArraySet())
        }
    }

    @Internal
    fun onChunkUnloaded(pos: ChunkPos) {
        if (!this.chunks.contains(this.level.dimension(), pos)) {
            ServerReplay.logger.error("Tried to unload chunk out of bounds!")
            return
        }

        this.loadedChunks.remove(pos.toLong())

        if (this.loadedChunks.isEmpty()) {
            this.pause()
        }
    }

    private fun spawnPlayer() {
        this.record(ClientboundAddEntityPacket(this.dummy))
        val tracked = this.dummy.entityData.nonDefaultValues
        if (tracked != null) {
            this.record(ClientboundSetEntityDataPacket(this.dummy.id, tracked))
        }
    }

    private fun pause() {
        if (!this.paused() && ServerReplay.config.skipWhenChunksUnloaded) {
            this.lastPaused = System.currentTimeMillis()

            if (ServerReplay.config.notifyPlayersLoadingChunks) {
                this.ignore {
                    this.server.playerList.broadcastSystemMessage(
                        Component.literal("Paused recording for ${this.getName()}, chunks were unloaded"),
                        false
                    )
                }
            }
        }
    }

    private fun resume() {
        if (this.paused()) {
            this.totalPausedTime += this.getCurrentPause()
            this.lastPaused = 0L

            if (ServerReplay.config.notifyPlayersLoadingChunks) {
                this.ignore {
                    this.server.playerList.broadcastSystemMessage(
                        Component.literal("Resumed recording for ${this.getName()}, chunks were loaded"),
                        false
                    )
                }
            }
        }
    }

    private fun getCurrentPause(): Long {
        if (this.paused()) {
            return System.currentTimeMillis() - this.lastPaused
        }
        return 0L
    }

    private fun paused(): Boolean {
        return this.lastPaused != 0L
    }

    companion object {
        private val PROFILE = UUIDUtil.createOfflineProfile("-ChunkRecorder-")
    }
}