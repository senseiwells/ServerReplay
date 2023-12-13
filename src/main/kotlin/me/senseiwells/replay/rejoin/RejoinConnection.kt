package me.senseiwells.replay.rejoin

import me.senseiwells.replay.mixin.rejoin.ConnectionAccessor
import net.minecraft.network.Connection
import net.minecraft.network.PacketListener
import net.minecraft.network.protocol.PacketFlow

class RejoinConnection: Connection(PacketFlow.SERVERBOUND) {
    @Suppress("CAST_NEVER_SUCCEEDS")
    override fun setListener(handler: PacketListener) {
        // We don't check if it's valid, we just trust it is
        (this as ConnectionAccessor).setPacketListener(handler)
    }
}