package me.senseiwells.replay.mixin.rejoin;

import me.senseiwells.replay.ducks.ServerReplay$PackTracker;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundResourcePackPacket;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerCommonPacketListenerImpl.class)
public class ServerCommonPacketListenerImplMixin implements ServerReplay$PackTracker {
	// We need to keep track of what pack a player has...
	// We don't really care if the player accepts / declines them, we'll record them anyway.
	@Unique @Nullable private ClientboundResourcePackPacket replay$pack = null;

	@Inject(
		method = "send(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketSendListener;)V",
		at = @At("HEAD")
	)
	private void onSendPacket(
		Packet<?> packet,
		@Nullable PacketSendListener packetSendListener,
		CallbackInfo ci
	) {
		if (packet instanceof ClientboundResourcePackPacket resources) {
			this.replay$pack = resources;
		}
	}

	@Override
	public void replay$setPack(@Nullable ClientboundResourcePackPacket pack) {
		this.replay$pack = pack;
	}

	@Override
	@Nullable
	public ClientboundResourcePackPacket replay$getPack() {
		return this.replay$pack;
	}
}
