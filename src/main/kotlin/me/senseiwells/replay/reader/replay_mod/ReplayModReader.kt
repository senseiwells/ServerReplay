package me.senseiwells.replay.reader.replay_mod

import com.google.common.collect.ImmutableMultimap
import com.google.common.collect.Multimap
import com.google.common.collect.TreeMultimap
import com.replaymod.replaystudio.PacketData
import com.replaymod.replaystudio.lib.viaversion.api.protocol.packet.State
import com.replaymod.replaystudio.lib.viaversion.api.protocol.version.ProtocolVersion
import com.replaymod.replaystudio.protocol.PacketTypeRegistry
import com.replaymod.replaystudio.replay.ZipReplayFile
import com.replaymod.replaystudio.studio.ReplayStudio
import me.senseiwells.replay.ServerReplay
import me.senseiwells.replay.http.DownloadPacksHttpInjector
import me.senseiwells.replay.reader.ReplayPacketData
import me.senseiwells.replay.reader.ReplayReader
import me.senseiwells.replay.util.ReplayMarker
import me.senseiwells.replay.util.ReplayModIO
import me.senseiwells.replay.viewer.ReplayViewer
import me.senseiwells.replay.viewer.ReplayViewerUtils.toClientboundConfigurationPacket
import me.senseiwells.replay.viewer.ReplayViewerUtils.toClientboundPlayPacket
import net.minecraft.SharedConstants
import net.minecraft.network.ConnectionProtocol
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket
import net.minecraft.world.phys.Vec2
import net.minecraft.world.phys.Vec3
import java.io.IOException
import java.io.InputStream
import java.nio.file.Path
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class ReplayModReader(
    private val viewer: ReplayViewer,
    private val path: Path
): ReplayReader {
    private val replay = ZipReplayFile(ReplayStudio(), this.path.toFile())

    override val duration: Duration
        get() = this.replay.metaData.duration.milliseconds

    override fun jumpTo(timestamp: Duration): Boolean {
        return false
    }

    override fun readPackets(): Sequence<ReplayPacketData> {
        val version = ProtocolVersion.getProtocol(SharedConstants.getProtocolVersion())
        return sequence {
            replay.getPacketData(PacketTypeRegistry.get(version, State.CONFIGURATION)).use { stream ->
                var data: PacketData? = stream.readPacket()
                while (data != null) {
                    val pair = when (data.packet.registry.state) {
                        State.CONFIGURATION -> ConnectionProtocol.CONFIGURATION to data.packet.toClientboundConfigurationPacket()
                        State.PLAY -> ConnectionProtocol.PLAY to data.packet.toClientboundPlayPacket(viewer.gameProtocol)
                        else -> null
                    }
                    if (pair != null) {
                        val reference = data
                        yield(ReplayPacketData(pair.first, modifyPacket(pair.second), data.time.milliseconds, reference::release))
                    } else {
                        data.release()
                    }
                    data = stream.readPacket()
                }
            }
        }
    }

    override fun readResourcePack(hash: String): InputStream? {
        return this.replay.getResourcePack(hash).orNull()
    }

    override fun readMarkers(): Multimap<String?, ReplayMarker> {
        val markers = this.replay.markers.orNull()
        if (markers.isNullOrEmpty()) {
            return ImmutableMultimap.of()
        }

        // TODO: Move this to ReplayViewer?
        val multimap = TreeMultimap.create<String?, ReplayMarker>(
            Comparator.nullsFirst<String?>(Comparator.naturalOrder()),
            Comparator.comparingLong { it.timestamp.inWholeMilliseconds }
        )
        for (marker in markers) {
            val instance = ReplayMarker(
                marker.name,
                Vec3(marker.x, marker.y, marker.z),
                Vec2(marker.pitch, marker.yaw),
                marker.time.milliseconds,
                0xFF0000
            )
            multimap.put(marker.name, instance)
        }
        return multimap
    }

    override fun close() {
        try {
            this.replay.close()
            ReplayModIO.deleteCaches(this.path)
        } catch (e: IOException) {
            ServerReplay.logger.error("Failed to close replay file being viewed at ${this.path}")
        }
    }

    private fun modifyPacket(packet: Packet<*>): Packet<*> {
        if (packet is ClientboundResourcePackPushPacket && packet.url.startsWith("replay://")) {
            val request = packet.url.removePrefix("replay://").toIntOrNull()
                ?: throw IllegalStateException("Malformed replay packet url")
            val hash = this.replay.resourcePackIndex[request]
            if (hash == null) {
                ServerReplay.logger.error("Unknown replay resource pack index, $request for replay ${this.path}")
                return packet
            }
            val url = DownloadPacksHttpInjector.createUrl(this.viewer, hash)
            return ClientboundResourcePackPushPacket(packet.id, url, "", packet.required, packet.prompt)
        }
        return packet
    }
}