package me.senseiwells.replay.processor

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
import net.casual.arcade.events.GlobalEventHandler
import net.casual.arcade.events.ListenerRegistry.Companion.register
import net.casual.arcade.events.server.ServerStartEvent
import net.casual.arcade.events.server.ServerStopEvent
import net.casual.arcade.replay.events.ReplayRecorderStartEvent
import net.casual.arcade.replay.events.ReplayRecorderStopEvent
import net.casual.arcade.replay.io.ReplayModIO
import net.casual.arcade.replay.recorder.ReplayRecorder
import net.minecraft.server.MinecraftServer
import net.minecraft.util.Util
import java.io.EOFException
import java.io.IOException
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import kotlin.io.path.*

@OptIn(ExperimentalSerializationApi::class)
object RecorderRecoverer {
    private val path = ReplayConfig.resolve("recordings.json")

    private val recordings = this.read()

    private var future: CompletableFuture<Void>? = null

    internal fun registerEvents() {
        GlobalEventHandler.Server.register<ServerStartEvent> { (server) ->
            this.tryRecover(server)
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
        write()
    }

    private fun remove(recorder: ReplayRecorder) {
        this.recordings.remove(recorder.location)
        write()
    }

    private fun tryRecover(server: MinecraftServer) {
        val recorders = this.recordings
        if (!ServerReplay.config.recoverUnsavedReplays || recorders.isEmpty()) {
            return
        }

        val recordings = if (recorders.size > 1) "recordings" else "recording"
        ServerReplay.logger.info("Detected unfinished replay $recordings that ended abruptly...")
        val futures = ArrayList<CompletableFuture<Void>>()
        for (recording in this.recordings) {
            ServerReplay.logger.info("Attempting to recover recording: $recording, please do not stop the server")

            futures.add(CompletableFuture.runAsync({ recover(recording) }, Util.ioPool()).thenRunAsync({
                this.recordings.remove(recording)
                write()
            }, server))
        }
        val future = CompletableFuture.allOf(*futures.toTypedArray())
        this.future = future
        future.thenRun { this.future = null }
    }

    private fun waitForRecovering() {
        val future = this.future ?: return
        ServerReplay.logger.warn("Waiting for recordings to be recovered, please do NOT kill the server")
        future.join()
        ServerReplay.logger.info("Finished recovering recordings")
    }

    private fun recover(recording: Path) {
        val temp = recording.parent.resolve(recording.name + ".tmp")
        if (temp.exists()) {
            this.recoverReplayModReplay(recording)
            return
        }

        ServerReplay.logger.warn("Failed to recover replay at path: $recording")
    }

    private fun recoverReplayModReplay(recording: Path) {
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
                    } catch (_: EOFException) {
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
            ReplayModIO.deleteCaches(recording)
            ServerReplay.logger.info("Successfully recovered recording $recording")
        } catch (_: IOException) {
            ServerReplay.logger.error("Failed to write unfinished replay $recording")
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
        if (!this.path.exists()) {
            return HashSet()
        }
        try {
            this.path.inputStream().use {
                return HashSet(Json.decodeFromStream(SetSerializer(PathSerializer), it))
            }
        } catch (e: Exception) {
            ServerReplay.logger.error("Failed to read replay recordings", e)
            return HashSet()
        }
    }
}