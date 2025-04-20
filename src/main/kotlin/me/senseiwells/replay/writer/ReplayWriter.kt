package me.senseiwells.replay.writer

import io.netty.buffer.ByteBuf
import me.senseiwells.replay.ServerReplay
import me.senseiwells.replay.api.network.RecordablePayload
import me.senseiwells.replay.mixin.network.IdDispatchCodecAccessor
import me.senseiwells.replay.recorder.ReplayRecorder
import me.senseiwells.replay.util.FileUtils
import me.senseiwells.replay.util.ReplayMarker
import net.minecraft.ChatFormatting
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.ProtocolInfo
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.PacketType
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket
import net.minecraft.network.protocol.common.CommonPacketTypes
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.ChunkPos
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import kotlin.io.path.fileSize
import kotlin.time.Duration

interface ReplayWriter {
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
        timestamp: Duration,
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

    fun writeMarker(marker: ReplayMarker) {

    }

    fun getRawRecordingSize(): Long

    fun getOutputPath(): Path

    fun close(duration: Duration, save: Boolean): CompletableFuture<Long>

    companion object {
        const val ENTRY_SERVER_REPLAY_META = "server_replay_meta.json"

        val ReplayWriter.name
            get() = this.recorder.getName()

        fun ReplayWriter.broadcastToOps(message: Component) {
            if (ServerReplay.config.notifyAdminsOfStatus) {
                this.recorder.server.execute {
                    this.recorder.server.playerList.players.filter {
                        this.recorder.server.playerList.isOp(it.gameProfile)
                    }.forEach { it.sendSystemMessage(message) }
                }
            }
        }

        fun ReplayWriter.broadcastToOpsAndConsole(message: String) {
            this.broadcastToOps(Component.literal(message))
            ServerReplay.logger.info(message)
        }

        fun ReplayWriter.broadcastToOpsAndConsole(message: Component) {
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

        internal fun ReplayWriter.closeWithFeedback(
            save: Boolean,
            writer: () -> Unit,
            closer: () -> Unit
        ): Long {
            var size = 0L
            try {
                val additional = Component.empty()
                if (save) {
                    this.broadcastToOpsAndConsole("Staring to save replay ${this.name}, please do not stop the server!")
                    writer.invoke()
                    val output = this.getOutputPath()
                    size = output.fileSize()

                    val click = ClickEvent.SuggestCommand(this.recorder.getViewingCommand())
                    val hover = HoverEvent.ShowText(Component.literal("Click to view replay"))
                    additional.append(" and saved to ")
                        .append(Component.literal(output.toString()).withStyle {
                            it.withClickEvent(click).withHoverEvent(hover).withColor(ChatFormatting.GREEN)
                        })
                        .append(", compressed to ${FileUtils.formatSize(size)}")
                }
                try {
                    closer.invoke()
                    this.broadcastToOpsAndConsole(
                        Component.literal("Successfully closed replay ${this.name}").append(additional)
                    )
                } catch (exception: Exception) {
                    val message = "Failed to close replay writer"
                    this.broadcastToOps(Component.literal(message).append(additional))
                    ServerReplay.logger.error(message, exception)
                }
            } catch (exception: Exception) {
                val message = "Failed to write replay ${this.name}"
                val hover = HoverEvent.ShowText(Component.literal(exception.stackTraceToString()))
                this.broadcastToOps(Component.literal(message).withStyle {
                    it.withHoverEvent(hover)
                })
                ServerReplay.logger.error(message, exception)
                throw exception
            }
            return size
        }
    }
}