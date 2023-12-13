package me.senseiwells.replay.player.predicates

import com.google.gson.JsonObject

class NonePredicate: ReplayPlayerPredicate(id) {
    override fun shouldRecord(context: ReplayPlayerContext): Boolean {
        return false
    }

    companion object: PredicateFactory {
        override val id = "none"

        override fun create(data: JsonObject): ReplayPlayerPredicate {
            return NonePredicate()
        }
    }
}