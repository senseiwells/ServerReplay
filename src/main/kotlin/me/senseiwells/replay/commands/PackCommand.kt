package me.senseiwells.replay.commands

import com.mojang.brigadier.Command
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import me.senseiwells.replay.ducks.`ServerReplay$PackTracker`
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.commands.arguments.UuidArgument
import net.minecraft.network.protocol.common.ClientboundResourcePackPopPacket
import net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket
import java.util.Optional
import java.util.UUID
import java.util.concurrent.CompletableFuture

object PackCommand {
    @JvmStatic
    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(
            Commands.literal("resource-pack").then(
                Commands.literal("push").then(
                    Commands.argument("url", StringArgumentType.string()).then(
                        Commands.argument("uuid", UuidArgument.uuid()).executes(this::pushPack)
                    ).executes { this.pushPack(it) }
                )
            ).then(
                Commands.literal("pop").then(
                    Commands.argument("uuid", UuidArgument.uuid()).suggests(this::suggestPacks).executes(this::popPack)
                )
            )
        )
    }

    private fun pushPack(context: CommandContext<CommandSourceStack>): Int {
        val url = StringArgumentType.getString(context, "url")
        val uuid = UUID.nameUUIDFromBytes(url.encodeToByteArray())
        return this.pushPack(context, uuid)
    }

    private fun pushPack(
        context: CommandContext<CommandSourceStack>,
        uuid: UUID = UuidArgument.getUuid(context, "uuid")
    ): Int {
        val url = StringArgumentType.getString(context, "url")
        val packet = ClientboundResourcePackPushPacket(uuid, url, "", false, null)
        for (player in context.source.server.playerList.players) {
            player.connection.send(packet)
        }
        return Command.SINGLE_SUCCESS
    }

    private fun popPack(context: CommandContext<CommandSourceStack>): Int {
        val uuid = UuidArgument.getUuid(context, "uuid")
        val packet = ClientboundResourcePackPopPacket(Optional.of(uuid))
        for (player in context.source.server.playerList.players) {
            player.connection.send(packet)
        }
        return Command.SINGLE_SUCCESS
    }

    private fun suggestPacks(
        context: CommandContext<CommandSourceStack>,
        builder: SuggestionsBuilder
    ): CompletableFuture<Suggestions> {
        val player = context.source.player ?: return Suggestions.empty()
        val packs = (player.connection as `ServerReplay$PackTracker`).`replay$getPacks`()
        return SharedSuggestionProvider.suggest(packs.map { it.id.toString() }, builder)
    }
}