package me.senseiwells.replay.viewer

import com.replaymod.replaystudio.PacketData
import com.replaymod.replaystudio.lib.viaversion.api.protocol.packet.State
import com.replaymod.replaystudio.lib.viaversion.api.protocol.version.ProtocolVersion
import com.replaymod.replaystudio.protocol.PacketTypeRegistry
import com.replaymod.replaystudio.replay.ReplayFile
import it.unimi.dsi.fastutil.ints.IntArrayList
import it.unimi.dsi.fastutil.ints.IntOpenHashSet
import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import kotlinx.coroutines.*
import me.senseiwells.replay.mixin.viewer.EntityInvoker
import me.senseiwells.replay.rejoin.RejoinedReplayPlayer
import me.senseiwells.replay.viewer.ReplayViewerUtils.getClientboundPlayPacketType
import me.senseiwells.replay.viewer.ReplayViewerUtils.sendReplayPacket
import me.senseiwells.replay.viewer.ReplayViewerUtils.setReplayViewer
import me.senseiwells.replay.viewer.ReplayViewerUtils.toClientboundPlayPacket
import net.minecraft.SharedConstants
import net.minecraft.core.UUIDUtil
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.*
import net.minecraft.network.protocol.game.ClientboundGameEventPacket.CHANGE_GAME_MODE
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.network.ServerGamePacketListenerImpl
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.GameType
import net.minecraft.world.phys.Vec3
import net.minecraft.world.scores.DisplaySlot
import java.util.*
import java.util.function.Supplier

