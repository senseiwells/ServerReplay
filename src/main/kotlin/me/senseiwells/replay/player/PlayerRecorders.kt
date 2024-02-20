package me.senseiwells.replay.player

import com.mojang.authlib.GameProfile
import me.senseiwells.replay.ServerReplay
import me.senseiwells.replay.api.RejoinedPacketSender
import me.senseiwells.replay.recorder.ReplayRecorder
import me.senseiwells.replay.rejoin.RejoinedReplayPlayer
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import java.util.*
import java.util.concurrent.CompletableFuture
import kotlin.collections.ArrayList

object PlayerRecorders {
    private val players = LinkedHashMap<UUID, PlayerRecorder>()
    private val closing = HashMap<UUID, CompletableFuture<Long>>()

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

        // If a player rejoins before their previous one has fully closed,
        // we wait for it to fully close; this blocks the main thread;
        // however, it's unlikely that this will be significant.
        this.closing[profile.id]?.join()

        val recorder = PlayerRecorder(
            server,
            profile,
            ServerReplay.config.playerRecordingPath.resolve(profile.id.toString())
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
        return this.players[uuid]
    }

    @JvmStatic
    fun all(): Collection<PlayerRecorder> {
        return ArrayList(this.players.values)
    }

    internal fun close(server: MinecraftServer, uuid: UUID, future: CompletableFuture<Long>) {
        this.players.remove(uuid)
        this.closing[uuid] = future
        future.thenRunAsync({ this.closing.remove(uuid) }, server)
    }
}