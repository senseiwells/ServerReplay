package me.senseiwells.replay.mixin.common;

import me.senseiwells.replay.ServerReplay;
import me.senseiwells.replay.recorder.chunk.ChunkRecorder;
import me.senseiwells.replay.recorder.chunk.ChunkRecorders;
import me.senseiwells.replay.recorder.player.PlayerRecorder;
import me.senseiwells.replay.recorder.player.PlayerRecorders;
import me.senseiwells.replay.util.processor.RecorderFixerUpper;
import me.senseiwells.replay.processor.RecorderRecoverer;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftServer.class)
public class MinecraftServerMixin {
	@Inject(
		method = "runServer",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/server/MinecraftServer;buildServerStatus()Lnet/minecraft/network/protocol/status/ServerStatus;",
			shift = At.Shift.AFTER
		)
	)
	private void onServerLoaded(CallbackInfo ci) {
		MinecraftServer instance = (MinecraftServer) (Object) this;
		RecorderRecoverer.tryRecover(instance);

		if (ServerReplay.getConfig().getEnabled()) {
			ServerReplay.getConfig().startChunks(instance);
		}
	}

	@Inject(
		method = "stopServer",
		at = @At("TAIL")
	)
	private void onServerStopped(CallbackInfo ci) {
		for (PlayerRecorder recorder : PlayerRecorders.recorders()) {
			recorder.stop();
		}

		for (ChunkRecorder recorder : ChunkRecorders.recorders()) {
			recorder.stop();
		}

		RecorderRecoverer.INSTANCE.waitForRecovering();
	}
}
