package me.senseiwells.replay.commands

import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.BoolArgumentType
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.ArgumentBuilder
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.SuggestionProvider
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import me.senseiwells.replay.ServerReplay
import me.senseiwells.replay.http.ReplayDownloaderInterceptor
import me.senseiwells.replay.processor.RecorderWarner
import net.casual.arcade.commands.*
import net.casual.arcade.commands.arguments.ChunkPosArgument
import net.casual.arcade.commands.arguments.EnumArgument
import net.casual.arcade.replay.io.FlashbackIO
import net.casual.arcade.replay.io.ReplayFormat
import net.casual.arcade.replay.io.ReplayModIO
import net.casual.arcade.replay.recorder.ReplayRecorder
import net.casual.arcade.replay.recorder.chunk.ChunkArea
import net.casual.arcade.replay.recorder.chunk.ReplayChunkRecorders
import net.casual.arcade.replay.recorder.player.ReplayPlayerRecorders
import net.casual.arcade.replay.util.FileUtils.streamDirectoryEntriesOrEmpty
import net.casual.arcade.replay.viewer.ReplayViewers
import net.casual.arcade.utils.component.bold
import net.casual.arcade.utils.component.link
import net.casual.arcade.utils.component.yellow
import net.minecraft.commands.CommandBuildContext
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.commands.arguments.DimensionArgument
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.permissions.PermissionLevel
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import kotlin.io.path.*

