package me.senseiwells.replay.ducks;

import me.senseiwells.replay.chunk.ChunkRecordable;
import me.senseiwells.replay.chunk.ChunkRecorder;
import org.jetbrains.annotations.NotNull;

public interface ServerReplay$ChunkRecordable extends ChunkRecordable {
	@Override
	default void addRecorder(@NotNull ChunkRecorder recorder) {
		this.replay$addRecorder(recorder);
	}

	@Override
	default void removeRecorder(@NotNull ChunkRecorder recorder) {
		this.replay$removeRecorder(recorder);
	}

	@Override
	default void removeAllRecorders() {
		this.replay$removeAllRecorders();
	}

	void replay$addRecorder(ChunkRecorder recorder);

	void replay$removeRecorder(ChunkRecorder recorder);

	void replay$removeAllRecorders();
}
