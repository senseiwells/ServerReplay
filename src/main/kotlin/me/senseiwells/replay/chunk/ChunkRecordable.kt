package me.senseiwells.replay.chunk

interface ChunkRecordable {
    fun addRecorder(recorder: ChunkRecorder)

    fun removeRecorder(recorder: ChunkRecorder)
}