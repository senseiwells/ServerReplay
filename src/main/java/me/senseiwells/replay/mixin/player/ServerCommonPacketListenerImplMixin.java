package me.senseiwells.replay.mixin.player;

import com.mojang.authlib.GameProfile;
import me.senseiwells.replay.ducks.ServerReplay$ReplayViewable;
import me.senseiwells.replay.player.PlayerRecorder;
import me.senseiwells.replay.player.PlayerRecorders;
import me.senseiwells.replay.viewer.ReplayViewer;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerCommonPacketListenerImpl.class)
public abstract class ServerCommonPacketListenerImplMixin {
	@Shadow protected abstract GameProfile playerProfile();

	@Inject(
		method = "send(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketSendListener;)V",
		at = @At("HEAD")
	)
	private void onPacket(Packet<?> packet, PacketSendListener listener, CallbackInfo ci) {
		PlayerRecorder recorder = PlayerRecorders.getByUUID(this.playerProfile().getId());
		if (recorder != null) {
			recorder.record(packet);
		}
	}

	@Inject(
		method = "onDisconnect",
		at = @At("TAIL")
	)
	private void onDisconnect(Component reason, CallbackInfo ci) {
		PlayerRecorder recorder = PlayerRecorders.getByUUID(this.playerProfile().getId());
		if (recorder != null) {
			recorder.stop();
		}

		if (this instanceof ServerReplay$ReplayViewable viewable) {
			ReplayViewer viewer = viewable.replay$getReplayViewer();
			if (viewer != null) {
				viewer.close();
			}
		}
	}
}
