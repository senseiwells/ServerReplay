package me.senseiwells.replay.rejoin

import io.netty.util.concurrent.Future
import io.netty.util.concurrent.GenericFutureListener
import me.senseiwells.replay.ServerReplay
import net.minecraft.network.Connection
import net.minecraft.network.protocol.Packet
import net.minecraft.server.network.ServerGamePacketListenerImpl

class RejoinGamePacketListener(
    replay: RejoinedReplayPlayer,
    connection: Connection
): ServerGamePacketListenerImpl(replay.server, connection, replay) {
    // We don't store extra fields in this class because certain
    // mods like sending packets DURING the construction, *cough* syncmatica *cough*
    private val replay: RejoinedReplayPlayer
        get() = this.player as RejoinedReplayPlayer


    override fun send(packet: Packet<*>, listener: GenericFutureListener<out Future<in Void>>?) {
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