package me.senseiwells.replay

import me.senseiwells.replay.api.ServerReplayPluginManager
import me.senseiwells.replay.commands.PackCommand
import me.senseiwells.replay.commands.ReplayCommand
import me.senseiwells.replay.config.ReplayConfig
import me.senseiwells.replay.http.DownloadPacksHttpInjector
import me.senseiwells.replay.http.DownloadReplaysHttpInjector
import me.senseiwells.replay.util.processor.RecorderFixerUpper
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
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

        RecorderFixerUpper.tryFixingUp()
    }

    fun getIp(server: MinecraftServer): String {
        val ip = this.config.replayServerIp ?: "127.0.0.1"
        return "${ip}:${server.port}"
    }

    fun reload() {
        this.config = ReplayConfig.read()
    }
}