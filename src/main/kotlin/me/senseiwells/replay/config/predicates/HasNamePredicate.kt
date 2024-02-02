package me.senseiwells.replay.config.predicates

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("has_name")
class HasNamePredicate(
    private val names: List<String>
): ReplayPlayerPredicate() {
    override fun shouldRecord(context: ReplayPlayerContext): Boolean {
        return this.names.contains(context.name)
    }
}