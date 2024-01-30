package me.senseiwells.replay.config

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import me.senseiwells.replay.ServerReplay
import me.senseiwells.replay.player.predicates.NonePredicate
import me.senseiwells.replay.player.predicates.PredicateFactory
import me.senseiwells.replay.player.predicates.ReplayPlayerContext
import me.senseiwells.replay.player.predicates.ReplayPlayerPredicate
import me.senseiwells.replay.util.FileUtils
import net.fabricmc.loader.api.FabricLoader
import java.nio.file.Path
import java.util.function.Predicate
import kotlin.io.path.*

object ReplayConfig {
    private val predicateFactories = HashMap<String, PredicateFactory>()

    private val gson = GsonBuilder().disableHtmlEscaping().setLenient().setPrettyPrinting().create()
    private var reloadablePredicate: ReplayPlayerPredicate = NonePredicate()

    @JvmStatic
    var enabled: Boolean = false

    @JvmStatic
    var skipWhenChunksUnloaded = false
    @JvmStatic
    var notifyPlayersLoadingChunks = true
    var worldName = "World"
    var serverName = "Server"
    var maxFileSizeString = "0GB"
    var maxFileSize = 0L
    var restartAfterMaxFileSize = false

    var chunkRecordingPath: Path = FabricLoader.getInstance().gameDir.resolve("recordings").resolve("chunks")
    var playerRecordingPath: Path = FabricLoader.getInstance().gameDir.resolve("recordings").resolve("players")

    @JvmField
    val predicate = Predicate<ReplayPlayerContext> { this.reloadablePredicate.shouldRecord(it) }

    @JvmStatic
    fun read() {
        // TODO: Make this better xD
        try {
            val path = this.getPath()
            if (!path.exists()) {
                this.write()
                return
            }
            val json = path.bufferedReader().use {
                this.gson.fromJson(it, JsonObject::class.java)
            }
            if (json == null) {
                this.write()
                return
            }
            if (json.has("enabled")) {
                this.enabled = json.get("enabled").asBoolean
            }
            if (json.has("world_name")) {
                this.worldName = json.get("world_name").asString
            }
            if (json.has("server_name")) {
                this.serverName = json.get("server_name").asString
            }
            if (json.has("max_file_size")) {
                this.maxFileSizeString = json.get("max_file_size").asString
                this.maxFileSize = FileUtils.parseSize(this.maxFileSizeString, 0)
            }
            if (json.has("restart_after_max_file_size")) {
                this.restartAfterMaxFileSize = json.get("restart_after_max_file_size").asBoolean
            }
            if (json.has("pause_unloaded_chunks")) {
                this.skipWhenChunksUnloaded = json.get("pause_unloaded_chunks").asBoolean
            }
            if (json.has("pause_notify_players")) {
                this.notifyPlayersLoadingChunks = json.get("pause_notify_players").asBoolean
            }
            if (json.has("recording_path")) {
                this.playerRecordingPath = Path.of(json.get("recording_path").asString)
            }
            if (json.has("player_recording_path")) {
                this.playerRecordingPath = Path.of(json.get("player_recording_path").asString)
            }
            if (json.has("chunk_recording_path")) {
                this.chunkRecordingPath = Path.of(json.get("chunk_recording_path").asString)
            }
            if (json.has("player_predicate")) {
                this.reloadablePredicate = this.deserializePlayerPredicate(json.getAsJsonObject("player_predicate"))
            }
        } catch (e: Exception) {
            ServerReplay.logger.error("Failed to read replay config", e)
        }
    }

    @JvmStatic
    fun write() {
        try {
            val json = JsonObject()
            json.addProperty("enabled", this.enabled)
            json.addProperty("world_name", this.worldName)
            json.addProperty("server_name", this.serverName)
            json.addProperty("max_file_size", this.maxFileSizeString)
            json.addProperty("restart_after_max_file_size", this.restartAfterMaxFileSize)
            json.addProperty("pause_unloaded_chunks", this.skipWhenChunksUnloaded)
            json.addProperty("pause_notify_players", this.notifyPlayersLoadingChunks)
            json.addProperty("player_recording_path", this.playerRecordingPath.pathString)
            json.addProperty("chunk_recording_path", this.chunkRecordingPath.pathString)
            json.add("player_predicate", this.reloadablePredicate.serialise())
            val path = this.getPath()
            path.parent.createDirectories()
            path.bufferedWriter().use {
                this.gson.toJson(json, it)
            }
        } catch (e: Exception) {
            ServerReplay.logger.error("Failed to write replay config", e)
        }
    }

    fun root(): Path {
        return FabricLoader.getInstance().configDir.resolve("ServerReplay")
    }

    fun addPredicateFactories(vararg factories: PredicateFactory) {
        for (factory in factories) {
            this.addPredicateFactory(factory)
        }
    }

    fun addPredicateFactory(factory: PredicateFactory): Boolean {
        if (this.predicateFactories.containsKey(factory.id)) {
            return false
        }
        this.predicateFactories[factory.id] = factory
        return true
    }

    fun deserializePlayerPredicate(json: JsonObject): ReplayPlayerPredicate {
        val type = json.get("type").asString
        val factory = this.predicateFactories[type]
        if (factory == null) {
            ServerReplay.logger.error("Failed to deserialize player predicate type '${type}'")
            return NonePredicate()
        }
        return try {
            factory.create(json)
        } catch (e: Exception) {
            ServerReplay.logger.error("Failed to deserialize PlayerPredicate {}", json, e)
            NonePredicate()
        }
    }

    private fun getPath(): Path {
        return this.root().resolve("config.json")
    }
}