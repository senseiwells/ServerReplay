package me.senseiwells.replay.mixin.compat.vmp;

import com.ishland.vmp.mixins.playerwatching.optimize_nearby_entity_tracking_lookups.MixinThreadedAnvilChunkStorageEntityTracker;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import me.senseiwells.replay.chunk.ChunkRecordable;
import me.senseiwells.replay.player.PlayerRecorders;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = ChunkMap.TrackedEntity.class, priority = 1100)
public class TrackedEntityMixin {
	@Shadow
	@Final
	Entity entity;

	@Dynamic(mixin = MixinThreadedAnvilChunkStorageEntityTracker.class)
	@ModifyExpressionValue(
		method = "tryTick",
		at = @At(
			value = "INVOKE",
			target = "Ljava/util/Set;isEmpty()Z"
		),
		remap = false
	)
	private boolean shouldNotTick(boolean original) {
		if (!original) {
			return false;
		}
		if (!((ChunkRecordable) this).getRecorders().isEmpty()) {
			return false;
		}
		if (this.entity instanceof ServerPlayer player) {
			return !PlayerRecorders.has(player);
		}
		return true;
	}
}
