package me.senseiwells.replay

import me.senseiwells.replay.api.ServerReplayPluginManager
import me.senseiwells.replay.config.ReplayConfig
import net.fabricmc.api.ModInitializer
import net.fabricmc.loader.api.FabricLoader
import net.fabricmc.loader.api.ModContainer
import org.slf4j.Logger
import org.slf4j.LoggerFactory

object ServerReplay: ModInitializer {
    const val MOD_ID = "server-replay"

    @JvmField
    val logger: Logger = LoggerFactory.getLogger("ServerReplay")

    val replay: ModContainer = FabricLoader.getInstance().getModContainer(MOD_ID).get()
    val version: String = replay.metadata.version.friendlyString

    @JvmStatic
    var config: ReplayConfig = ReplayConfig.read()

    override fun onInitialize() {
        ServerReplayPluginManager.loadPlugins()
    }
}