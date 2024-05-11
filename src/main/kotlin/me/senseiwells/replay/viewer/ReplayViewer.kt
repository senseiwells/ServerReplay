package me.senseiwells.replay.viewer

import com.replaymod.replaystudio.PacketData
import com.replaymod.replaystudio.io.ReplayInputStream
import com.replaymod.replaystudio.lib.viaversion.api.protocol.packet.State
import com.replaymod.replaystudio.lib.viaversion.api.protocol.version.ProtocolVersion
import com.replaymod.replaystudio.protocol.PacketTypeRegistry
import com.replaymod.replaystudio.replay.ZipReplayFile
import com.replaymod.replaystudio.studio.ReplayStudio
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import it.unimi.dsi.fastutil.ints.IntArrayList
import it.unimi.dsi.fastutil.ints.IntOpenHashSet
import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import kotlinx.coroutines.*
import kotlinx.coroutines.future.await
import me.senseiwells.replay.ServerReplay
import me.senseiwells.replay.ducks.`ServerReplay$PackTracker`
import me.senseiwells.replay.mixin.viewer.EntityInvoker
import me.senseiwells.replay.rejoin.RejoinedReplayPlayer
import me.senseiwells.replay.viewer.ReplayViewerUtils.getClientboundConfigurationPacketType
import me.senseiwells.replay.viewer.ReplayViewerUtils.getClientboundPlayPacketType
import me.senseiwells.replay.viewer.ReplayViewerUtils.getViewingReplay
import me.senseiwells.replay.viewer.ReplayViewerUtils.sendReplayPacket
import me.senseiwells.replay.viewer.ReplayViewerUtils.startViewingReplay
import me.senseiwells.replay.viewer.ReplayViewerUtils.stopViewingReplay
import me.senseiwells.replay.viewer.ReplayViewerUtils.toClientboundConfigurationPacket
import me.senseiwells.replay.viewer.ReplayViewerUtils.toClientboundPlayPacket
import me.senseiwells.replay.viewer.packhost.PackHost
import me.senseiwells.replay.viewer.packhost.ReplayPack
import net.minecraft.SharedConstants
import net.minecraft.core.UUIDUtil
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.common.ClientboundResourcePackPopPacket
import net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket
import net.minecraft.network.protocol.game.*
import net.minecraft.network.protocol.game.ClientboundGameEventPacket.CHANGE_GAME_MODE
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.network.ServerGamePacketListenerImpl
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.GameType
import net.minecraft.world.phys.Vec3
import net.minecraft.world.scores.DisplaySlot
import java.io.IOException
import java.nio.file.Path
import java.util.*
import java.util.function.Supplier
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively
import kotlin.io.path.name

