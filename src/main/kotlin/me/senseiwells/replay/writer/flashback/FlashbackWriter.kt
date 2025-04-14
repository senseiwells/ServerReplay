package me.senseiwells.replay.writer.flashback

import com.google.common.collect.HashMultimap
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import me.senseiwells.replay.ServerReplay
import me.senseiwells.replay.recorder.ReplayRecorder
import me.senseiwells.replay.util.DateTimeUtils
import me.senseiwells.replay.util.FileUtils
import me.senseiwells.replay.util.ReplayMarker
import me.senseiwells.replay.util.ReplayMetaUtils
import me.senseiwells.replay.util.flashback.FlashbackAction
import me.senseiwells.replay.util.flashback.FlashbackIO
import me.senseiwells.replay.util.flashback.FlashbackMarker.Location
import me.senseiwells.replay.writer.ReplayWriter
import me.senseiwells.replay.writer.ReplayWriter.Companion.broadcastToOps
import me.senseiwells.replay.writer.ReplayWriter.Companion.broadcastToOpsAndConsole
import me.senseiwells.replay.writer.ReplayWriter.Companion.name
import net.minecraft.network.ConnectionProtocol
import net.minecraft.network.ProtocolInfo
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.common.ClientboundDisconnectPacket
import net.minecraft.network.protocol.configuration.ClientboundFinishConfigurationPacket
import net.minecraft.network.protocol.game.*
import net.minecraft.resources.ResourceKey
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.Level
import org.apache.commons.io.file.PathUtils
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import kotlin.io.path.fileSize
import kotlin.io.path.name
import kotlin.io.path.writer
import kotlin.time.Duration

