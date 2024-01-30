package me.senseiwells.replay.chunk

interface ChunkRecorderTrackedEntity {
    fun addRecorder(recorder: ChunkRecorder)

    fun removeRecorder(recorder: ChunkRecorder)
}