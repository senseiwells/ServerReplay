package me.senseiwells.replay.chunk

import net.minecraft.world.level.ChunkPos
import kotlin.math.max
import kotlin.math.min

class ChunkArea(
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
            this.from.z + (this.from.z - this.from.z) / 2
        )
    }

    override fun iterator(): Iterator<ChunkPos> {
        // TODO: Implement this faster
        return ChunkPos.rangeClosed(this.from, this.to).iterator()
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
        return true
    }

    override fun hashCode(): Int {
        var result = this.from.hashCode()
        result = 31 * result + this.to.hashCode()
        return result
    }
}