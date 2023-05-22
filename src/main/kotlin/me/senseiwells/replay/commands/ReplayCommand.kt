package me.senseiwells.replay.commands

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.context.CommandContext
import me.senseiwells.replay.config.Config
import me.senseiwells.replay.player.PlayerRecorders
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.network.chat.Component

object ReplayCommand {
    @JvmStatic
    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(
            Commands.literal("replay").then(
                Commands.literal("enable").executes(this::onEnable)
            ).then(
                Commands.literal("disable").executes(this::onDisable)
            ).then(
                Commands.literal("stop").then(
                    Commands.argument("players", EntityArgument.players()).executes(this::onStop)
                ).executes(this::onStopAll)
            ).then(
                Commands.literal("reload").executes(this::onReload)
            )
        )
    }

    private fun onEnable(context: CommandContext<CommandSourceStack>): Int {
        if (Config.enabled) {
            context.source.sendFailure(Component.literal("ServerReplay is already enabled!"))
            return 0
        }
        Config.enabled = true
        context.source.sendSuccess(Component.literal("ServerReplay is now enabled! For players to be recorded they must re-log."), true)
        return 1
    }

    private fun onDisable(context: CommandContext<CommandSourceStack>): Int {
        if (!Config.enabled) {
            context.source.sendFailure(Component.literal("ServerReplay is already disabled!"))
            return 0
        }
        Config.enabled = false
        context.source.sendSuccess(Component.literal("ServerReplay is now disabled! Stopped all recordings."), true)
        return 1
    }

    private fun onStop(context: CommandContext<CommandSourceStack>): Int {
        val players = EntityArgument.getPlayers(context, "players")
        var i = 0
        for (player in players) {
            val recorder = PlayerRecorders.get(player)
            if (recorder != null) {
                recorder.stop()
                i++
            }
        }
        context.source.sendSuccess(Component.literal("Successfully stopped $i recordings."), true)
        return i
    }

    private fun onStopAll(context: CommandContext<CommandSourceStack>): Int {
        for (recorders in PlayerRecorders.all()) {
            recorders.stop()
        }
        context.source.sendSuccess(Component.literal("Successfully stopped all recordings."), true)
        return 1
    }

    private fun onReload(context: CommandContext<CommandSourceStack>): Int {
        Config.read()
        context.source.sendSuccess(Component.literal("Successfully reloaded config."), true)
        return 1
    }
}