package me.senseiwells.replay.config

import com.mojang.authlib.GameProfile
import kotlinx.serialization.*
import kotlinx.serialization.EncodeDefault.Mode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNames
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import me.senseiwells.replay.ServerReplay
import me.senseiwells.replay.config.chunk.ChunkAreaConfig
import me.senseiwells.replay.config.predicates.NonePredicate
import me.senseiwells.replay.config.predicates.ReplayPlayerPredicate
import me.senseiwells.replay.config.serialization.PathSerializer
import net.casual.arcade.replay.io.ReplayFormat
import net.casual.arcade.replay.recorder.settings.RecorderSettings
import net.casual.arcade.replay.recorder.settings.RecorderSettings.ChunkRecordingStrategy
import net.casual.arcade.replay.recorder.settings.SimpleRecorderSettings
import net.casual.arcade.replay.util.io.FileSize
import net.casual.arcade.utils.serialization.codec.ArcadeExtraCodecs
import net.casual.arcade.utils.serialization.kotlin.CodecSerializersModule
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.resources.ResourceLocation
import org.apache.commons.lang3.SerializationException
import java.io.IOException
import java.nio.file.Path
import kotlin.io.path.*
import kotlin.time.Duration

@Serializable
@OptIn(ExperimentalSerializationApi::class)
data class ReplayConfig(
    @SerialName("debug")
    @EncodeDefault(Mode.NEVER)
    val debug: Boolean = false,
    @Contextual
    @JsonNames("encoding")
    @SerialName("default_encoding")
    val defaultReplayFormat: ReplayFormat = ReplayFormat.ReplayMod,
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
    @Contextual
    @SerialName("max_file_size")
    val maxFileSize: FileSize = FileSize(0),
    @SerialName("restart_after_max_file_size")
    val restartAfterMaxFileSize: Boolean = false,
    @Contextual
    @SerialName("max_duration")
    val maxDuration: Duration = Duration.ZERO,
    @SerialName("restart_after_max_duration")
    val restartAfterMaxDuration: Boolean = false,
    @SerialName("recover_unsaved_replays")
    val recoverUnsavedReplays: Boolean = true,
    @Contextual
    @SerialName("delete_replays_after_duration")
    val deleteReplaysAfterDuration: Duration = Duration.ZERO,
    @SerialName("log_deleted_replays")
    val logDeletedReplays: Boolean = true,
    @EncodeDefault(Mode.NEVER)
    @SerialName("fixed_daylight_cycle")
    val fixedDaylightCycle: Long = -1L,
    @SerialName("chunk_recorder_load_radius")
    val chunkRecorderLoadRadius: Int = -1,
    @SerialName("chunk_recording_strategy")
    val chunkRecordingStrategy: ChunkRecordingStrategy = ChunkRecordingStrategy.Always,
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
    @SerialName("record_hotbar")
    val recordHotbar: Boolean = false,
    @SerialName("record_voice_chat")
    val recordVoiceChat: Boolean = false,
    @JsonNames("replay_viewer_pack_ip")
    @SerialName("replay_server_ip")
    val replayServerIp: String? = null,
    @SerialName("allow_downloading_replays")
    val allowDownloadingReplays: Boolean = false,
    @JsonNames("enabled")
    @SerialName("automatically_record")
    val automaticallyRecord: Boolean = false,
    @SerialName("player_predicate")
    val playerPredicate: ReplayPlayerPredicate = NonePredicate,
    @SerialName("chunks")
    val chunks: List<ChunkAreaConfig> = listOf(),
) {
    fun getPlayerRecordingLocation(profile: GameProfile): Path {
        val path = this.playerRecordingName
            .replace("{uuid}", profile.id.toString())
            .replace("{username}", profile.name)
        return this.playerRecordingPath.resolve(path)
    }

    fun getRootRecordingPaths(): List<Path> {
        return listOf(this.playerRecordingPath, this.chunkRecordingPath)
    }

    fun createSettings(): SimpleRecorderSettings {
        return SimpleRecorderSettings(
            this.debug,
            this.worldName,
            this.serverName,
            this.fixedDaylightCycle,
            this.includeResourcePacks,
            this.chunkRecorderLoadRadius,
            this.chunkRecordingStrategy,
            RecorderSettings.FileLimits(
                this.maxFileSize,
                this.restartAfterMaxFileSize,
                this.maxDuration,
                this.restartAfterMaxDuration
            ),
            RecorderSettings.IgnorePackets(
                this.ignoreCustomPayloads,
                this.ignoreSoundPackets,
                this.ignoreLightPackets,
                this.ignoreChatPackets,
                this.ignoreActionBarPackets,
                this.ignoreScoreboardPackets
            ),
            RecorderSettings.OptimizePackets(
                this.optimizeExplosionPackets,
                this.optimizeEntityPackets
            ),
            this.recordHotbar,
            this.recordVoiceChat
        )
    }

    companion object {
        private val recordings: Path = FabricLoader.getInstance().gameDir.resolve("recordings")

        private val root = FabricLoader.getInstance().configDir.resolve("server-replay")
        private val config = this.root.resolve("config.json")

        private val json = Json {
            encodeDefaults = true
            prettyPrint = true
            prettyPrintIndent = "  "
            ignoreUnknownKeys = true

            serializersModule = CodecSerializersModule {
                contextual(ReplayFormat.CODEC)
                contextual(ResourceLocation.CODEC)
                contextual(ChunkRecordingStrategy.CODEC)
                contextual(ArcadeExtraCodecs.DURATION.orElse(Duration.ZERO))
            }
        }

        fun resolve(path: String): Path {
            return this.root.resolve(path)
        }

        @JvmStatic
        fun read(): ReplayConfig {
            if (!this.config.exists()) {
                ServerReplay.logger.info("Generating default config")
                val config = ReplayConfig()
                this.write(config)
                return config
            }
            try {
                return this.config.inputStream().use {
                    json.decodeFromStream(it)
                }
            } catch (e: Exception) {
                ServerReplay.logger.error("Failed to read replay config, generating default", e)
                val config = ReplayConfig()
                this.write(config)
                return config
            }
        }

        @JvmStatic
        fun write(config: ReplayConfig) {
            try {
                this.config.createParentDirectories()
                this.config.outputStream().use {
                    json.encodeToStream(config, it)
                }
            } catch (e: IOException) {
                ServerReplay.logger.error("Failed to write replay config", e)
            } catch (e: SerializationException) {
                ServerReplay.logger.error("Failed to serialize replay config", e)
            }
        }

        @Deprecated("Temporary function to migrate old configs")
        @OptIn(ExperimentalPathApi::class)
        internal fun migrateOldConfigs() {
            val oldPath = this.config.resolveSibling("ServerReplay")
            try {
                if (oldPath.isDirectory()) {
                    oldPath.copyToRecursively(this.config, overwrite = false, followLinks = true)
                    oldPath.deleteRecursively()
                }
            } catch (e: IOException) {
                ServerReplay.logger.error("Failed to migrate ServerReplay configs!")
            }
        }
    }
}
