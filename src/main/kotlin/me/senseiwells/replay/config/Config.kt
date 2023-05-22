package me.senseiwells.replay.config

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import me.senseiwells.replay.ServerReplay
import me.senseiwells.replay.player.PlayerPredicate
import me.senseiwells.replay.player.PlayerRecorders
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.server.level.ServerPlayer
import java.nio.file.Path
import java.util.function.Predicate
import kotlin.io.path.*

object Config {
    private val gson = GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create()
    private var reloadablePredicate: PlayerPredicate = PlayerPredicate.none()

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
        val json = this.gson.fromJson(path.bufferedReader(), JsonObject::class.java)
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
        path.writeText(this.gson.toJson(json))
    }

    fun getPath(): Path {
        return FabricLoader.getInstance().configDir.resolve("ServerReplay").resolve("config.json")
    }

    private fun deserializePlayerPredicate(json: JsonObject): PlayerPredicate {
        return when (val type = json.get("type").asString) {
            "none" -> PlayerPredicate.none()
            "all" -> PlayerPredicate.all()
            "has_name" -> PlayerPredicate.hasName(json.get("names").asJsonArray.map { it.asString })
            "has_uuid" -> PlayerPredicate.hasUUID(json.get("uuids").asJsonArray.map { it.asString })
            "has_op" -> PlayerPredicate.hasOP(json.get("level").asInt)
            "in_team" -> PlayerPredicate.inTeam(json.get("teams").asJsonArray.map { it.asString })
            "not" -> PlayerPredicate.not(this.deserializePlayerPredicate(json.getAsJsonObject("predicate")))
            "or" -> PlayerPredicate.or(
                this.deserializePlayerPredicate(json.getAsJsonObject("first")),
                this.deserializePlayerPredicate(json.getAsJsonObject("second"))
            )
            "and" -> PlayerPredicate.and(
                this.deserializePlayerPredicate(json.getAsJsonObject("first")),
                this.deserializePlayerPredicate(json.getAsJsonObject("second"))
            )
            else -> {
                ServerReplay.logger.error("Failed to deserialize player predicate type '${type}'")
                PlayerPredicate.none()
            }
        }
    }
}