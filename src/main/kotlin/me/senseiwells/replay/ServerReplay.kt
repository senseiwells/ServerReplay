package me.senseiwells.replay

import me.senseiwells.replay.api.ServerReplayPluginManager
import me.senseiwells.replay.commands.PackCommand
import me.senseiwells.replay.commands.ReplayCommand
import me.senseiwells.replay.config.ReplayConfig
import me.senseiwells.replay.http.DownloadPacksHttpInjector
import me.senseiwells.replay.http.DownloadReplaysHttpInjector
import me.senseiwells.replay.recorder.ReplayRecorder
import me.senseiwells.replay.recorder.chunk.ChunkRecorder
import me.senseiwells.replay.recorder.chunk.ChunkRecorders
import me.senseiwells.replay.recorder.player.PlayerRecorder
import me.senseiwells.replay.recorder.player.PlayerRecorders
import me.senseiwells.replay.util.processor.RecorderFixerUpper
import me.senseiwells.replay.util.processor.ReplayCleanerUpper
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.loader.api.FabricLoader
import net.fabricmc.loader.api.ModContainer
import net.fabricmc.loader.api.metadata.ModOrigin
import net.fabricmc.loader.impl.metadata.AbstractModMetadata
import net.mcbrawls.inject.fabric.InjectFabric
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import org.slf4j.Logger
import org.slf4j.LoggerFactory

object ServerReplay: ModInitializer {
    private var warned: Boolean = false

    const val MOD_ID = "server-replay"

    @JvmField
    val logger: Logger = LoggerFactory.getLogger(MOD_ID)

    val replay: ModContainer = FabricLoader.getInstance().getModContainer(MOD_ID).get()
    val version: String = this.replay.metadata.version.friendlyString

    @JvmStatic
    var config: ReplayConfig = ReplayConfig()
        private set

    override fun onInitialize() {
        this.update { ReplayConfig.read() }

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

        ReplayCleanerUpper.run()
        RecorderFixerUpper.tryFixingUp()
        this.outputWarnings(this.logger::warn)
    }

    fun getIp(server: MinecraftServer): String {
        val ip = this.config.replayServerIp ?: "127.0.0.1"
        return "${ip}:${server.port}"
    }

    fun reload() {
        this.config = ReplayConfig.read()
    }

    fun update(mutator: (ReplayConfig) -> ReplayConfig) {
        this.config = mutator.invoke(this.config)
        ReplayConfig.write(this.config)
    }

    internal fun getLoadedMods(): Map<String, String> {
        return FabricLoader.getInstance().allMods
            .filter { it.origin.kind != ModOrigin.Kind.NESTED }
            .filter { it.metadata.type != AbstractModMetadata.TYPE_BUILTIN }
            .associateBy({ it.metadata.id }, { it.metadata.version.friendlyString })
    }

    internal fun outputWarnings(recorder: ReplayRecorder) {
        if (!this.warned) {
            this.warned = true
            val operators = recorder.server.playerList.players.filter {
                recorder.server.playerList.isOp(it.gameProfile)
            }
            this.outputWarnings { message ->
                val component = Component.literal(message)
                for (operator in operators) {
                    operator.sendSystemMessage(component)
                }
            }
        }
    }

    private fun outputWarnings(consumer: (String) -> Unit) {
        this.config.writerType.warn(consumer)
    }
}