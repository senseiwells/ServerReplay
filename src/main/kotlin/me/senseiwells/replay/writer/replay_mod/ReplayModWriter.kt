package me.senseiwells.replay.writer.replay_mod

import com.google.common.hash.Hashing
import com.replaymod.replaystudio.data.Marker
import com.replaymod.replaystudio.io.ReplayOutputStream
import com.replaymod.replaystudio.lib.viaversion.api.protocol.packet.State
import com.replaymod.replaystudio.lib.viaversion.api.protocol.version.ProtocolVersion
import com.replaymod.replaystudio.protocol.PacketTypeRegistry
import com.replaymod.replaystudio.replay.ReplayMetaData
import io.netty.buffer.Unpooled
import io.netty.handler.codec.EncoderException
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToStream
import me.senseiwells.replay.ServerReplay
import me.senseiwells.replay.config.ReplayConfig
import me.senseiwells.replay.recorder.ReplayRecorder
import me.senseiwells.replay.util.*
import me.senseiwells.replay.writer.ReplayWriter
import me.senseiwells.replay.writer.ReplayWriter.Companion.broadcastToOps
import me.senseiwells.replay.writer.ReplayWriter.Companion.broadcastToOpsAndConsole
import me.senseiwells.replay.writer.ReplayWriter.Companion.encodePacket
import me.senseiwells.replay.writer.ReplayWriter.Companion.name
import net.minecraft.ChatFormatting
import net.minecraft.SharedConstants
import net.minecraft.network.ConnectionProtocol
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.ProtocolInfo
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket
import net.minecraft.world.entity.EntityType
import java.io.IOException
import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import kotlin.io.path.*
import kotlin.time.Duration
import com.github.steveice10.netty.buffer.Unpooled as ReplayUnpooled
import com.replaymod.replaystudio.protocol.Packet as ReplayPacket

