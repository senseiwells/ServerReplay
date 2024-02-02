package me.senseiwells.replay.config.predicates

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("in_team")
class InTeamPredicate(
    val teams: List<String>
): ReplayPlayerPredicate() {
    override fun shouldRecord(context: ReplayPlayerContext): Boolean {
        val team = context.team?.name ?: return false
        return this.teams.contains(team)
    }
}