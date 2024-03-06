package me.senseiwells.replay.config.predicates

import com.mojang.authlib.GameProfile
import net.fabricmc.fabric.api.entity.FakePlayer
import net.fabricmc.fabric.api.util.TriState
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.scores.Team
import java.util.*

data class ReplayPlayerContext(
    val server: MinecraftServer,
    val profile: GameProfile,
    private val player: ServerPlayer? = null
) {
    val name: String get() = this.profile.name
    val uuid: UUID get() = this.profile.id
    val team: Team? get() = this.server.scoreboard.getPlayersTeam(this.name)
    val permissions: Int get() = this.server.getProfilePermissions(this.profile)

    fun isFakePlayer(): Boolean {
        // Technically we could have a fake player join where they
        // go through the login process, but why would you do that??
        return this.player != null && this.player::class != ServerPlayer::class
    }

    companion object {
        fun of(player: ServerPlayer): ReplayPlayerContext {
            return ReplayPlayerContext(player.server, player.gameProfile, player)
        }
    }
}