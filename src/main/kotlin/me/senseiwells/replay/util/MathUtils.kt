package me.senseiwells.replay.util

import net.minecraft.core.Vec3i
import net.minecraft.server.level.ChunkMap
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.levelgen.structure.BoundingBox
import java.util.function.Consumer
import kotlin.math.abs

object MathUtils {
    @JvmStatic
    fun createBoxAround(center: Vec3i, range: Int): BoundingBox {
        val absRange = abs(range)
        return BoundingBox(
            center.x - absRange, center.y - absRange, center.z - absRange,
            center.x + absRange, center.y + absRange, center.z + absRange
        )
    }

    @JvmStatic
    fun forEachChunkAround(chunk: ChunkPos, radius: Int, consumer: Consumer<ChunkPos>) {
        for (chunkX in chunk.x - radius - 1..chunk.x + radius + 1) {
            for (chunkZ in chunk.z - radius - 1..chunk.z + radius + 1) {
                if (ChunkMap.isChunkInRange(chunkX, chunkZ, chunk.x, chunk.z, radius)) {
                    consumer.accept(ChunkPos(chunkX, chunkZ))
                }
            }
        }
    }
}