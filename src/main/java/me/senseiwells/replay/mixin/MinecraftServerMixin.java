package me.senseiwells.replay.mixin;

import me.senseiwells.replay.config.ReplayConfig;
import me.senseiwells.replay.player.PlayerRecorder;
import me.senseiwells.replay.player.PlayerRecorders;
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
		ReplayConfig.read();
	}

	@Inject(
		method = "stopServer",
		at = @At("TAIL")
	)
	private void onServerStopped(CallbackInfo ci) {
		ReplayConfig.write();

		for (PlayerRecorder recorder : PlayerRecorders.all()) {
			recorder.stop();
		}
	}
}
