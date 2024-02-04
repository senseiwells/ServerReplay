package me.senseiwells.replay.config.chunk

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import me.senseiwells.replay.chunk.ChunkArea
import me.senseiwells.replay.config.serialization.ResourceLocationSerializer
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.Level

@Serializable
class ChunkAreaConfig(
    val name: String,
    @SerialName("dimension")
    @Serializable(with = ResourceLocationSerializer::class)
    private val location: ResourceLocation,
    @SerialName("from_x")
    val fromX: Int,
    @SerialName("from_z")
    val fromZ: Int,
    @SerialName("to_x")
    val toX: Int,
    @SerialName("to_z")
    val toZ: Int
) {
    val dimension: ResourceKey<Level> by lazy {
        ResourceKey.create(Registries.DIMENSION, this.location)
    }

    fun toChunkArea(server: MinecraftServer): ChunkArea? {
        val level = server.getLevel(this.dimension) ?: return null
        return ChunkArea(level, ChunkPos(this.fromX, this.fromZ), ChunkPos(this.toX, this.toZ))
    }
}