package me.senseiwells.replay.mixin.chunk;

import com.llamalad7.mixinextras.sugar.Local;
import me.senseiwells.replay.chunk.ChunkRecorder;
import me.senseiwells.replay.chunk.ChunkRecorders;
import me.senseiwells.replay.config.ReplayConfig;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.BooleanSupplier;

@Mixin(ChunkMap.class)
public class ChunkMapMixin {
	@Inject(
		method = "updateChunkScheduling",
		at = @At(
			value = "INVOKE",
			target = "Lit/unimi/dsi/fastutil/longs/Long2ObjectLinkedOpenHashMap;put(JLjava/lang/Object;)Ljava/lang/Object;",
			remap = false
		)
	)
	private void onUpdateChunkMap(
		long chunkPos,
		int newLevel,
		ChunkHolder holder,
		int oldLevel,
		CallbackInfoReturnable<ChunkHolder> cir
	) {
		ChunkPos pos = new ChunkPos(chunkPos);
		for (ChunkRecorder recorder : ChunkRecorders.all()) {
			if (recorder.getChunks().contains(pos)) {
				recorder.unpause(pos);
			}
		}
	}

	@Inject(
		method = "processUnloads",
		at = @At(
			value = "INVOKE",
			target = "Lit/unimi/dsi/fastutil/longs/Long2ObjectLinkedOpenHashMap;put(JLjava/lang/Object;)Ljava/lang/Object;",
			remap = false
		)
	)
	private void onUnloadChunk(
		BooleanSupplier hasMoreTime,
		CallbackInfo ci,
		@Local ChunkHolder holder
	) {
		if (ReplayConfig.getSkipWhenChunksUnloaded()) {
			for (ChunkRecorder recorder : ChunkRecorders.all()) {
				recorder.pause(holder.getPos());
			}
		}
	}
}
