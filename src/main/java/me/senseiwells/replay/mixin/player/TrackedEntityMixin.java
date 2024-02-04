package me.senseiwells.replay.mixin.player;

import com.llamalad7.mixinextras.sugar.Local;
import me.senseiwells.replay.player.PlayerRecorder;
import me.senseiwells.replay.player.PlayerRecorders;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

@Mixin(ChunkMap.TrackedEntity.class)
public class TrackedEntityMixin {
	@Shadow @Final Entity entity;
	@Shadow @Final ServerEntity serverEntity;

	@ModifyArg(
		method = "<init>",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/server/level/ServerEntity;<init>(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/Entity;IZLjava/util/function/Consumer;)V"
		),
		index = 4
	)
	private Consumer<Packet<?>> onCreateBroadcast(
		Consumer<Packet<?>> broadcast,
		@Local(argsOnly = true) Entity entity
	) {
		if (entity instanceof ServerPlayer player) {
			return packet -> {
				PlayerRecorder recorder = PlayerRecorders.get(player);
				if (recorder != null) {
					recorder.record(packet);
				}
				broadcast.accept(packet);
			};
		}
		return broadcast;
	}

	@Inject(
		method = "<init>",
		at = @At("TAIL")
	)
	private void onCreated(
		ChunkMap chunkMap,
		Entity entity,
		int range,
		int updateInterval,
		boolean trackDelta,
		CallbackInfo ci
	) {
		if (entity instanceof ServerPlayer player) {
			PlayerRecorder current = PlayerRecorders.get(player);
			if (current != null) {
				current.spawnPlayer(this.serverEntity);
			}
		}
	}

	@Inject(
		method = "broadcastRemoved",
		at = @At("TAIL")
	)
	private void onRemoved(CallbackInfo ci) {
		if (this.entity instanceof ServerPlayer player) {
			PlayerRecorder current = PlayerRecorders.get(player);
			if (current != null) {
				current.removePlayer(player);
			}
		}
	}
}
