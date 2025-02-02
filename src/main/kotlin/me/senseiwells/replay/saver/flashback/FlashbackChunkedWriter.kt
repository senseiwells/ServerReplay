package me.senseiwells.replay.saver.flashback

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToStream
import net.minecraft.core.RegistryAccess
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.RegistryFriendlyByteBuf
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.io.path.*

class FlashbackChunkedWriter(
    private val directory: Path,
    private val access: RegistryAccess
) {
    // TODO: Remember to free this memory!
    private val buffer = RegistryFriendlyByteBuf(Unpooled.buffer(), this.access)

    private var snapshot: SnapshotState = SnapshotState.Empty
    private var action: FlashbackAction? = null
    private var chunk = 0

    var meta = FlashbackMeta()
        private set

    init {
        this.writeHeader()
    }

    fun startSnapshot() {
        when (this.snapshot) {
            is SnapshotState.Taking -> throw IllegalStateException("Already taking a snapshot")
            is SnapshotState.Complete -> throw IllegalStateException("Cannot take multiple snapshots")
            is SnapshotState.Empty -> {}
        }

        val start = this.buffer.writerIndex()
        this.buffer.writeInt(0)
        this.snapshot = SnapshotState.Taking(start)
    }

    fun endSnapshot() {
        val snapshot = this.snapshot
        if (snapshot !is SnapshotState.Taking) {
            throw IllegalStateException("Cannot end snapshot if you're not taking a snapshot!")
        }

        val start = snapshot.start
        val end = this.buffer.writerIndex()
        val size = end - start - 4

        this.buffer.writerIndex(start)
        this.buffer.writeInt(size)
        this.buffer.writerIndex(end)

        this.snapshot = SnapshotState.Complete
    }

    fun writeAction(action: FlashbackAction) {
        return this.writeAction(action) { }
    }

    fun <T> writeAction(action: FlashbackAction, block: (RegistryFriendlyByteBuf) -> T): T {
        if (this.snapshot is SnapshotState.Empty) {
            throw IllegalStateException("Tried writing action before taking a snapshot!")
        }
        if (this.action != null) {
             throw IllegalStateException("Tried writing action within another action!")
        }
        this.action = action
        this.buffer.writeVarInt(action.ordinal)

        val result = this.writeSizeOf(this.buffer) {
            block.invoke(this.buffer)
        }
        this.action = null
        return result
    }

    fun endChunk(tick: Int) {
        val name = "c${this.chunk++}.flashback"

        val copy = ByteArray(this.buffer.writerIndex())
        this.buffer.getBytes(0, copy)

        val chunk = this.directory.resolve(name)
        chunk.writeBytes(copy)

        this.meta = this.meta.completeChunk(tick, name)
        val meta = this.directory.resolve("metadata.json")
        if (meta.exists()) {
            meta.moveTo(meta.resolveSibling("metadata.json.old"), true)
        }

        @OptIn(ExperimentalSerializationApi::class)
        meta.outputStream().use {
            Json.encodeToStream(this.meta, it)
        }

        this.snapshot = SnapshotState.Empty
        this.writeHeader()
    }

    fun writeLevelChunk(fileIndex: Int, block: (FriendlyByteBuf) -> Unit) {
        val buffer = FriendlyByteBuf(Unpooled.buffer())
        try {
            this.writeSizeOf(buffer) { block.invoke(buffer) }
            val copy = ByteArray(buffer.writerIndex())
            buffer.getBytes(0, copy)

            val path = this.directory.resolve("level_chunk_caches").resolve("$fileIndex")
            path.createParentDirectories()
            path.writeBytes(copy, StandardOpenOption.CREATE, StandardOpenOption.APPEND, StandardOpenOption.SYNC)
        } finally {
            buffer.release()
        }
    }

    private fun writeHeader() {
        this.buffer.writerIndex(0)
        this.buffer.writeInt(MAGIC_NUMBER)
        this.buffer.writeVarInt(FlashbackAction.entries.size)
        for (action in FlashbackAction.entries) {
            this.buffer.writeResourceLocation(action.id)
        }
    }

    private inline fun <T> writeSizeOf(buffer: ByteBuf, block: () -> T): T {
        val start = buffer.writerIndex()
        buffer.writeInt(0) // No dead beef here :(
        val result = block.invoke()

        val end = buffer.writerIndex()
        val size = end - start - 4

        buffer.writerIndex(start)
        buffer.writeInt(size)
        buffer.writerIndex(end)
        return result
    }

    private sealed interface SnapshotState {
        data object Empty: SnapshotState

        data class Taking(val start: Int): SnapshotState

        data object Complete: SnapshotState
    }

    companion object {
        // Magic number flashback uses to verify that it's a flashback file
        private const val MAGIC_NUMBER: Int = -0x287F177C
    }
}