package me.senseiwells.replay.player

import net.fabricmc.loader.api.FabricLoader
import net.minecraft.server.level.ServerPlayer
import java.nio.file.Path
import java.util.*

object PlayerRecorders {
    private val players = LinkedHashMap<UUID, PlayerRecorder>()

    @JvmStatic
    fun create(player: ServerPlayer): PlayerRecorder {
        return this.players.getOrPut(player.uuid) {
            PlayerRecorder(player, this.getRecordingPath().resolve(player.stringUUID))
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

    fun getRecordingPath(): Path {
        return FabricLoader.getInstance().gameDir.resolve("recordings")
    }

    @JvmStatic
    fun shouldRecordPlayer(player: ServerPlayer): Boolean {
        return true
    }
}