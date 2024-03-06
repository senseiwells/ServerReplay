package me.senseiwells.replay.util

import com.replaymod.replaystudio.replay.ZipReplayFile
import com.replaymod.replaystudio.studio.ReplayStudio
import com.replaymod.replaystudio.util.Utils
import me.senseiwells.replay.ServerReplay
import me.senseiwells.replay.mixin.studio.ZipReplayFileAccessor
import org.apache.commons.io.FileUtils as ApacheFileUtils
import org.apache.commons.lang3.mutable.MutableLong
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.OutputStream
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlin.collections.ArrayList

class SizedZipReplayFile(
    input: File? = null,
    out: File,
    cache: File = File(out.parentFile, out.name + ".cache")
): ZipReplayFile(ReplayStudio(), input, out, cache) {
    private val entries = HashMap<String, MutableLong>()

    override fun write(entry: String): OutputStream {
        val mutable = MutableLong()
        this.entries[entry] = mutable
        return CounterOutputStream(super.write(entry), mutable)
    }

    fun getRawFileSize(): Long {
        return this.entries.values.fold(0L) { a, l -> a + l.value }
    }

    fun getCompressedFileSize(executor: Executor = Executor(Runnable::run)): Long {
        // We basically write and count the entire zipped output stream
        val bytes = MutableLong()
        ZipOutputStream(CounterOutputStream(OutputStream.nullOutputStream(), bytes)).use { out ->
            // Our zip file is not being written to, so this is file to access async
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

            val futures = ArrayList<Pair<File, CompletableFuture<Void>>>(changedEntries.size)
            for ((key, value) in changedEntries) {
                out.putNextEntry(ZipEntry(key))
                // Writing to our output stream is slow (since we are compressing)
                // So, we want to copy the file into a temporary file (to be thread safe)
                // then from there we can compress it...
                val temp = value.parentFile.resolve(".tmp-compressing-${value.name}-copy")
                // We don't want this file to stick around if JVM terminates abruptly
                temp.deleteOnExit()

                val future = CompletableFuture.runAsync({
                    if (value.exists()) {
                        ApacheFileUtils.copyFile(value, temp)
                    }
                }, executor)
                futures.add(temp to future)
            }

            for ((temp, future) in futures) {
                try {
                    // Wait for copy on the main executor
                    future.join()
                    ApacheFileUtils.copyFile(temp, out)
                } catch (e: Exception) {
                    ServerReplay.logger.error("Failed to copy file for compression ${temp.absolutePath}", e)
                } finally {
                    if (temp.exists()) {
                        temp.delete()
                    }
                }
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