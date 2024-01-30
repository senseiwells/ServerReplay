package me.senseiwells.replay.mixin.rejoin;

import me.senseiwells.replay.ducks.ServerReplay$ChunkMapInvoker;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ThreadedLevelLightEngine;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(ChunkMap.class)
public abstract class ChunkMapMixin implements ServerReplay$ChunkMapInvoker {
	@Shadow @Final private ThreadedLevelLightEngine lightEngine;

	@Override
	public ChunkHolder replay$getUpdatingChunkIfPresent(long pos) {
		return this.getUpdatingChunkIfPresent(pos);
	}

	@NotNull
	@Override
	public ThreadedLevelLightEngine replay$getLightEngine() {
		return this.lightEngine;
	}
}
