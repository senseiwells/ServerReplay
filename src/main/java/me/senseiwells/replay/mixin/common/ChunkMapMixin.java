package me.senseiwells.replay.mixin.common;

import me.senseiwells.replay.player.PlayerRecorder;
import me.senseiwells.replay.player.PlayerRecorders;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChunkMap.class)
public class ChunkMapMixin {
	@Inject(
		method = "broadcast",
		at = @At("HEAD")
	)
	private void onBroadcast(Entity entity, Packet<?> packet, CallbackInfo ci) {
		if (entity instanceof ServerPlayer player) {
			PlayerRecorder recorder = PlayerRecorders.get(player);
			if (recorder != null) {
				recorder.record(packet);
			}
		}
	}
}
