package me.senseiwells.replay.commands

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.BoolArgumentType
import com.mojang.brigadier.context.CommandContext
import me.lucko.fabric.api.permissions.v0.Permissions
import me.senseiwells.replay.config.ReplayConfig
import me.senseiwells.replay.player.PlayerRecorders
import me.senseiwells.replay.player.predicates.ReplayPlayerContext
import me.senseiwells.replay.util.FileUtils
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.network.chat.Component
import org.apache.commons.lang3.builder.StandardToStringStyle
import org.apache.commons.lang3.builder.ToStringBuilder
import org.apache.logging.log4j.core.appender.rolling.FileSize

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
                    Commands.argument("players", EntityArgument.players()).then(
                        Commands.argument("save", BoolArgumentType.bool()).executes(this::onStop)
                    ).executes { this.onStop(it, true) }
                ).then(
                    Commands.literal("all").then(
                        Commands.argument("save", BoolArgumentType.bool()).executes(this::onStopAll)
                    ).executes { this.onStopAll(it, true) }
                )
            ).then(
                Commands.literal("reload").executes(this::onReload)
            ).then(
                Commands.literal("status").executes(this::status)
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

        for (player in context.source.server.playerList.players) {
            if (PlayerRecorders.predicate.test(ReplayPlayerContext.of(player))) {
                PlayerRecorders.create(player).start()
            }
        }

        return 1
    }

    private fun onDisable(context: CommandContext<CommandSourceStack>): Int {
        if (!ReplayConfig.enabled) {
            context.source.sendFailure(Component.literal("ServerReplay is already disabled!"))
            return 0
        }
        ReplayConfig.enabled = false
        for (recorders in PlayerRecorders.all()) {
            recorders.stop()
        }
        context.source.sendSuccess({ Component.literal("ServerReplay is now disabled! Stopped all recordings.") }, true)
        return 1
    }

    private fun onStart(context: CommandContext<CommandSourceStack>): Int {
        val players = EntityArgument.getPlayers(context, "players")
        var i = 0
        for (player in players) {
            if (!PlayerRecorders.has(player) && PlayerRecorders.create(player).start()) {
                i++
            }
        }
        context.source.sendSuccess({ Component.literal("Successfully started $i recordings") }, true)
        return i
    }

    private fun onStop(
        context: CommandContext<CommandSourceStack>,
        save: Boolean = BoolArgumentType.getBool(context, "save")
    ): Int {
        val players = EntityArgument.getPlayers(context, "players")
        var i = 0
        for (player in players) {
            val recorder = PlayerRecorders.get(player)
            if (recorder != null) {
                recorder.stop(save)
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
        for (recorders in PlayerRecorders.all().toList()) {
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

    private fun status(context: CommandContext<CommandSourceStack>): Int {
        val style = StandardToStringStyle().apply {
            fieldSeparator = ", "
            fieldNameValueSeparator = " = "
            isUseClassName = false
            isUseIdentityHashCode = false
        }

        val builder = StringBuilder("ServerReplay is ")
            .append(if (ReplayConfig.enabled) "enabled" else "disabled")
            .append("\n")

        val recorders = PlayerRecorders.all()
        if (recorders.isNotEmpty()) {
            builder.append("Currently Recording:").append("\n")
            for ((recorder, compressed) in recorders.map { it to it.getCompressedRecordingSize() }) {
                val seconds = recorder.getRecordingTimeMS() / 1000
                val hours = seconds / 3600
                val minutes = seconds % 3600 / 60
                val secs = seconds % 60
                val time = "%02d:%02d:%02d".format(hours, minutes, secs)

                val built = ToStringBuilder(recorder, style)
                    .append("name", recorder.playerName)
                    .append("time", time)
                    .append("raw", FileUtils.formatSize(recorder.getRawRecordingSize()))
                    .append("compressed", FileUtils.formatSize(compressed.join()))
                    .toString()
                builder.append(built).append("\n")
            }
        } else {
            builder.append("Not Currently Recording Players")
        }

        context.source.sendSystemMessage(Component.literal(builder.removeSuffix("\n").toString()))
        return 1
    }
}