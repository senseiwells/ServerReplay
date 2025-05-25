package me.senseiwells.replay.config

import com.mojang.authlib.GameProfile
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.EncodeDefault.Mode
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import me.senseiwells.replay.ServerReplay
import me.senseiwells.replay.config.chunk.ChunkAreaConfig
import me.senseiwells.replay.config.predicates.NonePredicate
import me.senseiwells.replay.config.predicates.ReplayPlayerContext
import me.senseiwells.replay.config.predicates.ReplayPlayerPredicate
import me.senseiwells.replay.config.serialization.DurationSerializer
import me.senseiwells.replay.config.serialization.PathSerializer
import me.senseiwells.replay.recorder.chunk.ChunkRecorders
import me.senseiwells.replay.recorder.player.PlayerRecorders
import me.senseiwells.replay.writer.ReplayWriterType
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
    val enabled: Boolean = false,
    @SerialName("debug")
    @EncodeDefault(Mode.NEVER)
    val debug: Boolean = false,
    @SerialName("encoding")
    @EncodeDefault(Mode.NEVER)
    val writerType: ReplayWriterType = ReplayWriterType.ReplayMod,
    @SerialName("async_thread_pool_size")
    @EncodeDefault(Mode.NEVER)
    val asyncThreadPoolSize: Int? = 1,
    @SerialName("world_name")
    val worldName: String = "World",
    @SerialName("server_name")
    val serverName: String = "Server",
    @SerialName("chunk_recording_path")
    @Serializable(with = PathSerializer::class)
    val chunkRecordingPath: Path = recordings.resolve("chunks"),
    @SerialName("player_recording_path")
    @Serializable(with = PathSerializer::class)
    val playerRecordingPath: Path = recordings.resolve("players"),
    @SerialName("player_recording_name")
    val playerRecordingName: String = "{uuid}",
    @SerialName("restart_after_max_file_size")
    val restartAfterMaxFileSize: Boolean = false,
    @SerialName("max_duration")
    @Serializable(with = DurationSerializer::class)
    val maxDuration: Duration = Duration.ZERO,
    @SerialName("restart_after_max_duration")
    val restartAfterMaxDuration: Boolean = false,
    @SerialName("recover_unsaved_replays")
    val recoverUnsavedReplays: Boolean = true,
    @SerialName("delete_replays_after_duration")
    @Serializable(with = DurationSerializer::class)
    val deleteReplaysAfterDuration: Duration = Duration.ZERO,
    @SerialName("log_deleted_replays")
    val logDeletedReplays: Boolean = true,
    @EncodeDefault(Mode.NEVER)
    @SerialName("fixed_daylight_cycle")
    val fixedDaylightCycle: Long = -1L,
    @SerialName("chunk_recorder_load_radius")
    val chunkRecorderLoadRadius: Int = -1,
    @SerialName("pause_unloaded_chunks")
    val skipWhenChunksUnloaded: Boolean = false,
    @SerialName("pause_notify_players")
    val notifyPlayersLoadingChunks: Boolean = true,
    @SerialName("notify_admins_of_status")
    val notifyAdminsOfStatus: Boolean = true,
    @SerialName("fix_carpet_bot_view_distance")
    val fixCarpetBotViewDistance: Boolean = false,
    @SerialName("include_resource_packs")
    val includeResourcePacks: Boolean = true,
    @SerialName("ignore_custom_payloads")
    val ignoreCustomPayloads: Boolean = false,
    @SerialName("ignore_sound_packets")
    val ignoreSoundPackets: Boolean = false,
    @SerialName("ignore_light_packets")
    val ignoreLightPackets: Boolean = true,
    @SerialName("ignore_chat_packets")
    val ignoreChatPackets: Boolean = false,
    @SerialName("ignore_action_bar_packets")
    val ignoreActionBarPackets: Boolean = false,
    @SerialName("ignore_scoreboard_packets")
    val ignoreScoreboardPackets: Boolean = false,
    @SerialName("optimize_explosion_packets")
    val optimizeExplosionPackets: Boolean = true,
    @SerialName("optimize_entity_packets")
    val optimizeEntityPackets: Boolean = false,
    @SerialName("record_voice_chat")
    val recordVoiceChat: Boolean = false,
    @JsonNames("replay_viewer_pack_ip")
    @SerialName("replay_server_ip")
    val replayServerIp: String? = null,
    @SerialName("allow_downloading_replays")
    val allowDownloadingReplays: Boolean = false,
    @SerialName("player_predicate")
    private val playerPredicate: ReplayPlayerPredicate = NonePredicate,
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

    fun getRootRecordingPaths(): List<Path> {
        return listOf(this.playerRecordingPath, this.chunkRecordingPath)
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
