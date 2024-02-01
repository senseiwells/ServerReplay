package me.senseiwells.replay.util

import net.minecraft.core.Vec3i
import net.minecraft.world.level.levelgen.structure.BoundingBox
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
}