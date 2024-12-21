package me.senseiwells.replay.viewer

import com.mojang.brigadier.Command
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.FloatArgumentType
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.tree.RootCommandNode
import me.senseiwells.replay.util.DateTimeUtils.formatHHMMSS
import me.senseiwells.replay.viewer.ReplayViewerUtils.getViewingReplay
import net.minecraft.ChatFormatting
import net.minecraft.commands.CommandSource
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.commands.arguments.TimeArgument
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientGamePacketListener
import net.minecraft.network.protocol.game.ClientboundCommandsPacket
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket
import java.util.function.Consumer
import kotlin.time.Duration.Companion.milliseconds

object ReplayViewerCommands {
    private val dispatcher = CommandDispatcher<CommandSourceStack>()

    init {
        registerReplayViewCommand()
    }

    fun sendCommandPacket(consumer: Consumer<Packet<ClientGamePacketListener>>) {
        // In vanilla, we would check whether the source has
        // access to the commands, see Commands#fillUsableCommands.
        // Here we just assume all the commands are accessible
        @Suppress("UNCHECKED_CAST")
        consumer.accept(
            ClientboundCommandsPacket(this.dispatcher.root as RootCommandNode<SharedSuggestionProvider>)
        )
    }

    fun handleCommand(command: String, viewer: ReplayViewer) {
        val player = viewer.player
        val source = player.createCommandSourceStack().withSource(ReplayViewerCommandSource(viewer))
        val result = this.dispatcher.parse(command, source)
        player.server.commands.performCommand(result, command)
    }

    private fun registerReplayViewCommand() {
        this.dispatcher.register(
            Commands.literal("replay").then(
                Commands.literal("view").then(
                    Commands.literal("close").executes(::stopViewingReplay)
                ).then(
                    Commands.literal("speed").then(
                        Commands.argument("multiplier", FloatArgumentType.floatArg(0.05F)).executes(::setViewingReplaySpeed)
                    )
                ).then(
                    Commands.literal("pause").executes { pauseViewingReplay(it, true) }
                ).then(
                    Commands.literal("unpause").executes { pauseViewingReplay(it, false) }
                ).then(
                    Commands.literal("restart").executes(::restartViewingReplay)
                ).then(
                    Commands.literal("progress").then(
                        Commands.literal("show").executes(::showReplayProgress)
                    ).then(
                        Commands.literal("hide").executes(::hideReplayProgress)
                    )
                ).then(
                    Commands.literal("camera").then(
                        Commands.literal("reset").executes(::resetCamera)
                    )
                ).then(
                    Commands.literal("jump").then(
                        Commands.literal("to").then(
                            Commands.literal("marker").then(
                                Commands.literal("unnamed").then(
                                    Commands.argument("offset", TimeArgument.time(Int.MIN_VALUE)).executes { jumpToMarker(it, name = null) }
                                ).executes { jumpToMarker(it, null, 0) }
                            ).then(
                                Commands.literal("named").then(
                                    Commands.argument("name", StringArgumentType.string()).then(
                                        Commands.argument("offset", TimeArgument.time(Int.MIN_VALUE)).executes(::jumpToMarker)
                                    ).executes { jumpToMarker(it, offset = 0) }
                                )
                            )
                        ).then(
                            Commands.literal("timestamp").then(
                                Commands.argument("time", TimeArgument.time()).executes(::jumpTo)
                            )
                        )
                    )
                ).then(
                    Commands.literal("list").then(
                        Commands.literal("markers").executes(::listMarkers)
                    )
                )
            )
        )
    }

    private fun stopViewingReplay(context: CommandContext<CommandSourceStack>): Int {
        context.source.getReplayViewer().stop()
        return Command.SINGLE_SUCCESS
    }

    private fun setViewingReplaySpeed(context: CommandContext<CommandSourceStack>): Int {
        val speed = FloatArgumentType.getFloat(context, "multiplier")
        val viewer = context.source.getReplayViewer()
        viewer.setSpeed(speed)
        context.source.sendSuccess({
            Component.literal("Successfully set replay speed to $speed")
        }, false)
        return Command.SINGLE_SUCCESS
    }

