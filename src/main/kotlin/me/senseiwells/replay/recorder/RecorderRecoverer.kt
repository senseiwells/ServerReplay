package me.senseiwells.replay.recorder

import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.replaymod.replaystudio.lib.viaversion.api.protocol.packet.State
import com.replaymod.replaystudio.protocol.PacketTypeRegistry
import com.replaymod.replaystudio.replay.ZipReplayFile
import com.replaymod.replaystudio.studio.ReplayStudio
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import me.senseiwells.replay.ServerReplay
import me.senseiwells.replay.config.ReplayConfig
import me.senseiwells.replay.config.serialization.PathSerializer
import net.minecraft.server.MinecraftServer
import net.minecraft.util.Mth
import org.jetbrains.annotations.ApiStatus.Internal
import java.io.EOFException
import java.io.IOException
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import kotlin.io.path.*

@OptIn(ExperimentalSerializationApi::class)
object RecorderRecoverer {
    private val path = ReplayConfig.root.resolve("recordings.json")

    private val recordings: MutableSet<@Serializable(with = PathSerializer::class) Path>

    init {
        this.recordings = this.read()
    }

    fun add(recorder: ReplayRecorder) {
        this.recordings.add(recorder.location)
        this.write()
    }

    fun remove(recorder: ReplayRecorder) {
        this.recordings.remove(recorder.location)
        this.write()
    }

    @Internal
    @JvmStatic
    fun tryRecover(server: MinecraftServer) {
        val recorders = this.recordings
        if (!ServerReplay.config.recoverUnsavedReplays || recorders.isEmpty()) {
            return
        }

        val recordings = if (recorders.size > 1) "recordings" else "recording"
        ServerReplay.logger.info("Detected unfinished replay $recordings that ended abruptly...")
        val executor = Executors.newFixedThreadPool(
            Mth.ceil(recorders.size / 2.0),
            ThreadFactoryBuilder().setNameFormat("replay-recoverer-%d").build()
        )
        for (recording in this.recordings) {
            ServerReplay.logger.info("Attempting to recover recording: $recording, please do not stop the server")

            CompletableFuture.runAsync({ this.recover(recording) }, executor).thenRunAsync({
                this.recordings.remove(recording)
                this.write()
            }, server)
        }
        executor.shutdown()
    }

    @OptIn(ExperimentalPathApi::class)
    private fun recover(recording: Path) {
        val temp = recording.parent.resolve(recording.name + ".tmp")
        if (temp.exists()) {
            val replay = ZipReplayFile(ReplayStudio(), recording.toFile())

            try {
                // We need to update the duration listed in the
                // metadata to ensure it's correct
                val meta = replay.metaData
                val protocol = meta.protocolVersion
                val registry = PacketTypeRegistry.get(protocol, State.LOGIN)
                val data = replay.getPacketData(registry)
                val first = data.readPacket()
                if (first != null) {
                    // We don't care about the contents, only the time
                    first.release()
                    var packet = first
                    while (true) {
                        try {
                            val next = data.readPacket()
                            if (next != null) {
                                next.release()
                                packet = next
                            } else {
                                break
                            }
                        } catch (e: EOFException) {
                            break
                        }
                    }
                    meta.duration = packet.time.toInt()
                    replay.writeMetaData(registry, meta)
                }
            } catch (e: IOException) {
                ServerReplay.logger.error("Failed to update meta for unfinished replay $recording, your recording may be corrupted...", e)
            }

            try {
                replay.saveTo(recording.parent.resolve(recording.name + ".mcpr").toFile())
                replay.close()
                ServerReplay.logger.info("Successfully recovered recording $recording")
            } catch (e: IOException) {
                ServerReplay.logger.error("Failed to write unfinished replay $recording")
            }
        } else {
            ServerReplay.logger.warn("Could not find unfinished replay files for $recording??")
        }

        val cache = recording.parent.resolve(recording.name + ".cache")
        if (cache.exists()) {
            try {
                cache.deleteRecursively()
            } catch (e: Exception) {
                ServerReplay.logger.error("Failed to delete replay cache for $recording")
            }
        }
    }

    private fun write() {
        try {
            this.path.parent.createDirectories()
            this.path.outputStream().use {
                Json.encodeToStream(this.recordings, it)
            }
        } catch (e: Exception) {
            ServerReplay.logger.error("Failed to write unfinished recorders", e)
        }
    }

    private inline fun <reified T> read(): MutableSet<T> {
        if (!this.path.exists()) {
            return HashSet()
        }
        return try {
            this.path.inputStream().use {
                Json.decodeFromStream<MutableSet<T>>(it)
            }
        } catch (e: Exception) {
            ServerReplay.logger.error("Failed to read replay recordings", e)
            HashSet()
        }
    }
}