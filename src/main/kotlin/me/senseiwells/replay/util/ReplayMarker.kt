package me.senseiwells.replay.util

import net.minecraft.world.phys.Vec2
import net.minecraft.world.phys.Vec3
import kotlin.time.Duration

data class ReplayMarker(
    val name: String?,
    val position: Vec3?,
    val rotation: Vec2?,
    val timestamp: Duration,
    val color: Int
)