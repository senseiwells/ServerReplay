package me.senseiwells.replay.mixin.chunk;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.datafixers.util.Either;
import me.senseiwells.replay.chunk.ChunkRecorder;
import me.senseiwells.replay.ducks.ServerReplay$ChunkRecordable;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Mixin(ChunkHolder.class)
public abstract class ChunkHolderMixin implements ServerReplay$ChunkRecordable {
	@Shadow private volatile CompletableFuture<Either<LevelChunk, ChunkHolder.ChunkLoadingFailure>> fullChunkFuture;

	@Unique private final Set<ChunkRecorder> replay$recorders = new HashSet<>();

	@Shadow public abstract @Nullable LevelChunk getFullChunk();

	@Inject(
		method = "broadcast",
		at = @At("HEAD")
	)
	private void onBroadcast(Packet<?> packet, boolean boundaryOnly, CallbackInfo ci) {
		for (ChunkRecorder recorder : this.replay$recorders) {
			recorder.record(packet);
		}
	}

	@Inject(
		method = "updateFutures",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/server/level/ChunkHolder;updateChunkToSave(Ljava/util/concurrent/CompletableFuture;Ljava/lang/String;)V",
			shift = At.Shift.AFTER,
			ordinal = 0
		)
	)
	private void onChunkLoad(ChunkMap chunkMap, Executor executor, CallbackInfo ci) {
		this.fullChunkFuture.thenAccept(result -> {
			result.ifLeft(chunk -> {
				for (ChunkRecorder recorder : this.getRecorders()) {
					recorder.onChunkLoaded(chunk);
				}
			});
		});
	}

	@Inject(
		method = "updateFutures",
		at = @At(
			value = "INVOKE",
			target = "Ljava/util/concurrent/CompletableFuture;complete(Ljava/lang/Object;)Z",
			ordinal = 0
		)
	)
	private void onChunkUnload(ChunkMap chunkMap, Executor executor, CallbackInfo ci) {
		LevelChunk chunk = this.getFullChunk();
		if (chunk != null) {
			for (ChunkRecorder recorder : this.getRecorders()) {
				recorder.onChunkUnloaded(chunk);
			}
		}
	}

	@Override
	public Collection<ChunkRecorder> replay$getRecorders() {
		return this.replay$recorders;
	}

	@Override
	public void replay$addRecorder(ChunkRecorder recorder) {
		if (this.replay$recorders.add(recorder)) {
			this.fullChunkFuture.thenAccept(result -> {
				result.ifLeft(recorder::onChunkLoaded);
			});

			recorder.addRecordable(this);
		}
	}

	@Override
	public void replay$removeRecorder(ChunkRecorder recorder) {
		if (this.replay$recorders.remove(recorder)) {
			LevelChunk chunk = this.getFullChunk();
			if (chunk != null) {
				recorder.onChunkUnloaded(chunk);
			}

			recorder.removeRecordable(this);
		}
	}

	@Override
	public void replay$removeAllRecorders() {
		LevelChunk chunk = this.getFullChunk();
		for (ChunkRecorder recorder : this.replay$recorders) {
			if (chunk != null) {
				recorder.onChunkUnloaded(chunk);
			}
			recorder.removeRecordable(this);
		}
		this.replay$recorders.clear();
	}
}
