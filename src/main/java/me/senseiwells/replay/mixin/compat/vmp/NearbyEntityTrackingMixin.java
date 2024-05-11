package me.senseiwells.replay.mixin.compat.vmp;

import com.ishland.vmp.common.playerwatching.NearbyEntityTracking;
import com.llamalad7.mixinextras.sugar.Local;
import me.senseiwells.replay.chunk.ChunkRecordable;
import me.senseiwells.replay.chunk.ChunkRecorders;
import me.senseiwells.replay.mixin.rejoin.ChunkMapAccessor;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = NearbyEntityTracking.class, remap = false)
public class NearbyEntityTrackingMixin {
    @Inject(
        method = "tick",
        at = @At(
            value = "INVOKE_ASSIGN",
            target = "Lcom/ishland/vmp/common/playerwatching/ServerPlayerEntityExtension;vmpTracking$isPositionUpdated()Z",
            shift = At.Shift.AFTER
        ),
        remap = false
    )
    @SuppressWarnings("resource")
    private void onPlayerTrackingTick(
        @Coerce Object ticketManager,
        CallbackInfo ci,
        @Local(name = "player") ServerPlayer player,
        @Local(name = "isPlayerPositionUpdated") boolean positionUpdated
    ) {
        if (positionUpdated) {
            ServerLevel level = player.serverLevel();
            ChunkMap map = level.getChunkSource().chunkMap;
            ChunkMap.TrackedEntity tracked = ((ChunkMapAccessor) map).getEntityMap().get(player.getId());
            ChunkRecorders.updateRecordable((ChunkRecordable) tracked, level.dimension(), player.chunkPosition());
        }
    }
}
