package me.senseiwells.replay.mixin.rejoin;

import com.llamalad7.mixinextras.injector.WrapWithCondition;
import me.senseiwells.replay.rejoin.RejoinedReplayPlayer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ServerPlayer.class)
public class ServerPlayerMixin {
	@WrapWithCondition(
		method = "<init>",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/server/players/PlayerList;getPlayerAdvancements(Lnet/minecraft/server/level/ServerPlayer;)Lnet/minecraft/server/PlayerAdvancements;"
		)
	)
	private boolean shouldUpdateAdvancements(PlayerList instance, ServerPlayer player) {
		return !(player instanceof RejoinedReplayPlayer);
	}
}
