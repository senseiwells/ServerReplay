package me.senseiwells.replay.config

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import me.senseiwells.replay.ServerReplay
import me.senseiwells.replay.chunk.ChunkArea
import me.senseiwells.replay.chunk.ChunkRecorders
import me.senseiwells.replay.chunk.ChunkRecorders.create
import me.senseiwells.replay.player.predicates.NonePredicate
import me.senseiwells.replay.player.predicates.PredicateFactory
import me.senseiwells.replay.player.predicates.ReplayPlayerContext
import me.senseiwells.replay.player.predicates.ReplayPlayerPredicate
import me.senseiwells.replay.util.FileUtils
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.Level
import java.nio.file.Path
import java.util.function.Predicate
import kotlin.io.path.*

// TODO: Make this nicer
object ReplayConfig {
    private val predicateFactories = HashMap<String, PredicateFactory>()

    private val gson = GsonBuilder().disableHtmlEscaping().setLenient().setPrettyPrinting().create()
    private var reloadablePredicate: ReplayPlayerPredicate = NonePredicate()

    @JvmStatic
    var enabled: Boolean = false
    @JvmStatic
    var debug: Boolean = false

    @JvmStatic
    var skipWhenChunksUnloaded = false
    @JvmStatic
    var notifyPlayersLoadingChunks = true
    @JvmStatic
    var fixCarpetBotViewDistance = false
    var ignoreSoundPackets = false
    var ignoreLightPackets = true
    var optimizeExplosionPackets = true
    var optimizeEntityPackets = false

    var worldName = "World"
    var serverName = "Server"
    var maxFileSizeString = "0GB"
    var maxFileSize = 0L
    var restartAfterMaxFileSize = false

    var chunkRecordingPath: Path = FabricLoader.getInstance().gameDir.resolve("recordings").resolve("chunks")
    var playerRecordingPath: Path = FabricLoader.getInstance().gameDir.resolve("recordings").resolve("players")

    @JvmField
    val predicate = Predicate<ReplayPlayerContext> { this.reloadablePredicate.shouldRecord(it) }

    @JvmField
    val chunks = ArrayList<ChunkAreaData>()

    @JvmStatic
    fun read() {
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
            if (json.has("debug")) {
                this.debug = json.get("debug").asBoolean
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
            if (json.has("fix_carpet_bot_view_distance")) {
                this.fixCarpetBotViewDistance = json.get("fix_carpet_bot_view_distance").asBoolean
            }
            if (json.has("ignore_sound_packets")) {
                this.ignoreSoundPackets = json.get("ignore_sound_packets").asBoolean
            }
            if (json.has("ignore_light_packets")) {
                this.ignoreLightPackets = json.get("ignore_light_packets").asBoolean
            }
            if (json.has("optimize_explosion_packets")) {
                this.optimizeExplosionPackets = json.get("optimize_explosion_packets").asBoolean
            }
            if (json.has("optimize_entity_packets")) {
                this.optimizeEntityPackets = json.get("optimize_entity_packets").asBoolean
            }
            if (json.has("pause_unloaded_chunks")) {
                this.skipWhenChunksUnloaded = json.get("pause_unloaded_chunks").asBoolean
            }
            if (json.has("pause_notify_players")) {
                this.notifyPlayersLoadingChunks = json.get("pause_notify_players").asBoolean
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
            if (json.has("chunks")) {
                this.chunks.clear()
                this.chunks.addAll(json.getAsJsonArray("chunks").map {
                    deserializerChunkData(it.asJsonObject)
                })
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
            if (this.debug) {
                json.addProperty("debug", true)
            }
            json.addProperty("world_name", this.worldName)
            json.addProperty("server_name", this.serverName)
            json.addProperty("max_file_size", this.maxFileSizeString)
            json.addProperty("restart_after_max_file_size", this.restartAfterMaxFileSize)
            json.addProperty("fix_carpet_bot_view_distance", this.fixCarpetBotViewDistance)
            json.addProperty("ignore_sound_packets", this.ignoreSoundPackets)
            json.addProperty("ignore_light_packets", this.ignoreLightPackets)
            json.addProperty("optimize_explosion_packets", this.optimizeExplosionPackets)
            json.addProperty("optimize_entity_packets", this.optimizeEntityPackets)
            json.addProperty("pause_unloaded_chunks", this.skipWhenChunksUnloaded)
            json.addProperty("pause_notify_players", this.notifyPlayersLoadingChunks)
            json.addProperty("player_recording_path", this.playerRecordingPath.pathString)
            json.addProperty("chunk_recording_path", this.chunkRecordingPath.pathString)
            json.add("player_predicate", this.reloadablePredicate.serialise())
            val chunks = JsonArray()
            for (chunk in this.chunks) {
                chunks.add(chunk.serialize())
            }
            json.add("chunks", chunks)
            val path = this.getPath()
            path.parent.createDirectories()
            path.bufferedWriter().use {
                this.gson.toJson(json, it)
            }
        } catch (e: Exception) {
            ServerReplay.logger.error("Failed to write replay config", e)
        }
    }

    @JvmStatic
    fun startChunks(server: MinecraftServer) {
        for (chunks in this.chunks) {
            val area = chunks.toChunkArea(server)
            if (area == null) {
                ServerReplay.logger.warn("Unable to find dimension {} for chunk recording", chunks.dimension.location())
                continue
            }
            if (ChunkRecorders.isAvailable(area, chunks.name)) {
                val recorder = create(area, chunks.name)
                recorder.tryStart(true)
            }
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

    fun deserializerChunkData(json: JsonObject): ChunkAreaData {
        val name = json.get("name").asString
        val dimension = ResourceKey.create(Registries.DIMENSION, ResourceLocation(json.get("dimension").asString))
        val fromX = json.get("fromX").asInt
        val toX = json.get("toX").asInt
        val fromZ = json.get("fromZ").asInt
        val toZ = json.get("toZ").asInt
        return ChunkAreaData(name, dimension, fromX, toX, fromZ, toZ)
    }

    private fun getPath(): Path {
        return this.root().resolve("config.json")
    }

    data class ChunkAreaData(
        val name: String,
        val dimension: ResourceKey<Level>,
        val fromX: Int,
        val fromZ: Int,
        val toX: Int,
        val toZ: Int
    ) {
        fun toChunkArea(server: MinecraftServer): ChunkArea? {
            val level = server.getLevel(this.dimension) ?: return null
            return ChunkArea(level, ChunkPos(this.fromX, this.fromZ), ChunkPos(this.toX, this.toZ))
        }

        fun serialize(): JsonObject {
            val json = JsonObject()
            json.addProperty("name", this.name)
            json.addProperty("dimension", this.dimension.location().toString())
            json.addProperty("fromX", this.fromX)
            json.addProperty("fromZ", this.fromZ)
            json.addProperty("toX", this.toX)
            json.addProperty("toZ", this.toZ)
            return json
        }
    }
}