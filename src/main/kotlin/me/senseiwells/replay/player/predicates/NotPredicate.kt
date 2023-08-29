package me.senseiwells.replay.player.predicates

import com.google.gson.JsonObject
import me.senseiwells.replay.config.ReplayConfig
import net.minecraft.server.level.ServerPlayer

class NotPredicate(
    private val predicate: ReplayPlayerPredicate
): ReplayPlayerPredicate(id) {
    override fun shouldRecord(player: ServerPlayer): Boolean {
        return !this.predicate.shouldRecord(player)
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