package me.senseiwells.replay.rejoin

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

            RejoinConfigurationPacketListener(rejoined, connection, cookies).startConfiguration()
            recorder.afterConfigure()

            rejoined.load(player.saveWithoutId(CompoundTag()))
            rejoined.place(connection, cookies)
        }
    }

    init {
        this.id = this.original.id
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

        this.recorder.record(ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(players.players))
        players.sendLevelInfo(this, level)

        for (event in server.customBossEvents.events) {
            if (event.players.contains(this.original) && event.isVisible) {
                this.recorder.record(ClientboundBossEventPacket.createAddPacket(event))
            }
        }

        for (mobEffectInstance in this.activeEffects) {
            this.recorder.record(ClientboundUpdateMobEffectPacket(this.id, mobEffectInstance))
        }
    }
}