package me.senseiwells.replay.player.predicates

import com.mojang.authlib.GameProfile
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.scores.Team
import java.util.*

data class ReplayPlayerContext(
    val server: MinecraftServer,
    val profile: GameProfile
) {
    val name: String get() = this.profile.name
    val uuid: UUID get() = this.profile.id
    val team: Team? get() = this.server.scoreboard.getPlayersTeam(this.name)
    val permissions: Int get() = this.server.getProfilePermissions(this.profile)

    companion object {
        fun of(player: ServerPlayer): ReplayPlayerContext {
            return ReplayPlayerContext(player.server, player.gameProfile)
        }
    }
}