package me.senseiwells.replay.util.processor

import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.replaymod.replaystudio.replay.ZipReplayFile
import com.replaymod.replaystudio.studio.ReplayStudio
import me.senseiwells.replay.ServerReplay
import me.senseiwells.replay.util.ReplayFileUtils
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import kotlin.io.path.*

object RecorderFixerUpper {
    private var future: CompletableFuture<Void>? = null

    @OptIn(ExperimentalPathApi::class)
    fun tryFixingUp() {
        val executor = Executors.newFixedThreadPool(
            ServerReplay.config.asyncThreadPoolSize ?: (Runtime.getRuntime().availableProcessors() / 3),
            ThreadFactoryBuilder().setNameFormat("replay-fixer-upper-%d").build()
        )
        val futures = ArrayList<CompletableFuture<Void>>()
        val paths = listOf(ServerReplay.config.playerRecordingPath, ServerReplay.config.chunkRecordingPath)
        for (path in paths) {
            if (path.isDirectory()) {
                path.visitFileTree {
                    onVisitFile { path, _ ->
                        futures.add(CompletableFuture.runAsync({
                            tryFixUpReplayFile(path)
                        }, executor))
                        FileVisitResult.CONTINUE
                    }
                }
            }
        }
        this.future = CompletableFuture.allOf(*futures.toTypedArray()).thenRun {
            this.future = null
        }
        executor.shutdown()
    }

    fun waitForFixingUp() {
        val future = this.future ?: return
        ServerReplay.logger.warn("Waiting for recordings to finish fixing up, please do NOT kill the server")
        future.join()
    }

    private fun tryFixUpReplayFile(path: Path) {
        if (!path.isReadable() || path.extension != "mcpr") {
            return
        }
        val replay = ZipReplayFile(ReplayStudio(), path.toFile())
        val meta = replay.metaData
        try {
            var dirty = false
            // This protocol version is incorrect
            if (meta.mcVersion == "1.21.4" && meta.rawProtocolVersion == 768) {
                ServerReplay.logger.info("Fixing up protocol version for replay '$path'")
                meta.setProtocolVersion(769)
                replay.writeMetaData(null, meta)
                dirty = true
            }
            if (dirty) {
                replay.save()
                ServerReplay.logger.info("Successfully fixed up replay '$path'")
            }
            replay.close()
            ReplayFileUtils.deleteCaches(path)
        } catch (e: IOException) {
            ServerReplay.logger.error("Failed to fix up replay file '$path'", e)
        }
    }
}