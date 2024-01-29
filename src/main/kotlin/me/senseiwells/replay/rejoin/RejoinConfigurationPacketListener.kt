package me.senseiwells.replay.rejoin

import me.senseiwells.replay.ServerReplay
import me.senseiwells.replay.mixin.rejoin.ServerConfigurationPacketListenerImplAccessor
import net.minecraft.network.Connection
import net.minecraft.network.PacketSendListener
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.common.ServerboundPongPacket
import net.minecraft.server.network.CommonListenerCookie
import net.minecraft.server.network.ConfigurationTask
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl
import java.util.*

class RejoinConfigurationPacketListener(
    private val replay: RejoinedReplayPlayer,
    connection: Connection,
    cookies: CommonListenerCookie
): ServerConfigurationPacketListenerImpl(replay.server, connection, cookies) {
    @Suppress("CAST_NEVER_SUCCEEDS")
    private val tasks: Queue<ConfigurationTask>
        get() = (this as ServerConfigurationPacketListenerImplAccessor).tasks()

    private var handledPong = false

    override fun startConfiguration() {
        super.startConfiguration()

        if (!this.handledPong) {
            this.handledPong = true
            // Fabric api pings the client before doing any more
            // configuration checks.
            // We must manually pong.
            this.handlePong(ServerboundPongPacket(0))
            return
        }

        // We do not have to wait for the client to respond
        for (task in this.tasks) {
            task.start(this::send)
        }
    }

    override fun send(packet: Packet<*>, packetSendListener: PacketSendListener?) {
        try {
            this.replay.recorder.record(packet)
        } catch (e: Exception) {
            ServerReplay.logger.error(
                "Failed to record rejoin configuration packet {} for {}",
                packet,
                this.replay.original.scoreboardName,
                e
            )
        }
    }
}