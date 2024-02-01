package me.senseiwells.replay.util

import me.senseiwells.replay.mixin.rejoin.ChunkMapAccessor
import net.minecraft.server.level.ServerLevel

object LevelUtils {
    @JvmStatic
    val ServerLevel.viewDistance
        get() = (this.chunkSource.chunkMap as ChunkMapAccessor).viewDistance
}