package me.senseiwells.replay.saver

import io.netty.buffer.ByteBuf
import me.senseiwells.replay.ServerReplay
import me.senseiwells.replay.api.network.RecordablePayload
import me.senseiwells.replay.mixin.network.IdDispatchCodecAccessor
import me.senseiwells.replay.recorder.ReplayRecorder
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.ProtocolInfo
import net.minecraft.network.chat.Component
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.PacketType
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket
import net.minecraft.network.protocol.common.CommonPacketTypes
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.phys.Vec2
import net.minecraft.world.phys.Vec3
import java.nio.file.Path
import java.util.concurrent.CompletableFuture

interface ReplaySaver {
    val recorder: ReplayRecorder
    val path: Path
    val closed: Boolean

    val markers: Int
        get() = 0

    val cacheChunksOnUnload: Boolean
        get() = false

    fun tick() {

    }

    fun prePacketRecord(packet: Packet<*>): Boolean

    fun writePacket(
        packet: Packet<*>,
        protocol: ProtocolInfo<*>,
        timestamp: Long,
        offThread: Boolean
    ): CompletableFuture<Int?>

    fun postPacketRecord(packet: Packet<*>)

    fun writePlayer(player: ServerPlayer, packets: Collection<Packet<*>>) {
        for (packet in packets) {
            this.recorder.record(packet)
        }
    }

    fun writeCachedChunk(pos: ChunkPos): Boolean {
        return false
    }

    fun writeMarker(name: String?, position: Vec3, rotation: Vec2, timestamp: Int) {

    }

    fun getRawRecordingSize(): Long

    @Deprecated("Getting the compressed recording size is computationally expensive")
    fun getCompressedRecordingSize(force: Boolean): CompletableFuture<Long>

    fun close(duration: Int, save: Boolean): CompletableFuture<Long>

    companion object {
        const val ENTRY_SERVER_REPLAY_META = "server_replay_meta.json"

        val ReplaySaver.name
            get() = this.recorder.getName()

        fun ReplaySaver.broadcastToOps(message: Component) {
            if (ServerReplay.config.notifyAdminsOfStatus) {
                this.recorder.server.execute {
                    this.recorder.server.playerList.players.filter {
                        this.recorder.server.playerList.isOp(it.gameProfile)
                    }.forEach { it.sendSystemMessage(message) }
                }
            }
        }

        fun ReplaySaver.broadcastToOpsAndConsole(message: String) {
            this.broadcastToOps(Component.literal(message))
            ServerReplay.logger.info(message)
        }

        fun ReplaySaver.broadcastToOpsAndConsole(message: Component) {
            this.broadcastToOps(message)
            ServerReplay.logger.info(message.string)
        }

        fun encodePacket(packet: Packet<*>, protocol: ProtocolInfo<*>, buf: FriendlyByteBuf) {
            @Suppress("UNCHECKED_CAST")
            val codec = (protocol.codec() as StreamCodec<ByteBuf, Packet<*>>)

            if (packet is ClientboundCustomPayloadPacket) {
                val payload = packet.payload
                if (payload is RecordablePayload) {
                    @Suppress("UNCHECKED_CAST")
                    codec as IdDispatchCodecAccessor<PacketType<*>>

                    val id = codec.typeToIdMap.getInt(CommonPacketTypes.CLIENTBOUND_CUSTOM_PAYLOAD)
                    buf.writeVarInt(id)
                    buf.writeResourceLocation(payload.type().id)
                    payload.record(buf)
                    return
                }
            }

            codec.encode(buf, packet)
        }
    }
}