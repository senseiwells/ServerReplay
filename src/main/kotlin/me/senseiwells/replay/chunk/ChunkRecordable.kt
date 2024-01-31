package me.senseiwells.replay.chunk

interface ChunkRecordable {
    fun getRecorders(): Collection<ChunkRecorder>

    fun addRecorder(recorder: ChunkRecorder)

    fun removeRecorder(recorder: ChunkRecorder)

    fun removeAllRecorders()
}