package me.senseiwells.replay.util.flashback

import java.nio.file.Path
import kotlin.io.path.extension

object FlashbackIO {
    // Magic number flashback uses to verify that it's a flashback file
    const val MAGIC_NUMBER: Int = -0x287F177C

    const val CHUNK_LENGTH = 5 * 60 * 20
    const val LEVEL_CHUNK_CACHE_SIZE = 10000

    const val METADATA = "metadata.json"
    const val METADATA_OLD = "$METADATA.old"
    const val CHUNK_CACHES = "level_chunk_caches"

    fun isFlashbackFile(path: Path): Boolean {
        return path.extension == "zip"
    }

    fun getChunkCacheFileIndex(index: Int): Int {
        return index / LEVEL_CHUNK_CACHE_SIZE
    }
}