package me.senseiwells.replay

import net.fabricmc.api.ModInitializer
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class ServerReplay: ModInitializer {
    override fun onInitialize() {

    }

    companion object {
        @JvmField
        val logger: Logger = LoggerFactory.getLogger("ServerReplay")

        @JvmField
        val version = "1.0.0"
    }
}