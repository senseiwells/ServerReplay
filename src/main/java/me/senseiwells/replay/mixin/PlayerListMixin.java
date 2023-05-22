package me.senseiwells.replay.mixin;

import me.senseiwells.replay.ServerReplay;
import me.senseiwells.replay.player.PlayerRecorder;
import me.senseiwells.replay.player.PlayerRecorders;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.login.ClientboundGameProfilePacket;
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
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;send(Lnet/minecraft/network/protocol/Packet;)V",
			ordinal = 0,
			shift = At.Shift.BEFORE
		)
	)
	private void onPlayerJoin(Connection netManager, ServerPlayer player, CallbackInfo ci) {
		if (PlayerRecorders.shouldRecordPlayer(player)) {
			ServerReplay.logger.info("Started to record player '{}'", player.getScoreboardName());
			PlayerRecorder recorder = PlayerRecorders.create(player);
			// This is sent to the player before the server creates
			// the GamePacketListenerImpl, so we need to "re-send" it.
			recorder.record(new ClientboundGameProfilePacket(player.getGameProfile()));
		}
	}
}
