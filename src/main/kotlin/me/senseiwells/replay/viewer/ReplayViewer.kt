package me.senseiwells.replay.viewer

import com.replaymod.replaystudio.PacketData
import com.replaymod.replaystudio.io.ReplayInputStream
import com.replaymod.replaystudio.lib.viaversion.api.protocol.packet.State
import com.replaymod.replaystudio.lib.viaversion.api.protocol.version.ProtocolVersion
import com.replaymod.replaystudio.protocol.PacketTypeRegistry
import com.replaymod.replaystudio.replay.ZipReplayFile
import com.replaymod.replaystudio.studio.ReplayStudio
import io.netty.buffer.Unpooled
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
import me.senseiwells.replay.util.MathUtils
import me.senseiwells.replay.viewer.ReplayViewerUtils.getViewingReplay
import me.senseiwells.replay.viewer.ReplayViewerUtils.sendReplayPacket
import me.senseiwells.replay.viewer.ReplayViewerUtils.startViewingReplay
import me.senseiwells.replay.viewer.ReplayViewerUtils.stopViewingReplay
import me.senseiwells.replay.viewer.ReplayViewerUtils.toClientboundPlayPacket
import me.senseiwells.replay.viewer.packhost.PackHost
import me.senseiwells.replay.viewer.packhost.ReplayPack
import net.minecraft.SharedConstants
import net.minecraft.core.UUIDUtil
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.*
import net.minecraft.network.protocol.game.ClientboundGameEventPacket.CHANGE_GAME_MODE
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.network.ServerGamePacketListenerImpl
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.GameType
import net.minecraft.world.level.biome.BiomeManager
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

    private val chunks = Collections.synchronizedCollection(LongOpenHashSet())
    private val entities = Collections.synchronizedCollection(IntOpenHashSet())
    private val players = Collections.synchronizedList(ArrayList<UUID>())

    private var previousPack: ClientboundResourcePackPacket? = null

    val server: MinecraftServer
        get() = this.player.server

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
            this.replay.close()
        } catch (e: IOException) {
            ServerReplay.logger.error("Failed to close replay file being viewed at ${this.location}")
        }
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

    private suspend fun streamReplay(active: Supplier<Boolean>) {
        val version = ProtocolVersion.getProtocol(SharedConstants.getProtocolVersion())

        this.replay.getPacketData(PacketTypeRegistry.get(version, State.CONFIGURATION)).use { stream ->
            this.sendPackets(stream, active)
        }
    }

    private suspend fun sendPackets(stream: ReplayInputStream, active: Supplier<Boolean>) {
        var lastTime = -1L
        var data: PacketData? = stream.readPacket()
        while (data != null && active.get()) {
            if (lastTime != -1L) {
                delay(((data.time - lastTime) / this.speedMultiplier).toLong())
            }

            while (this.paused) {
                delay(50)
            }

            when (data.packet.registry.state) {
                State.PLAY -> {
                    this.sendPlayPacket(data, active)
                    lastTime = data.time
                }
                else -> { }
            }

            data.release()
            data = stream.readPacket()
        }
        // Release any remaining data
        data?.release()
    }

    private fun sendPlayPacket(data: PacketData, active: Supplier<Boolean>) {
        val packet = data.packet.toClientboundPlayPacket()

        if (this.shouldSendPacket(packet)) {
            val modified = modifyPacketForViewer(packet)
            this.onSendPacket(modified)
            if (!active.get()) {
                return
            }
            this.send(modified)
            this.afterSendPacket(modified)
        }
    }

    private fun sendTickingState() {
        this.sendAbilities()
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

        val previous = this.previousPack
        if (previous != null) {
            this.connection.send(previous)
        }

        player.inventoryMenu.sendAllDataToRemote()
        this.connection.send(ClientboundSetHealthPacket(
            player.health,
            player.foodData.foodLevel,
            player.foodData.saturationLevel
        ))
        this.connection.send(ClientboundSetExperiencePacket(
            player.experienceProgress,
            player.totalExperience,
            player.experienceLevel
        ))
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
        MathUtils.forEachChunkAround(player.chunkPosition(), server.playerList.viewDistance) {
            this.send(ClientboundForgetLevelChunkPacket(it.x, it.z))
        }
        for (i in 0..18) {
            this.send(ClientboundSetDisplayObjectivePacket(i, null))
        }
        for (objective in server.scoreboard.objectives) {
            this.send(ClientboundSetObjectivePacket(objective, ClientboundSetObjectivePacket.METHOD_REMOVE))
        }
        for (bossbar in server.customBossEvents.events) {
            if (bossbar.players.contains(player)) {
                this.send(ClientboundBossEventPacket.createRemovePacket(bossbar.id))
            }
        }

        val previous = (this.connection as `ServerReplay$PackTracker`).`replay$getPack`()
        if (previous != null && previous !== EMPTY_PACK) {
            this.previousPack = previous
            this.send(EMPTY_PACK)
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
                this.connection.send(ClientboundForgetLevelChunkPacket(ChunkPos.getX(chunk), ChunkPos.getZ(chunk)))
            }
        }
        this.send(EMPTY_PACK)
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
            is ClientboundForgetLevelChunkPacket -> this.chunks.remove(ChunkPos.asLong(packet.x, packet.z))
            is ClientboundAddPlayerPacket -> this.entities.add(packet.entityId)
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
                packet.gameType,
                packet.previousGameType,
                packet.levels,
                packet.registryHolder,
                packet.dimensionType,
                packet.dimension,
                packet.seed,
                packet.maxPlayers,
                packet.chunkRadius,
                packet.simulationDistance,
                packet.reducedDebugInfo,
                packet.showDeathScreen,
                packet.isDebug,
                packet.isFlat,
                packet.lastDeathLocation,
                packet.portalCooldown
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
        if (packet is ClientboundAddPlayerPacket && packet.playerId == this.player.uuid) {
            val buf = FriendlyByteBuf(Unpooled.buffer())
            buf.writeVarInt(packet.entityId)
            buf.writeUUID(VIEWER_UUID)
            buf.writeDouble(packet.x)
            buf.writeDouble(packet.y)
            buf.writeDouble(packet.z)
            buf.writeByte(packet.getyRot().toInt())
            buf.writeByte(packet.getxRot().toInt())
            val playerPacket = ClientboundAddPlayerPacket(buf)
            buf.release()
            return playerPacket
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

        if (packet is ClientboundResourcePackPacket && packet.url.startsWith("replay://")) {
            val request = packet.url.removePrefix("replay://").toIntOrNull()
                ?: throw IllegalStateException("Malformed replay packet url")
            val url = this.packs[request]
            if (url == null) {
                ServerReplay.logger.warn("Tried viewing unknown request $request for player ${this.player.scoreboardName}")
                return packet
            }

            return ClientboundResourcePackPacket(url, "", packet.isRequired, packet.prompt)
        }

        return packet
    }

    private fun synchronizeClientLevel() {
        val level = this.player.serverLevel()
        this.send(ClientboundRespawnPacket(
            level.dimensionTypeId(),
            level.dimension(),
            BiomeManager.obfuscateSeed(level.seed),
            this.player.gameMode.gameModeForPlayer,
            this.player.gameMode.previousGameModeForPlayer,
            level.isDebug,
            level.isFlat,
            ClientboundRespawnPacket.KEEP_ALL_DATA,
            this.player.lastDeathLocation,
            this.player.portalCooldown
        ))
    }

    internal fun send(packet: Packet<*>) {
        this.connection.sendReplayPacket(packet)
    }

    private companion object {
        const val VIEWER_ID = Int.MAX_VALUE - 10
        val VIEWER_UUID: UUID = UUIDUtil.createOfflinePlayerUUID("-ViewingProfile-")

        val EMPTY_PACK = ClientboundResourcePackPacket(
            "https://static.planetminecraft.com/files/resource_media/texture/nothing.zip",
            "",
            false,
            null
        )
    }
}