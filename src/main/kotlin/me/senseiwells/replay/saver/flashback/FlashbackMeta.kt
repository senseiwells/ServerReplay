package me.senseiwells.replay.saver.flashback

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.EncodeDefault.Mode
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import me.senseiwells.replay.ServerReplay
import me.senseiwells.replay.config.serialization.UUIDSerializer
import net.minecraft.SharedConstants
import java.util.*
import kotlin.collections.LinkedHashMap

@Serializable
@OptIn(ExperimentalSerializationApi::class)
data class FlashbackMeta (
    @Serializable(UUIDSerializer::class)
    val uuid: UUID = UUID.randomUUID(),
    @EncodeDefault(Mode.ALWAYS)
    val name: String = "Unnamed",
    @EncodeDefault(Mode.ALWAYS)
    val version: String = SharedConstants.getCurrentVersion().name,
    @SerialName("world_name")
    @EncodeDefault(Mode.ALWAYS)
    val worldName: String = ServerReplay.config.worldName,
    @SerialName("data_version")
    @EncodeDefault(Mode.ALWAYS)
    val dataVersion: Int = SharedConstants.getCurrentVersion().dataVersion.version,
    @SerialName("protocol_version")
    @EncodeDefault(Mode.ALWAYS)
    val protocolVersion: Int = SharedConstants.getCurrentVersion().protocolVersion,
    @SerialName("total_ticks")
    @EncodeDefault(Mode.ALWAYS)
    val totalTicks: Int = 0,
    @EncodeDefault(Mode.ALWAYS)
    val chunks: Map<String, ChunkMeta> = mapOf()
) {
    fun completeChunk(totalTicks: Int, chunkName: String): FlashbackMeta {
        val duration = totalTicks - this.totalTicks
        val copy = LinkedHashMap(this.chunks)
        copy[chunkName] = ChunkMeta(duration)
        return this.copy(totalTicks = totalTicks, chunks = copy)
    }

    @Serializable
    class ChunkMeta(
        val duration: Int,
        /**
         * This property is only ever used when merging two
         * replays, and for our purposes will always be `false`
         */
        @Suppress("unused")
        @SerialName("forcePlayerSnapshot")
        @EncodeDefault(Mode.ALWAYS)
        val forcePlayerSnapshot: Boolean = false
    )
}