package me.senseiwells.replay.mixin.player;

import me.senseiwells.replay.ServerReplay;
import me.senseiwells.replay.config.predicates.ReplayPlayerContext;
import me.senseiwells.replay.player.PlayerRecorders;
import me.senseiwells.replay.recorder.ReplayRecorder;
import net.minecraft.network.Connection;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerList.class)
public class PlayerListMixin {
	@Inject(
		method = "placeNewPlayer",
		at = @At("HEAD")
	)
	private void onPlaceNewPlayer(
		Connection connection,
		ServerPlayer player,
		CallbackInfo ci
	) {
		if (ServerReplay.config.getEnabled()) {
			ReplayPlayerContext context = ReplayPlayerContext.Companion.of(player);
			if (ServerReplay.config.shouldRecordPlayer(context)) {
				ReplayRecorder recorder = PlayerRecorders.create(player);
				recorder.logStart();
				recorder.afterLogin();
			}
		}
	}
}
