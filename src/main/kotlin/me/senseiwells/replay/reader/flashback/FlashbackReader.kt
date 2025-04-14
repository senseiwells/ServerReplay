package me.senseiwells.replay.reader.flashback

import com.google.common.cache.CacheBuilder
import com.google.common.collect.HashMultimap
import com.google.common.collect.ImmutableMultimap
import com.google.common.collect.Multimap
import io.netty.buffer.Unpooled
import me.senseiwells.replay.ServerReplay
import me.senseiwells.replay.reader.ReplayPacketData
import me.senseiwells.replay.reader.ReplayReader
import me.senseiwells.replay.util.ReplayMarker
import me.senseiwells.replay.util.flashback.FlashbackAction
import me.senseiwells.replay.util.flashback.FlashbackIO
import me.senseiwells.replay.viewer.ReplayViewer
import me.senseiwells.replay.writer.flashback.EntityMovement
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.Registries
import net.minecraft.network.ConnectionProtocol
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.protocol.configuration.ConfigurationProtocols
import net.minecraft.network.protocol.game.*
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.PositionMoveRotation
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.phys.Vec2
import net.minecraft.world.phys.Vec3
import java.io.InputStream
import java.nio.file.FileSystems
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.inputStream
import kotlin.io.path.notExists
import kotlin.math.max
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class FlashbackReader(
    private val viewer: ReplayViewer,
    private val path: Path
): ReplayReader {
    private val system = FileSystems.newFileSystem(this.path)
    private val chunked = FlashbackChunkedReader(this.system, this.viewer.server.registryAccess())

    private val cache = CacheBuilder.newBuilder()
        .expireAfterAccess(10, TimeUnit.MINUTES)
        .build<Int, ClientboundLevelChunkWithLightPacket>()

    private var initial: Boolean = true
    private var player: Int = -1
    private var chunk: ChunkPos = ChunkPos.ZERO

    private var tick = 0
    private var respawned = false

    private val tickAsDuration: Duration
        get() = (this.tick * 50).milliseconds

    override val duration: Duration = (this.chunked.meta.totalTicks * 50).milliseconds

    override fun jumpTo(timestamp: Duration): Boolean {
        val tick = ((timestamp.inWholeMilliseconds / 50) - 1).toInt()
        val updated = this.chunked.jumpTo(max(0, tick), this.tick)
        if (updated != null) {
            this.tick = updated
            this.initial = true
            return true
        }
        return false
    }

    override fun readPackets(): Sequence<ReplayPacketData> {
        return sequence {
            do {
                if (initial || chunked.shouldPlaySnapshot()) {
                    initial = false
                    val packets = ArrayList<ReplayPacketData>()
                    chunked.consumeSnapshot { action, buffer ->
                        processAction(action, buffer, packets::add)
                    }
                    yieldAll(packets)
                }

                while (true) {
                    val packets = ArrayList<ReplayPacketData>()
                    val success = chunked.consumeNextAction { action, buffer ->
                        processAction(action, buffer, packets::add)
                    }
                    yieldAll(packets)
                    if (!success) {
                        break
                    }
                }
            } while (chunked.moveToNextChunk())
        }
    }

    override fun readResourcePack(hash: String): InputStream? {
        // Flashback doesn't have resource pack support yet
        return null
    }

    override fun readMarkers(): Multimap<String?, ReplayMarker> {
        if (this.chunked.meta.markers.isEmpty()) {
            return ImmutableMultimap.of()
        }

        val map = HashMultimap.create<String?, ReplayMarker>()
        for ((tick, marker) in this.chunked.meta.markers) {
            val time = 50.milliseconds * (tick.toIntOrNull() ?: continue)
            val instance = ReplayMarker(
                marker.description,
                marker.location?.position,
                Vec2.ZERO,
                time,
                marker.color
            )
            map.put(marker.description, instance)
        }
        return map
    }

    override fun close() {
        this.chunked.close()
        this.system.close()
        this.cache.invalidateAll()
    }

    private fun processAction(
        action: FlashbackAction,
        buffer: RegistryFriendlyByteBuf,
        consumer: (ReplayPacketData) -> Unit
    ) {
        when (action) {
            FlashbackAction.NextTick -> this.tick++
            FlashbackAction.ConfigurationPacket -> this.processConfigurationAction(buffer, consumer)
            FlashbackAction.GamePacket -> this.processPlayAction(buffer, consumer)
            FlashbackAction.CacheChunk -> this.processCachedChunk(buffer, consumer)
            FlashbackAction.CreatePlayer -> this.processCreatePlayer(buffer, consumer)
            FlashbackAction.MoveEntities -> this.processMoveEntities(buffer, consumer)
            FlashbackAction.VoiceChat -> { }
        }
    }

    private fun processConfigurationAction(buffer: RegistryFriendlyByteBuf, consumer: (ReplayPacketData) -> Unit) {
        val packet = ConfigurationProtocols.CLIENTBOUND.codec().decode(buffer)
        consumer.invoke(ReplayPacketData(ConnectionProtocol.CONFIGURATION, packet, this.tickAsDuration))
    }

    private fun processPlayAction(buffer: RegistryFriendlyByteBuf, consumer: (ReplayPacketData) -> Unit) {
        val packet = this.viewer.gameProtocol.codec().decode(buffer)
        if (packet is ClientboundLoginPacket) {
            this.player = packet.playerId
        }
        if (packet is ClientboundRespawnPacket) {
            this.respawned = true
        }
        if (packet is ClientboundEntityPositionSyncPacket && packet.id == this.player) {
            val position = packet.values.position
            this.updateChunkCacheCenter(position.x, position.y, position.z, consumer)
            val teleport = ClientboundPlayerPositionPacket(-1, packet.values, setOf())
            consumer.invoke(ReplayPacketData(ConnectionProtocol.PLAY, teleport, this.tickAsDuration))
            if (this.respawned) {
                this.respawned = false
                this.viewer.markForTeleportation()
            }
        }

        consumer.invoke(ReplayPacketData(ConnectionProtocol.PLAY, packet, this.tickAsDuration))
    }

    private fun processCachedChunk(buffer: RegistryFriendlyByteBuf, consumer: (ReplayPacketData) -> Unit) {
        val index = buffer.readVarInt()
        var packet = this.cache.getIfPresent(index)
        if (packet == null) {
            this.loadChunkCache(index)
            packet = this.cache.getIfPresent(index)
        }
        if (packet != null) {
            consumer.invoke(ReplayPacketData(ConnectionProtocol.PLAY, packet, this.tickAsDuration))
        }
    }

    private fun processCreatePlayer(buffer: RegistryFriendlyByteBuf, consumer: (ReplayPacketData) -> Unit) {
        val uuid = buffer.readUUID()
        val x = buffer.readDouble()
        val y = buffer.readDouble()
        val z = buffer.readDouble()
        val xRot = buffer.readFloat()
        val yRot = buffer.readFloat()
        val headRot = buffer.readFloat()
        val velocity = buffer.readVec3()
        @Suppress("UNUSED_VARIABLE")
        val profile = ByteBufCodecs.GAME_PROFILE.decode(buffer)
        @Suppress("UNUSED_VARIABLE")
        val gamemode = buffer.readVarInt()
        val packet = ClientboundAddEntityPacket(
            this.player, uuid, x, y, z, xRot, yRot, EntityType.PLAYER, 0, velocity, headRot.toDouble()
        )
        consumer.invoke(ReplayPacketData(ConnectionProtocol.PLAY, packet, this.tickAsDuration))
        this.updateChunkCacheCenter(x, y, z, consumer)
    }

    private fun processMoveEntities(buffer: RegistryFriendlyByteBuf, consumer: (ReplayPacketData) -> Unit) {
        val dimensions = buffer.readVarInt()
        for (i in 0..<dimensions) {
            @Suppress("UNUSED_VARIABLE")
            val dimension = buffer.readResourceKey(Registries.DIMENSION)
            val deltas = buffer.readVarInt()
            for (j in 0..<deltas) {
                val movement = EntityMovement.read(buffer)
                val packet = ClientboundTeleportEntityPacket(
                    movement.id,
                    PositionMoveRotation(movement.position, Vec3.ZERO, movement.rotation.y, movement.rotation.x),
                    setOf(),
                    movement.onGround
                )
                consumer.invoke(ReplayPacketData(ConnectionProtocol.PLAY, packet, this.tickAsDuration))
                if (movement.id != this.player) {
                    continue
                }
                val position = ClientboundPlayerPositionPacket(-1, packet.change, setOf())
                consumer.invoke(ReplayPacketData(ConnectionProtocol.PLAY, position, this.tickAsDuration))
                this.updateChunkCacheCenter(movement.position.x, movement.position.y, movement.position.z, consumer)
            }
        }
    }

    private fun updateChunkCacheCenter(x: Double, y: Double, z: Double, consumer: (ReplayPacketData) -> Unit) {
        val pos = ChunkPos(BlockPos.containing(x, y, z))
        if (pos != this.chunk) {
            this.chunk = pos
            val cache = ClientboundSetChunkCacheCenterPacket(chunk.x, chunk.z)
            consumer.invoke(ReplayPacketData(ConnectionProtocol.PLAY, cache, this.tickAsDuration))
        }
    }

    private fun loadChunkCache(index: Int) {
        val fileIndex = FlashbackIO.getChunkCacheFileIndex(index)
        val chunks = this.system.getPath(FlashbackIO.CHUNK_CACHES).resolve("$fileIndex")
        if (chunks.notExists()) {
            ServerReplay.logger.error("Failed to load chunk caches for file $fileIndex")
            return
        }
        try {
            chunks.inputStream().use { stream ->
                var i = fileIndex * FlashbackIO.LEVEL_CHUNK_CACHE_SIZE
                while (true) {
                    val bytes = stream.readNBytes(4)
                    if (bytes.size < 4) {
                        break
                    }
                    val size = this.bytesToInt(bytes)
                    val data = stream.readNBytes(size)
                    if (data.size < size) {
                        throw IllegalStateException("Failed to read chunk data, ran out of data!")
                    }

                    val packet = this.viewer.gameProtocol.codec().decode(Unpooled.wrappedBuffer(data))
                    if (packet !is ClientboundLevelChunkWithLightPacket) {
                        throw IllegalStateException("Chunk cache contains wrong packet type")
                    }
                    this.cache.put(i++, packet)
                }
            }
        } catch (e: Exception) {
            ServerReplay.logger.error("Failed to completely load chunk caches for file $fileIndex", e)
        }
    }

    private fun bytesToInt(bytes: ByteArray): Int {
        return ((bytes[0].toInt() and 0xFF) shl 24) or
            ((bytes[1].toInt() and 0xFF) shl 16) or
            ((bytes[2].toInt() and 0xFF) shl 8) or
            (bytes[3].toInt() and 0xFF)
    }
}