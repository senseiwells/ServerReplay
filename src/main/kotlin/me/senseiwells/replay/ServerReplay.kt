package me.senseiwells.replay

import com.google.gson.JsonObject
import me.senseiwells.replay.commands.PackCommand
import me.senseiwells.replay.commands.ReplayCommand
import me.senseiwells.replay.config.ReplayConfig
import me.senseiwells.replay.http.ReplayDownloaderInterceptor
import me.senseiwells.replay.processor.*
import net.casual.arcade.commands.register
import net.casual.arcade.events.GlobalEventHandler
import net.casual.arcade.events.ListenerRegistry.Companion.register
import net.casual.arcade.events.server.ServerRegisterCommandEvent
import net.casual.arcade.interceptor.ArcadeInterceptors
import net.casual.arcade.replay.events.ReplayRecorderStartEvent
import net.casual.arcade.replay.io.ReplayFormat
import net.fabricmc.api.ModInitializer
import net.fabricmc.loader.api.FabricLoader
import net.fabricmc.loader.api.ModContainer
import org.slf4j.Logger
import org.slf4j.LoggerFactory

object ServerReplay: ModInitializer {
    const val MOD_ID = "server-replay"

    @JvmField
    val logger: Logger = LoggerFactory.getLogger(MOD_ID)

    val container: ModContainer = FabricLoader.getInstance().getModContainer(MOD_ID).get()
    val version: String = this.container.metadata.version.friendlyString

    @JvmStatic
    var config: ReplayConfig = ReplayConfig()
        private set

    override fun onInitialize() {
        this.logger.info("Launching ServerReplay!")

        @Suppress("DEPRECATION")
        ReplayConfig.migrateOldConfigs()
        this.config = ReplayConfig.read()
        this.fixupConfig()

        ArcadeInterceptors.register(ReplayDownloaderInterceptor)

        AutomaticRecorders.registerEvents()
        RecorderNotifier.registerEvents()
        RecorderRecoverer.registerEvents()

        GlobalEventHandler.Server.register<ServerRegisterCommandEvent> {
            it.register(ReplayCommand)
            if (this.config.debug) {
                it.register(PackCommand)
            }
        }
        GlobalEventHandler.Server.register<ReplayRecorderStartEvent> { (recorder) ->
            recorder.addMetadataProvider(this::addMetadata)
        }

        ReplayCleanerUpper.run()
        RecorderWarner.output(this.logger::warn)
    }

    fun reload() {
        this.config = ReplayConfig.read()
        this.fixupConfig()
    }

    fun updateConfig(mutator: (ReplayConfig) -> ReplayConfig) {
        this.config = mutator.invoke(this.config)
        ReplayConfig.write(this.config)
    }

    private fun fixupConfig() {
        val current = this.config.defaultReplayFormat
        if (!current.supported) {
            this.logger.warn("Default replay format is currently set to $current, which is not yet supported")
            this.logger.warn("Falling back onto a supported format!")
            this.updateConfig { it.copy(defaultReplayFormat = ReplayFormat.Flashback) }
        }
    }

    private fun addMetadata(data: JsonObject) {
        data.addProperty("server_replay_version", this.version)
    }
}