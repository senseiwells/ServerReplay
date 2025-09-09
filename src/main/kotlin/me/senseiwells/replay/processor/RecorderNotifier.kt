package me.senseiwells.replay.processor

import me.senseiwells.replay.ServerReplay
import net.casual.arcade.commands.singleUseFunction
import net.casual.arcade.events.GlobalEventHandler
import net.casual.arcade.events.ListenerRegistry.Companion.register
import net.casual.arcade.replay.events.ReplayRecorderCloseEvent
import net.casual.arcade.replay.events.ReplayRecorderDurationLimitEvent
import net.casual.arcade.replay.events.ReplayRecorderSaveEvent
import net.casual.arcade.replay.events.ReplayRecorderStartEvent
import net.casual.arcade.replay.events.chunk.ReplayChunkRecorderLoadedResumeEvent
import net.casual.arcade.replay.events.chunk.ReplayChunkRecorderUnloadedPauseEvent
import net.casual.arcade.replay.events.player.ReplayRecorderFileSizeLimitEvent
import net.casual.arcade.replay.io.ReplayFormat
import net.casual.arcade.replay.util.FileUtils
import net.casual.arcade.replay.viewer.ReplayViewers
import net.casual.arcade.utils.component.hover
import net.casual.arcade.utils.component.lime
import net.casual.arcade.utils.PlayerUtils.broadcastToOps
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import java.nio.file.Path
import kotlin.io.path.fileSize
import kotlin.io.path.isReadable

object RecorderNotifier {
    internal fun registerEvents() {
        GlobalEventHandler.Server.register<ReplayRecorderStartEvent>(::onReplayRecorderStart)
        GlobalEventHandler.Server.register<ReplayChunkRecorderLoadedResumeEvent>(::onChunkRecorderLoaded)
        GlobalEventHandler.Server.register<ReplayChunkRecorderUnloadedPauseEvent>(::onChunkRecorderUnloaded)
        GlobalEventHandler.Server.register<ReplayRecorderSaveEvent>(phase = ReplayRecorderSaveEvent.PHASE_PRE, listener = ::onReplayRecorderSaving)
        GlobalEventHandler.Server.register<ReplayRecorderSaveEvent>(phase = ReplayRecorderSaveEvent.PHASE_POST, listener = ::onReplayRecorderSaved)
        GlobalEventHandler.Server.register<ReplayRecorderCloseEvent>(::onReplayRecorderClose)
        GlobalEventHandler.Server.register<ReplayRecorderDurationLimitEvent>(::onReplayRecorderDurationLimit)
        GlobalEventHandler.Server.register<ReplayRecorderFileSizeLimitEvent>(::onReplayRecorderFileSizeLimit)
    }

    private fun onReplayRecorderStart(event: ReplayRecorderStartEvent) {
        val (recorder, mode) = event
        recorder.server.broadcastToOpsAndConsole("${mode.getContinuousVerb()} replay for ${recorder.getName()}")
    }

    private fun onChunkRecorderLoaded(event: ReplayChunkRecorderLoadedResumeEvent) {
        val recorder = event.recorder
        if (ServerReplay.config.notifyPlayersLoadingChunks) {
            recorder.ignore {
                recorder.server.playerList.broadcastSystemMessage(
                    Component.literal("Resumed recording for ${recorder.getName()}"), false
                )
            }
        }
    }

    private fun onChunkRecorderUnloaded(event: ReplayChunkRecorderUnloadedPauseEvent) {
        val recorder = event.recorder
        if (ServerReplay.config.notifyPlayersLoadingChunks) {
            recorder.ignore {
                recorder.server.playerList.broadcastSystemMessage(
                    Component.literal("Paused recording for ${recorder.getName()}"), false
                )
            }
        }
    }

    private fun onReplayRecorderSaving(event: ReplayRecorderSaveEvent) {
        val recorder = event.recorder
        recorder.server.broadcastToOpsAndConsole(
            "Starting to save replay ${recorder.getName()}, please do not stop the server!"
        )
    }

    private fun onReplayRecorderSaved(event: ReplayRecorderSaveEvent) {
        val recorder = event.recorder
        val output = event.output
        val clickable = Component.literal("$output").lime()
            .hover(Component.literal("Click to view replay"))
            .singleUseFunction { this.tryViewReplay(it.player, output) }

        val message = Component.empty()
            .append("Successfully saved replay ${recorder.getName()} to ")
            .append(clickable)
            .append(", compressed to ${FileUtils.formatSize(event.output.fileSize())}")
        recorder.server.broadcastToOpsAndConsole(message)
    }

    private fun onReplayRecorderClose(event: ReplayRecorderCloseEvent) {
        val recorder = event.recorder
        recorder.server.broadcastToOpsAndConsole("Successfully closed replay ${recorder.getName()}")
    }

    private fun onReplayRecorderDurationLimit(event: ReplayRecorderDurationLimitEvent) {
        val recorder = event.recorder
        val limit = recorder.settings.limits.maxDuration
        recorder.server.broadcastToOpsAndConsole(
            "Stopped recording replay ${recorder.getName()}, past duration limit $limit"
        )
    }

    private fun onReplayRecorderFileSizeLimit(event: ReplayRecorderFileSizeLimitEvent) {
        val recorder = event.recorder
        val limit = recorder.settings.limits.maxRawSize
        recorder.server.broadcastToOpsAndConsole(
            "Stopped recording replay ${recorder.getName()}, past raw file size limit $limit"
        )
    }

    private fun tryViewReplay(player: ServerPlayer, path: Path) {
        val format = ReplayFormat.formatOf(path)
        if (format == null || !path.isReadable()) {
            player.sendSystemMessage(Component.literal("Replay is no longer valid for viewing!"))
            return
        }

        try {
            ReplayViewers.create(path, player).start()
        } catch (exception: Exception) {
            ServerReplay.logger.error("Failed to start viewing replay at $path", exception)
        }
    }

    private fun MinecraftServer.broadcastToOps(message: Component) {
        if (ServerReplay.config.notifyAdminsOfStatus) {
            this.execute { this.playerList.players.broadcastToOps(message) }
        }
    }

    private fun MinecraftServer.broadcastToOpsAndConsole(message: Component) {
        this.broadcastToOps(message)
        ServerReplay.logger.info(message.string)
    }

    private fun MinecraftServer.broadcastToOpsAndConsole(message: String) {
        this.broadcastToOps(Component.literal(message))
        ServerReplay.logger.info(message)
    }
}