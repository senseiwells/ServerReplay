package me.senseiwells.replay.mixin.chunk;

import me.senseiwells.replay.chunk.ChunkRecordable;
import me.senseiwells.replay.chunk.ChunkRecorders;
import me.senseiwells.replay.util.MathUtils;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.dimension.end.EndDragonFight;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EndDragonFight.class)
public class EndDragonFightMixin {
	@Shadow @Final private ServerBossEvent dragonEvent;

	@Shadow @Final private ServerLevel level;

	@Inject(
		method = "updatePlayers",
		at = @At("TAIL")
	)
	private void onUpdate(CallbackInfo ci) {
		int fightRange = 192;
		BoundingBox box = MathUtils.createBoxAround(Vec3i.ZERO, fightRange);
		ChunkRecorders.updateRecordable((ChunkRecordable) this.dragonEvent, this.level.dimension(), box);
	}
}
