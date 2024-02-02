package me.senseiwells.replay

import me.senseiwells.replay.config.ReplayConfig
import me.senseiwells.replay.player.predicates.*
import net.fabricmc.api.ModInitializer
import net.fabricmc.loader.api.FabricLoader
import net.fabricmc.loader.api.ModContainer
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class ServerReplay: ModInitializer {
    override fun onInitialize() {
        ReplayConfig.addPredicateFactories(
            AllPredicate,
            AndPredicate,
            HasNamePredicate,
            HasOpPredicate,
            InTeamPredicate,
            NonePredicate,
            NotPredicate,
            OrPredicate,
            UUIDPredicate
        )
    }

    companion object {
        @JvmField
        val logger: Logger = LoggerFactory.getLogger("ServerReplay")

        val replay: ModContainer = FabricLoader.getInstance().getModContainer("server-replay").get()
        val version: String = replay.metadata.version.friendlyString
    }
}