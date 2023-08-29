package me.senseiwells.replay.config

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import me.senseiwells.replay.ServerReplay
import me.senseiwells.replay.player.predicates.ReplayPlayerPredicate
import me.senseiwells.replay.player.PlayerRecorders
import me.senseiwells.replay.player.predicates.NonePredicate
import me.senseiwells.replay.player.predicates.PredicateFactory
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.server.level.ServerPlayer
import java.nio.file.Path
import java.util.function.Predicate
import kotlin.io.path.*

object ReplayConfig {
    private val predicateFactories = HashMap<String, PredicateFactory>()

    private val gson = GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create()
    private var reloadablePredicate: ReplayPlayerPredicate = NonePredicate()

    @JvmStatic
    var enabled: Boolean = false
        set(value) {
            if (!value) {
                for (recorders in PlayerRecorders.all()) {
                    recorders.stop()
                }
            }
            field = value
        }

    var worldName = "World"
    var serverName = "Server"
    var hasPredicate = true

    var recordingPath: Path = FabricLoader.getInstance().gameDir.resolve("recordings")

    val predicate = Predicate<ServerPlayer> { this.reloadablePredicate.shouldRecord(it) }

    @JvmStatic
    fun read() {
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
        if (json.has("recording_path")) {
            this.recordingPath = Path.of(json.get("recording_path").asString)
        }
        if (json.has("has_predicate")) {
            this.hasPredicate = json.get("has_predicate").asBoolean
        }
        if (this.hasPredicate && json.has("predicate")) {
            this.reloadablePredicate = this.deserializePlayerPredicate(json.getAsJsonObject("predicate"))
        }
    }

    @JvmStatic
    fun write() {
        val json = JsonObject()
        json.addProperty("enabled", this.enabled)
        json.addProperty("world_name", this.worldName)
        json.addProperty("server_name", this.serverName)
        json.addProperty("recording_path", this.recordingPath.absolutePathString())
        json.add("predicate", this.reloadablePredicate.serialise())
        val path = this.getPath()
        path.parent.createDirectories()
        path.bufferedWriter().use {
            this.gson.toJson(json, it)
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
        return factory.create(json)
    }

    private fun getPath(): Path {
        return this.root().resolve("config.json")
    }
}