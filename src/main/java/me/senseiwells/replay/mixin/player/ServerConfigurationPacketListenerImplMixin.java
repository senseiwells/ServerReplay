package me.senseiwells.replay.mixin.player;

import com.mojang.authlib.GameProfile;
import me.senseiwells.replay.player.PlayerRecorder;
import me.senseiwells.replay.player.PlayerRecorders;
import net.minecraft.network.protocol.configuration.ServerboundFinishConfigurationPacket;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerConfigurationPacketListenerImpl.class)
public class ServerConfigurationPacketListenerImplMixin {
	@Shadow @Final private GameProfile gameProfile;

	@Inject(
		method = "handleConfigurationFinished",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/server/players/PlayerList;getPlayerForLogin(Lcom/mojang/authlib/GameProfile;Lnet/minecraft/server/level/ClientInformation;)Lnet/minecraft/server/level/ServerPlayer;",
			shift = At.Shift.BEFORE
		)
	)
	private void beforePlacePlayer(
		ServerboundFinishConfigurationPacket serverboundFinishConfigurationPacket,
		CallbackInfo ci
	) {
		PlayerRecorder recorder = PlayerRecorders.getByUUID(this.gameProfile.getId());
		if (recorder != null) {
			recorder.afterConfigure();
		}
	}
}
