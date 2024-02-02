package me.senseiwells.replay.config.predicates

import kotlinx.serialization.Serializable

@Serializable
sealed class ReplayPlayerPredicate {
    abstract fun shouldRecord(context: ReplayPlayerContext): Boolean
}