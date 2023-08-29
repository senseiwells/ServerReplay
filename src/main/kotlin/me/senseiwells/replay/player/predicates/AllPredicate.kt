package me.senseiwells.replay.player.predicates

import com.google.gson.JsonObject
import net.minecraft.server.level.ServerPlayer

class AllPredicate: ReplayPlayerPredicate(id) {
    override fun shouldRecord(player: ServerPlayer): Boolean {
        return true
    }

    companion object: PredicateFactory {
        override val id = "all"

        override fun create(data: JsonObject): ReplayPlayerPredicate {
            return AllPredicate()
        }
    }
}