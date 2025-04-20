package me.senseiwells.replay.commands

import com.google.common.collect.Iterables
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.BoolArgumentType
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.SuggestionProvider
import me.lucko.fabric.api.permissions.v0.Permissions
import me.senseiwells.replay.ServerReplay
import me.senseiwells.replay.http.DownloadReplaysHttpInjector
import me.senseiwells.replay.recorder.ReplayRecorder
import me.senseiwells.replay.recorder.chunk.ChunkArea
import me.senseiwells.replay.recorder.chunk.ChunkRecorder
import me.senseiwells.replay.recorder.chunk.ChunkRecorders
import me.senseiwells.replay.recorder.player.PlayerRecorders
import me.senseiwells.replay.util.FileUtils.streamDirectoryEntriesOrEmpty
import me.senseiwells.replay.util.ReplayModIO
import me.senseiwells.replay.util.flashback.FlashbackIO
import me.senseiwells.replay.viewer.ReplayViewers
import me.senseiwells.replay.writer.ReplayWriterType
import net.minecraft.ChatFormatting
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.commands.arguments.DimensionArgument
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.ChunkPos
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import kotlin.io.path.*

object ReplayCommand {
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
                    Commands.literal("players").then(
                        Commands.argument("players", EntityArgument.players()).executes(this::onStartPlayer)
                    )
                ).then(
                    Commands.literal("chunks").then(
                        Commands.literal("from").then(
                            Commands.argument("fromX", IntegerArgumentType.integer()).suggests(this.suggestChunkX()).then(
                                Commands.argument("fromZ", IntegerArgumentType.integer()).suggests(this.suggestChunkZ()).then(
                                    Commands.literal("to").then(
                                        Commands.argument("toX", IntegerArgumentType.integer()).suggests(this.suggestChunkX()).then(
                                            Commands.argument("toZ", IntegerArgumentType.integer()).suggests(this.suggestChunkZ()).then(
                                                Commands.literal("in").then(
                                                    Commands.argument("dimension", DimensionArgument.dimension()).then(
                                                        Commands.literal("named").then(
                                                            Commands.argument("name", StringArgumentType.greedyString()).executes(this::onStartChunks)
                                                        )
                                                    ).executes { this.onStartChunks(it, name = null) }
                                                )
                                            ).executes { this.onStartChunks(it, it.source.level, null) }
                                        )
                                    )
                                )
                            )
                        )
                    ).then(
                        Commands.literal("around").then(
                            Commands.argument("x", IntegerArgumentType.integer()).suggests(this.suggestChunkX()).then(
                                Commands.argument("z", IntegerArgumentType.integer()).suggests(this.suggestChunkZ()).then(
                                    Commands.literal("radius").then(
                                        Commands.argument("radius", IntegerArgumentType.integer(1)).then(
                                            Commands.literal("in").then(
                                                Commands.argument("dimension", DimensionArgument.dimension()).then(
                                                    Commands.literal("named").then(
                                                        Commands.argument("name", StringArgumentType.greedyString()).executes(this::onStartChunksAround)
                                                    )
                                                ).executes { this.onStartChunksAround(it, name = null) }
                                            )
                                        ).executes { this.onStartChunksAround(it, it.source.level, null) }
                                    )
                                )
                            )
                        )
                    )
                )
            ).then(
                Commands.literal("stop").then(
                    Commands.literal("players").then(
                        Commands.argument("players", EntityArgument.players()).then(
                            Commands.argument("save", BoolArgumentType.bool()).executes(this::onStopPlayers)
                        ).executes { this.onStopPlayers(it, true) }
                    )
                ).then(
                    Commands.literal("chunks").then(
                        Commands.literal("from").then(
                            Commands.argument("fromX", IntegerArgumentType.integer()).suggests(this.suggestExistingFromChunkX()).then(
                                Commands.argument("fromZ", IntegerArgumentType.integer()).suggests(this.suggestExistingFromChunkZ()).then(
                                    Commands.literal("to").then(
                                        Commands.argument("toX", IntegerArgumentType.integer()).suggests(this.suggestExistingToChunkX()).then(
                                            Commands.argument("toZ", IntegerArgumentType.integer()).suggests(this.suggestExistingToChunkZ()).then(
                                                Commands.literal("in").then(
                                                    Commands.argument("dimension", DimensionArgument.dimension()).then(
                                                        Commands.argument("save", BoolArgumentType.bool()).executes(this::onStopChunks)
                                                    ).executes { this.onStopChunks(it, save = true) }
                                                )
                                            ).executes { this.onStopChunks(it, it.source.level, true) }
                                        )
                                    )
                                )
                            )
                        )
                    ).then(
                        Commands.literal("named").then(
                            Commands.argument("name", StringArgumentType.string()).suggests(this.suggestExistingName()).then(
                                Commands.argument("save", BoolArgumentType.bool()).executes(this::onStopChunksNamed)
                            ).executes { this.onStopChunksNamed(it, true) }
                        )
                    ).then(
                        Commands.literal("all").then(
                            Commands.argument("save", BoolArgumentType.bool()).executes { this.onStopAll(it, ChunkRecorders.recorders()) }
                        ).executes { this.onStopAll(it, ChunkRecorders.recorders(), true) }
                    )
                ).then(
                    Commands.literal("all").then(
                        Commands.argument("save", BoolArgumentType.bool()).executes { this.onStopAll(it, Iterables.concat(
                            ChunkRecorders.recorders(), PlayerRecorders.recorders())) }
                    ).executes { this.onStopAll(it, Iterables.concat(ChunkRecorders.recorders(), PlayerRecorders.recorders()), true) }
                )
            ).then(
                Commands.literal("reload").executes(this::onReload)
            ).then(
                Commands.literal("status").executes(this::status)
            ).then(
                Commands.literal("view").then(
                    Commands.literal("players").then(
                        Commands.argument("name", StringArgumentType.string()).suggests(this.suggestSavedPlayerName()).then(
                            Commands.argument("replay", StringArgumentType.string()).suggests(this.suggestSavedPlayerReplayName()).executes {
                                this.viewReplay(it, true)
                            }
                        )
                    )
                ).then(
                    Commands.literal("chunks").then(
                        Commands.argument("area", StringArgumentType.string()).suggests(this.suggestSavedChunkArea()).then(
                            Commands.argument("replay", StringArgumentType.string()).suggests(this.suggestSavedChunkReplayName()).executes {
                                this.viewReplay(it, false)
                            }
                        )
                    )
                )
            ).then(
                Commands.literal("download").then(
                    Commands.literal("players").then(
                        Commands.argument("name", StringArgumentType.string()).suggests(this.suggestSavedPlayerName()).then(
                            Commands.argument("replay", StringArgumentType.string()).suggests(this.suggestSavedPlayerReplayName()).executes {
                                this.downloadReplay(it, true)
                            }
                        )
                    )
                ).then(
                    Commands.literal("chunks").then(
                        Commands.argument("area", StringArgumentType.string()).suggests(this.suggestSavedChunkArea()).then(
                            Commands.argument("replay", StringArgumentType.string()).suggests(this.suggestSavedChunkReplayName()).executes {
                                this.downloadReplay(it, false)
                            }
                        )
                    )
                )
            ).then(
                Commands.literal("marker").then(
                    Commands.literal("add").then(
                        Commands.literal("players").then(
                            Commands.argument("players", EntityArgument.players()).then(
                                Commands.argument("marker", StringArgumentType.string()).executes(this::addPlayerMarker)
                            ).executes { this.addPlayerMarker(it, null) }
                        )
                    ).then(
                        Commands.literal("chunks").then(
                            Commands.literal("named").then(
                                Commands.argument("name", StringArgumentType.string()).suggests(this.suggestExistingName()).then(
                                    Commands.argument("marker", StringArgumentType.string()).executes(this::addChunkMarker)
                                ).executes { this.addChunkMarker(it, null) }
                            )
                        )
                    )
                )
            ).then(
                Commands.literal("encoding").then(
                    Commands.literal("set").then(
                        Commands.literal("flashback").executes { this.changeEncoding(it, ReplayWriterType.Flashback) }
                    ).then(
                        Commands.literal("replay-mod").executes { this.changeEncoding(it, ReplayWriterType.ReplayMod) }
                    )
                )
            )
        )
    }

    private fun onEnable(context: CommandContext<CommandSourceStack>): Int {
        if (ServerReplay.config.enabled) {
            context.source.sendFailure(Component.literal("ServerReplay is already enabled!"))
            return 0
        }
        ServerReplay.config.enabled = true
        context.source.sendSuccess({ Component.literal("ServerReplay is now enabled!") }, true)

        ServerReplay.config.startPlayers(context.source.server)
        ServerReplay.config.startChunks(context.source.server)

        return 1
    }

    private fun onDisable(context: CommandContext<CommandSourceStack>): Int {
        if (!ServerReplay.config.enabled) {
            context.source.sendFailure(Component.literal("ServerReplay is already disabled!"))
            return 0
        }
        ServerReplay.config.enabled = false
        for (recorders in PlayerRecorders.recorders()) {
            recorders.stop()
        }
        for (recorders in ChunkRecorders.recorders()) {
            recorders.stop()
        }
        context.source.sendSuccess({ Component.literal("ServerReplay is now disabled! Stopped all recordings.") }, true)
        return 1
    }

    private fun onStartPlayer(context: CommandContext<CommandSourceStack>): Int {
        val players = EntityArgument.getPlayers(context, "players")
        var i = 0
        for (player in players) {
            if (!PlayerRecorders.has(player) && PlayerRecorders.create(player).start()) {
                i++
            }
        }
        if (i > 0) {
            context.source.sendSuccess({ Component.literal("Successfully started $i recordings") }, true)
        } else {
            context.source.sendFailure(Component.literal("Failed to start any recordings"))
        }
        return i
    }

    private fun onStartChunks(
        context: CommandContext<CommandSourceStack>,
        level: ServerLevel = DimensionArgument.getDimension(context, "dimension"),
        name: String? = StringArgumentType.getString(context, "name")
    ): Int {
        val fromX = IntegerArgumentType.getInteger(context, "fromX")
        val fromZ = IntegerArgumentType.getInteger(context, "fromZ")
        val toX = IntegerArgumentType.getInteger(context, "toX")
        val toZ = IntegerArgumentType.getInteger(context, "toZ")

        val area = ChunkArea(level, ChunkPos(fromX, fromZ), ChunkPos(toX, toZ))
        return this.startChunks(context, area, name)
    }

    private fun onStartChunksAround(
        context: CommandContext<CommandSourceStack>,
        level: ServerLevel = DimensionArgument.getDimension(context, "dimension"),
        name: String? = StringArgumentType.getString(context, "name")
    ): Int {
        val x = IntegerArgumentType.getInteger(context, "x")
        val z = IntegerArgumentType.getInteger(context, "z")
        val radius = IntegerArgumentType.getInteger(context, "radius")

        val area = ChunkArea.of(level, x, z, radius)
        return this.startChunks(context, area, name)
    }

    private fun startChunks(
        context: CommandContext<CommandSourceStack>,
        area: ChunkArea,
        name: String?
    ): Int {
        val id = if (name != null) name else ChunkRecorders.generateName(area)
        if (!ChunkRecorders.isAvailable(area, id)) {
            context.source.sendFailure(Component.literal("Failed to start chunk replay, already exists"))
            return 0
        }
        val recorder = ChunkRecorders.create(area, id)
        recorder.start()
        context.source.sendSuccess({ Component.literal("Successfully started chunk replay: ${recorder.getName()}") }, true)
        return 1
    }

    private fun onStopPlayers(
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
        if (i > 0) {
            context.source.sendSuccess({ Component.literal("Successfully stopped $i recordings") }, true)
        } else {
            context.source.sendFailure(Component.literal("Failed to stop any recordings"))
        }
        return i
    }

    private fun onStopChunks(
        context: CommandContext<CommandSourceStack>,
        level: ServerLevel = DimensionArgument.getDimension(context, "dimension"),
        save: Boolean = BoolArgumentType.getBool(context, "save")
    ): Int {
        val fromX = IntegerArgumentType.getInteger(context, "fromX")
        val fromZ = IntegerArgumentType.getInteger(context, "fromZ")
        val toX = IntegerArgumentType.getInteger(context, "toX")
        val toZ = IntegerArgumentType.getInteger(context, "toZ")

        val area = ChunkArea(level, ChunkPos(fromX, fromZ), ChunkPos(toX, toZ))
        return this.stopChunkRecorder(context, ChunkRecorders.get(area), save)
    }

    private fun onStopChunksNamed(
        context: CommandContext<CommandSourceStack>,
        save: Boolean = BoolArgumentType.getBool(context, "save")
    ): Int {
        val name = StringArgumentType.getString(context, "name")
        return this.stopChunkRecorder(context, ChunkRecorders.get(name), save)
    }

    private fun stopChunkRecorder(
        context: CommandContext<CommandSourceStack>,
        recorder: ChunkRecorder?,
        save: Boolean
    ): Int {
        if (recorder == null) {
            context.source.sendFailure(Component.literal("No such recorder for that area exists"))
            return 0
        }
        recorder.stop(save)
        context.source.sendSuccess({ Component.literal("Successfully stopped recording") }, true)
        return 1
    }

    private fun onStopAll(
        context: CommandContext<CommandSourceStack>,
        recorders: Iterable<ReplayRecorder>,
        save: Boolean = BoolArgumentType.getBool(context, "save"),
    ): Int {
        for (recorder in recorders) {
            recorder.stop(save)
        }
        context.source.sendSuccess({ Component.literal("Successfully stopped all recordings.") }, true)
        return 1
    }

    private fun onReload(context: CommandContext<CommandSourceStack>): Int {
        ServerReplay.reload()
        context.source.sendSuccess({ Component.literal("Successfully reloaded config.") }, true)
        return 1
    }

    private fun status(context: CommandContext<CommandSourceStack>): Int {
        val builder = StringBuilder("ServerReplay is ")
            .append(if (ServerReplay.config.enabled) "enabled" else "disabled")
            .append("\n")

        val players = this.getStatusFuture("Players", PlayerRecorders.recorders())
        val chunks = this.getStatusFuture("Chunks", ChunkRecorders.recorders())
        val closing = listOf(PlayerRecorders.closing(), ChunkRecorders.closing()).flatten()

        CompletableFuture.runAsync {
            for (player in players) {
                builder.append("${player.join()}\n")
            }
            for (chunk in chunks) {
                builder.append("${chunk.join()}\n")
            }
            if (closing.isNotEmpty()) {
                builder.append("Currently Saving:\n")
                for (saving in closing) {
                    builder.append("${saving.getName()}\n")
                }
            }

            context.source.server.execute {
                context.source.sendSuccess(
                    { Component.literal(builder.removeSuffix("\n").toString()) },
                    true
                )
            }
        }

        context.source.sendSuccess({
            Component.literal("Generating replay status...")
        }, true)
        return 1
    }

    private fun viewReplay(
        context: CommandContext<CommandSourceStack>,
        isPlayer: Boolean
    ): Int {
        val player = context.source.playerOrException
        val path = if (isPlayer) {
            val name = StringArgumentType.getString(context, "name")
            ServerReplay.config.playerRecordingPath.resolve(name)
        } else {
            val area = StringArgumentType.getString(context, "area")
            ServerReplay.config.chunkRecordingPath.resolve(area)
        }

        val replayName = StringArgumentType.getString(context, "replay")
        var replayPath = path.resolve("${replayName}.mcpr")
        if (replayPath.notExists()) {
            replayPath = path.resolve("${replayName}.zip")
        }

        if (replayPath.exists()) {
            ReplayViewers.start(replayPath, player)
            return 1
        }

        context.source.sendFailure(Component.literal("Failed to view replay, file $replayName doesn't exist!"))
        return 0
    }

    private fun downloadReplay(
        context: CommandContext<CommandSourceStack>,
        isPlayer: Boolean
    ): Int {
        if (!ServerReplay.config.allowDownloadingReplays) {
            context.source.sendFailure(
                Component.literal("Downloading replays is disabled, you must enable it in the config")
            )
            return 0
        }

        val root = if (isPlayer) {
            val name = StringArgumentType.getString(context, "name")
            "player/${URLEncoder.encode(name, StandardCharsets.UTF_8)}"
        } else {
            val area = StringArgumentType.getString(context, "area")
            "chunk/${URLEncoder.encode(area, StandardCharsets.UTF_8)}"
        }

        val name = StringArgumentType.getString(context, "replay")
        val path = "$root/${URLEncoder.encode(name, StandardCharsets.UTF_8)}.mcpr"

        val url = DownloadReplaysHttpInjector.createUrl(context.source.server, path)
        val here = Component.literal("[here]")
            .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD)
            .withStyle { it.withClickEvent(ClickEvent(ClickEvent.Action.OPEN_URL, url)) }
        val message = Component.literal("You can download the replay ").append(here)
        context.source.sendSystemMessage(message)
        return 1
    }

    private fun addPlayerMarker(
        context: CommandContext<CommandSourceStack>,
        name: String? = StringArgumentType.getString(context, "marker")
    ): Int {
        val players = EntityArgument.getPlayers(context, "players")
        val recorders = players.mapNotNull(PlayerRecorders::get)
        return this.addMarker(context, name, recorders)
    }

    private fun addChunkMarker(
        context: CommandContext<CommandSourceStack>,
        name: String? = StringArgumentType.getString(context, "marker")
    ): Int {
        val area = StringArgumentType.getString(context, "name")
        return this.addMarker(context, name, listOfNotNull(ChunkRecorders.get(area)))
    }

    private fun addMarker(
        context: CommandContext<CommandSourceStack>,
        name: String?,
        recorders: Collection<ReplayRecorder>
    ): Int {
        for (recorder in recorders) {
            recorder.addMarker(name)
        }
        if (recorders.isNotEmpty()) {
            context.source.sendSuccess({ Component.literal("Successfully marked ${recorders.size} recordings") }, true)
        } else {
            context.source.sendFailure(Component.literal("Failed to mark any recordings"))
        }
        return 1
    }

    private fun getStatusFuture(
        type: String,
        recorders: Collection<ReplayRecorder>
    ): List<CompletableFuture<String>> {
        if (recorders.isNotEmpty()) {
            val futures = ArrayList<CompletableFuture<String>>()
            futures.add(CompletableFuture.completedFuture("Currently Recording $type:"))
            for (recorder in recorders) {
                futures.add(recorder.getStatusWithSize())
            }
            return futures
        }
        return listOf(CompletableFuture.completedFuture("Not Currently Recording $type"))
    }

    private fun changeEncoding(context: CommandContext<CommandSourceStack>, type: ReplayWriterType): Int {
        ServerReplay.config.writerType = type
        type.warn { message ->
            context.source.sendSystemMessage(Component.literal(message))
        }
        context.source.sendSuccess({
            Component.literal("Successfully changed encoding type to ${type.name}")
        }, true)
        return 1
    }

    private fun suggestChunkX(): SuggestionProvider<CommandSourceStack> {
        return SuggestionProvider<CommandSourceStack> { c, b ->
            val x = c.source.playerOrException.chunkPosition().x
            SharedSuggestionProvider.suggest(listOf(x.toString()), b)
        }
    }

    private fun suggestChunkZ(): SuggestionProvider<CommandSourceStack> {
        return SuggestionProvider<CommandSourceStack> { c, b ->
            val x = c.source.playerOrException.chunkPosition().z
            SharedSuggestionProvider.suggest(listOf(x.toString()), b)
        }
    }

    private fun suggestExistingFromChunkX(): SuggestionProvider<CommandSourceStack> {
        return SuggestionProvider<CommandSourceStack> { _, b ->
            SharedSuggestionProvider.suggest(ChunkRecorders.recorders().map { it.chunks.from.x.toString() }, b)
        }
    }

    private fun suggestExistingFromChunkZ(): SuggestionProvider<CommandSourceStack> {
        return SuggestionProvider<CommandSourceStack> { _, b ->
            SharedSuggestionProvider.suggest(ChunkRecorders.recorders().map { it.chunks.from.z.toString() }, b)
        }
    }

    private fun suggestExistingToChunkX(): SuggestionProvider<CommandSourceStack> {
        return SuggestionProvider<CommandSourceStack> { _, b ->
            SharedSuggestionProvider.suggest(ChunkRecorders.recorders().map { it.chunks.to.x.toString() }, b)
        }
    }

    private fun suggestExistingToChunkZ(): SuggestionProvider<CommandSourceStack> {
        return SuggestionProvider<CommandSourceStack> { _, b ->
            SharedSuggestionProvider.suggest(ChunkRecorders.recorders().map { it.chunks.to.z.toString() }, b)
        }
    }

    private fun suggestExistingName(): SuggestionProvider<CommandSourceStack> {
        return SuggestionProvider<CommandSourceStack> { _, b ->
            SharedSuggestionProvider.suggest(ChunkRecorders.recorders().map { "\"${it.getName()}\"" }, b)
        }
    }

    private fun suggestSavedChunkArea(): SuggestionProvider<CommandSourceStack> {
        return SuggestionProvider<CommandSourceStack> { _, b ->
            val names = ServerReplay.config.chunkRecordingPath.streamDirectoryEntriesOrEmpty()
                .filter { it.isDirectory() }
                .map { "\"${it.name}\"" }
            SharedSuggestionProvider.suggest(names, b)
        }
    }

    private fun suggestSavedPlayerName(): SuggestionProvider<CommandSourceStack> {
        return SuggestionProvider<CommandSourceStack> { _, b ->
            val names = ServerReplay.config.playerRecordingPath.streamDirectoryEntriesOrEmpty()
                .filter { it.isDirectory() }
                .map { "\"${it.name}\"" }
            SharedSuggestionProvider.suggest(names, b)
        }
    }

    private fun suggestSavedPlayerReplayName(): SuggestionProvider<CommandSourceStack> {
        return SuggestionProvider<CommandSourceStack> { c, b ->
            val name = StringArgumentType.getString(c, "name")
            val playerPath = ServerReplay.config.playerRecordingPath.resolve(name)
            val names = playerPath.streamDirectoryEntriesOrEmpty()
                .filter(this::isReplayFile)
                .map { "\"${it.nameWithoutExtension}\"" }
            SharedSuggestionProvider.suggest(names, b)
        }
    }

    private fun suggestSavedChunkReplayName(): SuggestionProvider<CommandSourceStack> {
        return SuggestionProvider<CommandSourceStack> { c, b ->
            val areaName = StringArgumentType.getString(c, "area")
            val chunkPath = ServerReplay.config.chunkRecordingPath.resolve(areaName)
            val names = chunkPath.streamDirectoryEntriesOrEmpty()
                .filter(this::isReplayFile)
                .map { "\"${it.nameWithoutExtension}\"" }
            SharedSuggestionProvider.suggest(names, b)
        }
    }

    private fun isReplayFile(path: Path): Boolean {
        return !path.isDirectory() && (ReplayModIO.isReplayFile(path) || FlashbackIO.isFlashbackFile(path))
    }
}