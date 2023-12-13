package me.senseiwells.replay.player.predicates

import com.google.gson.JsonObject

abstract class ReplayPlayerPredicate(
    val type: String
) {
    abstract fun shouldRecord(context: ReplayPlayerContext): Boolean

    fun serialise(): JsonObject {
        val json = JsonObject()
        json.addProperty("type", this.type)
        this.serialiseAdditional(json)
        return json
    }

    protected open fun serialiseAdditional(json: JsonObject) {

    }
}