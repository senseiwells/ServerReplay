package me.senseiwells.replay.mixin.chunk;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import me.senseiwells.replay.chunk.ChunkRecorder;
import me.senseiwells.replay.ducks.ServerReplay$ChunkRecordable;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Mixin(ChunkHolder.class)
public class ChunkHolderMixin implements ServerReplay$ChunkRecordable {
	@Unique private final Set<ChunkRecorder> replay$recorders = new HashSet<>();

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
		return noPlayers && !this.replay$recorders.isEmpty();
	}

	@Override
	public void replay$addRecorder(ChunkRecorder recorder) {
		this.replay$recorders.add(recorder);
		recorder.incrementChunksLoaded();
	}

	@Override
	public void replay$removeRecorder(ChunkRecorder recorder) {
		this.replay$recorders.add(recorder);
		recorder.decrementChunksLoaded();
	}
}
