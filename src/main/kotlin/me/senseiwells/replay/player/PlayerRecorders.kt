package me.senseiwells.replay.player

import me.senseiwells.replay.config.Config
import net.minecraft.server.level.ServerPlayer
import java.util.*

object PlayerRecorders {
    private val players = LinkedHashMap<UUID, PlayerRecorder>()

    @JvmField
    var predicate = Config.predicate

    @JvmStatic
    fun create(player: ServerPlayer): PlayerRecorder {
        return this.players.getOrPut(player.uuid) {
            PlayerRecorder(player, Config.recordingPath.resolve(player.stringUUID))
        }
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