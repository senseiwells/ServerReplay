package me.senseiwells.replay.chunk

import me.senseiwells.replay.recorder.ChunkSender
import me.senseiwells.replay.recorder.ReplayRecorder
import me.senseiwells.replay.rejoin.RejoinedReplayPlayer
import net.minecraft.core.UUIDUtil
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket
import net.minecraft.server.level.ChunkMap.TrackedEntity
import net.minecraft.server.level.ClientInformation
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.levelgen.Heightmap
import net.minecraft.world.phys.Vec3
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.function.Consumer

// TODO:
//  - When everything is unloaded in the chunk area then we skip forward...
//  - What happens when chunks are initially loaded?
class ChunkRecorder internal constructor(
    val chunks: ChunkArea,
    val recorderName: String,
    recordings: Path
): ReplayRecorder(chunks.level.server, PROFILE,recordings), ChunkSender {
    private val dummy = ServerPlayer(this.server, this.chunks.level, PROFILE, ClientInformation.createDefault())

    override val level: ServerLevel
        get() = this.chunks.level

    override fun getName(): String {
        return this.recorderName
    }

    override fun start(): Boolean {
        // We load all the chunks to ensure we can
        // correctly record the initial chunks.
        for (pos in this.chunks) {
            this.level.chunkSource.addRegionTicket(ChunkRecorders.RECORDING_TICKET, pos, 1, pos)
        }

        val center = this.getCenterChunk()
        val x = center.middleBlockX
        val z = center.middleBlockZ
        val y = this.level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z)
        this.dummy.setPosRaw(x.toDouble(), y + 10.0, z.toDouble())
        this.dummy.setServerLevel(this.level)
        this.dummy.isInvisible = true

        RejoinedReplayPlayer.rejoin(this.dummy, this)
        this.sendChunksAndEntities()
        return true
    }

    override fun restart(): Boolean {
        val recorder = ChunkRecorders.create(this.chunks, this.recorderName)
        return recorder.tryStart(false)
    }

    override fun closed(future: CompletableFuture<Long>) {
        ChunkRecorders.close(this.server, this.chunks, future)
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
        (tracking as ChunkRecorderTrackedEntity).addRecorder(this)
    }

    companion object {
        private val PROFILE = UUIDUtil.createOfflineProfile("ChunkRecorder")
    }
}