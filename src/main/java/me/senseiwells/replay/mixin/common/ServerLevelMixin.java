package me.senseiwells.replay.mixin.common;

import com.llamalad7.mixinextras.sugar.Local;
import me.senseiwells.replay.chunk.ChunkRecorder;
import me.senseiwells.replay.chunk.ChunkRecorders;
import me.senseiwells.replay.player.PlayerRecorder;
import me.senseiwells.replay.player.PlayerRecorders;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.protocol.game.ClientboundBlockDestructionPacket;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.WritableLevelData;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Supplier;

@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin extends Level {
	protected ServerLevelMixin(WritableLevelData levelData, ResourceKey<Level> dimension, RegistryAccess registryAccess, Holder<DimensionType> dimensionTypeRegistration, Supplier<ProfilerFiller> profiler, boolean isClientSide, boolean isDebug, long biomeZoomSeed, int maxChainedNeighborUpdates) {
		super(levelData, dimension, registryAccess, dimensionTypeRegistration, profiler, isClientSide, isDebug, biomeZoomSeed, maxChainedNeighborUpdates);
	}

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
		 for (ChunkRecorder recorder : ChunkRecorders.containing(this.dimension(), chunkPos)) {
			 recorder.record(new ClientboundBlockDestructionPacket(breakerId, pos, progress));
		 }
	}

	@Inject(
		method = "explode",
		at = @At("TAIL")
	)
	private void onExplode(
		Entity entity,
		DamageSource source,
		ExplosionDamageCalculator calculator,
		double posX,
		double posY,
		double posZ,
		float radius,
		boolean causeFire,
		Level.ExplosionInteraction interaction,
		CallbackInfoReturnable<Explosion> cir,
		@Local Explosion explosion
	) {
		ChunkPos chunkPos = new ChunkPos(BlockPos.containing(posX, posY, posZ));
		for (ChunkRecorder recorder : ChunkRecorders.containing(this.dimension(), chunkPos)) {
			recorder.record(new ClientboundExplodePacket(
				posX, posY, posZ, radius,
				explosion.getToBlow(),
				// Knock-back
				Vec3.ZERO
			));
		}
	}

	@Inject(
		method = "sendParticles(Lnet/minecraft/core/particles/ParticleOptions;DDDIDDDD)I",
		at = @At("TAIL")
	)
	private <T extends ParticleOptions> void onSendParticles(
		T type,
		double posX,
		double posY,
		double posZ,
		int particleCount,
		double xOffset,
		double yOffset,
		double zOffset,
		double speed,
		CallbackInfoReturnable<Integer> cir,
		@Local ClientboundLevelParticlesPacket packet
	) {
		ChunkPos chunkPos = new ChunkPos(BlockPos.containing(posX, posY, posZ));
		for (ChunkRecorder recorder : ChunkRecorders.containing(this.dimension(), chunkPos)) {
			recorder.record(packet);
		}
	}
}
