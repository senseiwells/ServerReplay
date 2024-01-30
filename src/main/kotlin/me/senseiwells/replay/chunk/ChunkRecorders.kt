package me.senseiwells.replay.chunk

import me.senseiwells.replay.config.ReplayConfig
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.TicketType
import net.minecraft.world.level.ChunkPos
import java.util.concurrent.CompletableFuture

object ChunkRecorders {
    private val recorders = LinkedHashMap<ChunkArea, ChunkRecorder>()
    private val closing = HashMap<ChunkArea, CompletableFuture<Long>>()

    val RECORDING_TICKET: TicketType<ChunkPos> = TicketType.create("chunk_recorder", Comparator.comparingLong(ChunkPos::toLong), 1)

    @JvmStatic
    fun create(level: ServerLevel, from: ChunkPos, to: ChunkPos, name: String): ChunkRecorder {
        return this.create(ChunkArea(level, from, to), name)
    }

    @JvmStatic
    @JvmOverloads
    fun create(area: ChunkArea, name: String = generateName(area)): ChunkRecorder {
        if (this.recorders.containsKey(area)) {
            throw IllegalArgumentException("Chunk area already has a recorder")
        }

        this.closing[area]?.join()

        val recorder = ChunkRecorder(
            area,
            name,
            ReplayConfig.chunkRecordingPath.resolve(name)
        )
        this.recorders[area] = recorder
        return recorder
    }

    @JvmStatic
    fun has(area: ChunkArea): Boolean {
        return this.recorders.containsKey(area)
    }

    @JvmStatic
    fun get(area: ChunkArea): ChunkRecorder? {
        return this.recorders[area]
    }

    @JvmStatic
    fun remove(area: ChunkArea): ChunkRecorder? {
        return this.recorders.remove(area)
    }

    @JvmStatic
    fun all(): Collection<ChunkRecorder> {
        return ArrayList(this.recorders.values)
    }

    internal fun close(server: MinecraftServer, area: ChunkArea, future: CompletableFuture<Long>) {
        this.recorders.remove(area)
        this.closing[area] = future
        future.thenRunAsync({ this.closing.remove(area) }, server)
    }

    private fun generateName(area: ChunkArea): String {
        return "Chunks (${area.from.x}, ${area.from.z}) to (${area.to.x}, ${area.to.z})"
    }
}