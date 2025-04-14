package me.senseiwells.replay.reader

import com.google.common.collect.Multimap
import me.senseiwells.replay.util.ReplayMarker
import java.io.InputStream
import kotlin.time.Duration

interface ReplayReader {
    val duration: Duration

    fun jumpTo(timestamp: Duration): Boolean

    fun readPackets(): Sequence<ReplayPacketData>

    fun readResourcePack(hash: String): InputStream?

    fun readMarkers(): Multimap<String?, ReplayMarker>

    fun close()
}