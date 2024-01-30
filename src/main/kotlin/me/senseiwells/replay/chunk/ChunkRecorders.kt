package me.senseiwells.replay.chunk

import me.senseiwells.replay.config.ReplayConfig
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.ChunkPos
import org.jetbrains.annotations.ApiStatus.Internal
import java.util.concurrent.CompletableFuture

object ChunkRecorders {
    private val chunks = LinkedHashMap<ChunkArea, ChunkRecorder>()
    private val chunksByName = LinkedHashMap<String, ChunkRecorder>()
    private val closing = HashMap<ChunkArea, CompletableFuture<Long>>()

    @JvmStatic
    fun create(level: ServerLevel, from: ChunkPos, to: ChunkPos, name: String): ChunkRecorder {
        return this.create(ChunkArea(level, from, to), name)
    }

    @JvmStatic
    @JvmOverloads
    fun create(area: ChunkArea, name: String = generateName(area)): ChunkRecorder {
        if (this.chunks.containsKey(area)) {
            throw IllegalArgumentException("Recorder for chunk area already exists")
        }
        if (this.chunksByName.containsKey(name)) {
            throw IllegalArgumentException("Recorder with name already exists")
        }

        this.closing[area]?.join()

        val recorder = ChunkRecorder(
            area,
            name,
            ReplayConfig.chunkRecordingPath.resolve(name)
        )
        this.chunks[area] = recorder
        return recorder
    }

    @JvmStatic
    fun has(name: String): Boolean {
        return this.chunksByName.containsKey(name)
    }

    @JvmStatic
    fun has(area: ChunkArea): Boolean {
        return this.chunks.containsKey(area)
    }

    @JvmStatic
    fun isAvailable(area: ChunkArea, name: String): Boolean {
        return !this.has(area) && !this.has(name)
    }

    @JvmStatic
    fun get(name: String): ChunkRecorder? {
        return this.chunksByName[name]
    }

    @JvmStatic
    fun get(area: ChunkArea): ChunkRecorder? {
        return this.chunks[area]
    }

    internal fun remove(area: ChunkArea): ChunkRecorder? {
        val recorder = this.chunks.remove(area)
        if (recorder != null) {
            this.chunksByName.remove(recorder.getName())
        }
        return recorder
    }

    @JvmStatic
    fun all(): Collection<ChunkRecorder> {
        return ArrayList(this.chunks.values)
    }

    fun generateName(area: ChunkArea): String {
        return "Chunks (${area.from.x}, ${area.from.z}) to (${area.to.x}, ${area.to.z})"
    }

    internal fun close(server: MinecraftServer, area: ChunkArea, future: CompletableFuture<Long>) {
        this.remove(area)
        this.closing[area] = future
        future.thenRunAsync({ this.closing.remove(area) }, server)
    }
}