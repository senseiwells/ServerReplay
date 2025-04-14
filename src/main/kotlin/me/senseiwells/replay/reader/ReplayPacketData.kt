package me.senseiwells.replay.reader

import net.minecraft.network.ConnectionProtocol
import net.minecraft.network.protocol.Packet
import kotlin.time.Duration

data class ReplayPacketData(
    val protocol: ConnectionProtocol,
    val packet: Packet<*>,
    val timestamp: Duration,
    private val release: () -> Unit = { }
): AutoCloseable {
    override fun close() {
        this.release.invoke()
    }
}