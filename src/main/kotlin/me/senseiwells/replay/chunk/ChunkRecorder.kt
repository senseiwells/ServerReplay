package me.senseiwells.replay.chunk

import com.mojang.authlib.GameProfile
import me.senseiwells.replay.recorder.ChunkSender
import me.senseiwells.replay.recorder.ReplayRecorder
import me.senseiwells.replay.rejoin.RejoinedReplayPlayer
import net.minecraft.Util
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket
import net.minecraft.server.level.ClientInformation
import net.minecraft.server.level.ServerEntity
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.phys.Vec3
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.function.Consumer

class ChunkRecorder internal constructor(
    override val level: ServerLevel,
    val chunks: ChunkArea,
    val name: String,
    recordings: Path
): ReplayRecorder(level.server, PROFILE,recordings), ChunkSender {
    private val dummy = ServerPlayer(this.server, this.level, PROFILE, ClientInformation.createDefault())

    override fun getName(): String {
        return this.name
    }

    override fun start(): Boolean {
        RejoinedReplayPlayer.rejoin(this.dummy, this)
        this.sendChunksAndEntities()
        return true
    }

    override fun restart(): Boolean {
        val recorder = ChunkRecorders.create(this.level, this.chunks, this.name)
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

    override fun addTrackedEntity(tracking: ServerEntity) {
        // TODO:
    }

    companion object {
        private val PROFILE = GameProfile(Util.NIL_UUID, "ChunkRecorder")
    }
}