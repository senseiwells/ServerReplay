package me.senseiwells.replay.viewer

import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientboundKeepAlivePacket

object ReplayViewerPackets {
    private val ALLOWED_CLIENTBOUND: Set<Class<out Packet<*>>> = setOf(
        ClientboundKeepAlivePacket::class.java
    )

    @JvmStatic
    fun clientboundBypass(packet: Packet<*>): Boolean {
        return ALLOWED_CLIENTBOUND.contains(packet::class.java)
    }
}