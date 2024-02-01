package me.senseiwells.replay.mixin.chunk;

import com.llamalad7.mixinextras.sugar.Local;
import me.senseiwells.replay.chunk.ChunkRecordable;
import me.senseiwells.replay.chunk.ChunkRecorder;
import me.senseiwells.replay.chunk.ChunkRecorders;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.BooleanSupplier;

@Mixin(ChunkMap.class)
public class ChunkMapMixin {
	@Shadow @Final ServerLevel level;

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
		ChunkPos pos = holder.getPos();
		for (ChunkRecorder recorder : ChunkRecorders.containing(this.level.dimension(), pos)) {
			((ChunkRecordable) holder).addRecorder(recorder);
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
		((ChunkRecordable) holder).removeAllRecorders();
	}
}
