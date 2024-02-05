package me.senseiwells.replay.mixin.chunk;

import me.senseiwells.replay.chunk.ChunkRecordable;
import me.senseiwells.replay.chunk.ChunkRecorder;
import me.senseiwells.replay.chunk.ChunkRecorders;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collection;

@Mixin(Raid.class)
public class RaidMixin {
	@Shadow private BlockPos center;

	@Shadow @Final private ServerLevel level;

	@Shadow @Final private ServerBossEvent raidEvent;

	@Inject(
		method = "updatePlayers",
		at = @At("TAIL")
	)
	private void onUpdate(CallbackInfo ci) {
		int raidRange = 96;
		int centerX = this.center.getX();
		int centerY = this.center.getY();
		int centerZ = this.center.getZ();
		BoundingBox box = new BoundingBox(
			centerX - raidRange, centerY - raidRange, centerZ - raidRange,
			centerX + raidRange, centerY + raidRange, centerZ + raidRange
		);

		ChunkRecorders.updateRecordable((ChunkRecordable) this.raidEvent, this.level.dimension(), box);
	}

	@Inject(
		method = "playSound",
		at = @At("TAIL")
	)
	private void onPlayerSound(BlockPos pos, CallbackInfo ci) {
		Collection<ChunkRecorder> recorders = ((ChunkRecordable) this.raidEvent).getRecorders();
		if (!recorders.isEmpty()) {
			ClientboundSoundPacket packet = new ClientboundSoundPacket(
				SoundEvents.RAID_HORN,
				SoundSource.NEUTRAL,
				this.center.getX(),
				this.center.getY(),
				this.center.getZ(),
				64,
				1.0F
			);
			for (ChunkRecorder recorder : recorders) {
				recorder.record(packet);
			}
		}
	}
}
