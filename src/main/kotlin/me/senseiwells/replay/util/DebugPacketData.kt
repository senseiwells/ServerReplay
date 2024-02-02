package me.senseiwells.replay.util

data class DebugPacketData(
    val type: Class<*>,
    var count: Int,
    var size: Long
) {
    fun increment(size: Int) {
        this.count++
        this.size += size
    }

    fun format(): String {
        return "Type: ${this.type.simpleName}, Size: ${FileUtils.formatSize(this.size)}, Count: ${this.count}"
    }
}