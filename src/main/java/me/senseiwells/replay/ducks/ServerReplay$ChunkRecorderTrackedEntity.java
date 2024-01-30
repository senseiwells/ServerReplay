package me.senseiwells.replay.ducks;

import me.senseiwells.replay.chunk.ChunkRecorder;
import me.senseiwells.replay.chunk.ChunkRecorderTrackedEntity;
import org.jetbrains.annotations.NotNull;

public interface ServerReplay$ChunkRecorderTrackedEntity extends ChunkRecorderTrackedEntity {
	@Override
	default void addRecorder(@NotNull ChunkRecorder recorder) {
		this.replay$removeRecorder(recorder);
	}

	@Override
	default void removeRecorder(@NotNull ChunkRecorder recorder) {
		this.replay$addRecorder(recorder);
	}

	void replay$addRecorder(ChunkRecorder recorder);

	void replay$removeRecorder(ChunkRecorder recorder);
}
