package me.senseiwells.replay.config.predicates

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.minecraft.server.permissions.Permission
import net.minecraft.server.permissions.PermissionLevel

@Serializable
@SerialName("has_op")
class HasOpPredicate(
    private val level: PermissionLevel
): ReplayPlayerPredicate() {
    override fun shouldRecord(context: ReplayPlayerContext): Boolean {
        return context.permissions.hasPermission(Permission.HasCommandLevel(this.level))
    }
}