class ReplayViewer(
    val replay: ReplayFile,
    val connection: ServerGamePacketListenerImpl
) {
    private var started = false
    private var teleported = false

    private val coroutineScope = CoroutineScope(Dispatchers.Default + Job())

    private var tickSpeed = 20.0F
    private var tickFrozen = false
    private val chunks = Collections.synchronizedCollection(LongOpenHashSet())
    private val entities = Collections.synchronizedCollection(IntOpenHashSet())
    private val players = Collections.synchronizedList(ArrayList<UUID>())

    val player: ServerPlayer
        get() = this.connection.player

    var speedMultiplier = 1.0F
        private set
    var paused: Boolean = false
        private set

    fun start() {
        if (this.started) {
            return
        }
        this.started = true
        this.setForReplayView()

        this.restart()
    }

    fun stop() {
        this.close()
        this.connection.setReplayViewer(null)

        this.removeReplayState()
        this.addBackToServer()
    }

    fun restart() {
        if (!this.started) {
            return
        }
        this.removeReplayState()
        this.close()
        this.coroutineScope.launch {
            streamReplay { this.isActive }
        }
    }

    fun close() {
        this.coroutineScope.coroutineContext.cancelChildren()
    }

    fun setSpeed(speed: Float) {
        if (speed <= 0) {
            throw IllegalArgumentException("Cannot set non-positive speed multiplier!")
        }
        this.speedMultiplier = speed
        this.sendTickingState()
    }

    fun setPaused(paused: Boolean) {
        this.paused = paused
        this.sendTickingState()
    }

    fun onServerboundPacket(packet: Packet<*>) {
        when (packet) {
            is ServerboundChatCommandPacket -> ReplayViewerCommands.handleCommand(packet.command, this)
        }
    }

    private suspend fun streamReplay(
        active: Supplier<Boolean>
    ) {
        val version = ProtocolVersion.getProtocol(SharedConstants.getProtocolVersion())
        // TODO: Send any configuration data (e.g. resource packs, tags, etc. to the client)
        this.replay.getPacketData(PacketTypeRegistry.get(version, State.PLAY)).use { stream ->
            var loggedIn = false
            var lastTime = 0L
            var data: PacketData? = stream.readPacket()
            while (data != null && active.get()) {
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

                delay(((data.time - lastTime) / this.speedMultiplier).toLong())

                while (this.paused) {
                    delay(50)
                }

                val packet = data.packet.toClientboundPlayPacket()
                if (shouldSendPacket(packet)) {
                    val modified = modifyPacketForViewer(packet)
                    onSendPacket(modified)
                    if (!active.get()) {
                        break
                    }
                    send(modified)
                }

                lastTime = data.time
                data.release()
                data = stream.readPacket()
            }
            // Release any remaining data
            data?.release()
        }
    }

    private fun sendTickingState() {
        this.send(this.getTickingStatePacket())
        this.sendAbilities()
    }

    private fun getTickingStatePacket(): ClientboundTickingStatePacket {
        return ClientboundTickingStatePacket(this.tickSpeed * this.speedMultiplier, this.paused || this.tickFrozen)
    }

    private fun sendAbilities() {
        // val abilities = Abilities()
        // abilities.flying = true
        // abilities.mayfly = true
        // abilities.invulnerable = true
        // abilities.flyingSpeed /= this.speedMultiplier
        // abilities.walkingSpeed /= this.speedMultiplier
        // this.send(ClientboundPlayerAbilitiesPacket(abilities))
    }

    private fun setForReplayView() {
        this.removeFromServer()
        this.connection.setReplayViewer(this)

        this.removeServerState()
        this.send(ClientboundGameEventPacket(CHANGE_GAME_MODE, GameType.SPECTATOR.id.toFloat()))
        ReplayViewerCommands.sendCommandPacket(this::send)
    }

    private fun addBackToServer() {
        val player = this.player
        val server = player.server
        val playerList = server.playerList
        val level = player.serverLevel()

        playerList.broadcastAll(
            ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(listOf(player))
        )
        playerList.players.add(player)

        RejoinedReplayPlayer.place(player, this.connection)
        (player as EntityInvoker).removeRemovalReason()
        level.addNewPlayer(player)
    }

    private fun removeFromServer() {
        val player = this.player
        val playerList = player.server.playerList
        playerList.broadcastAll(ClientboundPlayerInfoRemovePacket(listOf(player.uuid)))
        player.serverLevel().removePlayerImmediately(player, Entity.RemovalReason.CHANGED_DIMENSION)
        playerList.players.remove(player)
    }

    private fun removeServerState() {
        val player = this.player
        val server = player.server
        this.send(ClientboundPlayerInfoRemovePacket(server.playerList.players.map { it.uuid }))
        player.chunkTrackingView.forEach {
            this.send(ClientboundForgetLevelChunkPacket(it))
        }
        for (slot in DisplaySlot.entries) {
            this.send(ClientboundSetDisplayObjectivePacket(slot, null))
        }
        for (objective in server.scoreboard.objectives) {
            this.send(ClientboundSetObjectivePacket(objective, ClientboundSetObjectivePacket.METHOD_REMOVE))
        }
        for (bossbar in server.customBossEvents.events) {
            if (bossbar.players.contains(player)) {
                this.send(ClientboundBossEventPacket.createRemovePacket(bossbar.id))
            }
        }
    }

    private fun removeReplayState() {
        synchronized(this.players) {
            this.send(ClientboundPlayerInfoRemovePacket(this.players))
        }
        synchronized(this.entities) {
            this.send(ClientboundRemoveEntitiesPacket(IntArrayList(this.entities)))
        }
        synchronized(this.chunks) {
            for (chunk in this.chunks.iterator()) {
                this.connection.send(ClientboundForgetLevelChunkPacket(ChunkPos(chunk)))
            }
        }
    }

    private fun shouldSendPacket(packet: Packet<*>): Boolean {
        return when (packet) {
            is ClientboundGameEventPacket -> packet.event != CHANGE_GAME_MODE
            is ClientboundPlayerPositionPacket -> {
                // We want the client to teleport to the first initial position
                // subsequent positions will teleport the viewer which we don't want
                val teleported = this.teleported
                this.teleported = true
                return !teleported
            }
            else -> true
        }
    }

    private fun onSendPacket(packet: Packet<*>) {
        // We keep track of some state to revert later
        when (packet) {
            is ClientboundLevelChunkWithLightPacket -> this.chunks.add(ChunkPos.asLong(packet.x, packet.z))
            is ClientboundForgetLevelChunkPacket -> this.chunks.remove(packet.pos.toLong())
            is ClientboundAddEntityPacket -> this.entities.add(packet.id)
            is ClientboundRemoveEntitiesPacket -> this.entities.removeAll(packet.entityIds)
            is ClientboundPlayerInfoUpdatePacket -> {
                for (entry in packet.newEntries()) {
                    this.players.add(entry.profileId)
                }
            }
            is ClientboundPlayerInfoRemovePacket -> this.players.removeAll(packet.profileIds)
        }
    }

    private fun modifyPacketForViewer(packet: Packet<*>): Packet<*> {
        if (packet is ClientboundLoginPacket) {
            // Give the viewer a different ID to not conflict
            // with any entities in the replay
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
            val copy = ArrayList(packet.entries())
            if (packet.actions().contains(ClientboundPlayerInfoUpdatePacket.Action.INITIALIZE_CHAT)) {
                val iter = copy.listIterator()
                while (iter.hasNext()) {
                    val entry = iter.next()
                    iter.set(ClientboundPlayerInfoUpdatePacket.Entry(
                        entry.profileId,
                        entry.profile,
                        entry.listed,
                        entry.latency,
                        entry.gameMode,
                        entry.displayName,
                        null
                    ))
                }
            }

            val index = packet.entries().indexOfFirst { it.profileId == this.player.uuid }
            if (index >= 0) {
                val previous = copy[index]
                copy[index] = ClientboundPlayerInfoUpdatePacket.Entry(
                    VIEWER_UUID,
                    previous.profile,
                    previous.listed,
                    previous.latency,
                    previous.gameMode,
                    previous.displayName,
                    null
                )
            }
            return ReplayViewerUtils.createClientboundPlayerInfoUpdatePacket(packet.actions(), copy)
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
        if (packet is ClientboundTickingStatePacket) {
            this.tickSpeed = packet.tickRate
            this.tickFrozen = packet.isFrozen
            return this.getTickingStatePacket()
        }
        return packet
    }

    internal fun send(packet: Packet<*>) {
        this.connection.sendReplayPacket(packet)
    }

    private companion object {
        const val VIEWER_ID = Int.MAX_VALUE - 10
        val VIEWER_UUID: UUID = UUIDUtil.createOfflinePlayerUUID("-ViewingProfile-")
    }
}