package me.senseiwells.replay.viewer

import it.unimi.dsi.fastutil.ints.IntArrayList
import it.unimi.dsi.fastutil.ints.IntOpenHashSet
import it.unimi.dsi.fastutil.ints.IntSets
import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import it.unimi.dsi.fastutil.longs.LongSets
import kotlinx.coroutines.*
import me.senseiwells.replay.ServerReplay
import me.senseiwells.replay.ducks.PackTracker
import me.senseiwells.replay.mixin.viewer.EntityInvoker
import me.senseiwells.replay.reader.ReplayReader
import me.senseiwells.replay.reader.flashback.FlashbackReader
import me.senseiwells.replay.reader.replay_mod.ReplayModReader
import me.senseiwells.replay.recorder.rejoin.RejoinedReplayPlayer
import me.senseiwells.replay.util.DateTimeUtils.formatHHMMSS
import me.senseiwells.replay.util.ReplayMarker
import me.senseiwells.replay.viewer.ReplayViewerUtils.getViewingReplay
import me.senseiwells.replay.viewer.ReplayViewerUtils.sendReplayPacket
import me.senseiwells.replay.viewer.ReplayViewerUtils.startViewingReplay
import me.senseiwells.replay.viewer.ReplayViewerUtils.stopViewingReplay
import net.minecraft.ChatFormatting
import net.minecraft.core.UUIDUtil
import net.minecraft.network.ConnectionProtocol
import net.minecraft.network.ProtocolInfo
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.common.ClientboundResourcePackPopPacket
import net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket
import net.minecraft.network.protocol.game.*
import net.minecraft.network.protocol.game.ClientboundGameEventPacket.CHANGE_GAME_MODE
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket.Action
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerBossEvent
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.network.ServerGamePacketListenerImpl
import net.minecraft.world.BossEvent.BossBarColor
import net.minecraft.world.BossEvent.BossBarOverlay
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.PositionMoveRotation
import net.minecraft.world.entity.Relative
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.GameType
import net.minecraft.world.phys.Vec3
import net.minecraft.world.scores.DisplaySlot
import net.minecraft.world.scores.Objective
import net.minecraft.world.scores.criteria.ObjectiveCriteria
import java.io.InputStream
import java.nio.file.Path
import java.util.*
import java.util.function.Supplier
import kotlin.io.path.extension
import kotlin.io.path.nameWithoutExtension
import kotlin.math.abs
import kotlin.time.Duration

