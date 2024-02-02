package me.senseiwells.replay

import me.senseiwells.replay.config.ReplayConfig
import net.fabricmc.api.ModInitializer
import net.fabricmc.loader.api.FabricLoader
import net.fabricmc.loader.api.ModContainer
import org.slf4j.Logger
import org.slf4j.LoggerFactory

object ServerReplay: ModInitializer {
    @JvmField
    val logger: Logger = LoggerFactory.getLogger("ServerReplay")

    val replay: ModContainer = FabricLoader.getInstance().getModContainer("server-replay").get()
    val version: String = replay.metadata.version.friendlyString

    @JvmStatic
    lateinit var config: ReplayConfig

    override fun onInitialize() {
        this.config = ReplayConfig.read()
    }
}