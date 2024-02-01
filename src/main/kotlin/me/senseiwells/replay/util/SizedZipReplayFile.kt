package me.senseiwells.replay.util

import com.replaymod.replaystudio.replay.ZipReplayFile
import com.replaymod.replaystudio.studio.ReplayStudio
import com.replaymod.replaystudio.util.Utils
import me.senseiwells.replay.mixin.studio.ZipReplayFileAccessor
import org.apache.commons.lang3.mutable.MutableLong
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.OutputStream
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

class SizedZipReplayFile(out: File): ZipReplayFile(ReplayStudio(), out) {
    private val entries = HashMap<String, MutableLong>()

    override fun write(entry: String): OutputStream {
        val mutable = MutableLong()
        this.entries[entry] = mutable
        return CounterOutputStream(super.write(entry), mutable)
    }

    fun getRawFileSize(): Long {
        return this.entries.values.fold(0L) { a, l -> a + l.value }
    }

    fun getCompressedFileSize(): Long {
        // We basically write and count the entire zipped output stream
        val bytes = MutableLong()
        ZipOutputStream(CounterOutputStream(OutputStream.nullOutputStream(), bytes)).use { out ->
            val zipFile = this.getZipFile()
            val changedEntries = this.getChangedEntries()
            val removedEntries = this.getRemovedEntries()
            if (zipFile != null) {
                for (entry in Collections.list(zipFile.entries())) {
                    if (!changedEntries.containsKey(entry.name) && !removedEntries.contains(entry.name)) {
                        val copy = ZipEntry(entry)
                        copy.setCompressedSize(-1)
                        out.putNextEntry(copy)
                        Utils.copy(zipFile.getInputStream(copy), out)
                    }
                }
            }
            for ((key, value) in changedEntries) {
                out.putNextEntry(ZipEntry(key))
                Utils.copy(BufferedInputStream(FileInputStream(value)), out)
            }
        }
        return bytes.value
    }

    fun ZipReplayFile.getZipFile(): ZipFile? {
        return (this as ZipReplayFileAccessor).zipFile
    }

    fun ZipReplayFile.getChangedEntries(): Map<String, File> {
        return (this as ZipReplayFileAccessor).changedEntries
    }

    fun ZipReplayFile.getRemovedEntries(): Set<String> {
        return (this as ZipReplayFileAccessor).removedEntries
    }
}