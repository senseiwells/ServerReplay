package me.senseiwells.replay.mixin.player;

import com.mojang.authlib.GameProfile;
import me.senseiwells.replay.ServerReplay;
import me.senseiwells.replay.config.predicates.ReplayPlayerContext;
import me.senseiwells.replay.player.PlayerRecorders;
import me.senseiwells.replay.recorder.ReplayRecorder;
import net.minecraft.network.protocol.login.ServerboundLoginAcknowledgedPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerLoginPacketListenerImpl.class)
public class ServerLoginPacketListenerImplMixin {
	@Nullable @Shadow private GameProfile authenticatedProfile;

	@Shadow @Final MinecraftServer server;

	@Inject(
		method = "handleLoginAcknowledgement",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/server/network/CommonListenerCookie;createInitial(Lcom/mojang/authlib/GameProfile;Z)Lnet/minecraft/server/network/CommonListenerCookie;"
		)
	)
	private void onLoggedIn(
		ServerboundLoginAcknowledgedPacket serverboundLoginAcknowledgedPacket,
		CallbackInfo ci
	) {
		GameProfile profile = this.authenticatedProfile;
		if (profile != null && ServerReplay.getConfig().getEnabled()) {
			if (ServerReplay.getConfig().shouldRecordPlayer(new ReplayPlayerContext(this.server, profile, null))) {
				ReplayRecorder recorder = PlayerRecorders.create(this.server, profile);
				recorder.logStart();
				recorder.afterLogin();
			}
		}
	}
}
