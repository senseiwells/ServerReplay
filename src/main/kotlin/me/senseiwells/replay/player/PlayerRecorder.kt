package me.senseiwells.replay.player

import com.mojang.authlib.GameProfile
import me.senseiwells.replay.mixin.rejoin.TrackedEntityAccessor
import me.senseiwells.replay.recorder.ChunkSender
import me.senseiwells.replay.recorder.ReplayRecorder
import me.senseiwells.replay.rejoin.RejoinedReplayPlayer
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientGamePacketListener
import net.minecraft.network.protocol.game.ClientboundBundlePacket
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.*
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.ChunkPos
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.function.Consumer

class PlayerRecorder internal constructor(
    server: MinecraftServer,
    profile: GameProfile,
    recordings: Path,
): ReplayRecorder(server, profile, recordings), ChunkSender {
    private val player: ServerPlayer?
        get() = this.server.playerList.getPlayer(this.recordingPlayerUUID)

    override val level: ServerLevel
        get() = this.getPlayerOrThrow().serverLevel()

    fun getPlayerOrThrow(): ServerPlayer {
        return this.player ?: throw IllegalStateException("Tried to get player before player joined")
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
        return recorder.tryStart(true)
    }

    override fun closed(future: CompletableFuture<Long>) {
        PlayerRecorders.close(this.server, this, future)
    }

    fun spawnPlayer(player: ServerEntity) {
        val list = ArrayList<Packet<ClientGamePacketListener>>()
        // The player parameter is never used, we can just pass in null
        @Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
        player.sendPairingData(null, list::add)
        this.record(ClientboundBundlePacket(list))
    }

    fun removePlayer(player: ServerPlayer) {
        this.record(ClientboundRemoveEntitiesPacket(player.id))
    }

    override fun canContinueRecording(): Boolean {
        return this.player != null
    }

    override fun getCenterChunk(): ChunkPos {
        return this.getPlayerOrThrow().chunkPosition()
    }

    override fun forEachChunk(consumer: Consumer<ChunkPos>) {
        ChunkTrackingView.of(this.getCenterChunk(), this.server.playerList.viewDistance).forEach(consumer)
    }

    override fun sendPacket(packet: Packet<*>) {
        this.record(packet)
    }

    override fun isValidEntity(entity: Entity): Boolean {
        return true
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

    override fun addTrackedEntity(tracking: ChunkMap.TrackedEntity) {
        val list = ArrayList<Packet<ClientGamePacketListener>>()
        (tracking as TrackedEntityAccessor).serverEntity.sendPairingData(this.getPlayerOrThrow(), list::add)
        this.record(ClientboundBundlePacket(list))
    }
}