@OptIn(ExperimentalCoroutinesApi::class)
class ReplayViewer internal constructor(
    private val path: Path,
    val connection: ServerGamePacketListenerImpl
) {
    private val reader = this.createReader()
    private val markers by lazy { this.reader.readMarkers() }

    private var started = false
    private var teleported = false

    @OptIn(DelicateCoroutinesApi::class)
    private val coroutineContext = newSingleThreadContext("replay-viewer")
    private val coroutineScope = CoroutineScope(this.coroutineContext + Job())

    private var tickSpeed = 20.0F
    private var tickFrozen = false

    private val chunks = LongSets.synchronize(LongOpenHashSet())
    private val entities = IntSets.synchronize(IntOpenHashSet())
    private val players = Collections.synchronizedList(ArrayList<UUID>())
    private val objectives = Collections.synchronizedCollection(ArrayList<String>())

    private val bossbar = ServerBossEvent(Component.empty(), BossBarColor.BLUE, BossBarOverlay.PROGRESS)

    private val previousPacks = ArrayList<ClientboundResourcePackPushPacket>()

    private var lastSentProgress = Duration.INFINITE
    private var progress = Duration.ZERO
    private var target = Duration.ZERO

    private var position = Vec3.ZERO

    val gameProtocol: ProtocolInfo<ClientGamePacketListener> = GameProtocols.CLIENTBOUND_TEMPLATE.bind(
        RegistryFriendlyByteBuf.decorator(this.server.registryAccess())
    )

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

        ReplayViewers.remove(this.player.uuid)
    }

    fun close() {
        this.coroutineScope.coroutineContext.cancelChildren()
        this.coroutineScope.launch {
            reader.close()
        }
        this.coroutineContext.close()
        this.connection.stopViewingReplay()
    }

    fun jumpTo(timestamp: Duration): Boolean {
        if (timestamp.isNegative() || timestamp > this.reader.duration) {
            return false
        }

        this.coroutineScope.launch {
            if (reader.jumpTo(timestamp) || progress > timestamp) {
                restart()
            }
            target = timestamp
        }
        return true
    }

    fun jumpToMarker(name: String?, offset: Duration): Boolean {
        val markers = this.markers[name]
        if (markers.isEmpty()) {
            return false
        }
        val marker = markers.firstOrNull { it.timestamp > this.progress } ?: markers.first()
        return this.jumpTo(marker.timestamp + offset)
    }

    fun getMarkers(): List<ReplayMarker> {
        return this.markers.values().sortedBy { it.timestamp }
    }

    fun setSpeed(speed: Float) {
        if (speed <= 0) {
            throw IllegalArgumentException("Cannot set non-positive speed multiplier!")
        }
        this.speedMultiplier = speed
        this.sendTickingState()
    }

    fun setPaused(paused: Boolean): Boolean {
        if (this.paused == paused) {
            return false
        }
        this.paused = paused
        this.sendTickingState()
        this.updateProgress(this.progress)
        return true
    }

    fun showProgress(): Boolean {
        if (!this.bossbar.isVisible) {
            this.bossbar.isVisible = true
            this.send(ClientboundBossEventPacket.createAddPacket(this.bossbar))
            return true
        }
        return false
    }

    fun hideProgress(): Boolean {
        if (this.bossbar.isVisible) {
            this.bossbar.isVisible = false
            this.send(ClientboundBossEventPacket.createRemovePacket(this.bossbar.id))
            return true
        }
        return false
    }

    fun onServerboundPacket(packet: Packet<*>) {
        // To allow other packets, make sure you add them to the allowed packets in ReplayViewerPackets
        when (packet) {
            is ServerboundChatCommandPacket -> ReplayViewerCommands.handleCommand(packet.command, this)
            is ServerboundChatCommandSignedPacket -> ReplayViewerCommands.handleCommand(packet.command, this)
        }
    }

    fun getResourcePack(hash: String): InputStream? {
        return this.reader.readResourcePack(hash)
    }

    fun resetCamera() {
        this.send(ClientboundPlayerPositionPacket(
            0, PositionMoveRotation(this.position, Vec3.ZERO, 0.0F, 0.0F), setOf()
        ))
    }

    fun markForTeleportation() {
        this.teleported = false
    }

    private fun restart() {
        if (!this.started) {
            return
        }
        this.removeReplayState()
        this.coroutineScope.coroutineContext.cancelChildren()
        this.teleported = false
        this.target = Duration.ZERO

        this.sendViewerPlayerInfo()
        if (this.bossbar.isVisible) {
            this.send(ClientboundBossEventPacket.createAddPacket(this.bossbar))
        }

        this.coroutineScope.launch {
            // Un-lazy the markers
            markers

            try {
                streamReplay { this.isActive }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ServerReplay.logger.error("Exception while viewing replay", e)
                server.execute {
                    stop()
                    player.sendSystemMessage(
                        Component.literal("Exception while viewing replay, see logs for more info")
                            .withStyle(ChatFormatting.RED)
                    )
                }
            }
        }
    }

    private suspend fun streamReplay(active: Supplier<Boolean>) {
        var lastTime = Duration.ZERO
        val iterator = this.reader.readPackets().iterator()
        while (active.get() && iterator.hasNext()) {
            val element = iterator.next()

            element.use { (protocol, packet, time) ->
                if (protocol == ConnectionProtocol.PLAY && time > this.target) {
                    delay((time - lastTime) / this.speedMultiplier.toDouble())
                }

                while (this.paused) {
                    delay(50)
                }

                this.playbackPacket(protocol, packet, time, active)

                if (protocol == ConnectionProtocol.PLAY) {
                    this.updateProgress(time)
                    lastTime = time
                }
            }
        }
    }

    private fun playbackPacket(
        protocol: ConnectionProtocol,
        packet: Packet<*>,
        time: Duration,
        active: Supplier<Boolean>
    ) {
        // We don't reconfigure the client, so we just ignore config packets,
        // this should probably be reworked at some point...
        if (protocol == ConnectionProtocol.CONFIGURATION && packet !is ClientboundResourcePackPushPacket) {
            return
        }

        if (this.shouldSendPacket(packet, time)) {
            val modified = modifyPacketForViewer(packet)
            this.onSendPacket(modified)
            if (!active.get()) {
                return
            }
            this.send(modified)
            this.afterSendPacket(modified)
        }
    }

    private fun updateProgress(progress: Duration) {
        if (abs((this.lastSentProgress - progress).inWholeMilliseconds) < 500) {
            return
        }
        this.lastSentProgress = progress

        val title = Component.empty()
            .append(Component.literal(this.path.nameWithoutExtension).withStyle(ChatFormatting.GREEN))
            .append(" ")
            .append(Component.literal(progress.formatHHMMSS()).withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD))
        if (this.paused) {
            title.append(Component.literal(" (PAUSED)").withStyle(ChatFormatting.DARK_AQUA))
        }
        this.bossbar.name = title

        this.progress = progress
        this.bossbar.progress = progress.div(this.reader.duration).toFloat()

        if (this.bossbar.isVisible) {
            this.send(ClientboundBossEventPacket.createUpdateProgressPacket(this.bossbar))
            this.send(ClientboundBossEventPacket.createUpdateNamePacket(this.bossbar))
        }
    }

    private fun sendTickingState() {
        this.send(this.getTickingStatePacket())
    }

    private fun getTickingStatePacket(): ClientboundTickingStatePacket {
        return ClientboundTickingStatePacket(this.tickSpeed * this.speedMultiplier, this.paused || this.tickFrozen)
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

        this.previousPacks.addAll((this.connection as PackTracker).`replay$getPacks`())
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
        synchronized(this.objectives) {
            for (objective in this.objectives) {
                @Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
                val dummy = Objective(
                    null,
                    objective,
                    ObjectiveCriteria.DUMMY,
                    Component.empty(),
                    ObjectiveCriteria.RenderType.INTEGER,
                    false,
                    null
                )
                this.send(ClientboundSetObjectivePacket(dummy, ClientboundSetObjectivePacket.METHOD_REMOVE))
            }
        }

        this.send(ClientboundResourcePackPopPacket(Optional.empty()))

        if (this.bossbar.isVisible) {
            this.send(ClientboundBossEventPacket.createRemovePacket(this.bossbar.id))
        }
    }

    private fun sendViewerPlayerInfo() {
        this.players.add(this.player.uuid)

        val entry = ClientboundPlayerInfoUpdatePacket.Entry(
            this.player.uuid,
            this.player.gameProfile,
            false,
            0,
            GameType.SPECTATOR,
            null,
            true,
            0,
            null
        )
        this.send(ReplayViewerUtils.createClientboundPlayerInfoUpdatePacket(
            EnumSet.of(Action.ADD_PLAYER, Action.UPDATE_GAME_MODE),
            listOf(entry)
        ))
    }

    private fun shouldSendPacket(packet: Packet<*>, time: Duration): Boolean {
        return when (packet) {
            is ClientboundGameEventPacket -> packet.event != CHANGE_GAME_MODE
            is ClientboundPlayerPositionPacket -> {
                if (!packet.relatives.containsAll(setOf(Relative.X, Relative.Y, Relative.Z))) {
                    this.position = packet.change.position
                }
                // We want the client to teleport to the first initial position
                // later positions will teleport the viewer which we don't want
                val teleported = this.teleported
                this.teleported = true
                return !teleported || time < this.target
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
            is ClientboundSetObjectivePacket -> {
                if (packet.method == ClientboundSetObjectivePacket.METHOD_REMOVE) {
                    this.objectives.remove(packet.objectiveName)
                } else {
                    this.objectives.add(packet.objectiveName)
                }
            }
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
                packet.commonPlayerSpawnInfo,
                packet.enforcesSecureChat
            )
        }
        if (packet is ClientboundPlayerInfoUpdatePacket) {
            val copy = ArrayList(packet.entries())
            if (packet.actions().contains(Action.INITIALIZE_CHAT)) {
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
                        entry.showHat,
                        entry.listOrder,
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
                    previous.showHat,
                    previous.listOrder,
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
                packet.xRot,
                packet.yRot,
                packet.type,
                packet.data,
                Vec3(packet.xa, packet.ya, packet.za),
                packet.yHeadRot.toDouble()
            )
        }
        if (packet is ClientboundPlayerChatPacket) {
            // We don't want to deal with chat validation...
            val message = packet.unsignedContent ?: Component.literal(packet.body.content)
            val decorated = packet.chatType.decorate(message)
            return ClientboundSystemChatPacket(decorated, false)
        }
        if (packet is ClientboundTickingStatePacket) {
            this.tickSpeed = packet.tickRate
            this.tickFrozen = packet.isFrozen
            return this.getTickingStatePacket()
        }

        return packet
    }

    private fun synchronizeClientLevel() {
        this.send(ClientboundRespawnPacket(
            this.player.createCommonSpawnInfo(this.player.serverLevel()),
            ClientboundRespawnPacket.KEEP_ALL_DATA
        ))
    }

    internal fun send(packet: Packet<*>) {
        this.connection.sendReplayPacket(packet)
    }

    private fun createReader(): ReplayReader {
        if (this.path.extension == "mcpr") {
            return ReplayModReader(this, this.path)
        } else if (this.path.extension == "zip") {
            return FlashbackReader(this, this.path)
        }
        throw IllegalStateException("Tried to read unknown replay file type: ${this.path.extension}")
    }

    private companion object {
        const val VIEWER_ID = Int.MAX_VALUE - 10
        val VIEWER_UUID: UUID = UUIDUtil.createOfflinePlayerUUID("-ViewingProfile-")
    }
}