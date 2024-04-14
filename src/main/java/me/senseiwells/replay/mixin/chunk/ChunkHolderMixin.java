package me.senseiwells.replay.mixin.chunk;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import me.senseiwells.replay.chunk.ChunkRecorder;
import me.senseiwells.replay.ducks.ServerReplay$ChunkRecordable;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
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

@Mixin(ChunkHolder.class)
public abstract class ChunkHolderMixin implements ServerReplay$ChunkRecordable {
	@Unique private final Set<ChunkRecorder> replay$recorders = new HashSet<>();

	@Shadow @Final ChunkPos pos;

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
		if (this.replay$recorders.add(recorder)) {
			recorder.onChunkLoaded(this.pos);
			recorder.addRecordable(this);
		}
	}

	@Override
	public void replay$removeRecorder(ChunkRecorder recorder) {
		if (this.replay$recorders.remove(recorder)) {
			recorder.onChunkUnloaded(this.pos);
			recorder.removeRecordable(this);
		}
	}

	@Override
	public void replay$removeAllRecorders() {
		for (ChunkRecorder recorder : this.replay$recorders) {
			recorder.onChunkUnloaded(this.pos);
			recorder.removeRecordable(this);
		}
		this.replay$recorders.clear();
	}
}
