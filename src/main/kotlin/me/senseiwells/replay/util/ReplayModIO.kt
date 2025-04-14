package me.senseiwells.replay.util

import me.senseiwells.replay.ServerReplay
import java.io.IOException
import java.nio.file.Path
import kotlin.io.path.*

object ReplayModIO {
    fun isReplayFile(location: Path): Boolean {
        return location.extension == "mcpr"
    }

    fun deleteCaches(location: Path) {
        try {
            val caches = location.parent.resolve(location.name + ".cache")
            if (caches.exists()) {
                @OptIn(ExperimentalPathApi::class)
                caches.deleteRecursively()
            }
        } catch (e: IOException) {
            ServerReplay.logger.error("Failed to delete caches", e)
        }
    }
}