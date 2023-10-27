package me.senseiwells.replay.spoof

import net.minecraft.network.Connection
import net.minecraft.network.protocol.PacketFlow

class SpoofedConnection: Connection(PacketFlow.SERVERBOUND)