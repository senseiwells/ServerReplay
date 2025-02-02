package me.senseiwells.replay.saver

import me.senseiwells.replay.recorder.ReplayRecorder
import me.senseiwells.replay.saver.flashback.FlashbackSaver
import me.senseiwells.replay.saver.replay_mod.ReplayModSaver
import java.nio.file.Path

enum class ReplaySaverType {
    ReplayMod,
    Flashback;

    fun create(recordings: Path): (ReplayRecorder) -> ReplaySaver {
        return when (this) {
            ReplayMod -> ReplayModSaver.dated(recordings)
            Flashback -> FlashbackSaver.dated(recordings)
        }
    }
}