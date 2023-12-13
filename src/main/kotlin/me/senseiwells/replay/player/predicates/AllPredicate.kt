package me.senseiwells.replay.player.predicates

import com.google.gson.JsonObject

class AllPredicate: ReplayPlayerPredicate(id) {
    override fun shouldRecord(context: ReplayPlayerContext): Boolean {
        return true
    }

    companion object: PredicateFactory {
        override val id = "all"

        override fun create(data: JsonObject): ReplayPlayerPredicate {
            return AllPredicate()
        }
    }
}