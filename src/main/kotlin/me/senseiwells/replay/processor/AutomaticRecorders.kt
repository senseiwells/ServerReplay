package me.senseiwells.replay.processor

import me.senseiwells.replay.ServerReplay
import me.senseiwells.replay.config.predicates.ReplayPlayerContext
import net.casual.arcade.events.GlobalEventHandler
import net.casual.arcade.events.ListenerRegistry.Companion.register
import net.casual.arcade.events.server.ServerStartEvent
import net.casual.arcade.events.server.player.PlayerJoinEvent
import net.casual.arcade.replay.recorder.chunk.ChunkArea
import net.casual.arcade.replay.recorder.chunk.ReplayChunkRecorders
import net.casual.arcade.replay.recorder.player.ReplayPlayerRecorders
import net.casual.arcade.utils.toKey
import net.minecraft.core.registries.Registries
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.ChunkPos

object AutomaticRecorders {
    internal fun registerEvents() {
        GlobalEventHandler.Server.register<ServerStartEvent> { (server) ->
            if (ServerReplay.config.automaticallyRecord) {
                this.startChunks(server)
            }
        }
        GlobalEventHandler.Server.register<PlayerJoinEvent> { (player) ->
            if (ServerReplay.config.automaticallyRecord) {
                this.startPlayer(player)
            }
        }
    }

    private fun startChunks(server: MinecraftServer) {
        for (chunks in ServerReplay.config.chunks) {
            val dimension = chunks.dimension.toKey(Registries.DIMENSION)
            val level = server.getLevel(dimension)
            if (level == null) {
                ServerReplay.logger.warn("Unable to find dimension ${chunks.dimension} for chunk recording")
                continue
            }

            val area = ChunkArea(level, ChunkPos(chunks.fromX, chunks.fromZ), ChunkPos(chunks.toX, chunks.toZ))
            if (ReplayChunkRecorders.has(chunks.name)) {
                ServerReplay.logger.error("Failed to start chunk recording '${chunks.name}', it already exists!")
                continue
            }

            val path = ServerReplay.config.chunkRecordingPath.resolve(chunks.name)
            val format = ServerReplay.config.defaultReplayFormat
            val settings = ServerReplay.config.createSettings()
            val recorder = ReplayChunkRecorders.create(area, path, format, settings, chunks.name)
            recorder.start()
        }
    }

    @Suppress("UnstableApiUsage")
    private fun startPlayer(player: ServerPlayer) {
        val server = player.level().server
        val profile = player.gameProfile
        val context = ReplayPlayerContext(server, profile)
        if (ServerReplay.config.playerPredicate.shouldRecord(context)) {
            val path = ServerReplay.config.getPlayerRecordingLocation(profile)
            val format = ServerReplay.config.defaultReplayFormat
            val settings = ServerReplay.config.createSettings()
            ReplayPlayerRecorders.create(player, path, format, settings).start()
        }
    }
}
