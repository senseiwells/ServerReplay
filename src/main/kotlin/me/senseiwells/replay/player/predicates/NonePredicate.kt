package me.senseiwells.replay.player.predicates

import com.google.gson.JsonObject
import net.minecraft.server.level.ServerPlayer

class NonePredicate: ReplayPlayerPredicate(id) {
    override fun shouldRecord(player: ServerPlayer): Boolean {
        return false
    }

    companion object: PredicateFactory {
        override val id = "none"

        override fun create(data: JsonObject): ReplayPlayerPredicate {
            return NonePredicate()
        }
    }
}