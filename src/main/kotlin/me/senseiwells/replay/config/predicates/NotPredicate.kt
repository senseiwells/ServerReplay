package me.senseiwells.replay.config.predicates

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("not")
class NotPredicate(
    private val predicate: ReplayPlayerPredicate
): ReplayPlayerPredicate() {
    override fun shouldRecord(context: ReplayPlayerContext): Boolean {
        return !this.predicate.shouldRecord(context)
    }
}