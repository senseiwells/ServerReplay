package me.senseiwells.replay.mixin.compat.carpet;

import carpet.patches.NetHandlerPlayServerFake;
import me.senseiwells.replay.player.PlayerRecorder;
import me.senseiwells.replay.player.PlayerRecorders;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(NetHandlerPlayServerFake.class)
public class NetHandlerPlayServerFakeMixin extends ServerGamePacketListenerImpl {
	public NetHandlerPlayServerFakeMixin(MinecraftServer server, Connection connection, ServerPlayer serverPlayer) {
		super(server, connection, serverPlayer);
	}

	@Inject(
		method = "send",
		at = @At("HEAD")
	)
	private void onSendPacket(Packet<?> packet, CallbackInfo ci) {
		PlayerRecorder recorder = PlayerRecorders.get(this.player);
		if (recorder != null) {
			recorder.record(packet);
		}
	}
}
