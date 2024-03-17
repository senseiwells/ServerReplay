package me.senseiwells.replay.commands

import com.mojang.brigadier.Command
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.network.protocol.game.ClientboundResourcePackPacket

object PackCommand {
    @JvmStatic
    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(
            Commands.literal("resource-pack").then(
                Commands.literal("set").then(
                    Commands.argument("url", StringArgumentType.string()).executes(this::setPack)
                )
            )
        )
    }
    
    private fun setPack(context: CommandContext<CommandSourceStack>): Int {
        val url = StringArgumentType.getString(context, "url")
        val packet = ClientboundResourcePackPacket(url, "", false, null)
        for (player in context.source.server.playerList.players) {
            player.connection.send(packet)
        }
        return Command.SINGLE_SUCCESS
    }
}