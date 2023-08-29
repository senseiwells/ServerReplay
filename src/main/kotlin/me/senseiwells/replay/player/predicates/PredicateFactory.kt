package me.senseiwells.replay.player.predicates

import com.google.gson.JsonObject

interface PredicateFactory {
    val id: String

    fun create(data: JsonObject): ReplayPlayerPredicate
}