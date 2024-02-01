package me.senseiwells.replay.commands

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.BoolArgumentType
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.SuggestionProvider
import me.lucko.fabric.api.permissions.v0.Permissions
import me.senseiwells.replay.chunk.ChunkArea
import me.senseiwells.replay.chunk.ChunkRecorder
import me.senseiwells.replay.chunk.ChunkRecorders
import me.senseiwells.replay.config.ReplayConfig
import me.senseiwells.replay.player.PlayerRecorders
import me.senseiwells.replay.player.predicates.ReplayPlayerContext
import me.senseiwells.replay.recorder.ReplayRecorder
import me.senseiwells.replay.util.FileUtils
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.commands.arguments.DimensionArgument
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.ChunkPos
import org.apache.commons.lang3.builder.StandardToStringStyle
import org.apache.commons.lang3.builder.ToStringBuilder

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
                    ).then(
                        Commands.literal("all").then(
                            Commands.argument("save", BoolArgumentType.bool()).executes { this.onStopAll(it, PlayerRecorders.all()) }
                        ).executes { this.onStopAll(it, PlayerRecorders.all(), true) }
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
                            Commands.argument("save", BoolArgumentType.bool()).executes { this.onStopAll(it, ChunkRecorders.all()) }
                        ).executes { this.onStopAll(it, ChunkRecorders.all(), true) }
                    )
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
            if (ReplayConfig.predicate.test(ReplayPlayerContext.of(player))) {
                PlayerRecorders.create(player).tryStart()
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
        for (recorders in ChunkRecorders.all()) {
            recorders.stop()
        }
        context.source.sendSuccess({ Component.literal("ServerReplay is now disabled! Stopped all recordings.") }, true)
        return 1
    }

    private fun onStartPlayer(context: CommandContext<CommandSourceStack>): Int {
        val players = EntityArgument.getPlayers(context, "players")
        var i = 0
        for (player in players) {
            if (!PlayerRecorders.has(player) && PlayerRecorders.create(player).tryStart()) {
                i++
            }
        }
        context.source.sendSuccess({ Component.literal("Successfully started $i recordings") }, true)
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
        recorder.tryStart()
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
        context.source.sendSuccess({ Component.literal("Successfully stopped $i recordings") }, true)
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
        recorders: Collection<ReplayRecorder>,
        save: Boolean = BoolArgumentType.getBool(context, "save"),
    ): Int {
        for (recorder in recorders) {
            recorder.stop(save)
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

        this.appendRecorders(builder, "Players", PlayerRecorders.all(), style)
        this.appendRecorders(builder, "Chunks", ChunkRecorders.all(), style)

        context.source.sendSystemMessage(Component.literal(builder.removeSuffix("\n").toString()))
        return 1
    }

    private fun appendRecorders(
        builder: StringBuilder,
        type: String,
        recorders: Collection<ReplayRecorder>,
        style: StandardToStringStyle,
    ) {
        if (recorders.isNotEmpty()) {
            builder.append("Currently Recording $type:").append("\n")
            for ((recorder, compressed) in recorders.map { it to it.getCompressedRecordingSize() }) {
                val seconds = recorder.getTotalRecordingTime() / 1000
                val hours = seconds / 3600
                val minutes = seconds % 3600 / 60
                val secs = seconds % 60
                val time = "%02d:%02d:%02d".format(hours, minutes, secs)

                val built = ToStringBuilder(recorder, style)
                    .append("name", recorder.getName())
                    .append("time", time)
                    .append("raw", FileUtils.formatSize(recorder.getRawRecordingSize()))
                    .append("compressed", FileUtils.formatSize(compressed.join()))
                    .toString()
                builder.append(built).append("\n")
            }
        } else {
            builder.append("Not Currently Recording $type").append("\n")
        }
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
            SharedSuggestionProvider.suggest(ChunkRecorders.all().map { it.chunks.from.x.toString() }, b)
        }
    }

    private fun suggestExistingFromChunkZ(): SuggestionProvider<CommandSourceStack> {
        return SuggestionProvider<CommandSourceStack> { _, b ->
            SharedSuggestionProvider.suggest(ChunkRecorders.all().map { it.chunks.from.z.toString() }, b)
        }
    }

    private fun suggestExistingToChunkX(): SuggestionProvider<CommandSourceStack> {
        return SuggestionProvider<CommandSourceStack> { _, b ->
            SharedSuggestionProvider.suggest(ChunkRecorders.all().map { it.chunks.to.x.toString() }, b)
        }
    }

    private fun suggestExistingToChunkZ(): SuggestionProvider<CommandSourceStack> {
        return SuggestionProvider<CommandSourceStack> { _, b ->
            SharedSuggestionProvider.suggest(ChunkRecorders.all().map { it.chunks.to.z.toString() }, b)
        }
    }

    private fun suggestExistingName(): SuggestionProvider<CommandSourceStack> {
        return SuggestionProvider<CommandSourceStack> { _, b ->
            SharedSuggestionProvider.suggest(ChunkRecorders.all().map { "\"${it.getName()}\"" }, b)
        }
    }
}