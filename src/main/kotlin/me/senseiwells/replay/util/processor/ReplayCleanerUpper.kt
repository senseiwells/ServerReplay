package me.senseiwells.replay.util.processor

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.io.IOException
import me.senseiwells.replay.ServerReplay
import me.senseiwells.replay.util.ReplayModIO
import me.senseiwells.replay.util.flashback.FlashbackIO
import java.nio.file.FileVisitResult
import java.nio.file.Path
import kotlin.io.path.deleteIfExists
import kotlin.io.path.getLastModifiedTime
import kotlin.io.path.isDirectory
import kotlin.io.path.visitFileTree
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

object ReplayCleanerUpper {
    @OptIn(DelicateCoroutinesApi::class)
    internal fun run() = GlobalScope.launch {
        while (true) {
            delay(10.minutes)
            val duration = ServerReplay.config.deleteReplaysAfterDuration
            if (duration.isPositive()) {
                cleanUpFiles(duration)
            }
        }
    }

    private fun cleanUpFiles(duration: Duration) {
        for (path in ServerReplay.config.getRootRecordingPaths()) {
            if (path.isDirectory()) {
                path.visitFileTree {
                    onVisitFile { path, _ ->
                        cleanUpFile(path, duration)
                        FileVisitResult.CONTINUE
                    }
                }
            }
        }
    }

    private fun cleanUpFile(path: Path, duration: Duration) {
        if (!FlashbackIO.isFlashbackFile(path) && !ReplayModIO.isReplayFile(path)) {
            return
        }

        val delta = (System.currentTimeMillis() - path.getLastModifiedTime().toMillis()).milliseconds
        if (delta >= duration) {
            if (ServerReplay.config.logDeletedReplays) {
                ServerReplay.logger.info("Deleting stale replay, $path")
            }
            try {
                path.deleteIfExists()
            } catch (e: IOException) {
                ServerReplay.logger.error("Failed to delete stale replay at $path", e)
            }
        }
    }
}