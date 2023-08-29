package me.senseiwells.replay.player.predicates

import com.google.gson.JsonObject
import net.minecraft.server.level.ServerPlayer

abstract class ReplayPlayerPredicate(
    val type: String
) {
    abstract fun shouldRecord(player: ServerPlayer): Boolean

    fun serialise(): JsonObject {
        val json = JsonObject()
        json.addProperty("type", this.type)
        this.serialiseAdditional(json)
        return json
    }

    protected open fun serialiseAdditional(json: JsonObject) {

    }
}