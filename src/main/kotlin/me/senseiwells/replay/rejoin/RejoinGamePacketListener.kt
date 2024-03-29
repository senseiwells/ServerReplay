package me.senseiwells.replay.rejoin

import me.senseiwells.replay.ServerReplay
import net.minecraft.network.Connection
import net.minecraft.network.PacketSendListener
import net.minecraft.network.protocol.Packet
import net.minecraft.server.network.CommonListenerCookie
import net.minecraft.server.network.ServerGamePacketListenerImpl

class RejoinGamePacketListener(
    replay: RejoinedReplayPlayer,
    connection: Connection,
    cookies: CommonListenerCookie
): ServerGamePacketListenerImpl(replay.server, connection, replay, cookies) {
    // We don't store extra fields in this class because certain
    // mods like sending packets DURING the construction, *cough* syncmatica *cough*
    private val replay: RejoinedReplayPlayer
        get() = this.player as RejoinedReplayPlayer

    override fun send(packet: Packet<*>, listener: PacketSendListener?) {
        try {
            this.replay.recorder.record(packet)
        } catch (e: Exception) {
            ServerReplay.logger.error(
                "Failed to record rejoin packet {} for {}",
                packet,
                this.player.scoreboardName,
                e
            )
        }
    }
}