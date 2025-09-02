package me.senseiwells.replay.http

import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.DefaultFileRegion
import me.senseiwells.replay.ServerReplay
import net.casual.arcade.replay.io.FlashbackIO
import net.casual.arcade.replay.io.ReplayFormat
import net.casual.arcade.replay.io.ReplayModIO
import net.mcbrawls.inject.api.InjectorContext
import net.mcbrawls.inject.http.HttpByteBuf
import net.mcbrawls.inject.http.HttpInjector
import net.mcbrawls.inject.http.HttpRequest
import net.minecraft.server.MinecraftServer
import java.net.URLDecoder
import java.nio.channels.FileChannel
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.io.path.fileSize
import kotlin.io.path.isReadable
import kotlin.io.path.name
import kotlin.io.path.notExists

object DownloadReplaysHttpInjector: HttpInjector() {
    private const val PLAYER = "/player/"
    private const val CHUNK = "/chunk/"

    fun createUrl(server: MinecraftServer, path: String): String {
        return "http://${ServerReplay.getIp(server)}/replay/download/$path"
    }

    override fun isRelevant(ctx: InjectorContext, request: HttpRequest): Boolean {
        return request.requestURI.startsWith("/replay/download/")
    }

    override fun onRead(ctx: ChannelHandlerContext, buf: ByteBuf): Boolean {
        if (!ServerReplay.config.allowDownloadingReplays) {
            return super.onRead(ctx, buf)
        }

        val request = HttpRequest.parse(buf)
        val path = URLDecoder.decode(request.requestURI, StandardCharsets.UTF_8)
            .removePrefix("/replay/download")
        val success = when {
            path.startsWith(PLAYER) -> this.download(ctx, path, PLAYER, ServerReplay.config.playerRecordingPath)
            path.startsWith(CHUNK) -> this.download(ctx, path, CHUNK, ServerReplay.config.chunkRecordingPath)
            else -> false
        }
        return success || super.onRead(ctx, buf)
    }

    override fun intercept(ctx: ChannelHandlerContext, request: HttpRequest): HttpByteBuf {
        val buf = HttpByteBuf.httpBuf(ctx)
        if (!ServerReplay.config.allowDownloadingReplays) {
            buf.writeStatusLine("1.1", 403, "Forbidden")
        } else {
            buf.writeStatusLine("1.1", 404, "Not Found")
        }
        return buf
    }

    private fun download(ctx: ChannelHandlerContext, path: String, prefix: String, recordings: Path): Boolean {
        val name = path.removePrefix(prefix)
        var recording = recordings.resolve(name).normalize()
        val format = ReplayFormat.formatOf(recording)
        if (format == null) {
            recording = recordings.resolve(ReplayModIO.addFileExtension(name)).normalize()
            if (recording.notExists()) {
                recording = recordings.resolve(FlashbackIO.addFileExtension(name)).normalize()
            }
        }
        if (!recording.isReadable()) {
            return false
        }

        val size = recording.fileSize()

        val buf = HttpByteBuf.httpBuf(ctx)
        buf.writeStatusLine("1.1", 200, "OK")
        buf.writeHeader("user-agent", "kotlin/replay-download-host")
        buf.writeHeader("content-length", size.toString())
        buf.writeHeader("content-type", "application/octet-stream")
        buf.writeHeader("content-disposition", "attachment; filename=\"${recording.name}\"")
        buf.writeText("")
        ctx.writeAndFlush(buf.inner())

        val channel = FileChannel.open(recording, StandardOpenOption.READ)
        val region = DefaultFileRegion(channel, 0, size)
        ctx.writeAndFlush(region).addListener { channel.close() }
        return true
    }
}