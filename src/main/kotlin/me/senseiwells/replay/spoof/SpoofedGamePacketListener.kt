package me.senseiwells.replay.spoof

import me.senseiwells.replay.player.PlayerRecorders
import net.minecraft.network.Connection
import net.minecraft.network.PacketSendListener
import net.minecraft.network.protocol.Packet
import net.minecraft.server.network.ServerGamePacketListenerImpl

class SpoofedGamePacketListener(
    private val replay: SpoofedReplayPlayer,
    connection: Connection
): ServerGamePacketListenerImpl(replay.server, connection, replay) {
    override fun send(packet: Packet<*>, listener: PacketSendListener?) {
        PlayerRecorders.get(this.replay.original)?.record(packet)
    }
}