package me.senseiwells.replay.util.flashback

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import me.senseiwells.replay.config.serialization.Vec3Serializer
import net.minecraft.resources.ResourceKey
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3

@Suppress("unused")
@Serializable
class FlashbackMarker(
    @SerialName("colour")
    val color: Int,
    @SerialName("position")
    val location: Location? = null,
    val description: String? = null
) {
    @Serializable
    class Location(@Serializable(Vec3Serializer::class) val position: Vec3, val dimension: String) {
        companion object {
            fun from(position: Vec3?, dimension: ResourceKey<Level>): Location? {
                if (position == null) {
                    return null
                }
                return Location(position, dimension.toString())
            }
        }
    }
}