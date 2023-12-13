package me.senseiwells.replay.rejoin

import it.unimi.dsi.fastutil.ints.IntOpenHashSet
import it.unimi.dsi.fastutil.ints.IntSet
import me.senseiwells.replay.mixin.rejoin.ChunkMapAccessor
import me.senseiwells.replay.mixin.rejoin.TrackedEntityAccessor
import me.senseiwells.replay.player.PlayerRecorder
import me.senseiwells.replay.util.ducks.ChunkMapInvoker
import me.senseiwells.replay.util.ducks.TrackedEntityInvoker
import net.minecraft.core.SectionPos
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket
import net.minecraft.network.protocol.game.ClientboundSetChunkCacheCenterPacket
import net.minecraft.network.protocol.game.ClientboundSetEntityLinkPacket
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket
import net.minecraft.server.level.ChunkMap
import net.minecraft.server.level.ChunkMap.TrackedEntity
import net.minecraft.server.level.ChunkTrackingView
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.network.CommonListenerCookie
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.Mob
import net.minecraft.world.level.chunk.LevelChunk
import kotlin.math.min

class RejoinedReplayPlayer private constructor(
    val original: ServerPlayer,
    val recorder: PlayerRecorder
): ServerPlayer(original.server, original.serverLevel(), original.gameProfile, original.clientInformation()) {
    companion object {
        fun rejoin(player: ServerPlayer, recorder: PlayerRecorder) {
            recorder.afterLogin()

            val rejoined = RejoinedReplayPlayer(player, recorder)
            val connection = RejoinConnection()
            val cookies = CommonListenerCookie(player.gameProfile, 0, player.clientInformation())

            RejoinConfigurationPacketListener(rejoined, connection, cookies).startConfiguration()
            recorder.afterConfigure()
            rejoined.server.playerList.placeNewPlayer(connection, rejoined, cookies)

            val seen = IntOpenHashSet()
            // We have to manually re-send these packets
            rejoined.sendChunkUpdates(seen)
            rejoined.sendTrackedEntityUpdates(seen)
        }
    }

    init {
        this.id = this.original.id
    }

    private fun sendChunkUpdates(seen: IntSet) {
        val i = SectionPos.blockToSectionCoord(this.original.blockX)
        val j = SectionPos.blockToSectionCoord(this.original.blockZ)

        this.connection.send(ClientboundSetChunkCacheCenterPacket(i, j))

        val level = this.serverLevel()
        val chunks = level.chunkSource.chunkMap
        // We always send the max view distance possible...
        val view = (chunks as ChunkMapInvoker as ChunkMapAccessor).viewDistance

        ChunkTrackingView.of(this.original.chunkPosition(), view).forEach { pos ->
            val holder = chunks.getVisibleChunkIfExists(pos.toLong()) ?: return@forEach
            val chunk = holder.getTickingChunk()
            if (chunk != null) {
                this.sendChunkUpdate(chunks, chunk, seen)
            }
        }
    }

    private fun sendTrackedEntityUpdates(seen: IntSet) {
        val level = this.serverLevel()
        val chunks = level.chunkSource.chunkMap
        val entities = (chunks as ChunkMapAccessor).entityMap
        for (tracked in entities.values) {
            this.addTracked(tracked, chunks, seen)
        }
    }

    private fun sendChunkUpdate(chunks: ChunkMap, chunk: LevelChunk, seen: IntSet) {
        chunks as ChunkMapInvoker

        // We don't need to use the chunkSender
        // We are only writing the packets to disk...
        this.connection.send(ClientboundLevelChunkWithLightPacket(
            chunk,
            chunks.getLightEngine(),
            null,
            null
        ))

        val leashed = ArrayList<Mob>()
        val ridden = ArrayList<Entity>()

        for (tracked in (chunks as ChunkMapAccessor).entityMap.values) {
            val entity = (tracked as TrackedEntityAccessor).entity
            if (entity !== this.original && entity.chunkPosition() == chunk.pos) {
                this.addTracked(tracked, chunks, seen)
                if (entity is Mob && entity.getLeashHolder() != null) {
                    leashed.add(entity)
                }
                if (entity.passengers.isNotEmpty()) {
                    ridden.add(entity)
                }
            }
        }

        for (entity in leashed) {
            this.connection.send(ClientboundSetEntityLinkPacket(entity, entity.getLeashHolder()))
        }
        for (entity in ridden) {
            this.connection.send(ClientboundSetPassengersPacket(entity))
        }
    }

    private fun addTracked(tracked: TrackedEntity, chunks: ChunkMapAccessor, seen: IntSet) {
        val entity = (tracked as TrackedEntityAccessor).entity
        if (entity === this.original || seen.contains(entity.id)) {
            return
        }

        val delta = this.original.position().subtract(entity.position())
        val range = min((tracked as TrackedEntityInvoker).getRange(), (chunks.viewDistance * 16)).toDouble()
        val deltaSqr: Double = delta.x * delta.x + delta.z * delta.z
        val rangeSqr = range * range
        if (deltaSqr <= rangeSqr && entity.broadcastToPlayer(this.original)) {
            tracked.serverEntity.addPairing(this)
            seen.add(entity.id)
        }
    }
}