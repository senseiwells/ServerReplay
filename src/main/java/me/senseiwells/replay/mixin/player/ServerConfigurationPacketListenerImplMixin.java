package me.senseiwells.replay.mixin.player;

import com.mojang.authlib.GameProfile;
import me.senseiwells.replay.recorder.player.PlayerRecorder;
import me.senseiwells.replay.recorder.player.PlayerRecorders;
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
			target = "Lnet/minecraft/server/players/PlayerList;placeNewPlayer(Lnet/minecraft/network/Connection;Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/server/network/CommonListenerCookie;)V",
			shift = At.Shift.BEFORE
		)
	)
	@SuppressWarnings("DiscouragedShift")
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
