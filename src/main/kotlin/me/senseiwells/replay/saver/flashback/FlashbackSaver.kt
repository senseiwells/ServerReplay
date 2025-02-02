package me.senseiwells.replay.saver.flashback

import com.google.common.collect.HashMultimap
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap
import me.senseiwells.replay.ServerReplay
import me.senseiwells.replay.recorder.ReplayRecorder
import me.senseiwells.replay.saver.ReplaySaver
import me.senseiwells.replay.util.DateTimeUtils
import me.senseiwells.replay.util.FileUtils
import net.minecraft.network.ConnectionProtocol
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.ProtocolInfo
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.common.ClientboundDisconnectPacket
import net.minecraft.network.protocol.configuration.ClientboundFinishConfigurationPacket
import net.minecraft.network.protocol.game.*
import net.minecraft.resources.ResourceKey
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec2
import net.minecraft.world.phys.Vec3
import org.apache.commons.io.file.PathUtils
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors

class FlashbackSaver(
    override val recorder: ReplayRecorder,
    override val path: Path
): ReplaySaver {
    private val executor = Executors.newSingleThreadExecutor()

    private val writer = FlashbackChunkedWriter(this.path, this.recorder.server.registryAccess())

    private val movement = HashMultimap.create<ResourceKey<Level>, Movement>()
    private val chunks = Object2IntOpenHashMap<ChunkPacketIdentity>()

    private var ticks = 1

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

        this.writeActionAsync(FlashbackAction.NextTick)
        this.ticks++
        val ticks = this.ticks
        val chunkTicks = ticks - this.writer.meta.totalTicks
        if (chunkTicks < CHUNK_LENGTH) {
            return
        }

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
        timestamp: Long,
        offThread: Boolean
    ): CompletableFuture<Int?> {
        val action = when (protocol.id()) {
            ConnectionProtocol.PLAY -> FlashbackAction.GamePacket
            ConnectionProtocol.CONFIGURATION -> FlashbackAction.ConfigurationPacket
            else -> return CompletableFuture.completedFuture(null)
        }

        if (packet is ClientboundLevelChunkWithLightPacket) {
            return this.writeCachedChunk(packet, protocol)
        } else if (packet is ClientboundMoveEntityPacket) {
            return this.writeMovement(packet)
        }

        return this.writeActionAsync(action) { buf ->
            val start = buf.writerIndex()
            ReplaySaver.encodePacket(packet, protocol, buf)
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
    }

    override fun getRawRecordingSize(): Long {
        return PathUtils.sizeOf(this.path)
    }

    @Deprecated("Getting the compressed recording size is computationally expensive")
    override fun getCompressedRecordingSize(force: Boolean): CompletableFuture<Long> {
        return CompletableFuture.completedFuture(0)
    }

    override fun close(duration: Int, save: Boolean): CompletableFuture<Long> {
        val future = CompletableFuture.supplyAsync({
            this.writer.endChunk(this.ticks)
            0L
        }, this.executor)
        this.executor.shutdown()
        return future
    }

    private fun writeCachedChunk(
        packet: ClientboundLevelChunkWithLightPacket,
        protocol: ProtocolInfo<*>
    ): CompletableFuture<Int?> {
        return this.writeActionAsync(FlashbackAction.CacheChunk) { buf ->
            val identity = ChunkPacketIdentity.of(packet)
            var index = this.chunks.getInt(identity)
            var size = -buf.writerIndex()
            if (index == -1) {
                index = this.chunks.size
                val fileIndex = index / LEVEL_CHUNK_CACHE_SIZE
                this.writer.writeLevelChunk(fileIndex) { chunkBuf ->
                    val start = chunkBuf.writerIndex()
                    ReplaySaver.encodePacket(packet, protocol, chunkBuf)
                    size += (chunkBuf.writerIndex() - start)
                }
                this.chunks.put(identity, index)
            }
            buf.writeVarInt(index)
            size + buf.writerIndex()
        }
    }

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
        // TODO: Lerp?
        val id = entity.id
        val position = entity.position()
        val rotation = entity.rotationVector
        val headRot = entity.yHeadRot
        val onGround = entity.onGround()
        this.executor.execute {
            this.movement.put(level.dimension(), Movement(id, position, rotation, headRot, onGround))
        }
        return CompletableFuture.completedFuture(Movement.size())
    }

    private class Movement(
        val id: Int,
        val position: Vec3,
        val rotation: Vec2,
        val headRot: Float,
        val onGround: Boolean
    ) {
        fun write(buf: FriendlyByteBuf) {
            buf.writeVarInt(this.id)
            buf.writeDouble(this.position.x)
            buf.writeDouble(this.position.y)
            buf.writeDouble(this.position.z)
            buf.writeFloat(this.rotation.y)
            buf.writeFloat(this.rotation.x)
            buf.writeFloat(this.headRot)
            buf.writeBoolean(this.onGround)
        }

        companion object {
            fun size(): Int {
                return 4 + 3 * 8 + 2 * 4 + 4 + 1
            }
        }
    }

    companion object {
        const val CHUNK_LENGTH = 5 * 60 * 20
        const val LEVEL_CHUNK_CACHE_SIZE = 10000

        private val IGNORED_PACKETS = setOf(
            ClientboundChunkBatchStartPacket::class.java,
            ClientboundChunkBatchFinishedPacket::class.java,
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
            ClientboundDeleteChatPacket::class.java,
            ClientboundPlayerChatPacket::class.java,
        )

        fun dated(recordings: Path): (ReplayRecorder) -> FlashbackSaver {
            val date = DateTimeUtils.getFormattedDate()
            return { FlashbackSaver(it, FileUtils.findNextAvailable(recordings.resolve(date))) }
        }
    }
}