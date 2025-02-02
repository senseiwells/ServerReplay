package me.senseiwells.replay

import me.senseiwells.replay.api.ServerReplayPluginManager
import me.senseiwells.replay.chunk.ChunkRecorder
import me.senseiwells.replay.chunk.ChunkRecorders
import me.senseiwells.replay.commands.PackCommand
import me.senseiwells.replay.commands.ReplayCommand
import me.senseiwells.replay.config.ReplayConfig
import me.senseiwells.replay.http.DownloadPacksHttpInjector
import me.senseiwells.replay.http.DownloadReplaysHttpInjector
import me.senseiwells.replay.player.PlayerRecorder
import me.senseiwells.replay.player.PlayerRecorders
import me.senseiwells.replay.util.processor.RecorderFixerUpper
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.loader.api.FabricLoader
import net.fabricmc.loader.api.ModContainer
import net.mcbrawls.inject.fabric.InjectFabric
import net.minecraft.server.MinecraftServer
import org.slf4j.Logger
import org.slf4j.LoggerFactory

object ServerReplay: ModInitializer {
    const val MOD_ID = "server-replay"

    @JvmField
    val logger: Logger = LoggerFactory.getLogger(MOD_ID)

    val replay: ModContainer = FabricLoader.getInstance().getModContainer(MOD_ID).get()
    val version: String = this.replay.metadata.version.friendlyString

    @JvmStatic
    var config: ReplayConfig = ReplayConfig()
        private set

    override fun onInitialize() {
        this.config = ReplayConfig.read()

        InjectFabric.INSTANCE.registerInjector(DownloadPacksHttpInjector)
        InjectFabric.INSTANCE.registerInjector(DownloadReplaysHttpInjector)

        ServerReplayPluginManager.loadPlugins()

        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            ReplayCommand.register(dispatcher)

            if (this.config.debug) {
                PackCommand.register(dispatcher)
            }
        }
        ServerTickEvents.END_SERVER_TICK.register {
            PlayerRecorders.recorders().forEach(PlayerRecorder::tick)
            ChunkRecorders.recorders().forEach(ChunkRecorder::tick)
        }

        RecorderFixerUpper.tryFixingUp()
        this.warnDeprecatedConfig()
    }

    fun getIp(server: MinecraftServer): String {
        val ip = this.config.replayServerIp ?: "127.0.0.1"
        return "${ip}:${server.port}"
    }

    fun reload() {
        this.config = ReplayConfig.read()
    }

    private fun warnDeprecatedConfig() {
        if (this.config.includeCompressedReplaySizeInStatus) {
            this.logger.warn("\"include_compressed_in_status\" is enabled in your config, this option is deprecated and will be removed soon")
        }
        if (this.config.maxFileSize.bytes > 0) {
            this.logger.warn("\"max_file_size\" is configured in your config, this option is deprecated and will be removed soon")
            this.logger.warn("consider using \"max_duration\" instead")
        }
    }
}