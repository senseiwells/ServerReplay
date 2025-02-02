package me.senseiwells.replay.saver.flashback

import me.senseiwells.replay.mixin.flashback.BlockEntityInfoAccessor
import me.senseiwells.replay.mixin.flashback.ClientboundLevelChunkPacketDataAccessor
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket

class ChunkPacketIdentity private constructor(
    val x: Int,
    val z: Int,
    val hashes: IntArray
) {
    val hash = this.hashes.contentHashCode()

    override fun hashCode(): Int {
        return this.hash
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other !is ChunkPacketIdentity) {
            return false
        }
        if (this.hash != other.hash) {
            return false
        }
        if (this.x != other.x || this.z != other.z) {
            return false
        }
        return this.hashes.contentEquals(other.hashes)
    }

    companion object {
        fun of(packet: ClientboundLevelChunkWithLightPacket): ChunkPacketIdentity {
            val hashes = IntArray(5)
            hashes[0] = packet.x
            hashes[1] = packet.z
            val chunkData = packet.chunkData as ClientboundLevelChunkPacketDataAccessor
            hashes[2] = chunkData.buffer.contentHashCode()
            hashes[3] = packet.chunkData.heightmaps.hashCode()
            val blockEntityHashes = IntArray(chunkData.blockEntitiesData.size)
            val sortedBlockEntityData = chunkData.blockEntitiesData.sortedBy { data ->
                data as BlockEntityInfoAccessor
                (data.y shl 8) or (data.packedXZ)
            }
            for ((i, data) in sortedBlockEntityData.withIndex()) {
                data as BlockEntityInfoAccessor
                val intermediary = intArrayOf(
                    data.packedXZ,
                    data.y,
                    BuiltInRegistries.BLOCK_ENTITY_TYPE.getId(data.type),
                    data.tag?.hashCode() ?: 0
                )
                blockEntityHashes[i] = intermediary.contentHashCode()
            }
            hashes[4] = blockEntityHashes.contentHashCode()

            return ChunkPacketIdentity(packet.x, packet.z, hashes)
        }
    }
}