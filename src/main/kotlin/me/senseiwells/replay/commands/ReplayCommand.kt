package me.senseiwells.replay.commands

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.BoolArgumentType
import com.mojang.brigadier.context.CommandContext
import me.lucko.fabric.api.permissions.v0.Permissions
import me.senseiwells.replay.config.ReplayConfig
import me.senseiwells.replay.player.PlayerRecorders
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.network.chat.Component

object ReplayCommand {
    @JvmStatic
    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(
            Commands.literal("replay").requires {
                Permissions.check(it, "replay.commands.replay", 4)
            }.then(
                Commands.literal("enable").executes(this::onEnable)
            ).then(
                Commands.literal("disable").executes(this::onDisable)
            ).then(
                Commands.literal("start").then(
                    Commands.argument("players", EntityArgument.players()).executes(this::onStart)
                )
            ).then(
                Commands.literal("stop").then(
                    Commands.argument("players", EntityArgument.players()).executes(this::onStop)
                ).then(
                    Commands.argument("save", BoolArgumentType.bool()).executes(this::onStopAll)
                ).executes { this.onStopAll(it, true) }
            ).then(
                Commands.literal("reload").executes(this::onReload)
            )
        )
    }

    private fun onEnable(context: CommandContext<CommandSourceStack>): Int {
        if (ReplayConfig.enabled) {
            context.source.sendFailure(Component.literal("ServerReplay is already enabled!"))
            return 0
        }
        ReplayConfig.enabled = true
        context.source.sendSuccess({ Component.literal("ServerReplay is now enabled!") }, true)
        return 1
    }

    private fun onDisable(context: CommandContext<CommandSourceStack>): Int {
        if (!ReplayConfig.enabled) {
            context.source.sendFailure(Component.literal("ServerReplay is already disabled!"))
            return 0
        }
        ReplayConfig.enabled = false
        context.source.sendSuccess({ Component.literal("ServerReplay is now disabled! Stopped all recordings.") }, true)
        return 1
    }

    private fun onStart(context: CommandContext<CommandSourceStack>): Int {
        val players = EntityArgument.getPlayers(context, "players")
        var i = 0
        for (player in players) {
            if (!PlayerRecorders.has(player)) {
                PlayerRecorders.create(player).start()
                i++
            }
        }
        context.source.sendSuccess({ Component.literal("Successfully started $i recordings") }, true)
        return i
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
        context.source.sendSuccess({ Component.literal("Successfully stopped $i recordings") }, true)
        return i
    }

    private fun onStopAll(
        context: CommandContext<CommandSourceStack>,
        save: Boolean = BoolArgumentType.getBool(context, "save")
    ): Int {
        for (recorders in PlayerRecorders.all()) {
            recorders.stop(save)
        }
        context.source.sendSuccess({ Component.literal("Successfully stopped all recordings.") }, true)
        return 1
    }

    private fun onReload(context: CommandContext<CommandSourceStack>): Int {
        ReplayConfig.read()
        context.source.sendSuccess({ Component.literal("Successfully reloaded config.") }, true)
        return 1
    }
}