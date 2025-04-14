package me.senseiwells.replay.mixin.compat.c2me;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import me.senseiwells.replay.ducks.ChunkRecordable;
import me.senseiwells.replay.recorder.chunk.ChunkRecorder;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkResult;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

@Mixin(ChunkHolder.class)
public abstract class ChunkHolderMixin extends GenerationChunkHolder implements ChunkRecordable {
	@Unique private final Set<ChunkRecorder> replay$recorders = new HashSet<>();

	public ChunkHolderMixin(ChunkPos pos) {
		super(pos);
	}

	@Shadow public abstract CompletableFuture<ChunkResult<LevelChunk>> getFullChunkFuture();

	@Inject(
		method = "broadcast",
		at = @At("HEAD")
	)
	private void onBroadcast(List<ServerPlayer> players, Packet<?> packet, CallbackInfo ci) {
		for (ChunkRecorder recorder : this.replay$recorders) {
			recorder.record(packet);
		}
	}

	@ModifyExpressionValue(
		method = "broadcastChanges",
		at = @At(
			value = "INVOKE",
			target = "Ljava/util/List;isEmpty()Z",
			remap = false
		)
	)
	private boolean shouldSkipBroadcasting(boolean noPlayers) {
		return noPlayers && this.replay$recorders.isEmpty();
	}

	@Override
	public Collection<ChunkRecorder> replay$getRecorders() {
		return this.replay$recorders;
	}

	@Override
	public void replay$addRecorder(ChunkRecorder recorder) {
		CompletableFuture<ChunkResult<LevelChunk>> future = this.getFullChunkFuture();
		if (future.isDone() && !future.getNow(ChunkHolder.UNLOADED_LEVEL_CHUNK).isSuccess()) {
			return;
		}

		if (this.replay$recorders.add(recorder)) {
			this.getFullChunkFuture().thenAccept(result -> {
				result.ifSuccess(recorder::onChunkLoaded);
			});

			recorder.addRecordable(this);
		}
	}

	@Override
	public void replay$removeRecorder(ChunkRecorder recorder) {
		if (this.replay$recorders.remove(recorder)) {
			ChunkResult<LevelChunk> chunk = this.getFullChunkFuture().getNow(ChunkHolder.UNLOADED_LEVEL_CHUNK);
			recorder.onChunkUnloaded(this.pos, chunk.orElse(null));
			recorder.removeRecordable(this);
		}
	}

	@Override
	public void replay$removeAllRecorders() {
		LevelChunk chunk = this.getFullChunkFuture().getNow(ChunkHolder.UNLOADED_LEVEL_CHUNK).orElse(null);
		for (ChunkRecorder recorder : this.replay$recorders) {
			recorder.onChunkUnloaded(this.pos, chunk);
			recorder.removeRecordable(this);
		}
		this.replay$recorders.clear();
	}
}
