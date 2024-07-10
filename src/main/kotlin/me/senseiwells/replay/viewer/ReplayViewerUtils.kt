package me.senseiwells.replay.viewer

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import me.senseiwells.replay.ducks.`ServerReplay$ReplayViewable`
import me.senseiwells.replay.mixin.viewer.ClientboundPlayerInfoUpdatePacketAccessor
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.ProtocolInfo
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.configuration.ConfigurationProtocols
import net.minecraft.network.protocol.game.ClientGamePacketListener
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket.Action
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket.Entry
import net.minecraft.server.network.ServerGamePacketListenerImpl
import java.util.*
import com.replaymod.replaystudio.protocol.Packet as ReplayPacket

object ReplayViewerUtils {
    fun ReplayPacket.toClientboundPlayPacket(protocol: ProtocolInfo<ClientGamePacketListener>): Packet<*> {
        val wrapper = FriendlyByteBuf(Unpooled.buffer())
        val buf = toByteBuf(this.buf)
        try {
            wrapper.writeVarInt(this.id)
            wrapper.writeBytes(buf)
            return protocol.codec().decode(wrapper)
        } finally {
            buf.release()
            wrapper.release()
        }
    }

    fun ReplayPacket.toClientboundConfigurationPacket(): Packet<*> {
        val wrapper = FriendlyByteBuf(Unpooled.buffer())
        val buf = toByteBuf(this.buf)
        try {
            wrapper.writeVarInt(this.id)
            wrapper.writeBytes(buf)
            return ConfigurationProtocols.CLIENTBOUND.codec().decode(wrapper)
        } finally {
            buf.release()
            wrapper.release()
        }
    }

    private fun toByteBuf(buf: com.github.steveice10.netty.buffer.ByteBuf): ByteBuf {
        // When we compile we map steveice10.netty -> io.netty
        // We just need this check for dev environment
        @Suppress("USELESS_IS_CHECK")
        if (buf is ByteBuf) {
            return buf
        }

        val array = ByteArray(buf.readableBytes())
        buf.readBytes(array)
        return Unpooled.wrappedBuffer(array)
    }

    fun ServerGamePacketListenerImpl.sendReplayPacket(packet: Packet<*>) {
        (this as `ServerReplay$ReplayViewable`).`replay$sendReplayViewerPacket`(packet)
    }

    fun ServerGamePacketListenerImpl.startViewingReplay(viewer: ReplayViewer) {
        (this as `ServerReplay$ReplayViewable`).`replay$startViewingReplay`(viewer)
    }

    fun ServerGamePacketListenerImpl.stopViewingReplay() {
        (this as `ServerReplay$ReplayViewable`).`replay$stopViewingReplay`()
    }

    fun ServerGamePacketListenerImpl.getViewingReplay(): ReplayViewer? {
        return (this as `ServerReplay$ReplayViewable`).`replay$getViewingReplay`()
    }

    fun createClientboundPlayerInfoUpdatePacket(
        actions: EnumSet<Action>,
        entries: List<Entry>
    ): ClientboundPlayerInfoUpdatePacket {
        val packet = ClientboundPlayerInfoUpdatePacket(actions, listOf())
        @Suppress("KotlinConstantConditions")
        (packet as ClientboundPlayerInfoUpdatePacketAccessor).setEntries(entries)
        return packet
    }
}