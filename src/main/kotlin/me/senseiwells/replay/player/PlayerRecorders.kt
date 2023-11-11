package me.senseiwells.replay.player

import me.senseiwells.replay.config.ReplayConfig
import me.senseiwells.replay.rejoin.RejoinedReplayPlayer
import net.minecraft.server.level.ServerPlayer
import java.util.*

object PlayerRecorders {
    private val players = LinkedHashMap<UUID, PlayerRecorder>()

    @JvmField
    var predicate = ReplayConfig.predicate

    @JvmStatic
    fun create(player: ServerPlayer): PlayerRecorder {
        if (this.players.containsKey(player.uuid)) {
            throw IllegalArgumentException("Player already has a recorder")
        }
        if (player is RejoinedReplayPlayer) {
            throw IllegalArgumentException("Cannot create a replay for a rejoining player")
        }
        val recorder = PlayerRecorder(player, ReplayConfig.recordingPath.resolve(player.stringUUID))
        this.players[player.uuid] = recorder
        return recorder

    }

    @JvmStatic
    fun has(player: ServerPlayer): Boolean {
        return this.players.containsKey(player.uuid)
    }

    @JvmStatic
    fun get(player: ServerPlayer): PlayerRecorder? {
        return this.players[player.uuid]
    }

    @JvmStatic
    fun remove(player: ServerPlayer): PlayerRecorder? {
        return this.players.remove(player.uuid)
    }

    @JvmStatic
    fun all(): Iterable<PlayerRecorder> {
        return this.players.values
    }
}