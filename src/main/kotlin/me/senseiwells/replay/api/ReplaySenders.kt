package me.senseiwells.replay.api

object ReplaySenders {
    internal val senders = ArrayList<RejoinedPacketSender>()
    
    fun addSender(sender: RejoinedPacketSender) {
        this.senders.add(sender)
    }
}