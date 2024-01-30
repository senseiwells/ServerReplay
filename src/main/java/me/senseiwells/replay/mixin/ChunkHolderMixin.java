package me.senseiwells.replay.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import me.senseiwells.replay.chunk.ChunkRecorder;
import me.senseiwells.replay.chunk.ChunkRecorders;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(ChunkHolder.class)
public class ChunkHolderMixin {
	@Shadow @Final ChunkPos pos;

	@Inject(
		method = "broadcast",
		at = @At("HEAD")
	)
	private void onBroadcast(List<ServerPlayer> players, Packet<?> packet, CallbackInfo ci) {
		for (ChunkRecorder recorder : ChunkRecorders.all()) {
			if (recorder.getChunks().contains(this.pos)) {
				recorder.record(packet);
			}
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
		if (!noPlayers) {
			return false;
		}
		for (ChunkRecorder recorder : ChunkRecorders.all()) {
			if (recorder.getChunks().contains(this.pos)) {
				return false;
			}
		}
		return true;
	}
}
