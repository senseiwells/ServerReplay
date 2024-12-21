package me.senseiwells.replay.mixin.common;

import me.senseiwells.replay.ServerReplay;
import me.senseiwells.replay.chunk.ChunkRecorder;
import me.senseiwells.replay.chunk.ChunkRecorders;
import me.senseiwells.replay.config.ReplayConfig;
import me.senseiwells.replay.player.PlayerRecorder;
import me.senseiwells.replay.player.PlayerRecorders;
import me.senseiwells.replay.util.processor.RecorderFixerUpper;
import me.senseiwells.replay.util.processor.RecorderRecoverer;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

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
		RecorderRecoverer.tryRecover((MinecraftServer) (Object) this);

		if (ServerReplay.getConfig().getEnabled()) {
			ServerReplay.getConfig().startChunks((MinecraftServer) (Object) this);
		}
	}

	@Inject(
		method = "saveAllChunks",
		at = @At("TAIL")
	)
	private void onSave(
		boolean suppressLog,
		boolean flush,
		boolean forced,
		CallbackInfoReturnable<Boolean> cir
	) {
		ReplayConfig.write(ServerReplay.getConfig());
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
		RecorderFixerUpper.INSTANCE.waitForFixingUp();
	}
}
