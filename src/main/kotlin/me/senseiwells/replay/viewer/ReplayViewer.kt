package me.senseiwells.replay.viewer

import com.replaymod.replaystudio.PacketData
import com.replaymod.replaystudio.lib.viaversion.api.protocol.packet.State
import com.replaymod.replaystudio.lib.viaversion.api.protocol.version.ProtocolVersion
import com.replaymod.replaystudio.protocol.PacketTypeRegistry
import com.replaymod.replaystudio.replay.ReplayFile
import it.unimi.dsi.fastutil.ints.IntArrayList
import it.unimi.dsi.fastutil.ints.IntList
import it.unimi.dsi.fastutil.ints.IntOpenHashSet
import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import kotlinx.coroutines.*
import me.senseiwells.replay.mixin.viewer.EntityInvoker
import me.senseiwells.replay.viewer.ReplayViewerUtils.getClientboundPlayPacketType
import me.senseiwells.replay.viewer.ReplayViewerUtils.sendReplayPacket
import me.senseiwells.replay.viewer.ReplayViewerUtils.setReplayViewer
import me.senseiwells.replay.viewer.ReplayViewerUtils.toClientboundPlayPacket
import net.minecraft.SharedConstants
import net.minecraft.core.UUIDUtil
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.*
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket.Action
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.network.ServerGamePacketListenerImpl
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.phys.Vec3
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

class ReplayViewer(
    val replay: ReplayFile,
    val connection: ServerGamePacketListenerImpl
) {
    private var started = false

    private val coroutineScope by lazy {
        CoroutineScope(Dispatchers.Default + Job())
    }
    private val chunks = Collections.synchronizedCollection(LongOpenHashSet())
    private val entities = Collections.synchronizedCollection(IntOpenHashSet())

    private val player: ServerPlayer
        get() = this.connection.player

    fun start() {
        if (this.started) {
            return
        }
        this.started = true
        this.setForReplayView()

        this.coroutineScope.launch {
            val version = ProtocolVersion.getProtocol(SharedConstants.getProtocolVersion())
            // TODO: Send any configuration data (e.g. resource packs, tags, etc. to the client)
            val stream = replay.getPacketData(PacketTypeRegistry.get(version, State.PLAY))

            var loggedIn = false
            var lastTime = 0L
            var data: PacketData? = stream.readPacket()
            while (data != null) {
                // We don't care about all the packets before the player logs in.
                if (!loggedIn) {
                    val type = data.packet.getClientboundPlayPacketType()
                    if (type != ClientboundLoginPacket::class.java) {
                        data.release()
                        data = stream.readPacket()
                        continue
                    }
                    loggedIn = true
                }

                delay(data.time - lastTime)

                val packet = data.packet.toClientboundPlayPacket()
                send(modifyViewingPacket(packet))

                lastTime = data.time
                data.release()
                data = stream.readPacket()
            }
        }
    }

    fun stop() {
        this.coroutineScope.cancel()
        this.connection.setReplayViewer(null)

        this.connection.send(ClientboundRemoveEntitiesPacket(IntArrayList(this.entities)))

        for (chunk in this.chunks) {
            this.connection.send(ClientboundForgetLevelChunkPacket(ChunkPos(chunk)))
        }
        val player = this.player
        val level = player.serverLevel()
        level.addNewPlayer(player)
        this.connection.send(ClientboundRespawnPacket(player.createCommonSpawnInfo(level), 3.toByte()))
        (player as EntityInvoker).removeRemovalReason()
        this.connection.teleport(player.x, player.y, player.z, player.yRot, player.xRot)
        player.server.playerList.sendLevelInfo(player, level)
    }

    private fun setForReplayView() {
        this.connection.setReplayViewer(this)
        this.player.serverLevel().removePlayerImmediately(this.player, Entity.RemovalReason.CHANGED_DIMENSION);

        this.send(
            ClientboundPlayerInfoUpdatePacket(
                Action.UPDATE_GAME_MODE,
                this.player
            )
        )
        this.player.chunkTrackingView.forEach {
            this.send(ClientboundForgetLevelChunkPacket(it))
        }
    }

    private fun modifyViewingPacket(packet: Packet<*>): Packet<*> {
        if (packet is ClientboundLevelChunkWithLightPacket) {
            this.chunks.add(ChunkPos.asLong(packet.x, packet.z))
        }
        if (packet is ClientboundForgetLevelChunkPacket) {
            this.chunks.remove(packet.pos.toLong())
        }
        if (packet is ClientboundAddEntityPacket) {
            this.entities.add(packet.id)
        }
        if (packet is ClientboundRemoveEntitiesPacket) {
            this.entities.removeAll(packet.entityIds)
        }

        if (packet is ClientboundLoginPacket) {
            return ClientboundLoginPacket(
                VIEWER_ID,
                packet.hardcore,
                packet.levels,
                packet.maxPlayers,
                packet.chunkRadius,
                packet.simulationDistance,
                packet.reducedDebugInfo,
                packet.showDeathScreen,
                packet.doLimitedCrafting,
                packet.commonPlayerSpawnInfo
            )
        }
        if (packet is ClientboundPlayerInfoUpdatePacket) {
            val index = packet.entries().indexOfFirst { it.profileId == this.player.uuid }
            if (index >= 0) {
                val copy = ArrayList(packet.entries())
                val previous = copy[index]
                copy[index] = ClientboundPlayerInfoUpdatePacket.Entry(
                    VIEWER_UUID,
                    previous.profile,
                    false,
                    previous.latency,
                    previous.gameMode,
                    previous.displayName,
                    previous.chatSession
                )
                return ReplayViewerUtils.createClientboundPlayerInfoUpdatePacket(packet.actions(), copy)
            }
        }
        if (packet is ClientboundAddEntityPacket && packet.uuid == this.player.uuid) {
            return ClientboundAddEntityPacket(
                packet.id,
                VIEWER_UUID,
                packet.x,
                packet.y,
                packet.z,
                packet.yRot,
                packet.xRot,
                packet.type,
                packet.data,
                Vec3(packet.xa, packet.ya, packet.za),
                packet.yHeadRot.toDouble()
            )
        }
        if (packet is ClientboundPlayerChatPacket) {
            // We don't want to deal with chat validation...
            val message = packet.unsignedContent ?: Component.literal(packet.body.content)
            val type = packet.chatType.resolve(this.player.server.registryAccess())
            val decorated = if (type.isPresent) {
                type.get().decorate(message)
            } else {
                Component.literal("<").append(packet.chatType.name).append("> ").append(message)
            }
            return ClientboundSystemChatPacket(decorated, false)
        }
        return packet
    }

    private fun send(packet: Packet<*>) {
        this.connection.sendReplayPacket(packet)
    }

    private companion object {
        const val VIEWER_ID = Int.MAX_VALUE - 10
        val VIEWER_UUID: UUID = UUIDUtil.createOfflinePlayerUUID("-ViewingProfile-")
    }
}