package me.senseiwells.replay.rejoin

import me.senseiwells.replay.api.ServerReplayPluginManager
import me.senseiwells.replay.chunk.ChunkRecorder
import me.senseiwells.replay.mixin.common.PlayerListAccessor
import me.senseiwells.replay.ducks.`ServerReplay$PackTracker`
import me.senseiwells.replay.player.PlayerRecorder
import me.senseiwells.replay.recorder.ReplayRecorder
import me.senseiwells.replay.viewer.ReplayViewerUtils
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.protocol.game.*
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket.Action
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.network.ServerGamePacketListenerImpl
import net.minecraft.tags.TagNetworkSerialization
import net.minecraft.world.flag.FeatureFlags
import net.minecraft.world.level.GameRules
import net.minecraft.world.level.biome.BiomeManager
import net.minecraft.world.scores.Objective
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashSet

class RejoinedReplayPlayer private constructor(
    val original: ServerPlayer,
    val recorder: ReplayRecorder
): ServerPlayer(original.server, original.serverLevel(), original.gameProfile) {
    init {
        this.id = this.original.id
    }

    private fun sendResourcePacks() {
        val connection = this.original.connection
        // Our connection may be null if we're using a fake player
        if (connection is `ServerReplay$PackTracker`) {
            val packet = connection.`replay$getPack`() ?: return
            this.recorder.record(packet)
        } else {
            this.server.serverResourcePack.ifPresent { packet ->
                this.sendTexturePack(packet.url, packet.hash, packet.isRequired, packet.prompt)
            }
        }
    }

    companion object {
        fun rejoin(player: ServerPlayer, recorder: ReplayRecorder) {
            recorder.afterLogin()

            val rejoined = RejoinedReplayPlayer(player, recorder)
            val connection = RejoinConnection()

            rejoined.load(player.saveWithoutId(CompoundTag()))
            place(rejoined, RejoinGamePacketListener(rejoined, connection), player, rejoined::sendResourcePacks) {
                recorder.shouldHidePlayerFromTabList(it)
            }

            for (plugin in ServerReplayPluginManager.plugins) {
                when (recorder) {
                    is PlayerRecorder -> plugin.onPlayerReplayStart(recorder)
                    is ChunkRecorder -> plugin.onChunkReplayStart(recorder)
                }
            }
        }

        fun place(
            player: ServerPlayer,
            listener: ServerGamePacketListenerImpl,
            old: ServerPlayer = player,
            afterLogin: () -> Unit = {},
            shouldHidePlayer: (ServerPlayer) -> Boolean = { false }
        ) {
            val server = player.server
            val players = server.playerList
            val level = player.serverLevel()
            val levelData = level.levelData
            val rules = level.gameRules
            listener.send(
                ClientboundLoginPacket(
                    player.id,
                    levelData.isHardcore,
                    old.gameMode.gameModeForPlayer,
                    old.gameMode.previousGameModeForPlayer,
                    server.levelKeys(),
                    (players as PlayerListAccessor).frozenRegistries,
                    level.dimensionTypeId(),
                    level.dimension(),
                    BiomeManager.obfuscateSeed(level.seed),
                    players.maxPlayers,
                    players.viewDistance,
                    players.simulationDistance,
                    rules.getBoolean(GameRules.RULE_REDUCEDDEBUGINFO),
                    !rules.getBoolean(GameRules.RULE_DO_IMMEDIATE_RESPAWN),
                    level.isDebug,
                    level.isFlat,
                    old.lastDeathLocation,
                    old.portalCooldown
                )
            )
            afterLogin()

            listener.send(ClientboundUpdateEnabledFeaturesPacket(FeatureFlags.REGISTRY.toNames(level.enabledFeatures())))
            listener.send(
                ClientboundCustomPayloadPacket(
                    ClientboundCustomPayloadPacket.BRAND,
                    PacketByteBufs.create().writeUtf(server.serverModName)
                )
            )
            listener.send(ClientboundChangeDifficultyPacket(levelData.difficulty, levelData.isDifficultyLocked))
            listener.send(ClientboundPlayerAbilitiesPacket(player.abilities))
            listener.send(ClientboundSetCarriedItemPacket(player.inventory.selected))
            listener.send(ClientboundUpdateRecipesPacket(server.recipeManager.recipes))
            listener.send(ClientboundUpdateTagsPacket(TagNetworkSerialization.serializeTagsToNetwork(server.registries())))
            players.sendPlayerPermissionLevel(player)

            player.recipeBook.sendInitialRecipeBook(player)

            val scoreboard = server.scoreboard
            for (playerTeam in scoreboard.playerTeams) {
                listener.send(ClientboundSetPlayerTeamPacket.createAddOrModifyPacket(playerTeam, true))
            }

            val set = HashSet<Objective>()
            for (i in 0..18) {
                val objective = scoreboard.getDisplayObjective(i)
                if (objective != null && !set.contains(objective)) {
                    for (packet in scoreboard.getStartTrackingPackets(objective)) {
                        listener.send(packet)
                    }
                    set.add(objective)
                }
            }

            listener.teleport(player.x, player.y, player.z, player.yRot, player.xRot)
            server.status?.let { player.sendServerStatus(it) }

            // We do this to ensure that we have ALL the players
            // including any 'fake' chunk players
            val uniques = HashSet(players.players)
            if (!uniques.contains(old)) {
                uniques.add(player)
            }

            listener.send(ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(uniques))
            val hidden = ArrayList<ClientboundPlayerInfoUpdatePacket.Entry>()
            for (unique in uniques) {
                val replaced = if (unique.uuid == old.uuid) old else unique
                if (shouldHidePlayer(replaced)) {
                    hidden.add(
                        ClientboundPlayerInfoUpdatePacket.Entry(
                            unique.uuid,
                            unique.gameProfile,
                            false,
                            0,
                            unique.gameMode.gameModeForPlayer,
                            null,
                            null
                        )
                    )
                }
            }
            if (hidden.isNotEmpty()) {
                listener.send(
                    ReplayViewerUtils.createClientboundPlayerInfoUpdatePacket(
                        EnumSet.of(Action.UPDATE_LISTED),
                        hidden
                    )
                )
            }

            players.sendLevelInfo(player, level)

            for (event in server.customBossEvents.events) {
                if (event.players.contains(old) && event.isVisible) {
                    listener.send(ClientboundBossEventPacket.createAddPacket(event))
                }
            }

            for (mobEffectInstance in player.activeEffects) {
                listener.send(ClientboundUpdateMobEffectPacket(player.id, mobEffectInstance))
            }
        }
    }
}