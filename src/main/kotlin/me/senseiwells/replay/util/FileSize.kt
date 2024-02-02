package me.senseiwells.replay.util

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