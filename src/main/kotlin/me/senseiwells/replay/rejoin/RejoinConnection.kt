package me.senseiwells.replay.rejoin

import net.minecraft.network.Connection
import net.minecraft.network.protocol.PacketFlow

class RejoinConnection: Connection(PacketFlow.SERVERBOUND)