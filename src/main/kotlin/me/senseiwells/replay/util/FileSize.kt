package me.senseiwells.replay.util

import kotlinx.serialization.Serializable
import me.senseiwells.replay.config.serialization.FileSizeSerializer

@Serializable(with = FileSizeSerializer::class)
class FileSize(var raw: String) {
    val bytes: Long

    init {
        val bytes = FileUtils.parseSize(this.raw)
        if (bytes == null) {
            this.raw = "0GB"
            this.bytes = 0L
        } else {
            this.bytes = bytes
        }
    }
}