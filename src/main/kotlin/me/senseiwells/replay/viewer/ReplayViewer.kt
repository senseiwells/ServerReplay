package me.senseiwells.replay.viewer

import com.replaymod.replaystudio.lib.viaversion.api.protocol.packet.State
import com.replaymod.replaystudio.lib.viaversion.api.protocol.version.ProtocolVersion
import com.replaymod.replaystudio.protocol.Packet
import com.replaymod.replaystudio.protocol.PacketTypeRegistry
import com.replaymod.replaystudio.replay.ReplayFile
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import me.senseiwells.replay.ducks.`ServerReplay$ReplayViewable`
import net.fabricmc.fabric.impl.networking.payload.RetainedPayload
import net.minecraft.SharedConstants
import net.minecraft.network.ConnectionProtocol
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.protocol.PacketFlow
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket
import net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket
import net.minecraft.network.protocol.game.ClientboundLoginPacket
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket
import net.minecraft.server.network.ServerGamePacketListenerImpl
import net.minecraft.network.protocol.Packet as MinecraftPacket

class ReplayViewer(
    val replay: ReplayFile,
    val connection: ServerGamePacketListenerImpl
) {
    fun view() {
        (this.connection as `ServerReplay$ReplayViewable`).`replay$setReplayViewer`(this)

        this.connection.`replay$sendReplayViewerPacket`(
            ClientboundPlayerInfoUpdatePacket(
                ClientboundPlayerInfoUpdatePacket.Action.UPDATE_GAME_MODE,
                this.connection.player
            )
        )
        this.connection.player.chunkTrackingView.forEach {
            this.connection.`replay$sendReplayViewerPacket`(ClientboundForgetLevelChunkPacket(it))
        }

        // TODO: Wtf is this???
        //   This is so fucking jank please fix this :)
        Thread {
            val version = ProtocolVersion.getProtocol(SharedConstants.getProtocolVersion())
            // TODO: Send any configuration data (e.g. resource packs, tags, etc. to the client)
            val data = this.replay.getPacketData(PacketTypeRegistry.get(version, State.PLAY))

            var pastLogin = false
            var previousTime = 0L
            var timed = data.readPacket()
            while (timed != null) {
                // TODO: Ew, please make this nicer
                val type = ConnectionProtocol.PLAY.getPacketsByIds(PacketFlow.CLIENTBOUND).get(timed.packet.id)
                if (type == ClientboundLoginPacket::class.java) {
                    pastLogin = true
                    timed.release()
                    timed = data.readPacket()
                    continue
                } else if (!pastLogin) {
                    timed.release()
                    timed = data.readPacket()
                    continue
                }
                // TODO: Use co-routines for non-blocking delay
                Thread.sleep(timed.time - previousTime)
                this.connection.`replay$sendReplayViewerPacket`(toMinecraftPacket(timed.packet))

                previousTime = timed.time
                timed.release()
                timed = data.readPacket()
            }
        }.start()
    }

    private companion object {
        @Suppress("UnstableApiUsage")
        fun toMinecraftPacket(packet: Packet): MinecraftPacket<*> {
            val codec = ConnectionProtocol.PLAY.codec(PacketFlow.CLIENTBOUND)
            val decoded = codec.createPacket(packet.id, toFriendlyByteBuf(packet.buf))
                ?: throw IllegalStateException("Failed to create packet with id ${packet.id}")

            if (decoded is ClientboundCustomPayloadPacket) {
                val payload = decoded.payload
                if (payload is RetainedPayload) {
                    return ClientboundCustomPayloadPacket(payload.resolve(null))
                }
            }
            return decoded
        }

        @Suppress("USELESS_IS_CHECK")
        fun toFriendlyByteBuf(buf: com.github.steveice10.netty.buffer.ByteBuf): FriendlyByteBuf {
            // When we compile we map steveice10.netty -> io.netty
            // We just need this check for dev environment
            if (buf is ByteBuf) {
                return FriendlyByteBuf(buf)
            }

            val array = ByteArray(buf.readableBytes())
            buf.readBytes(array)
            return FriendlyByteBuf(Unpooled.wrappedBuffer(array))
        }
    }
}