package me.senseiwells.replay.mixin;

import me.senseiwells.replay.player.PlayerRecorder;
import me.senseiwells.replay.player.PlayerRecorders;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public class ServerGamePacketListenerImplMixin {
	@Shadow public ServerPlayer player;

	@Inject(
		method = "onDisconnect",
		at = @At("HEAD")
	)
	private void onDisconnect(Component reason, CallbackInfo ci) {
		PlayerRecorder recorder = PlayerRecorders.remove(this.player);
		if (recorder != null) {
			recorder.disconnect();
		}
	}

	@Inject(
		method = "send(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketSendListener;)V",
		at = @At("HEAD")
	)
	private void onPacket(Packet<?> packet, PacketSendListener listener, CallbackInfo ci) {
		PlayerRecorder recorder = PlayerRecorders.get(this.player);
		if (recorder != null) {
			recorder.record(packet);
		}
	}
}
