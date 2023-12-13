package me.senseiwells.replay.player.predicates

import com.google.gson.JsonObject

class HasOpPredicate(
    private val level: Int
): ReplayPlayerPredicate(id) {
    override fun shouldRecord(context: ReplayPlayerContext): Boolean {
        return context.permissions >= this.level
    }

    override fun serialiseAdditional(json: JsonObject) {
        json.addProperty("level", this.level)
    }

    companion object: PredicateFactory {
        override val id = "has_op"

        override fun create(data: JsonObject): ReplayPlayerPredicate {
            return HasOpPredicate(data.get("level").asInt)
        }
    }
}