class FlashbackWriter(
    override val recorder: ReplayRecorder,
    override val path: Path
): ReplayWriter {
    private val executor = Executors.newSingleThreadExecutor()

    private val writer = FlashbackChunkedWriter(this.path, this.recorder.server.registryAccess())

    private val movement = HashMultimap.create<ResourceKey<Level>, EntityMovement>()
    private val chunks = Object2IntOpenHashMap<ChunkPacketIdentity>()
    private val recent = Object2ObjectOpenHashMap<ResourceKey<Level>, Long2IntOpenHashMap>()

    private var dimension: ResourceKey<Level>? = null

    private var ticks = 1
    private var last = 0

    override var markers: Int = 0

    override val cacheChunksOnUnload: Boolean
        get() = true

    override val closed: Boolean
        get() = this.executor.isShutdown

    init {
        this.chunks.defaultReturnValue(-1)

        // Initial snapshot is pointless
        this.executor.execute {
            this.writer.startSnapshot()
            this.writer.endSnapshot()
            this.writer.writeAction(FlashbackAction.NextTick)
        }
    }

    override fun tick() {
        this.writeEntityMovement()
        if (this.recorder.paused) {
            return
        }

        val previous = this.dimension
        this.dimension = this.recorder.level.dimension()

        this.writeActionAsync(FlashbackAction.NextTick)
        this.ticks++
        val ticks = this.ticks
        val chunkTicks = ticks - this.last
        if (chunkTicks < FlashbackIO.CHUNK_LENGTH && (previous == null || previous == this.dimension)) {
            return
        }
        this.last = ticks

        this.executor.execute {
            this.writer.endChunk(ticks)
            this.writer.startSnapshot()
        }
        this.recorder.takeSnapshot()
        this.executor.execute {
            this.writer.endSnapshot()
        }
    }

    override fun prePacketRecord(packet: Packet<*>): Boolean {
        return IGNORED_PACKETS.contains(packet::class.java)
    }

    override fun writePacket(
        packet: Packet<*>,
        protocol: ProtocolInfo<*>,
        timestamp: Duration,
        offThread: Boolean
    ): CompletableFuture<Int?> {
        val action = when (protocol.id()) {
            ConnectionProtocol.PLAY -> FlashbackAction.GamePacket
            ConnectionProtocol.CONFIGURATION -> FlashbackAction.ConfigurationPacket
            else -> return CompletableFuture.completedFuture(null)
        }

        val replacement = when (packet) {
            is ClientboundLevelChunkWithLightPacket -> return this.writeCachedChunk(packet, protocol)
            is ClientboundMoveEntityPacket -> return this.writeMovement(packet)
            is ClientboundPlayerChatPacket -> {
                val content = packet.unsignedContent ?: Component.literal(packet.body.content)
                ClientboundSystemChatPacket(packet.chatType.decorate(content), false)
            }
            else -> packet
        }

        return this.writeActionAsync(action) { buf ->
            val start = buf.writerIndex()
            ReplayWriter.encodePacket(replacement, protocol, buf)
            buf.writerIndex() - start
        }
    }

    override fun postPacketRecord(packet: Packet<*>) {

    }

    override fun writePlayer(player: ServerPlayer, packets: Collection<Packet<*>>) {
        val uuid = player.uuid
        val position = player.position()
        val rotation = player.rotationVector
        val headRot = player.yHeadRot
        val velocity = player.deltaMovement
        val profile = player.gameProfile
        val gamemode = player.gameMode.gameModeForPlayer.id
        this.writeActionAsync(FlashbackAction.CreatePlayer) { buf ->
            buf.writeUUID(uuid)
            buf.writeDouble(position.x)
            buf.writeDouble(position.y)
            buf.writeDouble(position.z)
            buf.writeFloat(rotation.x)
            buf.writeFloat(rotation.y)
            buf.writeFloat(headRot)
            buf.writeVec3(velocity)
            ByteBufCodecs.GAME_PROFILE.encode(buf, profile)
            buf.writeVarInt(gamemode)
        }
        val filtered = packets.filter { it !is ClientboundAddEntityPacket }
        for (packet in filtered) {
            this.recorder.record(packet)
        }
    }

    override fun writeCachedChunk(pos: ChunkPos): Boolean {
        val dimension = this.recorder.level.dimension()
        val chunks = this.recent[dimension] ?: return false
        val posAsLong = pos.toLong()
        if (!chunks.containsKey(posAsLong)) {
            return false
        }
        val index = chunks.get(posAsLong)
        this.writeActionAsync(FlashbackAction.CacheChunk) { buf ->
            buf.writeVarInt(index)
        }
        return true
    }

    override fun writeMarker(marker: ReplayMarker) {
        this.markers++
        this.executor.execute {
            val location = Location.from(marker.position, this.recorder.level.dimension())
            this.writer.addMarker(this.ticks, marker.name, marker.color, location)
        }
    }

    override fun getRawRecordingSize(): Long {
        return PathUtils.sizeOf(this.path)
    }

    override fun close(duration: Duration, save: Boolean): CompletableFuture<Long> {
        val future = CompletableFuture.supplyAsync({
            var size = 0L
            try {
                val additional = Component.empty()
                if (save) {
                    this.writer.endChunk(this.ticks)
                    this.writeCustomMeta()
                    val path = this.recording()
                    this.broadcastToOpsAndConsole("Staring to save replay ${this.name}, please do not stop the server!")
                    FileUtils.zip(this.path, path)
                    size = path.fileSize()

                    additional.append(" and saved to ")
                        .append(path.toString())
                        .append(", compressed to ${FileUtils.formatSize(size)}")
                }
                try {
                    this.writer.close()
                    this.broadcastToOpsAndConsole(
                        Component.literal("Successfully closed replay ${this.name}").append(additional)
                    )
                } catch (exception: Exception) {
                    val message = "Failed to close replay writer"
                    this.broadcastToOps(Component.literal(message).append(additional))
                    ServerReplay.logger.error(message, exception)
                }
            } catch (exception: Exception) {
                val message = "Failed to write replay ${this.name}"
                val hover = HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(exception.stackTraceToString()))
                this.broadcastToOps(Component.literal(message).withStyle {
                    it.withHoverEvent(hover)
                })
                ServerReplay.logger.error(message, exception)
                throw exception
            }
            size
        }, this.executor)
        this.executor.shutdown()
        return future
    }

    private fun writeCachedChunk(
        packet: ClientboundLevelChunkWithLightPacket,
        protocol: ProtocolInfo<*>
    ): CompletableFuture<Int?> {
        val dimension = this.recorder.level.dimension()
        return this.writeActionAsync(FlashbackAction.CacheChunk) { buf ->
            val identity = ChunkPacketIdentity.of(packet)
            var index = this.chunks.getInt(identity)
            var size = -buf.writerIndex()
            if (index == -1) {
                index = this.chunks.size
                val fileIndex = FlashbackIO.getChunkCacheFileIndex(index)
                this.writer.writeLevelChunk(fileIndex) { chunkBuf ->
                    val start = chunkBuf.writerIndex()
                    ReplayWriter.encodePacket(packet, protocol, chunkBuf)
                    size += (chunkBuf.writerIndex() - start)
                }
                this.chunks.put(identity, index)
            }
            val map = this.recent.getOrPut(dimension, ::Long2IntOpenHashMap)
            map.put(ChunkPos.asLong(packet.x, packet.z), index)
            buf.writeVarInt(index)
            size + buf.writerIndex()
        }
    }

    @Suppress("SameParameterValue")
    private fun writeActionAsync(action: FlashbackAction) {
        this.executor.execute {
            this.writer.writeAction(action)
        }
    }

    private fun <T> writeActionAsync(
        action: FlashbackAction,
        block: (RegistryFriendlyByteBuf) -> T
    ): CompletableFuture<T> {
        return CompletableFuture.supplyAsync({
            this.writer.writeAction(action, block)
        }, this.executor).exceptionally { e ->
            ServerReplay.logger.error("Something went wrong writing action $action", e)
            null
        }
    }

    private fun writeCustomMeta() {
        try {
            val meta = HashMap<String, Any>()
            this.recorder.addMetadata(meta)
            val path = this.path.resolve(ReplayWriter.ENTRY_SERVER_REPLAY_META)
            ReplayMetaUtils.serialize(meta, path.writer())
        } catch (exception: Exception) {
            ServerReplay.logger.error("Failed to write ServerReplay meta!", exception)
        }
    }

    private fun writeEntityMovement() {
        this.executor.execute {
            if (this.movement.keySet().isNotEmpty()) {
                this.writer.writeAction(FlashbackAction.MoveEntities) { buf ->
                    buf.writeVarInt(this.movement.keySet().size)
                    for ((dimension, deltas) in this.movement.asMap()) {
                        buf.writeResourceKey(dimension)
                        buf.writeVarInt(deltas.size)
                        for (movement in deltas) {
                            movement.write(buf)
                        }
                    }
                    this.movement.clear()
                }
            }
        }
    }

    private fun writeMovement(packet: ClientboundMoveEntityPacket): CompletableFuture<Int?> {
        val level = this.recorder.level
        val entity = packet.getEntity(level) ?: return CompletableFuture.completedFuture(null)
        val id = entity.id
        val position = entity.position()
        val rotation = entity.rotationVector
        val headRot = entity.yHeadRot
        val onGround = entity.onGround()
        this.executor.execute {
            this.movement.put(level.dimension(), EntityMovement(id, position, rotation, headRot, onGround))
        }
        return CompletableFuture.completedFuture(EntityMovement.size())
    }

    private fun recording(): Path {
        return this.path.parent.resolve(this.path.name + ".zip")
    }

    companion object {

        private val IGNORED_PACKETS = setOf(
            ClientboundStartConfigurationPacket::class.java,
            ClientboundFinishConfigurationPacket::class.java,
            ClientboundSetChunkCacheCenterPacket::class.java,
            ClientboundSetSimulationDistancePacket::class.java,
            ClientboundSetChunkCacheRadiusPacket::class.java,
            ClientboundDisconnectPacket::class.java,
            ClientboundCooldownPacket::class.java,
            ClientboundTickingStepPacket::class.java,
            ClientboundTickingStatePacket::class.java,
            ClientboundPlayerPositionPacket::class.java,
            ClientboundMoveMinecartPacket::class.java,

            ClientboundForgetLevelChunkPacket::class.java,
            ClientboundDeleteChatPacket::class.java
        )

        fun dated(recordings: Path): (ReplayRecorder) -> FlashbackWriter {
            val date = DateTimeUtils.getFormattedDate()
            return { FlashbackWriter(it, FileUtils.findNextAvailable(recordings.resolve(date))) }
        }
    }
}