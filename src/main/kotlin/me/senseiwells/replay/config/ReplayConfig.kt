package me.senseiwells.replay.config

import com.mojang.authlib.GameProfile
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.EncodeDefault.Mode
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import me.senseiwells.replay.ServerReplay
import me.senseiwells.replay.chunk.ChunkRecorders
import me.senseiwells.replay.config.chunk.ChunkAreaConfig
import me.senseiwells.replay.config.predicates.NonePredicate
import me.senseiwells.replay.config.predicates.ReplayPlayerContext
import me.senseiwells.replay.config.predicates.ReplayPlayerPredicate
import me.senseiwells.replay.config.serialization.DurationSerializer
import me.senseiwells.replay.config.serialization.PathSerializer
import me.senseiwells.replay.player.PlayerRecorders
import me.senseiwells.replay.util.FileSize
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.server.MinecraftServer
import org.apache.commons.lang3.SerializationException
import java.io.IOException
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.io.path.outputStream
import kotlin.time.Duration

@Serializable
@OptIn(ExperimentalSerializationApi::class)
data class ReplayConfig(
    @SerialName("enabled")
    var enabled: Boolean = false,
    @SerialName("debug")
    @EncodeDefault(Mode.NEVER)
    var debug: Boolean = false,
    @SerialName("async_thread_pool_size")
    @EncodeDefault(Mode.NEVER)
    var asyncThreadPoolSize: Int? = 1,
    @SerialName("world_name")
    var worldName: String = "World",
    @SerialName("server_name")
    var serverName: String = "Server",
    @SerialName("chunk_recording_path")
    @Serializable(with = PathSerializer::class)
    var chunkRecordingPath: Path = recordings.resolve("chunks"),
    @SerialName("player_recording_path")
    @Serializable(with = PathSerializer::class)
    var playerRecordingPath: Path = recordings.resolve("players"),
    @SerialName("player_recording_name")
    var playerRecordingName: String = "{uuid}",
    @SerialName("max_file_size")
    var maxFileSize: FileSize = FileSize("0GB"),
    @SerialName("restart_after_max_file_size")
    var restartAfterMaxFileSize: Boolean = false,
    @SerialName("max_duration")
    @Serializable(with = DurationSerializer::class)
    var maxDuration: Duration = Duration.ZERO,
    @SerialName("restart_after_max_duration")
    var restartAfterMaxDuration: Boolean = false,
    @SerialName("recover_unsaved_replays")
    var recoverUnsavedReplays: Boolean = true,
    @SerialName("include_compressed_in_status")
    var includeCompressedReplaySizeInStatus: Boolean = true,
    @SerialName("fixed_daylight_cycle")
    var fixedDaylightCycle: Long = -1L,
    @SerialName("chunk_recorder_load_radius")
    var chunkRecorderLoadRadius: Int = -1,
    @SerialName("pause_unloaded_chunks")
    var skipWhenChunksUnloaded: Boolean = false,
    @SerialName("pause_notify_players")
    var notifyPlayersLoadingChunks: Boolean = true,
    @SerialName("notify_admins_of_status")
    var notifyAdminsOfStatus: Boolean = true,
    @SerialName("fix_carpet_bot_view_distance")
    var fixCarpetBotViewDistance: Boolean = false,
    @SerialName("include_resource_packs")
    var includeResourcePacks: Boolean = true,
    @SerialName("ignore_sound_packets")
    var ignoreSoundPackets: Boolean = false,
    @SerialName("ignore_light_packets")
    var ignoreLightPackets: Boolean = true,
    @SerialName("ignore_chat_packets")
    var ignoreChatPackets: Boolean = false,
    @SerialName("ignore_action_bar_packets")
    var ignoreActionBarPackets: Boolean = false,
    @SerialName("ignore_scoreboard_packets")
    var ignoreScoreboardPackets: Boolean = false,
    @SerialName("optimize_explosion_packets")
    var optimizeExplosionPackets: Boolean = true,
    @SerialName("optimize_entity_packets")
    var optimizeEntityPackets: Boolean = false,
    @SerialName("record_voice_chat")
    var recordVoiceChat: Boolean = false,
    @JsonNames("replay_viewer_pack_ip")
    @SerialName("replay_server_ip")
    var replayServerIp: String? = null,
    @SerialName("allow_downloading_replays")
    var allowDownloadingReplays: Boolean = false,
    @SerialName("player_predicate")
    private var playerPredicate: ReplayPlayerPredicate = NonePredicate,
    @SerialName("chunks")
    private val chunks: List<ChunkAreaConfig> = listOf(),
) {
    fun getPlayerRecordingLocation(profile: GameProfile): Path {
        val path = this.playerRecordingName
            .replace("{uuid}", profile.id.toString())
            .replace("{username}", profile.name)
        return this.playerRecordingPath.resolve(path)
    }

    fun shouldRecordPlayer(context: ReplayPlayerContext): Boolean {
        return this.playerPredicate.shouldRecord(context)
    }

    @JvmOverloads
    fun startPlayers(server: MinecraftServer, log: Boolean = true) {
        for (player in server.playerList.players) {
            if (!PlayerRecorders.has(player) && this.shouldRecordPlayer(ReplayPlayerContext.of(player))) {
                PlayerRecorders.create(player).start(log)
            }
        }
    }

    @JvmOverloads
    fun startChunks(server: MinecraftServer, log: Boolean = true) {
        for (chunks in this.chunks) {
            val area = chunks.toChunkArea(server)
            if (area == null) {
                ServerReplay.logger.warn("Unable to find dimension ${chunks.dimension.location()} for chunk recording")
                continue
            }
            if (ChunkRecorders.isAvailable(area, chunks.name)) {
                val recorder = ChunkRecorders.create(area, chunks.name)
                recorder.start(log)
            }
        }
    }

    companion object {
        val recordings: Path = FabricLoader.getInstance().gameDir.resolve("recordings")
        val root: Path = FabricLoader.getInstance().configDir.resolve("ServerReplay")

        private val config = this.root.resolve("config.json")
        private val json = Json {
            encodeDefaults = true
            prettyPrint = true
            prettyPrintIndent = "  "
            ignoreUnknownKeys = true
        }

        fun read(): ReplayConfig {
            if (!this.config.exists()) {
                ServerReplay.logger.info("Generating default config")
                return ReplayConfig().also { this.write(it) }
            }
            return try {
                this.config.inputStream().use {
                    json.decodeFromStream(it)
                }
            } catch (e: Exception) {
                ServerReplay.logger.error("Failed to read replay config, generating default", e)
                ReplayConfig().also { this.write(it) }
            }
        }

        @JvmStatic
        fun write(config: ReplayConfig) {
            try {
                this.config.parent.createDirectories()
                this.config.outputStream().use {
                    json.encodeToStream(config, it)
                }
            } catch (e: IOException) {
                ServerReplay.logger.error("Failed to write replay config", e)
            } catch (e: SerializationException) {
                ServerReplay.logger.error("Failed to serialize replay config", e)
            }
        }

        internal fun toJson(config: ReplayConfig): JsonElement {
            return json.encodeToJsonElement(config)
        }
    }
}
