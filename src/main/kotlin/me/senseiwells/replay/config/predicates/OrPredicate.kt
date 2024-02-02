package me.senseiwells.replay.config.predicates

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("or")
class OrPredicate(
    private val predicates: List<ReplayPlayerPredicate>
): ReplayPlayerPredicate() {
    override fun shouldRecord(context: ReplayPlayerContext): Boolean {
        return this.predicates.any { it.shouldRecord(context) }
    }
}