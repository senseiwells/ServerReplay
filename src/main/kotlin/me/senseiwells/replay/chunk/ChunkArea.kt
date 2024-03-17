package me.senseiwells.replay.chunk

import net.minecraft.core.SectionPos
import net.minecraft.resources.ResourceKey
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.Level
import net.minecraft.world.level.levelgen.structure.BoundingBox
import kotlin.math.max
import kotlin.math.min

/**
 * This class represents an area of chunks in a given [level].
 *
 * @param level The level that the chunk area is in.
 * @param from The first chunk corner.
 * @param to The second chunk corner.
 */
class ChunkArea(
    val level: ServerLevel,
    from: ChunkPos,
    to: ChunkPos
): Iterable<ChunkPos> {
    /**
     * The north-west most chunk corner.
     */
    val from: ChunkPos

    /**
     * The south-east most chunk corner.
     */
    val to: ChunkPos

    /**
     * The center most chunk position.
     */
    val center: ChunkPos

    /**
     * The largest distance from the center to the edge chunk.
     */
    val viewDistance: Int

    init {
        this.from = ChunkPos(min(from.x, to.x), min(from.z, to.z))
        this.to = ChunkPos(max(from.x, to.x), max(from.z, to.z))

        val dx = (this.to.x - this.from.x) / 2
        val dz = (this.to.z - this.from.z) / 2
        this.center = ChunkPos(this.from.x + dx, this.from.z + dz)
        this.viewDistance = max(dx, dz)
    }

    /**
     * Checks whether this area is in a given [level] and whether it
     * contains the given [pos].
     *
     * @param level The level to check.
     * @param pos The position to check.
     * @return Whether the location is inside this chunk area.
     */
    fun contains(level: ResourceKey<Level>, pos: ChunkPos): Boolean {
        return this.level.dimension() == level && this.contains(pos)
    }

    /**
     * Checks whether this area is in a given [level] and whether
     * it intersects the given [box].
     *
     * @param level The level to check.
     * @param box The position to check.
     * @return Whether the location intersects with this chunk area.
     */
    fun intersects(level: ResourceKey<Level>, box: BoundingBox): Boolean {
        if (this.level.dimension() != level) {
            return false
        }
        val fromX = SectionPos.blockToSectionCoord(box.minX())
        val toX = SectionPos.blockToSectionCoord(box.maxX())
        val fromZ = SectionPos.blockToSectionCoord(box.minZ())
        val toZ = SectionPos.blockToSectionCoord(box.maxZ())
        return this.to.x >= fromX && this.from.x <= toX && this.to.z >= fromZ && this.from.z <= toZ
    }

    private fun contains(pos: ChunkPos): Boolean {
        return this.from.x <= pos.x && this.from.z <= pos.z && this.to.x >= pos.x && this.to.z >= pos.z
    }

    /**
     * Returns an iterator that iterates over every chunk position
     * in this chunk area.
     *
     * @return The chunk position iterator.
     */
    override fun iterator(): Iterator<ChunkPos> {
        val dx = this.to.x - this.from.x + 1
        val dz = this.to.z - this.from.z + 1
        val total = dx * dz
        return object: Iterator<ChunkPos> {
            private var index = 0

            override fun hasNext(): Boolean {
                return this.index < total
            }

            override fun next(): ChunkPos {
                val x = this.index % dx
                val z = this.index / dx
                this.index++
                return ChunkPos(from.x + x, from.z + z)
            }
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other !is ChunkArea) {
            return false
        }
        if (this.from != other.from || this.to != other.to) {
            return false
        }
        if (this.level.dimension() != other.level.dimension()) {
            return false
        }
        return true
    }

    override fun hashCode(): Int {
        var result = this.level.dimension().hashCode()
        result = 31 * result + this.from.hashCode()
        result = 31 * result + this.to.hashCode()
        return result
    }

    override fun toString(): String {
        return "Chunks[in ${this.level.dimension().location()}, from (${this.from.x}, ${this.from.z}) to (${this.to.x}, ${this.to.z})]"
    }

    companion object {
        fun of(level: ServerLevel, x: Int, z: Int, radius: Int): ChunkArea {
            return ChunkArea(
                level,
                ChunkPos(x - radius, z - radius),
                ChunkPos(x + radius, z + radius)
            )
        }
    }
}