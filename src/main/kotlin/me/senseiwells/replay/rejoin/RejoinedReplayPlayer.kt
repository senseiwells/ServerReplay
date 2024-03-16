package me.senseiwells.replay.rejoin

import me.senseiwells.replay.api.ReplayPluginManager
import me.senseiwells.replay.chunk.ChunkRecorder
import me.senseiwells.replay.ducks.`ServerReplay$PackTracker`
import me.senseiwells.replay.player.PlayerRecorder
import me.senseiwells.replay.recorder.ReplayRecorder
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.protocol.game.*
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.network.CommonListenerCookie
import net.minecraft.world.level.GameRules
import net.minecraft.world.scores.DisplaySlot
import net.minecraft.world.scores.Objective

class RejoinedReplayPlayer private constructor(
    val original: ServerPlayer,
    val recorder: ReplayRecorder
): ServerPlayer(original.server, original.serverLevel(), original.gameProfile, original.clientInformation()) {
    companion object {
        fun rejoin(player: ServerPlayer, recorder: ReplayRecorder) {
            recorder.afterLogin()

            val rejoined = RejoinedReplayPlayer(player, recorder)
            val connection = RejoinConnection()
            val cookies = CommonListenerCookie(player.gameProfile, 0, player.clientInformation())

            val config = RejoinConfigurationPacketListener(rejoined, connection, cookies)
            config.startConfiguration()
            rejoined.sendResourcePacks()
            config.runConfigurationTasks()
            recorder.afterConfigure()

            rejoined.load(player.saveWithoutId(CompoundTag()))
            rejoined.place(connection, cookies)
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

    private fun place(
        connection: RejoinConnection,
        cookies: CommonListenerCookie
    ) {
        // Create the fake packet listener
        val listener = RejoinGamePacketListener(this, connection, cookies)

        val server = this.server
        val players = server.playerList
        val level = this.serverLevel()
        val levelData = level.levelData
        val rules = level.gameRules
        this.recorder.record(ClientboundLoginPacket(
            this.id,
            levelData.isHardcore,
            server.levelKeys(),
            players.maxPlayers,
            players.viewDistance,
            players.simulationDistance,
            rules.getBoolean(GameRules.RULE_REDUCEDDEBUGINFO),
            !rules.getBoolean(GameRules.RULE_DO_IMMEDIATE_RESPAWN),
            rules.getBoolean(GameRules.RULE_LIMITED_CRAFTING),
            this.createCommonSpawnInfo(level)
        ))
        this.recorder.record(ClientboundChangeDifficultyPacket(levelData.difficulty, levelData.isDifficultyLocked))
        this.recorder.record(ClientboundPlayerAbilitiesPacket(this.abilities))
        this.recorder.record(ClientboundSetCarriedItemPacket(this.inventory.selected))
        this.recorder.record(ClientboundUpdateRecipesPacket(server.recipeManager.recipes))
        players.sendPlayerPermissionLevel(this)

        this.recipeBook.sendInitialRecipeBook(this)

        val scoreboard = server.scoreboard
        for (playerTeam in scoreboard.playerTeams) {
            this.recorder.record(ClientboundSetPlayerTeamPacket.createAddOrModifyPacket(playerTeam, true))
        }

        val set = HashSet<Objective>()
        for (displaySlot in DisplaySlot.values()) {
            val objective = scoreboard.getDisplayObjective(displaySlot)
            if (objective != null && !set.contains(objective)) {
                for (packet in scoreboard.getStartTrackingPackets(objective)) {
                    this.recorder.record(packet)
                }
                set.add(objective)
            }
        }

        listener.teleport(this.x, this.y, this.z, this.yRot, this.xRot)
        server.status?.let { this.sendServerStatus(it) }

        // We do this to ensure that we have ALL the players
        // including any 'fake' chunk players
        val uniques = HashSet(players.players)
        if (!uniques.contains(this.original)) {
            uniques.add(this)
        }

        this.recorder.record(ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(uniques))
        players.sendLevelInfo(this, level)

        for (event in server.customBossEvents.events) {
            if (event.players.contains(this.original) && event.isVisible) {
                this.recorder.record(ClientboundBossEventPacket.createAddPacket(event))
            }
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