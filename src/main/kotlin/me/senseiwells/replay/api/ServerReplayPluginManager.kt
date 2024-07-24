package me.senseiwells.replay.api

import me.senseiwells.replay.ServerReplay
import me.senseiwells.replay.api.ServerReplayPluginManager.registerPlugin
import me.senseiwells.replay.chunk.ChunkRecorder
import me.senseiwells.replay.player.PlayerRecorder
import net.fabricmc.loader.api.FabricLoader

/**
 * Plugin manager that manages any plugins for ServerReplay.
 *
 * Your plugins should be specified in your fabric.mod.json,
 * see [registerPlugin] for more information.
 */
object ServerReplayPluginManager {
    internal val plugins = ArrayList<ServerReplayPlugin>()

    /**
     * This registers a [ServerReplayPlugin].
     *
     * You should add an entrypoint in your `fabric.mod.json` under
     * `server_replay` instead. For example:
     * ```json
     * {
     *   // ...
     *   "entrypoints": {
     *     "main": [
     *       // ...
     *     ],
     *     "server_replay": [
     *       "com.example.MyServerReplayPlugin"
     *     ]
     *   }
     *   // ...
     * }
     * ```
     * If this is not an option, then you may register your plugin using this method.
     *
     * @param plugin The plugin to register.
     */
    @Suppress("DeprecatedCallableAddReplaceWith")
    @Deprecated("You should add an entrypoint for server_replay in your fabric.mod.json for your plugin")
    fun registerPlugin(plugin: ServerReplayPlugin) {
        this.plugins.add(plugin)
    }

    internal fun startReplay(recorder: PlayerRecorder) {
        for (plugin in this.plugins) {
            plugin.onPlayerReplayStart(recorder)
        }
    }

    internal fun startReplay(recorder: ChunkRecorder) {
        for (plugin in this.plugins) {
            plugin.onChunkReplayStart(recorder)
        }
    }

    internal fun loadPlugins() {
        val containers = FabricLoader.getInstance().getEntrypointContainers("server_replay", ServerReplayPlugin::class.java)
        for (container in containers) {
            val entrypoint = container.entrypoint
            val modInfo = "${container.provider.metadata.id} (${container.provider.metadata.version.friendlyString})"
            ServerReplay.logger.info("Loading plugin (${entrypoint::class.java.simpleName}) from mod $modInfo")
            this.plugins.add(entrypoint)
        }
    }
}