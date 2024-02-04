package me.senseiwells.replay.config.predicates

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("all")
data object AllPredicate: ReplayPlayerPredicate() {
    override fun shouldRecord(context: ReplayPlayerContext): Boolean {
        return true
    }
}