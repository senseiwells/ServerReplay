package me.senseiwells.replay

import me.senseiwells.replay.config.ReplayConfig
import me.senseiwells.replay.player.predicates.*
import net.fabricmc.api.ModInitializer
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

        @JvmField
        val version = "1.0.0"
    }
}