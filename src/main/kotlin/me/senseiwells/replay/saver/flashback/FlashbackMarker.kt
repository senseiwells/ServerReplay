package me.senseiwells.replay.saver.flashback

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import me.senseiwells.replay.config.serialization.Vec3Serializer
import net.minecraft.world.phys.Vec3

@Serializable
class FlashbackMarker(
    @SerialName("colour")
    val color: Int,
    @SerialName("position")
    val location: Location? = null,
    val description: String? = null
) {
    @Serializable
    class Location(@Serializable(Vec3Serializer::class) val position: Vec3, val dimension: String)
}