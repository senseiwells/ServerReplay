package me.senseiwells.replay.player.predicates

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import net.minecraft.server.level.ServerPlayer

class UUIDPredicate(
    private val uuids: List<String>
): ReplayPlayerPredicate(id) {
    override fun shouldRecord(player: ServerPlayer): Boolean {
        return this.uuids.contains(player.stringUUID) || this.uuids.contains(player.stringUUID.replace("-", ""))
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