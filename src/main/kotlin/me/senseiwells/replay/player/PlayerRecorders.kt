package me.senseiwells.replay.player

import com.mojang.authlib.GameProfile
import me.senseiwells.replay.config.ReplayConfig
import me.senseiwells.replay.rejoin.RejoinedReplayPlayer
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import java.util.*

object PlayerRecorders {
    private val players = LinkedHashMap<UUID, PlayerRecorder>()

    @JvmField
    var predicate = ReplayConfig.predicate

    @JvmStatic
    fun create(player: ServerPlayer): PlayerRecorder {
        if (player is RejoinedReplayPlayer) {
            throw IllegalArgumentException("Cannot create a replay for a rejoining player")
        }
        return this.create(player.server, player.gameProfile)
    }

    @JvmStatic
    fun create(server: MinecraftServer, profile: GameProfile): PlayerRecorder {
        if (this.players.containsKey(profile.id)) {
            throw IllegalArgumentException("Player already has a recorder")
        }
        val recorder = PlayerRecorder(
            server,
            profile,
            ReplayConfig.recordingPath.resolve(profile.id.toString())
        )
        this.players[profile.id] = recorder
        return recorder
    }

    @JvmStatic
    fun has(player: ServerPlayer): Boolean {
        return this.players.containsKey(player.uuid)
    }

    @JvmStatic
    fun get(player: ServerPlayer): PlayerRecorder? {
        return this.getByUUID(player.uuid)
    }

    @JvmStatic
    fun getByUUID(uuid: UUID): PlayerRecorder? {
        return this.players[uuid];
    }

    @JvmStatic
    fun remove(player: ServerPlayer): PlayerRecorder? {
        return this.removeByUUID(player.uuid)
    }

    @JvmStatic
    fun removeByUUID(uuid: UUID): PlayerRecorder? {
        return this.players.remove(uuid)
    }

    @JvmStatic
    fun all(): Collection<PlayerRecorder> {
        return this.players.values
    }
}