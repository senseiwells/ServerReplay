package me.senseiwells.replay.player.predicates

import com.google.gson.JsonArray
import com.google.gson.JsonObject

class UUIDPredicate(
    private val uuids: List<String>
): ReplayPlayerPredicate(id) {
    override fun shouldRecord(context: ReplayPlayerContext): Boolean {
        val uuid = context.uuid.toString()
        return this.uuids.contains(uuid) || this.uuids.contains(uuid.replace("-", ""))
    }

    override fun serialiseAdditional(json: JsonObject) {
        val uuids = JsonArray()
        for (uuid in this.uuids) {
            uuids.add(uuid)
        }
        json.add("uuids", uuids)
    }

    companion object: PredicateFactory {
        override val id = "has_uuid"

        override fun create(data: JsonObject): ReplayPlayerPredicate {
            return UUIDPredicate(data.getAsJsonArray("uuids").map { it.asString })
        }
    }
}