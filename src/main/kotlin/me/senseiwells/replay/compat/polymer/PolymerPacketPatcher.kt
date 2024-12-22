package me.senseiwells.replay.compat.polymer

import eu.pb4.polymer.core.impl.networking.PacketPatcher
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.network.protocol.Packet
import net.minecraft.server.network.ServerCommonPacketListenerImpl

object PolymerPacketPatcher {
    private val hasPolymer = FabricLoader.getInstance().isModLoaded("polymer-core")

    fun replace(listener: ServerCommonPacketListenerImpl, packet: Packet<*>): Packet<*> {
        if (this.hasPolymer) {
            return PacketPatcher.replace(listener, packet)
        }
        return packet
    }
}