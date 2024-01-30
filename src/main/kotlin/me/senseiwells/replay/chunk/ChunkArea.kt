package me.senseiwells.replay.chunk

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.ChunkPos
import kotlin.math.max
import kotlin.math.min

class ChunkArea(
    val level: ServerLevel,
    from: ChunkPos,
    to: ChunkPos
): Iterable<ChunkPos> {
    val from: ChunkPos
    val to: ChunkPos

    val center: ChunkPos

    init {
        this.from = ChunkPos(min(from.x, to.x), min(from.z, to.z))
        this.to = ChunkPos(max(from.x, to.x), max(from.z, to.z))

        this.center = ChunkPos(
            this.from.x + (this.to.x - this.from.x) / 2,
            this.from.z + (this.to.z - this.from.z) / 2
        )
    }

    fun contains(pos: ChunkPos): Boolean {
        return this.from.x <= pos.x && this.from.z <= pos.z && this.to.x >= pos.x && this.to.z >= pos.z
    }

    fun contains(pos: BlockPos): Boolean {
        return this.contains(ChunkPos(pos))
    }

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
}