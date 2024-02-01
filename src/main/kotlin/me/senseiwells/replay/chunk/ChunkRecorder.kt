package me.senseiwells.replay.chunk

import me.senseiwells.replay.ServerReplay
import me.senseiwells.replay.config.ReplayConfig
import me.senseiwells.replay.mixin.chunk.WitherBossAccessor
import me.senseiwells.replay.mixin.rejoin.ChunkMapAccessor
import me.senseiwells.replay.recorder.ChunkSender
import me.senseiwells.replay.recorder.ReplayRecorder
import me.senseiwells.replay.rejoin.RejoinedReplayPlayer
import net.minecraft.core.UUIDUtil
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket
import net.minecraft.network.protocol.game.ClientboundSetChunkCacheRadiusPacket
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket
import net.minecraft.server.level.ChunkMap.TrackedEntity
import net.minecraft.server.level.ClientInformation
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.boss.wither.WitherBoss
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.levelgen.Heightmap
import net.minecraft.world.phys.Vec3
import org.jetbrains.annotations.ApiStatus.Internal
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.function.Consumer

class ChunkRecorder internal constructor(
    val chunks: ChunkArea,
    val recorderName: String,
    recordings: Path
): ReplayRecorder(chunks.level.server, PROFILE,recordings), ChunkSender {
    private val dummy = ServerPlayer(this.server, this.chunks.level, PROFILE, ClientInformation.createDefault())

    private var totalPausedTime: Long = 0
    private var lastPaused: Long = 0

    private var loadedChunks = 0

    override val level: ServerLevel
        get() = this.chunks.level

    override fun getName(): String {
        return this.recorderName
    }

    override fun start(): Boolean {
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
        this.sendChunksAndEntities()

        val chunks = this.level.chunkSource.chunkMap as ChunkMapAccessor
        for (pos in this.chunks) {
            val holder = chunks.getTickingChunk(pos.toLong())
            if (holder != null) {
                (holder as ChunkRecordable).addRecorder(this)
            }
        }

        return true
    }

    override fun restart(): Boolean {
        val recorder = ChunkRecorders.create(this.chunks, this.recorderName)
        return recorder.tryStart(false)
    }

    override fun closed(future: CompletableFuture<Long>) {
        ChunkRecorders.close(this.server, this.chunks, future, this.getName())
    }

    override fun spawnPlayer() {
        val center = this.chunks.center
        this.record(ClientboundAddEntityPacket(
            this.dummy.id,
            this.dummy.uuid,
            center.middleBlockX.toDouble(),
            100.0,
            center.middleBlockZ.toDouble(),
            0.0F,
            0.0F,
            EntityType.PLAYER,
            0,
            Vec3.ZERO,
            0.0
        ))
        val tracked = this.dummy.entityData.nonDefaultValues
        if (tracked != null) {
            this.record(ClientboundSetEntityDataPacket(this.dummy.id, tracked))
        }
    }

    override fun getTimestamp(): Long {
        return super.getTimestamp() - this.totalPausedTime - this.getCurrentPause()
    }

    override fun canContinueRecording(): Boolean {
        return true
    }

    override fun getCenterChunk(): ChunkPos {
        return this.chunks.center
    }

    override fun forEachChunk(consumer: Consumer<ChunkPos>) {
        this.chunks.forEach(consumer)
    }

    override fun sendPacket(packet: Packet<*>) {
        this.record(packet)
    }

    override fun isValidEntity(entity: Entity): Boolean {
        return true
    }

    override fun shouldTrackEntity(tracking: Entity, range: Double): Boolean {
        return this.chunks.contains(tracking.chunkPosition())
    }

    override fun addTrackedEntity(tracking: TrackedEntity) {
        (tracking as ChunkRecordable).addRecorder(this)
    }

    override fun getViewDistance(): Int {
        return this.chunks.viewDistance
    }

    override fun canRecordPacket(packet: Packet<*>): Boolean {
        if (packet is ClientboundSetChunkCacheRadiusPacket) {
            return packet.radius == this.getViewDistance()
        }
        return super.canRecordPacket(packet)
    }

    @Internal
    fun onEntityTracked(entity: Entity) {
        if (entity is WitherBoss) {
            ((entity as WitherBossAccessor).bossEvent as ChunkRecordable).addRecorder(this)
        }
    }

    @Internal
    fun onEntityUntracked(entity: Entity) {
        if (entity is WitherBoss) {
            ((entity as WitherBossAccessor).bossEvent as ChunkRecordable).removeRecorder(this)
        }
    }

    @Internal
    fun incrementChunksLoaded() {
        this.loadedChunks++
        this.resume()
    }

    @Internal
    fun decrementChunksLoaded() {
        this.loadedChunks -= 1
        if (this.loadedChunks < 0) {
            ServerReplay.logger.error("Unloaded more chunks than was possible?")
            this.loadedChunks = 0
        }
        if (this.loadedChunks == 0) {
            this.pause()
        }
    }

    private fun pause() {
        if (!this.paused() && ReplayConfig.skipWhenChunksUnloaded) {
            this.lastPaused = System.currentTimeMillis()

            if (ReplayConfig.notifyPlayersLoadingChunks) {
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

            if (ReplayConfig.notifyPlayersLoadingChunks) {
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

    @Internal
    internal fun paused(): Boolean {
        return this.lastPaused != 0L
    }

    companion object {
        private val PROFILE = UUIDUtil.createOfflineProfile("-ChunkRecorder-")
    }
}