class ReplayModWriter(
    override val recorder: ReplayRecorder,
    override val path: Path
): ReplayWriter {
    private val executor = Executors.newSingleThreadExecutor()

    private val replay: SizedZipReplayFile = SizedZipReplayFile(out = this.path.toFile())
    private val output: ReplayOutputStream = this.replay.writePacketData()
    private val meta: ReplayMetaData = this.createNewMeta()

    private val packs = HashMap<Int, String>()
    private var packId = 0

    override var markers: Int = 0
    override val closed: Boolean
        get() = this.executor.isShutdown

    override fun prePacketRecord(packet: Packet<*>): Boolean {
        when (packet) {
            is ClientboundAddEntityPacket -> {
                if (packet.type == EntityType.PLAYER) {
                    val uuids = this.meta.players.toMutableSet()
                    uuids.add(packet.uuid.toString())
                    this.meta.players = uuids.toTypedArray()
                    this.saveMeta()
                }
            }
            is ClientboundResourcePackPushPacket -> {
                return this.downloadAndRecordResourcePack(packet)
            }
        }
        return false
    }

    override fun writePacket(
        packet: Packet<*>,
        protocol: ProtocolInfo<*>,
        timestamp: Duration,
        offThread: Boolean
    ): CompletableFuture<Int?> {
        return CompletableFuture.supplyAsync({
            this.writePacketSync(packet, protocol, timestamp, offThread)
        }, this.executor)
    }

    override fun postPacketRecord(packet: Packet<*>) {

    }

    override fun writeMarker(marker: ReplayMarker) {
        this.markers++
        val instance = Marker()
        instance.time = marker.timestamp.inWholeMilliseconds.toInt()
        instance.name = marker.name
        if (marker.position != null) {
            instance.x = marker.position.x
            instance.y = marker.position.y
            instance.z = marker.position.z
        }
        if (marker.rotation != null) {
            instance.pitch = marker.rotation.x
            instance.yaw = marker.rotation.y
        }
        this.executor.execute {
            val markers = this.replay.markers.or(::HashSet)
            markers.add(instance)
            this.replay.writeMarkers(markers)
        }
    }

    override fun getRawRecordingSize(): Long {
        return this.replay.getRawFileSize()
    }

    override fun close(duration: Duration, save: Boolean): CompletableFuture<Long> {
        if (save) {
            this.meta.duration = duration.inWholeMilliseconds.toInt()
            this.saveMeta()
        }
        val future = CompletableFuture.supplyAsync({
            var size = 0L
            try {
                val path = this.recording()
                this.output.close()

                val additional = Component.empty()
                if (save) {
                    this.broadcastToOpsAndConsole("Starting to save replay ${this.name}, please do not stop the server!")

                    this.replay.saveTo(path.toFile())
                    size = path.fileSize()
                    val click = ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, this.recorder.getViewingCommand())
                    val hover = HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Click to view replay"))
                    additional.append(" and saved to ")
                        .append(Component.literal(path.toString()).withStyle {
                            it.withClickEvent(click).withHoverEvent(hover).withColor(ChatFormatting.GREEN)
                        })
                        .append(", compressed to ${FileUtils.formatSize(size)}")
                }

                this.replay.close()
                ReplayModIO.deleteCaches(this.path)
                this.broadcastToOpsAndConsole(
                    Component.literal("Successfully closed replay ${this.name}").append(additional)
                )
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

    private fun writePacketSync(
        packet: Packet<*>,
        protocol: ProtocolInfo<*>,
        timestamp: Duration,
        offThread: Boolean
    ): Int? {
        val saved = try {
            this.encodePacket(packet, protocol)
        } catch (e: EncoderException) {
            val name = packet.getDebugName()
            if (!offThread) {
                ServerReplay.logger.error("Failed to encode packet $name, skipping", e)
                return null
            }
            ServerReplay.logger.error(
                "Failed to encode packet $name during ${protocol.id()} likely due to being off-thread, skipping", e
            )
            return null
        }
        val bytes = saved.buf.readableBytes()

        try {
            this.output.write(timestamp.inWholeMilliseconds, saved)
        } catch (e: IOException) {
            ServerReplay.logger.error("Failed to write packet", e)
        }
        return bytes
    }

    private fun encodePacket(packet: Packet<*>, protocol: ProtocolInfo<*>): ReplayPacket {
        val version = ProtocolVersion.getProtocol(SharedConstants.getProtocolVersion())
        val registry = PacketTypeRegistry.get(version, this.protocolAsState(protocol))

        val friendly = FriendlyByteBuf(Unpooled.buffer())
        try {
            encodePacket(packet, protocol, friendly)
            val id = friendly.readVarInt()
            return ReplayPacket(registry, id, ReplayUnpooled.wrappedBuffer(friendly.toByteArray()))
        } finally {
            friendly.release()
        }
    }

    private fun protocolAsState(protocol: ProtocolInfo<*>): State {
        return when (protocol.id()) {
            ConnectionProtocol.PLAY -> State.PLAY
            ConnectionProtocol.CONFIGURATION -> State.CONFIGURATION
            ConnectionProtocol.LOGIN -> State.LOGIN
            else -> throw IllegalStateException("Expected connection protocol to be 'PLAY', 'CONFIGURATION' or 'LOGIN'")
        }
    }

    private fun downloadAndRecordResourcePack(packet: ClientboundResourcePackPushPacket): Boolean {
        if (!ServerReplay.config.includeResourcePacks || packet.url.startsWith("replay://")) {
            return false
        }
        @Suppress("DEPRECATION")
        val pathHash = Hashing.sha1().hashString(packet.url, StandardCharsets.UTF_8).toString()
        val path = ReplayConfig.root.resolve("packs").resolve(pathHash)

        val requestId = this.packId++
        if (!path.exists() || !this.writeResourcePack(path.readBytes(), packet.hash, requestId)) {
            CompletableFuture.runAsync {
                path.parent.createDirectories()
                val bytes = URI(packet.url).toURL().openStream().readAllBytes()
                path.writeBytes(bytes)
                if (!this.writeResourcePack(bytes, packet.hash, requestId)) {
                    ServerReplay.logger.error("Resource pack hashes do not match! Pack '${packet.url}' will not be loaded...")
                }
            }.exceptionally {
                ServerReplay.logger.error("Failed to download resource pack", it)
                null
            }
        }
        this.executor.execute {
            this.packs[requestId] = packet.url
        }
        this.recorder.record(ClientboundResourcePackPushPacket(
            packet.id,
            "replay://${requestId}",
            "",
            packet.required,
            packet.prompt
        ))
        return true
    }

    private fun writeResourcePack(bytes: ByteArray, expectedHash: String, id: Int): Boolean {
        @Suppress("DEPRECATION")
        val packHash = Hashing.sha1().hashBytes(bytes).toString()
        if (expectedHash == "" || expectedHash == packHash) {
            this.executor.execute {
                try {
                    val index = this.replay.resourcePackIndex ?: HashMap()
                    val write = !index.containsValue(packHash)
                    index[id] = packHash
                    this.replay.writeResourcePackIndex(index)
                    if (write) {
                        this.replay.writeResourcePack(packHash).use {
                            it.write(bytes)
                        }
                    }
                } catch (e: IOException) {
                    ServerReplay.logger.warn("Failed to write resource pack", e)
                }
            }
            return true
        }
        return false
    }

    private fun createNewMeta(): ReplayMetaData {
        val meta = ReplayMetaData()
        meta.isSingleplayer = false
        meta.serverName = ServerReplay.config.worldName
        meta.customServerName = ServerReplay.config.serverName
        meta.generator = "ServerReplay v${ServerReplay.version}"
        meta.date = System.currentTimeMillis()
        meta.mcVersion = SharedConstants.getCurrentVersion().name
        return meta
    }

    @OptIn(ExperimentalSerializationApi::class)
    private fun saveMeta() {
        val version = ProtocolVersion.getProtocol(SharedConstants.getProtocolVersion())
        val registry = PacketTypeRegistry.get(version, State.LOGIN)

        this.executor.execute {
            // When updating before ReplayStudio ensure to write the correct meta
            this.replay.writeMetaData(registry, this.meta)

            this.replay.write(ReplayWriter.ENTRY_SERVER_REPLAY_META).use {
                val meta = HashMap<String, Any>()
                this.recorder.addMetadata(meta)

                ReplayMetaUtils.serialize(meta, it.writer())
            }

            this.replay.write(ENTRY_SERVER_REPLAY_PACKS).use {
                Json.encodeToStream(this.packs, it)
            }
        }
    }

    private fun recording(): Path {
        return this.path.parent.resolve(this.path.name + ".mcpr")
    }

    companion object {
        private const val ENTRY_SERVER_REPLAY_PACKS = "server_replay_packs.json"

        fun dated(recordings: Path): (ReplayRecorder) -> ReplayModWriter {
            val date = DateTimeUtils.getFormattedDate()
            return { ReplayModWriter(it, FileUtils.findNextAvailable(recordings.resolve(date))) }
        }
    }
}