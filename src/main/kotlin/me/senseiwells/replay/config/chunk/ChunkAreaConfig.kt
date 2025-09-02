package me.senseiwells.replay.config.chunk

import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.minecraft.resources.ResourceLocation

@Serializable
class ChunkAreaConfig(
    val name: String,
    @Contextual
    @SerialName("dimension")
    val dimension: ResourceLocation,
    @SerialName("from_x")
    val fromX: Int,
    @SerialName("from_z")
    val fromZ: Int,
    @SerialName("to_x")
    val toX: Int,
    @SerialName("to_z")
    val toZ: Int
)