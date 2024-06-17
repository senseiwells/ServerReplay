package me.senseiwells.replay.viewer

import com.mojang.brigadier.Command
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.FloatArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.tree.RootCommandNode
import me.senseiwells.replay.viewer.ReplayViewerUtils.getViewingReplay
import net.minecraft.commands.CommandSource
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.network.chat.ChatType
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientGamePacketListener
import net.minecraft.network.protocol.game.ClientboundChatPacket
import net.minecraft.network.protocol.game.ClientboundCommandsPacket
import java.util.*
import java.util.function.Consumer

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
        // TODO: Exception handling
        this.dispatcher.execute(command.substring(1), source)
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
        return Command.SINGLE_SUCCESS
    }

    private fun pauseViewingReplay(context: CommandContext<CommandSourceStack>, paused: Boolean): Int {
        val viewer = context.source.getReplayViewer()
        viewer.setPaused(paused)
        return Command.SINGLE_SUCCESS
    }

    private fun restartViewingReplay(context: CommandContext<CommandSourceStack>): Int {
        val viewer = context.source.getReplayViewer()
        viewer.restart()
        return Command.SINGLE_SUCCESS
    }

    private fun CommandSourceStack.getReplayViewer(): ReplayViewer {
        val player = this.playerOrException
        return player.connection.getViewingReplay()
            ?: throw IllegalStateException("Player not viewing replay managed to execute this command!?")
    }

    private class ReplayViewerCommandSource(private val viewer: ReplayViewer): CommandSource {
        override fun sendMessage(component: Component, senderUUID: UUID) {
            this.viewer.send(ClientboundChatPacket(component, ChatType.SYSTEM, senderUUID))
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