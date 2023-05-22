package me.senseiwells.replay.mixin;

import me.senseiwells.replay.player.PlayerRecorder;
import me.senseiwells.replay.player.PlayerRecorders;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.protocol.game.ClientboundBlockDestructionPacket;
import net.minecraft.network.protocol.game.ClientboundLevelEventPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin {
	@Shadow @Nullable public abstract Entity getEntity(int id);

	@Inject(
		method = "destroyBlockProgress",
		at = @At("TAIL")
	)
	private void onDestroyBlockProgress(int breakerId, BlockPos pos, int progress, CallbackInfo ci) {
		 Entity breaker = this.getEntity(breakerId);
		 if (breaker instanceof ServerPlayer player) {
			 PlayerRecorder recorder = PlayerRecorders.get(player);
			 if (recorder != null) {
				 recorder.record(new ClientboundBlockDestructionPacket(breakerId, pos, progress));
			 }
		 }
	}

	@Inject(
		method = "playSeededSound(Lnet/minecraft/world/entity/player/Player;DDDLnet/minecraft/core/Holder;Lnet/minecraft/sounds/SoundSource;FFJ)V",
		at = @At("HEAD")
	)
	private void onPlaySound(
		@Nullable Player player,
		double x,
		double y,
		double z,
		Holder<SoundEvent> sound,
		SoundSource source,
		float volume,
		float pitch,
		long seed,
		CallbackInfo ci
	) {
		ClientboundSoundPacket packet = new ClientboundSoundPacket(sound, source, x, y, z, volume, pitch, seed);
		for (PlayerRecorder recorder : PlayerRecorders.all()) {
			if (recorder.getPlayer() != player) {
				recorder.record(packet);
			}
		}
	}

	@Inject(
		method = "levelEvent",
		at = @At("HEAD")
	)
	private void onLevelEvent(@Nullable Player player, int type, BlockPos pos, int data, CallbackInfo ci) {
		ClientboundLevelEventPacket packet = new ClientboundLevelEventPacket(type, pos, data, false);
		for (PlayerRecorder recorder : PlayerRecorders.all()) {
			if (recorder.getPlayer() != player) {
				recorder.record(packet);
			}
		}
	}
}
