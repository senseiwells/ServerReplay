package me.senseiwells.replay.mixin.chunk;

import me.senseiwells.replay.chunk.ChunkRecorder;
import me.senseiwells.replay.ducks.ServerReplay$ChunkRecordable;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.level.ChunkHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

@Mixin(ChunkHolder.class)
public class ChunkHolderMixin implements ServerReplay$ChunkRecordable {
	@Unique private final Set<ChunkRecorder> replay$recorders = new HashSet<>();

	@Inject(
		method = "broadcast",
		at = @At("HEAD")
	)
	private void onBroadcast(Packet<?> packet, boolean boundaryOnly, CallbackInfo ci) {
		for (ChunkRecorder recorder : this.replay$recorders) {
			recorder.record(packet);
		}
	}

	@Override
	public Collection<ChunkRecorder> replay$getRecorders() {
		return this.replay$recorders;
	}

	@Override
	public void replay$addRecorder(ChunkRecorder recorder) {
		if (this.replay$recorders.add(recorder)) {
			recorder.incrementChunksLoaded();
			recorder.addRecordable(this);
		}
	}

	@Override
	public void replay$removeRecorder(ChunkRecorder recorder) {
		if (this.replay$recorders.remove(recorder)) {
			recorder.decrementChunksLoaded();
			recorder.removeRecordable(this);
		}
	}

	@Override
	public void replay$removeAllRecorders() {
		for (ChunkRecorder recorder : this.replay$recorders) {
			recorder.decrementChunksLoaded();
			recorder.removeRecordable(this);
		}
		this.replay$recorders.clear();
	}
}
