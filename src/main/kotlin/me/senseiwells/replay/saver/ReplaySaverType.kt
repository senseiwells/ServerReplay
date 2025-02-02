package me.senseiwells.replay.saver

import kotlinx.serialization.SerialName
import me.senseiwells.replay.recorder.ReplayRecorder
import me.senseiwells.replay.saver.flashback.FlashbackSaver
import me.senseiwells.replay.saver.replay_mod.ReplayModSaver
import java.nio.file.Path

enum class ReplaySaverType {
    @SerialName("replay_mod")
    ReplayMod,
    @SerialName("flashback")
    Flashback;

    fun create(recordings: Path): (ReplayRecorder) -> ReplaySaver {
        return when (this) {
            ReplayMod -> ReplayModSaver.dated(recordings)
            Flashback -> FlashbackSaver.dated(recordings)
        }
    }
}