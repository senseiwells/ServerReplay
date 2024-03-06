package me.senseiwells.replay.config.predicates

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("is_fake")
data object IsFakePredicate: ReplayPlayerPredicate() {
    override fun shouldRecord(context: ReplayPlayerContext): Boolean {
        return context.isFakePlayer()
    }
}