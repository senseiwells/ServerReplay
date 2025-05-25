package me.senseiwells.replay.ducks;

import me.senseiwells.replay.recorder.chunk.ChunkRecorder;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

@ApiStatus.Internal
public interface ChunkRecordable extends me.senseiwells.replay.recorder.chunk.ChunkRecordable {
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
	default void resendPackets(@NotNull ChunkRecorder recorder) {
		this.replay$resendPackets(recorder);
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

	void replay$resendPackets(ChunkRecorder recorder);

	void replay$removeRecorder(ChunkRecorder recorder);

	void replay$removeAllRecorders();
}
