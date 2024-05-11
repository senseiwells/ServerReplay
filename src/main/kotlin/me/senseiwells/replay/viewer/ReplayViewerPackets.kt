package me.senseiwells.replay.viewer

import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.common.ClientboundKeepAlivePacket
import net.minecraft.network.protocol.common.ServerboundKeepAlivePacket
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket

object ReplayViewerPackets {
    private val ALLOWED_SERVERBOUND: Set<Class<out Packet<*>>> = setOf(
        ServerboundChatCommandPacket::class.java,
        ServerboundKeepAlivePacket::class.java
    )
    private val ALLOWED_CLIENTBOUND: Set<Class<out Packet<*>>> = setOf(
        ClientboundKeepAlivePacket::class.java
    )

    @JvmStatic
    fun clientboundBypass(packet: Packet<*>): Boolean {
        return ALLOWED_CLIENTBOUND.contains(packet::class.java)
    }

    @JvmStatic
    fun serverboundBypass(packet: Packet<*>): Boolean {
        return ALLOWED_SERVERBOUND.contains(packet::class.java)
    }
}