package me.senseiwells.replay.http

import me.senseiwells.replay.ServerReplay
import net.casual.arcade.interceptor.http.resource.HttpResourceInterceptor
import net.casual.arcade.replay.io.FlashbackIO
import net.casual.arcade.replay.io.ReplayFormat
import net.casual.arcade.replay.io.ReplayModIO
import net.casual.arcade.utils.network.ResolvableURL
import net.minecraft.server.MinecraftServer
import java.nio.file.Path
import kotlin.io.path.*

object ReplayDownloaderInterceptor: HttpResourceInterceptor(
    prefix = "/replay/download/",
    server = "kotlin/server-replay-downloader"
) {
    private const val PLAYER = "player/"
    private const val CHUNK = "chunk/"

    fun createUrl(server: MinecraftServer, path: String): ResolvableURL {
        return ResolvableURL.local("http", ServerReplay.config.replayServerIp, server.port, "replay/download/$path")
    }

    override fun getResource(path: String): HttpResource? {
        if (!ServerReplay.config.allowDownloadingReplays) {
            return null
        }

        return when {
            path.startsWith(PLAYER) -> this.getReplayResource(ServerReplay.config.playerRecordingPath, path.removePrefix(PLAYER))
            path.startsWith(CHUNK) -> this.getReplayResource(ServerReplay.config.chunkRecordingPath, path.removePrefix(CHUNK))
            else -> null
        }
    }

    private fun getReplayResource(root: Path, path: String): HttpResource? {
        var recording = root.resolve(path).normalize()
        val format = ReplayFormat.formatOf(recording)
        if (format == null) {
            recording = root.resolve(ReplayModIO.addFileExtension(path)).normalize()
            if (recording.notExists()) {
                recording = root.resolve(FlashbackIO.addFileExtension(path)).normalize()
            }
        }
        if (!recording.startsWith(root.normalize()) || !recording.isReadable()) {
            return null
        }

        return HttpResource(recording.inputStream(), recording.name, recording.fileSize())
    }
}