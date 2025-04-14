package me.senseiwells.replay.writer

import kotlinx.serialization.SerialName
import me.senseiwells.replay.recorder.ReplayRecorder
import me.senseiwells.replay.writer.flashback.FlashbackWriter
import me.senseiwells.replay.writer.replay_mod.ReplayModWriter
import java.nio.file.Path

enum class ReplayWriterType {
    @SerialName("replay_mod")
    ReplayMod,
    @SerialName("flashback")
    Flashback;

    fun create(recordings: Path): (ReplayRecorder) -> ReplayWriter {
        return when (this) {
            ReplayMod -> ReplayModWriter.dated(recordings)
            Flashback -> FlashbackWriter.dated(recordings)
        }
    }
}