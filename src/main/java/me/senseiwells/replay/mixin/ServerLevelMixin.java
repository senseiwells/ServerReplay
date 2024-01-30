package me.senseiwells.replay.mixin;

import me.senseiwells.replay.chunk.ChunkRecorder;
import me.senseiwells.replay.chunk.ChunkRecorders;
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
import net.minecraft.world.level.ChunkPos;
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

		 ChunkPos chunkPos = new ChunkPos(pos);
		 for (ChunkRecorder recorder : ChunkRecorders.all()) {
			 if (recorder.getChunks().contains(chunkPos)) {
				 recorder.record(new ClientboundBlockDestructionPacket(breakerId, pos, progress));
			 }
		 }
	}
}
