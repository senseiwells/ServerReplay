package me.senseiwells.replay.player.predicates

import com.google.gson.JsonObject
import me.senseiwells.replay.config.ReplayConfig

class NotPredicate(
    private val predicate: ReplayPlayerPredicate
): ReplayPlayerPredicate(id) {
    override fun shouldRecord(context: ReplayPlayerContext): Boolean {
        return !this.predicate.shouldRecord(context)
    }

    override fun serialiseAdditional(json: JsonObject) {
        json.add("predicate", this.predicate.serialise())
    }

    companion object: PredicateFactory {
        override val id = "not"

        override fun create(data: JsonObject): ReplayPlayerPredicate {
            return NotPredicate(ReplayConfig.deserializePlayerPredicate(data.getAsJsonObject("predicate")))
        }
    }
}