package me.senseiwells.replay.reader.flashback

import io.netty.buffer.Unpooled
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import it.unimi.dsi.fastutil.ints.IntOpenHashSet
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import me.senseiwells.replay.util.flashback.FlashbackAction
import me.senseiwells.replay.util.flashback.FlashbackIO
import me.senseiwells.replay.util.flashback.FlashbackMeta
import net.minecraft.core.RegistryAccess
import net.minecraft.network.RegistryFriendlyByteBuf
import java.nio.file.FileSystem
import java.nio.file.Path
import java.util.*
import kotlin.io.path.inputStream
import kotlin.io.path.notExists
import kotlin.io.path.readBytes

class FlashbackChunkedReader(
    private val system: FileSystem,
    private val access: RegistryAccess
) {
    private val chunks = TreeMap<Int, PlayableChunk>()
    private var current: Map.Entry<Int, PlayableChunk>

    private val buffer = RegistryFriendlyByteBuf(Unpooled.buffer(), this.access)
    private var snapshotIndex: Int = 0
    private var playbackIndex: Int = 0

    private val actions = Int2ObjectOpenHashMap<FlashbackAction>()
    private val ignored = IntOpenHashSet()

    val meta = this.readMeta()

    init {
        var ticks = 0
        for ((name, meta) in this.meta.chunks) {
            this.chunks[ticks] = PlayableChunk(this.system.getPath(name), meta)
            ticks += meta.duration
        }
        this.current = this.chunks.floorEntry(0)
        this.readHeader()
    }

    fun shouldPlaySnapshot(): Boolean {
        return this.current.value.meta.forcePlayerSnapshot
    }

    fun consumeSnapshot(consumer: (FlashbackAction, RegistryFriendlyByteBuf) -> Unit) {
        this.buffer.readerIndex(this.snapshotIndex)
        while (this.buffer.readerIndex() < this.playbackIndex) {
            this.consumeAction(consumer)
        }
    }

    fun consumeNextAction(consumer: (FlashbackAction, RegistryFriendlyByteBuf) -> Unit): Boolean {
        if (this.buffer.readerIndex() >= this.buffer.writerIndex()) {
            return false
        }
        if (this.buffer.readerIndex() < this.playbackIndex) {
            this.buffer.readerIndex(this.playbackIndex)
        }

        this.consumeAction(consumer)
        return true
    }

    fun jumpTo(tick: Int, current: Int): Int? {
        val entry = this.chunks.floorEntry(tick)
        if (entry == this.current && current < tick) {
            return null
        }
        this.current = entry
        this.readHeader()
        return entry.key
    }

    fun moveToNextChunk(): Boolean {
        val duration = this.current.value.meta.duration
        val tick = this.current.key + duration
        val entry = this.chunks.floorEntry(tick)
        if (entry == this.current) {
            return false
        }
        this.current = entry
        this.readHeader()
        return true
    }

    fun close() {
        this.buffer.release()
        this.chunks.clear()
    }

    private fun consumeAction(consumer: (FlashbackAction, RegistryFriendlyByteBuf) -> Unit) {
        val id = this.buffer.readVarInt()
        val action = this.actions.get(id)
        if (action == null) {
            if (!this.ignored.contains(id)) {
                throw IllegalStateException("Replay tried to play unregistered action $id")
            }
            val size = this.buffer.readInt()
            this.buffer.skipBytes(size)
            return
        }

        val size = this.buffer.readInt()
        val slice = this.buffer.readSlice(size)
        consumer.invoke(action, RegistryFriendlyByteBuf(slice, this.access))

        if (slice.readerIndex() < slice.writerIndex()) {
            throw IllegalStateException("Action ${action.id} wasn't processed correctly")
        }
    }

    private fun readHeader() {
        this.actions.clear()
        this.ignored.clear()

        this.buffer.readerIndex(0)
        this.buffer.writerIndex(0)
        this.buffer.writeBytes(this.current.value.path.readBytes())

        val magic = this.buffer.readInt()
        if (magic != FlashbackIO.MAGIC_NUMBER) {
            throw IllegalStateException("Flashback chunk has invalid magic!")
        }

        val actions = this.buffer.readVarInt()
        for (i in 0..<actions) {
            val id = this.buffer.readResourceLocation()
            val action = FlashbackAction.from(id)
            if (action == null) {
                if (id.path.endsWith("optional")) {
                    this.ignored.add(i)
                } else {
                    throw IllegalStateException("Unknown action $id")
                }
            } else {
                this.actions.put(i, action)
            }
        }

        val snapshotSize = this.buffer.readInt()
        this.snapshotIndex = this.buffer.readerIndex()
        this.buffer.skipBytes(snapshotSize)
        this.playbackIndex = this.buffer.readerIndex()
    }

    private fun readMeta(): FlashbackMeta {
        var metadata = this.system.getPath(FlashbackIO.METADATA)
        if (metadata.notExists()) {
            metadata = this.system.getPath(FlashbackIO.METADATA_OLD)
            if (metadata.notExists()) {
                throw IllegalStateException("Flashback file has no metadata!")
            }
        }

        @OptIn(ExperimentalSerializationApi::class)
        metadata.inputStream().use {
            return Json.decodeFromStream<FlashbackMeta>(it)
        }
    }

    private data class PlayableChunk(
        val path: Path,
        val meta: FlashbackMeta.ChunkMeta
    )
}