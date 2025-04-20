package me.senseiwells.replay.writer

import kotlinx.serialization.SerialName
import me.senseiwells.replay.recorder.ReplayRecorder
import me.senseiwells.replay.writer.flashback.FlashbackWriter
import me.senseiwells.replay.writer.replay_mod.ReplayModWriter
import java.nio.file.Path

enum class ReplayWriterType(val stable: Boolean) {
    @SerialName("replay_mod")
    ReplayMod(false),
    @SerialName("flashback")
    Flashback(true);

    fun create(recordings: Path): (ReplayRecorder) -> ReplayWriter {
        return when (this) {
            ReplayMod -> ReplayModWriter.dated(recordings)
            Flashback -> FlashbackWriter.dated(recordings)
        }
    }

    fun warn(consumer: (String) -> Unit) {
        if (this == Flashback) {
            consumer.invoke("Flashback support is currently experimental: you may encounter issues with your recordings, including issues that may cause recordings to be corrupt, you have been warned!")
            consumer.invoke("If you do encounter any issues please submit an issue report to https://github.com/senseiwells/ServerReplay/issues")
        }
        if (!this.stable) {
            consumer.invoke("${this.name} support is currently unstable: ${this.name} hasn't released yet, your replays may not be compatible in the future, you have been warned!")
        }
    }
}