package me.senseiwells.replay.recorder.player

import com.mojang.authlib.GameProfile
import me.senseiwells.replay.ServerReplay
import me.senseiwells.replay.recorder.rejoin.RejoinedReplayPlayer
import me.senseiwells.replay.util.processor.RecorderRecoverer
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import java.util.*
import java.util.concurrent.CompletableFuture

/**
 * This object manages all [PlayerRecorder]s.
 */
object PlayerRecorders {
    private val players = LinkedHashMap<UUID, PlayerRecorder>()
    private val closing = HashMap<UUID, PlayerRecorder>()

    /**
     * This creates a [PlayerRecorder] for a given [player].
     *
     * @param player The player you want to record.
     * @return The created recorder.
     * @see create
     */
    @JvmStatic
    fun create(player: ServerPlayer): PlayerRecorder {
        if (player is RejoinedReplayPlayer) {
            throw IllegalArgumentException("Cannot create a replay for a rejoining player")
        }
        return create(player.server, player.gameProfile)
    }

    /**
     * This creates a [PlayerRecorder] for a given [profile].
     *
     * @param server The [MinecraftServer] instance.
     * @param profile The profile of the player you are going to record.
     * @return The created recorder.
     * @see create
     */
    @JvmStatic
    fun create(server: MinecraftServer, profile: GameProfile): PlayerRecorder {
        if (players.containsKey(profile.id)) {
            throw IllegalArgumentException("Player already has a recorder")
        }

        val path = ServerReplay.config.getPlayerRecordingLocation(profile)
        val recorder = PlayerRecorder(
            server,
            profile,
            ServerReplay.config.saverType.create(path)
        )
        players[profile.id] = recorder
        RecorderRecoverer.add(recorder)
        return recorder
    }

    /**
     * Checks whether a player has a recorder.
     *
     * @param player The player to check.
     * @return Whether the player has a recorder.
     */
    @JvmStatic
    fun has(player: ServerPlayer): Boolean {
        return players.containsKey(player.uuid)
    }

    /**
     * Gets a player recorder if one is present.
     *
     * @param player The player to get the recorder for.
     * @return The [PlayerRecorder], null if it is not present.
     */
    @JvmStatic
    fun get(player: ServerPlayer): PlayerRecorder? {
        return getByUUID(player.uuid)
    }

    /**
     * Gets a player recorder if one is present using the
     * player's UUID.
     *
     * @param uuid The uuid of the player to get the recorder for.
     * @return The [PlayerRecorder], null if it is not present.
     */
    @JvmStatic
    fun getByUUID(uuid: UUID): PlayerRecorder? {
        return players[uuid]
    }

    /**
     * Gets a collection of all the currently recording player recorders.
     *
     * @return A collection of all the player recorders.
     */
    @JvmStatic
    fun recorders(): Collection<PlayerRecorder> {
        return ArrayList(players.values)
    }

    /**
     * Gets a collection of all the currently closing player recorders.
     *
     * @return A collection of all the closing player recorders.
     */
    @JvmStatic
    fun closing(): Collection<PlayerRecorder> {
        return ArrayList(closing.values)
    }

    internal fun close(server: MinecraftServer, recorder: PlayerRecorder, future: CompletableFuture<Long>) {
        val uuid = recorder.recordingPlayerUUID
        players.remove(uuid)
        closing[uuid] = recorder
        future.thenRunAsync({
            closing.remove(uuid)
            RecorderRecoverer.remove(recorder)
        }, server)
    }
}