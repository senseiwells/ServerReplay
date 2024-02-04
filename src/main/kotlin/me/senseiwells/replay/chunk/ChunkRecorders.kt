package me.senseiwells.replay.chunk

import me.senseiwells.replay.ServerReplay
import me.senseiwells.replay.recorder.ReplayRecorder
import net.minecraft.resources.ResourceKey
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.Level
import net.minecraft.world.level.levelgen.structure.BoundingBox
import java.util.concurrent.CompletableFuture

object ChunkRecorders {
    private val chunks = LinkedHashMap<ChunkArea, ChunkRecorder>()
    private val chunksByName = LinkedHashMap<String, ChunkRecorder>()
    private val closing = HashMap<String, CompletableFuture<Long>>()

    @JvmStatic
    fun create(level: ServerLevel, from: ChunkPos, to: ChunkPos, name: String): ReplayRecorder {
        return this.create(ChunkArea(level, from, to), name)
    }

    @JvmStatic
    @JvmOverloads
    fun create(area: ChunkArea, name: String = generateName(area)): ReplayRecorder {
        if (this.chunks.containsKey(area)) {
            throw IllegalArgumentException("Recorder for chunk area already exists")
        }
        if (this.chunksByName.containsKey(name)) {
            throw IllegalArgumentException("Recorder with name already exists")
        }

        this.closing[name]?.join()

        val recorder = ChunkRecorder(
            area,
            name,
            ServerReplay.config.chunkRecordingPath.resolve(name)
        )
        this.chunks[area] = recorder
        this.chunksByName[name] = recorder
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

    @JvmStatic
    fun containing(level: ResourceKey<Level>, chunk: ChunkPos): List<ChunkRecorder> {
        return this.chunks.values.filter { it.chunks.contains(level, chunk) }
    }

    @JvmStatic
    @Suppress("unused")
    fun intersecting(level: ResourceKey<Level>, box: BoundingBox): List<ChunkRecorder> {
        return this.chunks.values.filter { it.chunks.intersects(level, box) }
    }

    @JvmStatic
    fun all(): Collection<ChunkRecorder> {
        return ArrayList(this.chunks.values)
    }

    @JvmStatic
    fun updateRecordable(
        recordable: ChunkRecordable,
        level: ResourceKey<Level>,
        chunk: ChunkPos
    ) {
        this.updateRecordable(recordable) { it.contains(level, chunk) }
    }

    @JvmStatic
    fun updateRecordable(
        recordable: ChunkRecordable,
        level: ResourceKey<Level>,
        box: BoundingBox
    ) {
        this.updateRecordable(recordable) { it.intersects(level, box) }
    }

    private fun updateRecordable(
        recordable: ChunkRecordable,
        predicate: (ChunkArea) -> Boolean
    ) {
        val existing = recordable.getRecorders()
        for (recorder in this.chunks.values) {
            if (predicate(recorder.chunks)) {
                if (!existing.contains(recorder)) {
                    recordable.addRecorder(recorder)
                }
            } else if (existing.contains(recorder)) {
                recordable.removeRecorder(recorder)
            }
        }
    }

    internal fun remove(area: ChunkArea): ChunkRecorder? {
        val recorder = this.chunks.remove(area)
        if (recorder != null) {
            this.chunksByName.remove(recorder.getName())
        }
        return recorder
    }

    fun generateName(area: ChunkArea): String {
        return "Chunks (${area.from.x}, ${area.from.z}) to (${area.to.x}, ${area.to.z})"
    }

    internal fun close(server: MinecraftServer, area: ChunkArea, future: CompletableFuture<Long>, name: String) {
        this.remove(area)
        this.closing[name] = future
        future.thenRunAsync({ this.closing.remove(name) }, server)
    }
}