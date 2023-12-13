package me.senseiwells.replay.player.predicates

import com.google.gson.JsonObject
import me.senseiwells.replay.config.ReplayConfig.deserializePlayerPredicate

class AndPredicate(
    private val first: ReplayPlayerPredicate,
    private val second: ReplayPlayerPredicate
): ReplayPlayerPredicate(id) {
    override fun shouldRecord(context: ReplayPlayerContext): Boolean {
        return this.first.shouldRecord(context) && this.second.shouldRecord(context)
    }

    override fun serialiseAdditional(json: JsonObject) {
        json.add("first", this.first.serialise())
        json.add("second", this.second.serialise())
    }

    companion object: PredicateFactory {
        override val id = "and"

        override fun create(data: JsonObject): ReplayPlayerPredicate {
            return AndPredicate(
                deserializePlayerPredicate(data.getAsJsonObject("first")),
                deserializePlayerPredicate(data.getAsJsonObject("second"))
            )
        }
    }
}