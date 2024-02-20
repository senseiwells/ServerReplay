package me.senseiwells.replay.api

import me.senseiwells.replay.chunk.ChunkRecorder
import me.senseiwells.replay.player.PlayerRecorder

interface RejoinedPacketSender {
    fun recordAdditionalPlayerPackets(recorder: PlayerRecorder)

    fun recordAdditionalChunkPackets(recorder: ChunkRecorder)
}