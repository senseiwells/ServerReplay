package me.senseiwells.replay.ducks;

import me.senseiwells.replay.chunk.ChunkRecordable;
import me.senseiwells.replay.chunk.ChunkRecorder;
import me.senseiwells.replay.chunk.ChunkRecorders;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public interface ServerReplay$ChunkRecordable extends ChunkRecordable {
	@NotNull
	@Override
	default Collection<ChunkRecorder> getRecorders() {
		return this.replay$getRecorders();
	}

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

	Collection<ChunkRecorder> replay$getRecorders();

	void replay$addRecorder(ChunkRecorder recorder);

	void replay$removeRecorder(ChunkRecorder recorder);

	void replay$removeAllRecorders();
}
