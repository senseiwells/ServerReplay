package me.senseiwells.replay.player

import com.mojang.authlib.GameProfile
import me.senseiwells.replay.recorder.ChunkSender
import me.senseiwells.replay.recorder.ReplayRecorder
import me.senseiwells.replay.rejoin.RejoinedReplayPlayer
import me.senseiwells.replay.util.LevelUtils.viewDistance
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ChunkTrackingView
import net.minecraft.server.level.ServerEntity
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.ChunkPos
import org.jetbrains.annotations.ApiStatus.Internal
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.function.Consumer

class PlayerRecorder internal constructor(
    server: MinecraftServer,
    profile: GameProfile,
    recordings: Path,
): ReplayRecorder(server, profile, recordings), ChunkSender {
    private val state: PlayerState

    init {
        this.state = PlayerState(this)
    }

    private val player: ServerPlayer?
        get() = this.server.playerList.getPlayer(this.recordingPlayerUUID)

    override val level: ServerLevel
        get() = this.getPlayerOrThrow().serverLevel()

    fun getPlayerOrThrow(): ServerPlayer {
        return this.player ?: throw IllegalStateException("Tried to get player before player joined")
    }

    @Internal
    fun tick(player: ServerPlayer) {
        this.state.tick(player)
    }

    override fun getName(): String {
        return this.profile.name
    }

    override fun start(): Boolean {
        val player = this.player ?: return false
        RejoinedReplayPlayer.rejoin(player, this)
        this.sendChunksAndEntities()
        return true
    }

    override fun restart(): Boolean {
        val recorder = PlayerRecorders.create(this.server, this.profile)
        return recorder.tryStart(false)
    }

    override fun closed(future: CompletableFuture<Long>) {
        PlayerRecorders.close(this.server, this.recordingPlayerUUID, future)
    }

    override fun spawnPlayer() {
        val player = this.getPlayerOrThrow()
        this.record(ClientboundAddEntityPacket(player))
        val tracked = player.entityData.nonDefaultValues
        if (tracked != null) {
            this.record(ClientboundSetEntityDataPacket(player.id, tracked))
        }
    }

    override fun canContinueRecording(): Boolean {
        return this.player != null
    }

    override fun getCenterChunk(): ChunkPos {
        return this.getPlayerOrThrow().chunkPosition()
    }

    override fun forEachChunk(consumer: Consumer<ChunkPos>) {
        ChunkTrackingView.of(this.getCenterChunk(), this.level.viewDistance).forEach(consumer)
    }

    override fun sendPacket(packet: Packet<*>) {
        this.record(packet)
    }

    override fun isValidEntity(entity: Entity): Boolean {
        return entity != this.player
    }

    override fun shouldTrackEntity(tracking: Entity, range: Double): Boolean {
        if (!super.shouldTrackEntity(tracking, range)) {
            return false
        }
        val player = this.getPlayerOrThrow()
        val delta = player.position().subtract(tracking.position())
        val deltaSqr = delta.x * delta.x + delta.z * delta.z
        val rangeSqr = range * range
        return deltaSqr <= rangeSqr && tracking.broadcastToPlayer(player)
    }

    override fun addTrackedEntity(tracking: ServerEntity) {
        tracking.addPairing(this.getPlayerOrThrow())
    }
}