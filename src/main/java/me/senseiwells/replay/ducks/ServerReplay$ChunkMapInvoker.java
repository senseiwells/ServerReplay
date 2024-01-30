package me.senseiwells.replay.ducks;

import me.senseiwells.replay.util.ducks.ChunkMapInvoker;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ThreadedLevelLightEngine;
import org.jetbrains.annotations.NotNull;

public interface ServerReplay$ChunkMapInvoker extends ChunkMapInvoker {
	@Override
	default ChunkHolder getUpdatingChunkIfPresent(long pos) {
		return this.replay$getUpdatingChunkIfPresent(pos);
	}

	@NotNull
	@Override
	default ThreadedLevelLightEngine getLightEngine() {
		return this.replay$getLightEngine();
	}

	ChunkHolder replay$getUpdatingChunkIfPresent(long pos);

	ThreadedLevelLightEngine replay$getLightEngine();
}
