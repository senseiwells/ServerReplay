package me.senseiwells.replay.api

import org.jetbrains.annotations.ApiStatus.Experimental

object ReplaySenders {
    internal val senders = ArrayList<RejoinedPacketSender>()

    @Experimental
    fun addSender(sender: RejoinedPacketSender) {
        this.senders.add(sender)
    }
}