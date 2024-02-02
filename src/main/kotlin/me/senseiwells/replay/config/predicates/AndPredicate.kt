package me.senseiwells.replay.config.predicates

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("and")
class AndPredicate(
    private val predicates: List<ReplayPlayerPredicate>
): ReplayPlayerPredicate() {
    override fun shouldRecord(context: ReplayPlayerContext): Boolean {
        return this.predicates.all { it.shouldRecord(context) }
    }
}