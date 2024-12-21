package me.senseiwells.replay.util.processor

import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.replaymod.replaystudio.lib.viaversion.api.protocol.packet.State
import com.replaymod.replaystudio.protocol.PacketTypeRegistry
import com.replaymod.replaystudio.replay.ZipReplayFile
import com.replaymod.replaystudio.studio.ReplayStudio
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.builtins.SetSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import me.senseiwells.replay.ServerReplay
import me.senseiwells.replay.config.ReplayConfig
import me.senseiwells.replay.config.serialization.PathSerializer
import me.senseiwells.replay.recorder.ReplayRecorder
import me.senseiwells.replay.util.ReplayFileUtils
import net.minecraft.server.MinecraftServer
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

    private val recordings: MutableSet<Path>

    private var future: CompletableFuture<Void>? = null

    init {
        recordings = read()
    }

    fun add(recorder: ReplayRecorder) {
        recordings.add(recorder.location)
        write()
    }

    fun remove(recorder: ReplayRecorder) {
        recordings.remove(recorder.location)
        write()
    }

    @Internal
    @JvmStatic
    fun tryRecover(server: MinecraftServer) {
        val recorders = recordings
        if (!ServerReplay.config.recoverUnsavedReplays || recorders.isEmpty()) {
            return
        }

        val recordings = if (recorders.size > 1) "recordings" else "recording"
        ServerReplay.logger.info("Detected unfinished replay $recordings that ended abruptly...")
        val executor = Executors.newFixedThreadPool(
            ServerReplay.config.asyncThreadPoolSize ?: (Runtime.getRuntime().availableProcessors() / 3),
            ThreadFactoryBuilder().setNameFormat("replay-recoverer-%d").build()
        )
        val futures = ArrayList<CompletableFuture<Void>>()
        for (recording in RecorderRecoverer.recordings) {
            ServerReplay.logger.info("Attempting to recover recording: $recording, please do not stop the server")

            futures.add(CompletableFuture.runAsync({ recover(recording) }, executor).thenRunAsync({
                RecorderRecoverer.recordings.remove(recording)
                write()
            }, server))
        }
        this.future = CompletableFuture.allOf(*futures.toTypedArray()).thenRun {
            this.future = null
        }
        executor.shutdown()
    }

    fun waitForRecovering() {
        val future = this.future ?: return
        ServerReplay.logger.warn("Waiting for recordings to be recovered, please do NOT kill the server")
        future.join()
    }

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
                ReplayFileUtils.deleteCaches(recording)
                ServerReplay.logger.info("Successfully recovered recording $recording")
            } catch (e: IOException) {
                ServerReplay.logger.error("Failed to write unfinished replay $recording")
            }
        } else {
            ServerReplay.logger.warn("Could not find unfinished replay files for $recording??")
        }
    }

    private fun write() {
        try {
            path.parent.createDirectories()
            path.outputStream().use {
                Json.encodeToStream(SetSerializer(PathSerializer), recordings, it)
            }
        } catch (e: Exception) {
            ServerReplay.logger.error("Failed to write unfinished recorders", e)
        }
    }

    private fun read(): MutableSet<Path> {
        if (!path.exists()) {
            return HashSet()
        }
        return try {
            path.inputStream().use {
                HashSet(Json.decodeFromStream(SetSerializer(PathSerializer), it))
            }
        } catch (e: Exception) {
            ServerReplay.logger.error("Failed to read replay recordings", e)
            HashSet()
        }
    }
}