package me.senseiwells.replay.recorder

import it.unimi.dsi.fastutil.ints.IntOpenHashSet
import it.unimi.dsi.fastutil.ints.IntSet
import me.senseiwells.replay.ServerReplay
import me.senseiwells.replay.mixin.rejoin.ChunkMapAccessor
import me.senseiwells.replay.mixin.rejoin.TrackedEntityAccessor
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.*
import net.minecraft.server.level.ChunkMap
import net.minecraft.server.level.ChunkMap.TrackedEntity
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

    fun getViewDistance(): Int {
        return this.level.server.playerList.viewDistance
    }

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
        this.sendPacket(ClientboundSetChunkCacheRadiusPacket(this.getViewDistance()))
        this.sendPacket(ClientboundSetSimulationDistancePacket(this.getViewDistance()))

        val source = this.level.chunkSource
        val chunks = source.chunkMap
        this.forEachChunk { pos ->
            val chunk = source.getChunk(pos.x, pos.z, true)
            if (chunk != null) {
                this.sendChunk(chunks, chunk, seen)
            } else {
                ServerReplay.logger.warn("Failed to get chunk at $pos, didn't send")
            }
        }
    }

    @Internal
    fun sendChunk(
        chunks: ChunkMap,
        chunk: LevelChunk,
        seen: IntSet,
    ) {
        chunks as ChunkMapAccessor

        // We don't need to use the chunkSender
        // We are only writing the packets to disk...
        this.sendPacket(ClientboundLevelChunkWithLightPacket(
            chunk,
            chunks.lightEngine,
            null,
            null
        ))

        val leashed = ArrayList<Mob>()
        val ridden = ArrayList<Entity>()

        val viewDistance = this.level.server.playerList.viewDistance
        for (tracked in chunks.entityMap.values) {
            val entity = (tracked as TrackedEntityAccessor).entity
            if (this.isValidEntity(entity) && entity.chunkPosition() == chunk.pos) {
                if (!seen.contains(entity.id)) {
                    val range = min(tracked.getRange(), viewDistance * 16).toDouble()
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
        val viewDistance = this.level.server.playerList.viewDistance
        for (tracked in entities.values) {
            val entity = (tracked as TrackedEntityAccessor).entity
            val range = min(tracked.getRange(), viewDistance * 16).toDouble()
            if (this.shouldTrackEntity(entity, range)) {
                this.addTrackedEntity(tracked)
            }
        }
    }
}