class ReplayViewer(
    private val location: Path,
    val connection: ServerGamePacketListenerImpl
) {
    private val replay = ZipReplayFile(ReplayStudio(), this.location.toFile())

    private var started = false
    private var teleported = false

    private val coroutineScope = CoroutineScope(Dispatchers.Default + Job())
    private val packHost = PackHost(1)
    private val packs = Int2ObjectOpenHashMap<String>()

    private var tickSpeed = 20.0F
    private var tickFrozen = false
    private val chunks = Collections.synchronizedCollection(LongOpenHashSet())
    private val entities = Collections.synchronizedCollection(IntOpenHashSet())
    private val players = Collections.synchronizedList(ArrayList<UUID>())

    private val previousPacks = ArrayList<ClientboundResourcePackPushPacket>()

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
        if (this.connection.getViewingReplay() != null) {
            ServerReplay.logger.error("Player ${this.player.scoreboardName} tried watching 2 replays at once?!")
            return
        }

        this.started = true
        this.setForReplayView()

        this.restart()
    }

    fun stop() {
        this.close()

        this.removeReplayState()
        this.addBackToServer()
    }

    fun restart() {
        if (!this.started) {
            return
        }
        this.removeReplayState()
        this.coroutineScope.coroutineContext.cancelChildren()
        this.teleported = false
        this.coroutineScope.launch {
            hostResourcePacks()
            streamReplay { this.isActive }
        }
    }

    fun close() {
        this.packHost.shutdown()
        this.coroutineScope.coroutineContext.cancelChildren()
        this.connection.stopViewingReplay()

        try {
            val caches = this.location.parent.resolve(this.location.name + ".cache")
            @OptIn(ExperimentalPathApi::class)
            caches.deleteRecursively()
        } catch (e: IOException) {
            ServerReplay.logger.error("Failed to delete caches", e)
        }
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
        // To allow other packets, make sure you add them to the allowed packets in ReplayViewerPackets
        when (packet) {
            is ServerboundChatCommandPacket -> ReplayViewerCommands.handleCommand(packet.command, this)
        }
    }

    private suspend fun hostResourcePacks() {
        if (this.packHost.running) {
            return
        }

        val indices = this.replay.resourcePackIndex
        if (indices == null || indices.isEmpty()) {
            return
        }

        for (hash in indices.values) {
            this.packHost.addPack(ReplayPack(hash, this.replay))
        }

        this.packHost.start(ServerReplay.config.replayViewerPackIp, ServerReplay.config.replayViewerPackPort).await()

        for ((id, hash) in indices) {
            val hosted = this.packHost.getHostedPack(hash) ?: continue
            this.packs[id] = hosted.url
        }
    }

    private suspend fun streamReplay(
        active: Supplier<Boolean>
    ) {
        val version = ProtocolVersion.getProtocol(SharedConstants.getProtocolVersion())

        this.replay.getPacketData(PacketTypeRegistry.get(version, State.CONFIGURATION)).use { stream ->
            this.sendConfigurationPackets(stream, active)
        }
        this.replay.getPacketData(PacketTypeRegistry.get(version, State.PLAY)).use { stream ->
            this.sendPlayPackets(stream, active)
        }
    }

    private fun sendConfigurationPackets(stream: ReplayInputStream, active: Supplier<Boolean>) {
        var data: PacketData? = stream.readPacket()
        while (data != null && active.get()) {
            val type = data.packet.getClientboundConfigurationPacketType()
            if (type == ClientboundResourcePackPushPacket::class.java) {
                val packet = data.packet.toClientboundConfigurationPacket()
                if (this.shouldSendPacket(packet)) {
                    val modified = modifyPacketForViewer(packet)
                    this.onSendPacket(modified)
                    this.send(modified)
                    this.afterSendPacket(modified)
                }
            }

            data.release()
            data = stream.readPacket()
        }
        data?.release()
    }

    private suspend fun sendPlayPackets(stream: ReplayInputStream, active: Supplier<Boolean>) {
        var lastTime = -1L
        var data: PacketData? = stream.readPacket()
        while (data != null && active.get()) {
            // We don't care about all the packets before the player logs in.
            if (lastTime < 0) {
                val type = data.packet.getClientboundPlayPacketType()
                if (type != ClientboundLoginPacket::class.java) {
                    data.release()
                    data = stream.readPacket()
                    continue
                }
                lastTime = data.time
            }

            delay(((data.time - lastTime) / this.speedMultiplier).toLong())

            while (this.paused) {
                delay(50)
            }

            val packet = data.packet.toClientboundPlayPacket()
            if (this.shouldSendPacket(packet)) {
                val modified = modifyPacketForViewer(packet)
                this.onSendPacket(modified)
                if (!active.get()) {
                    break
                }
                this.send(modified)
                this.afterSendPacket(modified)
            }

            lastTime = data.time
            data.release()
            data = stream.readPacket()
        }
        // Release any remaining data
        data?.release()
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
        this.connection.startViewingReplay(this)

        this.removeServerState()
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

        RejoinedReplayPlayer.place(player, this.connection, afterLogin = {
            this.synchronizeClientLevel()
        })

        (player as EntityInvoker).removeRemovalReason()
        level.addNewPlayer(player)

        for (pack in this.previousPacks) {
            this.connection.send(pack)
        }
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

        this.previousPacks.addAll((this.connection as `ServerReplay$PackTracker`).`replay$getPacks`())
        this.send(ClientboundResourcePackPopPacket(Optional.empty()))
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
        this.send(ClientboundResourcePackPopPacket(Optional.empty()))
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
            is ClientboundRespawnPacket -> this.teleported = false
        }
    }

    private fun afterSendPacket(packet: Packet<*>) {
        when (packet) {
            is ClientboundLoginPacket -> {
                this.synchronizeClientLevel()
                this.send(ClientboundGameEventPacket(CHANGE_GAME_MODE, GameType.SPECTATOR.id.toFloat()))
            }
            is ClientboundRespawnPacket -> {
                this.send(ClientboundGameEventPacket(CHANGE_GAME_MODE, GameType.SPECTATOR.id.toFloat()))
            }
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

        if (packet is ClientboundResourcePackPushPacket && packet.url.startsWith("replay://")) {
            val request = packet.url.removePrefix("replay://").toIntOrNull()
                ?: throw IllegalStateException("Malformed replay packet url")
            val url = this.packs[request]
            if (url == null) {
                ServerReplay.logger.warn("Tried viewing unknown request $request for player ${this.player.scoreboardName}")
                return packet
            }

            return ClientboundResourcePackPushPacket(packet.id, url, "", packet.required, packet.prompt)
        }

        return packet
    }

    private fun synchronizeClientLevel() {
        this.send(ClientboundRespawnPacket(
            player.createCommonSpawnInfo(player.serverLevel()),
            ClientboundRespawnPacket.KEEP_ALL_DATA
        ))
    }

    internal fun send(packet: Packet<*>) {
        this.connection.sendReplayPacket(packet)
    }

    private companion object {
        const val VIEWER_ID = Int.MAX_VALUE - 10
        val VIEWER_UUID: UUID = UUIDUtil.createOfflinePlayerUUID("-ViewingProfile-")
    }
}