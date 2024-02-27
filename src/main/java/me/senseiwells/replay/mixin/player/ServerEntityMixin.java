package me.senseiwells.replay.mixin.player;

import me.senseiwells.replay.player.PlayerRecorder;
import me.senseiwells.replay.player.PlayerRecorders;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

@Mixin(ServerEntity.class)
public class ServerEntityMixin {
	@Shadow @Mutable @Final private Consumer<Packet<?>> broadcast;

	@Inject(
		method = "<init>",
		at = @At("TAIL")
	)
	private void onInit(
		ServerLevel level,
		Entity entity,
		int updateInterval,
		boolean trackDelta,
		Consumer<Packet<?>> broadcast,
		CallbackInfo ci
	) {
		// I previously had this ModifyArg into TrackedEntity<init>
		// into ServerEntity<init>; however, polymer redirects this
		// constructor, so I need another way of doing this...
		if (entity instanceof ServerPlayer player) {
			Consumer<Packet<?>> original = this.broadcast;
			this.broadcast = packet -> {
				PlayerRecorder recorder = PlayerRecorders.get(player);
				if (recorder != null) {
					recorder.record(packet);
				}
				original.accept(packet);
			};
		}
	}
}
