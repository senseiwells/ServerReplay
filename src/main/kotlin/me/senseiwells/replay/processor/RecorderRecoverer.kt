package me.senseiwells.replay.processor

import com.replaymod.replaystudio.lib.viaversion.api.protocol.packet.State
import com.replaymod.replaystudio.protocol.PacketTypeRegistry
import com.replaymod.replaystudio.replay.ZipReplayFile
import com.replaymod.replaystudio.studio.ReplayStudio
import kotlinx.coroutines.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.builtins.SetSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import me.senseiwells.replay.ServerReplay
import me.senseiwells.replay.config.ReplayConfig
import me.senseiwells.replay.config.serialization.PathSerializer
import net.casual.arcade.events.GlobalEventHandler
import net.casual.arcade.events.server.ServerStartEvent
import net.casual.arcade.events.server.ServerStopEvent
import net.casual.arcade.events.utils.register
import net.casual.arcade.replay.events.ReplayRecorderStartEvent
import net.casual.arcade.replay.events.ReplayRecorderStopEvent
import net.casual.arcade.replay.io.FlashbackIO
import net.casual.arcade.replay.io.ReplayModIO
import net.casual.arcade.replay.recorder.ReplayRecorder
import net.casual.arcade.replay.util.FileUtils
import java.io.EOFException
import java.io.IOException
import java.nio.file.Path
import kotlin.io.path.*

@OptIn(ExperimentalSerializationApi::class)
object RecorderRecoverer {
    private val path = ReplayConfig.resolve("recordings.json")
    private val recordings = this.read()

    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob() +  CoroutineExceptionHandler { _, throwable ->
        ServerReplay.logger.error("Uncaught exception while trying to recover replay", throwable)
    })
    private val recovering = ArrayList<Pair<Path, Job>>()

    internal fun registerEvents() {
        GlobalEventHandler.Server.register<ServerStartEvent> {
            this.tryRecoverReplays()
        }
        GlobalEventHandler.Server.register<ServerStopEvent>(phase = ServerStopEvent.PHASE_POST) {
            this.waitForRecovering()
        }
        GlobalEventHandler.Server.register<ReplayRecorderStartEvent> { (recorder) ->
            this.add(recorder)
        }
        GlobalEventHandler.Server.register<ReplayRecorderStopEvent> { (recorder) ->
            this.remove(recorder)
        }
    }

    private fun add(recorder: ReplayRecorder) {
        this.recordings.add(recorder.location)
        this.write()
    }

    private fun remove(recorder: ReplayRecorder) {
        this.recordings.remove(recorder.location)
        this.write()
    }

    private fun tryRecoverReplays() {
        if (!ServerReplay.config.recoverUnsavedReplays || this.recordings.isEmpty()) {
            return
        }

        val recoverable = this.recordings.toList()
        val noun = if (recoverable.size > 1) "recordings" else "recording"
        ServerReplay.logger.info("Detected unfinished replay $noun that ended abruptly...")

        for (recording in recoverable) {
            ServerReplay.logger.info("Attempting to recover recording: $recording, please do not stop the server")
            this.recovering.add(recording to this.coroutineScope.launch { tryRecoverReplay(recording) })
        }
    }

    private fun waitForRecovering() {
        if (this.recovering.isEmpty()) {
            return
        }
        ServerReplay.logger.warn("Waiting for recordings to be recovered, please do NOT kill the server")
        runBlocking {
            for ((path, job) in recovering) {
                job.join()
                recordings.remove(path)
            }
            write()
        }
        this.recovering.clear()
        ServerReplay.logger.info("Finished recovering recordings")
    }

    private fun tryRecoverReplay(recording: Path) {
        val temp = recording.parent.resolve(recording.name + ".tmp")
        if (temp.exists()) {
            this.tryRecoverReplayModReplay(recording)
            return
        }
        if (recording.resolve(FlashbackIO.CHUNK_CACHES).exists()) {
            this.tryRecoverFlashbackReplay(recording)
            return
        }

        ServerReplay.logger.warn("Failed to recover replay at path: $recording")
    }

    private fun tryRecoverReplayModReplay(path: Path) {
        val replay = ZipReplayFile(ReplayStudio(), path.toFile())

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
                    } catch (_: EOFException) {
                        break
                    }
                }
                meta.duration = packet.time.toInt()
                replay.writeMetaData(registry, meta)
            }
        } catch (e: IOException) {
            ServerReplay.logger.error("Failed to update meta for unfinished replay $path, your recording may be corrupted...", e)
        }

        try {
            replay.saveTo(path.parent.resolve(path.name + ".mcpr").toFile())
            replay.close()
            ReplayModIO.deleteCaches(path)
            ServerReplay.logger.info("Successfully recovered recording $path")
        } catch (_: IOException) {
            ServerReplay.logger.error("Failed to write unfinished replay mod replay $path")
        }
    }

    @OptIn(ExperimentalPathApi::class)
    private fun tryRecoverFlashbackReplay(path: Path) {
        try {
            FileUtils.zip(path, path.parent.resolve(path.name + ".zip"))
            try {
                path.deleteRecursively()
            } catch (e: IOException) {
                ServerReplay.logger.warn("Successfully zipped flashback replay, but failed to delete raw recording", e)
            }
        } catch (_: IOException) {
            ServerReplay.logger.error("Failed to write unfinished flashback replay $path")
        }
    }

    private fun write() {
        try {
            this.path.createParentDirectories()
            this.path.outputStream().use {
                Json.encodeToStream(SetSerializer(PathSerializer), this.recordings, it)
            }
        } catch (e: Exception) {
            ServerReplay.logger.error("Failed to write unfinished recorders", e)
        }
    }

    private fun read(): MutableSet<Path> {
        if (this.path.exists()) {
            try {
                this.path.inputStream().use {
                    return HashSet(Json.decodeFromStream(SetSerializer(PathSerializer), it))
                }
            } catch (e: Exception) {
                ServerReplay.logger.error("Failed to read replay recordings", e)
            }
        }
        return HashSet()
    }
}