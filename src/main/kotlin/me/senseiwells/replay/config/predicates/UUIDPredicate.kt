package me.senseiwells.replay.config.predicates

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("has_uuid")
class UUIDPredicate(
    private val uuids: List<String>
): ReplayPlayerPredicate() {
    override fun shouldRecord(context: ReplayPlayerContext): Boolean {
        val uuid = context.uuid.toString()
        return this.uuids.contains(uuid) || this.uuids.contains(uuid.replace("-", ""))
    }
}