package me.senseiwells.replay.util.ducks

import net.minecraft.server.level.ChunkHolder
import net.minecraft.server.level.ThreadedLevelLightEngine

interface ChunkMapInvoker {
    fun getUpdatingChunkIfPresent(pos: Long): ChunkHolder?

    fun getLightEngine(): ThreadedLevelLightEngine
}