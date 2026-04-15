package me.senseiwells.replay.commands

import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import net.casual.arcade.commands.CommandTree
import net.casual.arcade.commands.argument
import net.casual.arcade.commands.literal
import net.casual.arcade.replay.ducks.PackTracker
import net.minecraft.commands.CommandBuildContext
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.commands.arguments.UuidArgument
import net.minecraft.network.protocol.common.ClientboundResourcePackPopPacket
import net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket
import java.util.*
import java.util.concurrent.CompletableFuture

object PackCommand: CommandTree<CommandSourceStack> {
    override fun create(buildContext: CommandBuildContext): LiteralArgumentBuilder<CommandSourceStack> {
        return CommandTree.buildLiteral("resource-pack") {
            literal("push") {
                argument("url", StringArgumentType.string()) {
                    executes(::pushPackSimple)
                    argument("uuid", UuidArgument.uuid()) {
                        executes(::pushPack)
                    }
                }
            }
            literal("pop") {
                argument("uuid", UuidArgument.uuid()) {
                    suggests(::suggestPacks)
                    executes(::popPack)
                }
            }
        }
    }

    private fun pushPackSimple(context: CommandContext<CommandSourceStack>): Int {
        val url = StringArgumentType.getString(context, "url")
        val uuid = UUID.nameUUIDFromBytes(url.encodeToByteArray())
        return this.pushPack(context, uuid)
    }

    private fun pushPack(
        context: CommandContext<CommandSourceStack>,
        uuid: UUID = UuidArgument.getUuid(context, "uuid")
    ): Int {
        val url = StringArgumentType.getString(context, "url")
        val packet = ClientboundResourcePackPushPacket(uuid, url, "", false, Optional.empty())
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

    @Suppress("UnstableApiUsage")
    private fun suggestPacks(
        context: CommandContext<CommandSourceStack>,
        builder: SuggestionsBuilder
    ): CompletableFuture<Suggestions> {
        val player = context.source.player ?: return Suggestions.empty()
        val packs = (player.connection as PackTracker).`replay$getPacks`()
        return SharedSuggestionProvider.suggest(packs.map { it.id.toString() }, builder)
    }
}