package me.senseiwells.replay.recorder

import it.unimi.dsi.fastutil.ints.IntOpenHashSet
import it.unimi.dsi.fastutil.ints.IntSet
import me.senseiwells.replay.mixin.rejoin.ChunkMapAccessor
import me.senseiwells.replay.mixin.rejoin.TrackedEntityAccessor
import me.senseiwells.replay.util.ducks.ChunkMapInvoker
import me.senseiwells.replay.util.ducks.TrackedEntityInvoker
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket
import net.minecraft.network.protocol.game.ClientboundSetChunkCacheCenterPacket
import net.minecraft.network.protocol.game.ClientboundSetEntityLinkPacket
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket
import net.minecraft.server.level.ChunkMap
import net.minecraft.server.level.ChunkMap.TrackedEntity
import net.minecraft.server.level.ServerEntity
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.Mob
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.chunk.LevelChunk
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.ApiStatus.NonExtendable
import java.util.function.Consumer
import kotlin.math.min

interface ChunkSender {
    val level: ServerLevel

    fun getCenterChunk(): ChunkPos

    fun forEachChunk(consumer: Consumer<ChunkPos>)

    fun sendPacket(packet: Packet<*>)

    fun isValidEntity(entity: Entity): Boolean

    fun shouldTrackEntity(tracking: Entity, range: Double): Boolean {
        return this.isValidEntity(tracking)
    }

    fun addTrackedEntity(tracking: TrackedEntity)

    @NonExtendable
    fun sendChunksAndEntities() {
        val seen = IntOpenHashSet()
        this.sendChunks(seen)
        this.sendChunkEntities(seen)
    }

    @Internal
    fun sendChunks(seen: IntSet) {
        val center = this.getCenterChunk()

        this.sendPacket(ClientboundSetChunkCacheCenterPacket(center.x, center.z))

        val chunks = this.level.chunkSource.chunkMap
        chunks as ChunkMapInvoker
        this.forEachChunk { pos ->
            val holder = chunks.getVisibleChunkIfExists(pos.toLong()) ?: return@forEachChunk
            val chunk = holder.tickingChunk
            if (chunk != null) {
                this.sendChunk(chunks, chunk, seen)
            }
        }
    }

    @Internal
    fun sendChunk(
        chunks: ChunkMap,
        chunk: LevelChunk,
        seen: IntSet,
    ) {
        chunks as ChunkMapInvoker

        // We don't need to use the chunkSender
        // We are only writing the packets to disk...
        this.sendPacket(ClientboundLevelChunkWithLightPacket(
            chunk,
            chunks.getLightEngine(),
            null,
            null
        ))

        val leashed = ArrayList<Mob>()
        val ridden = ArrayList<Entity>()

        for (tracked in (chunks as ChunkMapAccessor).entityMap.values) {
            val entity = (tracked as TrackedEntityAccessor).entity
            if (this.isValidEntity(entity) && entity.chunkPosition() == chunk.pos) {
                if (!seen.contains(entity.id)) {
                    val range = getRangeOfEntity(tracked, chunks)
                    if (this.shouldTrackEntity(entity, range)) {
                        this.addTrackedEntity(tracked)
                        seen.add(entity.id)
                    }
                }

                if (entity is Mob && entity.leashHolder != null) {
                    leashed.add(entity)
                }
                if (entity.passengers.isNotEmpty()) {
                    ridden.add(entity)
                }
            }
        }

        for (entity in leashed) {
            this.sendPacket(ClientboundSetEntityLinkPacket(entity, entity.leashHolder))
        }
        for (entity in ridden) {
            this.sendPacket(ClientboundSetPassengersPacket(entity))
        }
    }

    @Internal
    fun sendChunkEntities(seen: IntSet) {
        val chunks = this.level.chunkSource.chunkMap
        val entities = (chunks as ChunkMapAccessor).entityMap
        for (tracked in entities.values) {
            val entity = (tracked as TrackedEntityAccessor).entity
            val range = getRangeOfEntity(tracked, chunks)
            if (this.shouldTrackEntity(entity, range)) {
                this.addTrackedEntity(tracked)
            }
        }
    }

    companion object {
        private fun getRangeOfEntity(tracked: TrackedEntity, chunks: ChunkMap): Double {
            return min(
                (tracked as TrackedEntityInvoker).getRange(),
                (chunks as ChunkMapAccessor).viewDistance * 16
            ).toDouble()
        }
    }
}