package me.senseiwells.replay.player.predicates

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import net.minecraft.server.level.ServerPlayer

class HasNamePredicate(
    private val names: List<String>
): ReplayPlayerPredicate(id) {
    override fun shouldRecord(player: ServerPlayer): Boolean {
        return this.names.contains(player.scoreboardName)
    }

    override fun serialiseAdditional(json: JsonObject) {
        val names = JsonArray()
        for (name in this.names) {
            names.add(name)
        }
        json.add("names", names)
    }

    companion object: PredicateFactory {
        override val id = "has_name"

        override fun create(data: JsonObject): ReplayPlayerPredicate {
            return HasNamePredicate(data.getAsJsonArray("names").map { it.asString })
        }
    }
}