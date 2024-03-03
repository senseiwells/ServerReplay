package me.senseiwells.replay.player

import com.mojang.authlib.GameProfile
import me.senseiwells.replay.ServerReplay
import me.senseiwells.replay.recorder.ReplayRecorder
import me.senseiwells.replay.recorder.RecorderRecoverer
import me.senseiwells.replay.rejoin.RejoinedReplayPlayer
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import java.util.*
import java.util.concurrent.CompletableFuture
import kotlin.collections.ArrayList

object PlayerRecorders {
    private val players = LinkedHashMap<UUID, PlayerRecorder>()
    private val closing = HashMap<UUID, PlayerRecorder>()

    @JvmStatic
    fun create(player: ServerPlayer): ReplayRecorder {
        if (player is RejoinedReplayPlayer) {
            throw IllegalArgumentException("Cannot create a replay for a rejoining player")
        }
        return this.create(player.server, player.gameProfile)
    }

    @JvmStatic
    fun create(server: MinecraftServer, profile: GameProfile): ReplayRecorder {
        if (this.players.containsKey(profile.id)) {
            throw IllegalArgumentException("Player already has a recorder")
        }

        val recorder = PlayerRecorder(
            server,
            profile,
            ServerReplay.config.playerRecordingPath.resolve(profile.id.toString())
        )
        this.players[profile.id] = recorder
        RecorderRecoverer.add(recorder)
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
        return this.players[uuid]
    }

    @JvmStatic
    fun recorders(): Collection<PlayerRecorder> {
        return ArrayList(this.players.values)
    }

    @JvmStatic
    fun closing(): Collection<PlayerRecorder> {
        return ArrayList(this.closing.values)
    }

    internal fun close(server: MinecraftServer, recorder: PlayerRecorder, future: CompletableFuture<Long>) {
        val uuid = recorder.recordingPlayerUUID
        this.players.remove(uuid)
        this.closing[uuid] = recorder
        future.thenRunAsync({
            this.closing.remove(uuid)
            RecorderRecoverer.remove(recorder)
        }, server)
    }
}