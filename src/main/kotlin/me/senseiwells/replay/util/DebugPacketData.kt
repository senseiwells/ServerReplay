package me.senseiwells.replay.util

data class DebugPacketData(
    val type: String,
    var count: Int,
    var size: Long
) {
    fun increment(size: Int) {
        this.count++
        this.size += size
    }

    fun format(): String {
        return "Type: ${this.type}, Size: ${FileUtils.formatSize(this.size)}, Count: ${this.count}"
    }
}