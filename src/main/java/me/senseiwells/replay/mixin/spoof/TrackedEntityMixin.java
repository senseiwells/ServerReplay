package me.senseiwells.replay.mixin.spoof;

import me.senseiwells.replay.ducks.ServerReplay$TrackedEntityInvoker;
import net.minecraft.server.level.ChunkMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(ChunkMap.TrackedEntity.class)
public abstract class TrackedEntityMixin implements ServerReplay$TrackedEntityInvoker {
	@Shadow protected abstract int getEffectiveRange();

	@Override
	public int replay$getEffectiveRange() {
		return this.getEffectiveRange();
	}
}
