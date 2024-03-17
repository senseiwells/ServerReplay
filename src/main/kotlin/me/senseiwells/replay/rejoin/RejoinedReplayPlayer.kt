package me.senseiwells.replay.rejoin

import me.senseiwells.replay.api.ReplayPluginManager
import me.senseiwells.replay.chunk.ChunkRecorder
import me.senseiwells.replay.ducks.`ServerReplay$PackTracker`
import me.senseiwells.replay.player.PlayerRecorder
import me.senseiwells.replay.recorder.ReplayRecorder
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs
import net.minecraft.core.RegistryAccess
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.protocol.game.*
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.GameRules
import net.minecraft.world.level.biome.BiomeManager
import net.minecraft.world.scores.Objective

class RejoinedReplayPlayer private constructor(
    val original: ServerPlayer,
    val recorder: ReplayRecorder
): ServerPlayer(original.server, original.getLevel(), original.gameProfile) {
    companion object {
        fun rejoin(player: ServerPlayer, recorder: ReplayRecorder) {
            recorder.afterLogin()

            val rejoined = RejoinedReplayPlayer(player, recorder)
            val connection = RejoinConnection()

            rejoined.load(player.saveWithoutId(CompoundTag()))
            rejoined.place(connection)
        }
    }

    init {
        this.id = this.original.id
    }

    private fun sendResourcePacks() {
        val connection = this.original.connection
        // Our connection may be null if we're using a fake player
        if (connection is `ServerReplay$PackTracker`) {
            val packet = connection.`replay$getPack`() ?: return
            this.recorder.record(packet)
        }
    }

    private fun place(connection: RejoinConnection) {
        // Create the fake packet listener
        val listener = RejoinGamePacketListener(this, connection)

        val server = this.server
        val players = server.playerList
        val level = this.getLevel()
        val levelData = level.levelData
        val rules = level.gameRules
        this.recorder.record(ClientboundLoginPacket(
            this.id,
            this.original.gameMode.gameModeForPlayer,
            this.original.gameMode.previousGameModeForPlayer,
            BiomeManager.obfuscateSeed(level.seed),
            levelData.isHardcore,
            server.levelKeys(),
            server.registryAccess() as RegistryAccess.RegistryHolder,
            level.dimensionType(),
            level.dimension(),
            players.maxPlayers,
            players.viewDistance,
            rules.getBoolean(GameRules.RULE_REDUCEDDEBUGINFO),
            !rules.getBoolean(GameRules.RULE_DO_IMMEDIATE_RESPAWN),
            level.isDebug,
            level.isFlat
        ))
        this.recorder.record(ClientboundCustomPayloadPacket(ClientboundCustomPayloadPacket.BRAND, PacketByteBufs.create().writeUtf(this.server.serverModName)))
        this.recorder.record(ClientboundChangeDifficultyPacket(levelData.difficulty, levelData.isDifficultyLocked))
        this.recorder.record(ClientboundPlayerAbilitiesPacket(this.abilities))
        this.recorder.record(ClientboundSetCarriedItemPacket(this.inventory.selected))
        this.recorder.record(ClientboundUpdateRecipesPacket(server.recipeManager.recipes))
        this.recorder.record(ClientboundUpdateTagsPacket(this.server.tags.serializeToNetwork(server.registryAccess())))
        players.sendPlayerPermissionLevel(this)

        this.recipeBook.sendInitialRecipeBook(this)

        val scoreboard = server.scoreboard
        for (playerTeam in scoreboard.playerTeams) {
            this.recorder.record(ClientboundSetPlayerTeamPacket.createAddOrModifyPacket(playerTeam, true))
        }

        val set = HashSet<Objective>()
        for (i in 0..18) {
            val objective = scoreboard.getDisplayObjective(i)
            if (objective != null && !set.contains(objective)) {
                for (packet in scoreboard.getStartTrackingPackets(objective)) {
                    this.recorder.record(packet)
                }
                set.add(objective)
            }
        }

        listener.teleport(this.x, this.y, this.z, this.yRot, this.xRot)

        // We do this to ensure that we have ALL the players
        // including any 'fake' chunk players
        val uniques = HashSet(players.players)
        if (!uniques.contains(this.original)) {
            uniques.add(this)
        }

        for (player in uniques) {
            this.recorder.record(ClientboundPlayerInfoPacket(ClientboundPlayerInfoPacket.Action.ADD_PLAYER, player))
        }
        players.sendLevelInfo(this, level)

        for (event in server.customBossEvents.events) {
            if (event.players.contains(this.original) && event.isVisible) {
                this.recorder.record(ClientboundBossEventPacket.createAddPacket(event))
            }
        }

        this.sendResourcePacks()
        if (this.server.resourcePack.isNotEmpty()) {
            this.sendTexturePack(
                server.resourcePack,
                server.resourcePackHash,
                server.isResourcePackRequired,
                server.resourcePackPrompt
            )
        }

        for (mobEffectInstance in this.activeEffects) {
            this.recorder.record(ClientboundUpdateMobEffectPacket(this.id, mobEffectInstance))
        }

        for (plugin in ReplayPluginManager.plugins) {
            when (this.recorder) {
                is PlayerRecorder -> plugin.onPlayerReplayStart(this.recorder)
                is ChunkRecorder -> plugin.onChunkReplayStart(this.recorder)
            }
        }
    }
}