object ReplayCommand: CommandTree<CommandSourceStack> {
    override fun create(buildContext: CommandBuildContext): LiteralArgumentBuilder<CommandSourceStack> {
        return CommandTree.buildLiteral("replay") {
            requiresPermission(ServerReplay.id("commands.replay"), PermissionLevel.GAMEMASTERS)
            literal("start") {
                literal("players") {
                    argument("players", EntityArgument.players()) {
                        executes(::startPlayerRecorders)
                    }
                }
                literal("chunks") {
                    literal("from") {
                        argument("from", ChunkPosArgument.position()) {
                            literal("to") {
                                argument("to", ChunkPosArgument.position()) {
                                    executes { startChunkRecorder(it, it.source.level, null) }
                                    literal("in") {
                                        argument("dimension", DimensionArgument.dimension()) {
                                            executes { startChunkRecorder(it, name = null) }
                                            literal("named") {
                                                argument("name", StringArgumentType.greedyString()) {
                                                    executes(::startChunkRecorder)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    literal("around") {
                        argument("chunk", ChunkPosArgument.position()) {
                            literal("radius") {
                                argument("radius", IntegerArgumentType.integer(1)) {
                                    executes { startChunkRecorderAround(it, it.source.level, null) }
                                    literal("in") {
                                        argument("dimension", DimensionArgument.dimension()) {
                                            executes { startChunkRecorderAround(it, name = null) }
                                            literal("named") {
                                                argument("name", StringArgumentType.greedyString()) {
                                                    executes(::startChunkRecorderAround)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            literal("stop") {
                literal("players") {
                    argument("players", EntityArgument.players()) {
                        executes { stopPlayerRecorders(it, save = true) }
                        argument("save", BoolArgumentType.bool()) {
                            executes(::stopPlayerRecorders)
                        }
                    }
                }
                literal("chunks") {
                    literal("named") {
                        argument("name", StringArgumentType.string()) {
                            suggests { _ -> getChunkRecorderNames() }
                            executes { stopChunkRecorder(it, save = true) }
                            argument("save", BoolArgumentType.bool()) {
                                executes(::stopChunkRecorder)
                            }
                        }
                    }
                    literal("all") {
                        executes { stopRecorders(it, ReplayChunkRecorders.recorders(), save = true) }
                        argument("save", BoolArgumentType.bool()) {
                            executes { stopRecorders(it, ReplayChunkRecorders.recorders()) }
                        }
                    }
                }
                literal("all") {
                    executes { stopRecorders(it, recorders(), save = true) }
                    argument("save", BoolArgumentType.bool()) {
                        executes { stopRecorders(it, recorders()) }
                    }
                }
            }
            literal("status") {
                executes(::queryStatuses)
            }
            literal("reload") {
                executes(::reload)
            }
            literal("download") {
                savedReplayTree(::downloadReplay)
            }
            literal("view") {
                savedReplayTree(::viewReplay)
            }
            literal("marker") {
                literal("add") {
                    literal("players") {
                        argument("players", EntityArgument.players()) {
                            executes { addMarkerToPlayerRecorder(it, name = null) }
                            argument("marker", StringArgumentType.string()) {
                                executes(::addMarkerToPlayerRecorder)
                            }
                        }
                    }
                    literal("chunks") {
                        literal("named") {
                            argument("name", StringArgumentType.string()) {
                                suggests { _ -> getChunkRecorderNames() }
                                executes { addMarkerToChunkRecorder(it, marker = null) }
                                argument("marker", StringArgumentType.string()) {
                                    executes(::addMarkerToChunkRecorder)
                                }
                            }
                        }
                    }
                }
            }
            literal("encoding") {
                literal("default") {
                    literal("set") {
                        argument("encoding", EnumArgument.enumeration<ReplayFormat> { it.id() }) {
                            executes(::setDefaultEncoding)
                        }
                    }
                }
            }
        }
    }

    private fun ArgumentBuilder<CommandSourceStack, *>.savedReplayTree(
        executor: (CommandContext<CommandSourceStack>, RecorderType) -> Int
    ) {
        option<RecorderType, _, _> { type ->
            argument("name", StringArgumentType.string()) {
                suggests(type.nameSuggestionProvider)
                argument("replay", StringArgumentType.string()) {
                    suggests(type.replaySuggestionProvider)
                    executes { context -> executor.invoke(context, type) }
                }
            }
        }
    }

    private fun startPlayerRecorders(context: CommandContext<CommandSourceStack>): Int {
        val players = EntityArgument.getPlayers(context, "players")
        val format = ServerReplay.config.defaultReplayFormat
        val settings = ServerReplay.config.createSettings()
        RecorderWarner.output(context.source, format)
        val successes = players.count { player ->
            val exists = ReplayPlayerRecorders.has(player.uuid, format)
            val path = ServerReplay.config.getPlayerRecordingLocation(player.gameProfile)
            !exists && ReplayPlayerRecorders.create(player, path, format, settings).start()
        }
        if (successes > 0) {
            return context.source.success("Successfully started $successes recordings", true)
        }
        return context.source.fail("Failed to start any recordings")
    }

    private fun startChunkRecorder(
        context: CommandContext<CommandSourceStack>,
        level: ServerLevel = DimensionArgument.getDimension(context, "dimension"),
        name: String? = StringArgumentType.getString(context, "name")
    ): Int {
        val from = ChunkPosArgument.getPosition(context, "from")
        val to = ChunkPosArgument.getPosition(context, "to")

        val area = ChunkArea(level, from, to)
        return this.startChunks(context, area, name)
    }

    private fun startChunkRecorderAround(
        context: CommandContext<CommandSourceStack>,
        level: ServerLevel = DimensionArgument.getDimension(context, "dimension"),
        name: String? = StringArgumentType.getString(context, "name")
    ): Int {
        val pos = ChunkPosArgument.getPosition(context, "chunk")
        val radius = IntegerArgumentType.getInteger(context, "radius")

        val area = ChunkArea.of(level, pos.x, pos.z, radius)
        return this.startChunks(context, area, name)
    }

    private fun startChunks(
        context: CommandContext<CommandSourceStack>,
        area: ChunkArea,
        name: String?
    ): Int {
        val id = if (name != null) name else ReplayChunkRecorders.createNameFor(area)
        if (ReplayChunkRecorders.has(id)) {
            return context.source.fail("Failed to start chunk recorder, already exists")
        }
        val format = ServerReplay.config.defaultReplayFormat
        RecorderWarner.output(context.source, format)
        val path = ServerReplay.config.chunkRecordingPath.resolve(id)
        val settings = ServerReplay.config.createSettings()
        ReplayChunkRecorders.create(area, path, format, settings, null, id).start()
        return context.source.success("Successfully started chunk replay: $id", true)
    }

    private fun stopPlayerRecorders(
        context: CommandContext<CommandSourceStack>,
        save: Boolean = BoolArgumentType.getBool(context, "save")
    ): Int {
        val players = EntityArgument.getPlayers(context, "players")
        var successes = 0
        for (player in players) {
            val recorders = ReplayPlayerRecorders.get(player)
            for (recorder in recorders) {
                recorder.stop(save)
                successes++
            }
        }
        if (successes > 0) {
            return context.source.success("Successfully stopped $successes recordings", true)
        }
        return context.source.fail("Failed to stop any recordings")
    }

    private fun stopChunkRecorder(
        context: CommandContext<CommandSourceStack>,
        save: Boolean = BoolArgumentType.getBool(context, "save")
    ): Int {
        val name = StringArgumentType.getString(context, "name")
        val recorder = ReplayChunkRecorders.get(name)
            ?: return context.source.fail("No such recorder with name '$name' exists")
        recorder.stop(save)
        return context.source.success("Successfully stopped chunk recording '$name'", true)
    }

    private fun stopRecorders(
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

    private fun reload(context: CommandContext<CommandSourceStack>): Int {
        ServerReplay.reload()
        context.source.sendSuccess({ Component.literal("Successfully reloaded config.") }, true)
        return 1
    }

    private fun queryStatuses(context: CommandContext<CommandSourceStack>): Int {
        val builder = StringBuilder("Replay Status:\n")

        val players = getStatusFor("Players", ReplayPlayerRecorders.recorders())
        val chunks = getStatusFor("Chunks", ReplayChunkRecorders.recorders())
        val closing = listOf(ReplayPlayerRecorders.closing(), ReplayChunkRecorders.closing()).flatten()

        for (player in players) {
            builder.append("${player}\n")
        }
        for (chunk in chunks) {
            builder.append("${chunk}\n")
        }
        if (closing.isNotEmpty()) {
            builder.append("Currently Saving:\n")
            for (saving in closing) {
                builder.append("${saving.getName()}\n")
            }
        }

        return context.source.success(builder.removeSuffix("\n").toString())
    }

    private fun viewReplay(
        context: CommandContext<CommandSourceStack>,
        type: RecorderType
    ): Int {
        val player = context.source.playerOrException
        if (ReplayPlayerRecorders.has(player)) {
            return context.source.fail("Cannot view replay while recording, please stop your player recording before viewing!")
        }

        val name = StringArgumentType.getString(context, "name")
        val replay = StringArgumentType.getString(context, "replay")
        val directory = when (type) {
            RecorderType.Players -> ServerReplay.config.playerRecordingPath.resolve(name)
            RecorderType.Chunks -> ServerReplay.config.chunkRecordingPath.resolve(name)
        }

        var path = directory.resolve(ReplayModIO.addFileExtension(replay))
        if (path.notExists()) {
            path = directory.resolve(FlashbackIO.addFileExtension(replay))
        }

        if (path.exists()) {
            ReplayViewers.create(path, player).start()
            return Command.SINGLE_SUCCESS
        }
        return context.source.fail("Failed to view replay, replay $name/$replay doesn't exist!")
    }

    private fun downloadReplay(
        context: CommandContext<CommandSourceStack>,
        type: RecorderType
    ): Int {
        if (!ServerReplay.config.allowDownloadingReplays) {
            return context.source.fail("Downloading replays is disabled, you must enable it in the config")
        }

        val name = StringArgumentType.getString(context, "name")
        val replay = StringArgumentType.getString(context, "replay")
        val root = when (type) {
            RecorderType.Players -> "player/${URLEncoder.encode(name, StandardCharsets.UTF_8)}"
            RecorderType.Chunks -> "chunk/${URLEncoder.encode(name, StandardCharsets.UTF_8)}"
        }

        val path = "$root/${URLEncoder.encode(replay, StandardCharsets.UTF_8)}"
        val url = ReplayDownloaderInterceptor.createUrl(context.source.server, path)
        val resolved = when {
            context.source.isPlayer -> url.resolve(context.source.playerOrException.connection)
            else -> url.resolve()
        }

        val here = Component.literal("[here]").yellow().bold().link(resolved)
        val message = Component.literal("You can download the replay ").append(here)
        context.source.sendSystemMessage(message)
        return Command.SINGLE_SUCCESS
    }

    private fun addMarkerToPlayerRecorder(
        context: CommandContext<CommandSourceStack>,
        name: String? = StringArgumentType.getString(context, "marker")
    ): Int {
        val players = EntityArgument.getPlayers(context, "players")
        val recorders = players.flatMap(ReplayPlayerRecorders::get)
        return this.addMarker(context, name, recorders)
    }

    private fun addMarkerToChunkRecorder(
        context: CommandContext<CommandSourceStack>,
        marker: String? = StringArgumentType.getString(context, "marker")
    ): Int {
        val name = StringArgumentType.getString(context, "name")
        return this.addMarker(context, marker, listOfNotNull(ReplayChunkRecorders.get(name)))
    }

    private fun addMarker(
        context: CommandContext<CommandSourceStack>,
        name: String?,
        recorders: Collection<ReplayRecorder>
    ): Int {
        if (recorders.isEmpty()) {
            return context.source.fail("Failed to mark any recordings")
        }

        for (recorder in recorders) {
            recorder.addMarker(name)
        }
        return context.source.success("Successfully marked ${recorders.size} recordings", true)
    }

    private fun setDefaultEncoding(context: CommandContext<CommandSourceStack>): Int {
        val format = EnumArgument.getEnumeration<ReplayFormat>(context, "encoding")
        if (!format.supported) {
            return context.source.fail("Encoding ${format.id()} is not yet supported for this version of Minecraft")
        }

        ServerReplay.updateConfig { config -> config.copy(defaultReplayFormat = format) }
        format.warn { message ->
            context.source.sendSystemMessage(Component.literal(message))
        }
        return context.source.success("Successfully changed encoding type to ${format.id()}")
    }

    private fun getStatusFor(type: String, recorders: Collection<ReplayRecorder>): List<String> {
        if (recorders.isNotEmpty()) {
            val lines = ArrayList<String>()
            lines.add("Currently Recording $type:")
            for (recorder in recorders) {
                lines.add(recorder.getStatus())
            }
            return lines
        }
        return listOf("Not Currently Recording $type")
    }

    private fun getChunkRecorderNames(): List<String> {
        return ReplayChunkRecorders.recorders().map { "\"${it.getName()}\"" }
    }

    @Suppress("UNUSED_PARAMETER")
    private fun suggestSavedPlayerName(
        context: CommandContext<CommandSourceStack>,
        builder: SuggestionsBuilder
    ): CompletableFuture<Suggestions> {
        val names = ServerReplay.config.playerRecordingPath.streamDirectoryEntriesOrEmpty()
            .filter { it.isDirectory() }
            .map { "\"${it.name}\"" }
        return SharedSuggestionProvider.suggest(names, builder)
    }

    @Suppress("UNUSED_PARAMETER")
    private fun suggestSavedChunkArea(
        context: CommandContext<CommandSourceStack>,
        builder: SuggestionsBuilder
    ): CompletableFuture<Suggestions> {
        val names = ServerReplay.config.chunkRecordingPath.streamDirectoryEntriesOrEmpty()
            .filter { it.isDirectory() }
            .map { "\"${it.name}\"" }
        return SharedSuggestionProvider.suggest(names, builder)
    }

    private fun suggestSavedPlayerReplayName(
        context: CommandContext<CommandSourceStack>,
        builder: SuggestionsBuilder
    ): CompletableFuture<Suggestions> {
        val name = StringArgumentType.getString(context, "name")
        val playerPath = ServerReplay.config.playerRecordingPath.resolve(name)
        val names = playerPath.streamDirectoryEntriesOrEmpty()
            .filter(this::isReplayFile)
            .map { "\"${it.nameWithoutExtension}\"" }
        return SharedSuggestionProvider.suggest(names, builder)
    }

    private fun suggestSavedChunkReplayName(
        context: CommandContext<CommandSourceStack>,
        builder: SuggestionsBuilder
    ): CompletableFuture<Suggestions> {
        val areaName = StringArgumentType.getString(context, "name")
        val chunkPath = ServerReplay.config.chunkRecordingPath.resolve(areaName)
        val names = chunkPath.streamDirectoryEntriesOrEmpty()
            .filter(this::isReplayFile)
            .map { "\"${it.nameWithoutExtension}\"" }
        return SharedSuggestionProvider.suggest(names, builder)
    }

    private fun isReplayFile(path: Path): Boolean {
        return ReplayFormat.formatOf(path) != null
    }

    private fun recorders(): List<ReplayRecorder> {
        return ReplayChunkRecorders.recorders() + ReplayPlayerRecorders.recorders()
    }

    private enum class RecorderType(
        val nameSuggestionProvider: SuggestionProvider<CommandSourceStack>,
        val replaySuggestionProvider: SuggestionProvider<CommandSourceStack>
    ) {
        Players(::suggestSavedPlayerName, ::suggestSavedPlayerReplayName),
        Chunks(::suggestSavedChunkArea, ::suggestSavedChunkReplayName);
    }
}