package me.senseiwells.replay.player

import com.mojang.authlib.GameProfile
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import me.senseiwells.replay.mixin.rejoin.ChunkMapAccessor
import me.senseiwells.replay.mixin.rejoin.TrackedEntityAccessor
import me.senseiwells.replay.recorder.ChunkSender
import me.senseiwells.replay.recorder.ReplayRecorder
import me.senseiwells.replay.rejoin.RejoinedReplayPlayer
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientGamePacketListener
import net.minecraft.network.protocol.game.ClientboundBundlePacket
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ChunkMap
import net.minecraft.server.level.ServerEntity
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
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
        get() = this.getPlayerOrThrow().getLevel()

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
        return recorder.tryStart(false)
    }

    override fun closed(future: CompletableFuture<Long>) {
        PlayerRecorders.close(this.server, this.recordingPlayerUUID, future)
    }

    override fun spawnPlayer() {
        val player = this.getPlayerOrThrow()
        if (!player.isRemoved) {
            this.spawnPlayer(this.getPlayerServerEntity())
        }
    }

    fun spawnPlayer(player: ServerEntity) {
        val list = ArrayList<Packet<ClientGamePacketListener>>()
        player.sendPairingData(list::add)
        this.record(ClientboundBundlePacket(list))
    }

    fun removePlayer(player: ServerPlayer) {
        this.record(ClientboundRemoveEntitiesPacket(player.id))
    }

    override fun addMetadata(map: MutableMap<String, JsonElement>) {
        super.addMetadata(map)
        map["player_name"] = JsonPrimitive(this.profile.name)
    }

    override fun canContinueRecording(): Boolean {
        return this.player != null
    }

    override fun getCenterChunk(): ChunkPos {
        return this.getPlayerOrThrow().chunkPosition()
    }

    override fun forEachChunk(consumer: Consumer<ChunkPos>) {
        val centerChunkX = this.getCenterChunk().x
        val centerChunkZ = this.getCenterChunk().z
        val viewDistance = this.server.playerList.viewDistance
        for (chunkX in centerChunkX - viewDistance - 1..centerChunkX + viewDistance + 1) {
            for (chunkZ in centerChunkZ - viewDistance - 1..centerChunkZ + viewDistance + 1) {
                if (ChunkMap.isChunkInRange(chunkX, chunkZ, centerChunkX, centerChunkZ, viewDistance)) {
                    consumer.accept(ChunkPos(chunkX, chunkZ))
                }
            }
        }
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

    override fun addTrackedEntity(tracking: ChunkMap.TrackedEntity) {
        val list = ArrayList<Packet<ClientGamePacketListener>>()
        (tracking as TrackedEntityAccessor).serverEntity.sendPairingData(this.getPlayerOrThrow(), list::add)
        this.record(ClientboundBundlePacket(list))
    }
}