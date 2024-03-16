package me.senseiwells.replay.chunk

/**
 * This interface represents an object that can be recorded
 * by a [ChunkRecorder].
 *
 * If a [ChunkRecorder] is added to this object via [addRecorder]
 * any packets that are a result of this object should also then be
 * recorded by that recorder.
 *
 * In order to update the recorders that are able to record this
 * object you should call [ChunkRecorders.updateRecordable] to
 * add and remove any [ChunkRecorder]s as necessary.
 *
 * For example:
 * ```kotlin
 * class MyChunkRecordable: ChunkRecordable {
 *     // ...
 *
 *     fun tick() {
 *         // The level that your recordable object is in.
 *         val level: ServerLevel = // ...
 *         // If your object is within a chunk:
 *         val chunkPos: ChunkPos = // ...
 *         ChunkRecorders.updateRecordable(this, level.dimension(), chunkPos)
 *
 *         // Alternatively if your object spans multiple chunks:
 *         val boundingBox: BoundingBox = // ...
 *         ChunkRecorders.updateRecordable(this, level.dimension(), boundingBox)
 *     }
 *
 *     // ...
 * }
 * ```
 *
 * @see ChunkRecorder
 * @see ChunkRecorders.updateRecordable
 */
interface ChunkRecordable {
    /**
     * This gets all the [ChunkRecorder]s that are currently
     * recording this object.
     *
     * @return All the chunk recorders recording this.
     */
    fun getRecorders(): Collection<ChunkRecorder>

    /**
     * Adds a [ChunkRecorder] to record all packets produced
     * by this object.
     *
     * @param recorder The recorder to add.
     */
    fun addRecorder(recorder: ChunkRecorder)

    /**
     * Removes a [ChunkRecorder] from recording packets
     * produced by this object.
     *
     * @param recorder The recorder to remove.
     */
    fun removeRecorder(recorder: ChunkRecorder)

    /**
     * Removes all [ChunkRecorder]s from recording
     * packets produced by this object.
     */
    fun removeAllRecorders()
}