    private fun pauseViewingReplay(context: CommandContext<CommandSourceStack>, paused: Boolean): Int {
        val viewer = context.source.getReplayViewer()
        if (viewer.setPaused(paused)) {
            context.source.sendSuccess({
                Component.literal("Successfully paused replay")
            }, false)
            return Command.SINGLE_SUCCESS
        }
        context.source.sendFailure(Component.literal("Replay was already paused"))
        return 0
    }

    private fun restartViewingReplay(context: CommandContext<CommandSourceStack>): Int {
        val viewer = context.source.getReplayViewer()
        viewer.restart()
        context.source.sendSuccess({
            Component.literal("Successfully restarted replay")
        }, false)
        return Command.SINGLE_SUCCESS
    }

    private fun showReplayProgress(context: CommandContext<CommandSourceStack>): Int {
        val viewer = context.source.getReplayViewer()
        if (viewer.showProgress()) {
            context.source.sendSuccess({
                Component.literal("Successfully showing replay progress bar")
            }, false)
            return Command.SINGLE_SUCCESS
        }
        context.source.sendFailure(Component.literal("Progress bar was already shown"))
        return 0
    }

    private fun hideReplayProgress(context: CommandContext<CommandSourceStack>): Int {
        val viewer = context.source.getReplayViewer()
        if (viewer.hideProgress()) {
            context.source.sendSuccess({
                Component.literal("Successfully hidden replay progress bar")
            }, false)
            return Command.SINGLE_SUCCESS
        }
        context.source.sendFailure(Component.literal("Progress bar was already hidden"))
        return 0
    }

    private fun resetCamera(context: CommandContext<CommandSourceStack>): Int {
        val viewer = context.source.getReplayViewer()
        viewer.resetCamera()
        context.source.sendSuccess({
            Component.literal("Successfully reset camera")
        }, false)
        return Command.SINGLE_SUCCESS
    }

    private fun jumpToMarker(
        context: CommandContext<CommandSourceStack>,
        name: String? = StringArgumentType.getString(context, "name"),
        offset: Int = IntegerArgumentType.getInteger(context, "offset")
    ): Int {
        val viewer = context.source.getReplayViewer()
        if (viewer.jumpToMarker(name, (offset * 50).milliseconds)) {
            context.source.sendSuccess({
                Component.literal("Successfully jumped to marker")
            }, false)
            return Command.SINGLE_SUCCESS
        }
        context.source.sendFailure(Component.literal("No such marker found, or offset too large"))
        return 0
    }

    private fun jumpTo(context: CommandContext<CommandSourceStack>): Int {
        val viewer = context.source.getReplayViewer()
        val time = IntegerArgumentType.getInteger(context, "time")
        if (viewer.jumpTo((time * 50).milliseconds)) {
            context.source.sendSuccess({
                Component.literal("Successfully jumped to timestamp")
            }, false)
            return Command.SINGLE_SUCCESS
        }
        context.source.sendFailure(Component.literal("Timestamp provided was outside of recording"))
        return 0
    }

    private fun listMarkers(context: CommandContext<CommandSourceStack>): Int {
        val viewer = context.source.getReplayViewer()
        val markers = viewer.getMarkers()
        if (markers.isEmpty()) {
            context.source.sendSuccess({ Component.literal("Replay has no markers") }, false)
            return 0
        }
        val component = Component.empty()
        val iter = viewer.getMarkers().iterator()
        for (marker in iter) {
            val time = marker.time.milliseconds.formatHHMMSS()
            component.append(Component.literal(time).withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD))
            component.append(": ")
            component.append(Component.literal(marker.name ?: "Unnamed").withStyle(ChatFormatting.GREEN))
            if (iter.hasNext()) {
                component.append("\n")
            }
        }
        context.source.sendSuccess({ component }, false)
        return Command.SINGLE_SUCCESS
    }

    private fun CommandSourceStack.getReplayViewer(): ReplayViewer {
        val player = this.playerOrException
        return player.connection.getViewingReplay()
            ?: throw IllegalStateException("Player not viewing replay managed to execute this command!?")
    }

    private class ReplayViewerCommandSource(private val viewer: ReplayViewer): CommandSource {
        override fun sendSystemMessage(component: Component) {
            this.viewer.send(ClientboundSystemChatPacket(component, false))
        }

        override fun acceptsSuccess(): Boolean {
            return true
        }

        override fun acceptsFailure(): Boolean {
            return true
        }

        override fun shouldInformAdmins(): Boolean {
            return true
        }
    }
}