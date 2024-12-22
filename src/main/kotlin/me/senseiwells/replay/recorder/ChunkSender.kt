package me.senseiwells.replay.recorder

import it.unimi.dsi.fastutil.ints.IntOpenHashSet
import it.unimi.dsi.fastutil.ints.IntSet
import me.senseiwells.replay.ServerReplay
import me.senseiwells.replay.chunk.ChunkRecorder
import me.senseiwells.replay.mixin.rejoin.ChunkMapAccessor
import me.senseiwells.replay.mixin.rejoin.TrackedEntityAccessor
import me.senseiwells.replay.player.PlayerRecorder
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.*
import net.minecraft.server.level.ChunkMap
import net.minecraft.server.level.ChunkMap.TrackedEntity
import net.minecraft.server.level.ServerEntity
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.Mob
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.chunk.LevelChunk
import org.jetbrains.annotations.ApiStatus.*
import java.util.function.Consumer
import kotlin.math.min

/**
 * This interface provides a way to resend any given chunks
 * and any entities within those chunks.
 *
 * @see PlayerRecorder
 * @see ChunkRecorder
 */
interface ChunkSender {
    /**
     * The level of which the chunks are in.
     */
    val level: ServerLevel

    /**
     * The center chunk position of all the chunks being sent.
     *
     * @return The center most chunk position.
     */
    fun getCenterChunk(): ChunkPos

    /**
     * This will iterate over every chunk position that is going
     * to be sent, each chunk position will be accepted into the
     * [consumer].
     *
     * @param consumer The consumer that will accept the given chunks positions.
     */
    fun forEachChunk(consumer: Consumer<ChunkPos>)

    /**
     * This determines whether a given [entity] should be sent.
     *
     * @param entity The entity to check.
     * @param range The entity's tracking range.
     * @return Whether the entity should be tracked.
     */
    fun shouldTrackEntity(entity: Entity, range: Double): Boolean

    /**
     * This method should consume a packet.
     * This is used to send the chunk and entity packets.
     *
     * @param packet The packet to send.
     */
    fun sendChunkPacket(packet: Packet<*>)

    /**
     * This is called when [shouldTrackEntity] returns `true`,
     * this should be used to send any additional packets for this entity.
     *
     * @param tracked The [WrappedTrackedEntity].
     */
    fun addTrackedEntity(tracked: WrappedTrackedEntity)

    /**
     * This gets the view distance of the server.
     *
     * @return The view distance of the server.
     */
    fun getViewDistance(): Int {
        return this.level.server.playerList.viewDistance
    }

    /**
     * This is called when a chunk is successfully sent to the client.
     *
     * @param chunk The chunk that was sent.
     */
    @OverrideOnly
    fun onChunkSent(chunk: LevelChunk) {

    }

    /**
     * This sends all chunk and entity packets.
     */
    @NonExtendable
    fun sendChunksAndEntities() {
        val seen = IntOpenHashSet()
        this.sendChunkViewDistance()
        this.sendChunks(seen)
        this.sendChunkEntities(seen)
    }

    /**
     * This sends all the chunk view distance and simulation distance packets.
     */
    @Internal
    fun sendChunkViewDistance() {
        val center = this.getCenterChunk()
        this.sendChunkPacket(ClientboundSetChunkCacheCenterPacket(center.x, center.z))
        this.sendChunkPacket(ClientboundSetChunkCacheRadiusPacket(this.getViewDistance()))
        this.sendChunkPacket(ClientboundSetSimulationDistancePacket(this.getViewDistance()))
    }

    /**
     * This sends all chunk packets.
     *
     * @param seen The [IntSet] of entity ids that have already been seen.
     */
    @Internal
    fun sendChunks(seen: IntSet) {
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

    /**
     * This sends a specific chunk packet.
     *
     * @param chunks The [ChunkMap] containing all chunks.
     * @param chunk The current chunk that is being sent.
     * @param seen The [IntSet] of entity ids that have already been seen.
     */
    @Internal
    fun sendChunk(
        chunks: ChunkMap,
        chunk: LevelChunk,
        seen: IntSet,
    ) {
        chunks as ChunkMapAccessor

        // We don't need to use the chunkSender
        // We are only writing the packets to disk...
        this.sendChunkPacket(ClientboundLevelChunkWithLightPacket(
            chunk,
            chunk.level.lightEngine,
            null,
            null
        ))

        val leashed = ArrayList<Mob>()
        val ridden = ArrayList<Entity>()

        val viewDistance = this.level.server.playerList.viewDistance
        for (tracked in chunks.entityMap.values) {
            val entity = (tracked as TrackedEntityAccessor).entity
            if (entity.chunkPosition() == chunk.pos) {
                if (!seen.contains(entity.id)) {
                    val range = min(tracked.getRange(), viewDistance * 16).toDouble()
                    if (this.shouldTrackEntity(entity, range)) {
                        this.addTrackedEntity(WrappedTrackedEntity(tracked))
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
            this.sendChunkPacket(ClientboundSetEntityLinkPacket(entity, entity.leashHolder))
        }
        for (entity in ridden) {
            this.sendChunkPacket(ClientboundSetPassengersPacket(entity))
        }

        this.onChunkSent(chunk)
    }

    /**
     * This sends all the entities.
     *
     * @param seen The [IntSet] of entity ids that have already been seen.
     */
    @Internal
    fun sendChunkEntities(seen: IntSet) {
        val chunks = this.level.chunkSource.chunkMap
        val entities = (chunks as ChunkMapAccessor).entityMap
        val viewDistance = this.level.server.playerList.viewDistance
        for (tracked in entities.values) {
            val entity = (tracked as TrackedEntityAccessor).entity
            val range = min(tracked.getRange(), viewDistance * 16).toDouble()
            if (this.shouldTrackEntity(entity, range)) {
                this.addTrackedEntity(WrappedTrackedEntity(tracked))
            }
        }
    }

    /**
     * We wrap the tracked entity into a new class because
     * [TrackedEntity] by default is a package-private class.
     *
     * We don't want to force mods that need to implement [ChunkSender]
     * to have an access-widener for this class.
     */
    class WrappedTrackedEntity(val tracked: TrackedEntity) {
        /**
         * Gets the [Entity] being tracked.
         *
         * @return The tracked entity.
         */
        @Suppress("unused")
        fun getEntity(): Entity {
            return (this.tracked as TrackedEntityAccessor).entity
        }

        /**
         * Gets the [ServerEntity] being tracked.
         *
         * @return The server entity.
         */
        fun getServerEntity(): ServerEntity {
            return (this.tracked as TrackedEntityAccessor).serverEntity
        }
    }
}