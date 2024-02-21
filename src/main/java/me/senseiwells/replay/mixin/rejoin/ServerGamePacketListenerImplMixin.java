package me.senseiwells.replay.mixin.rejoin;

import io.netty.util.concurrent.GenericFutureListener;
import me.senseiwells.replay.ducks.ServerReplay$PackTracker;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundResourcePackPacket;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public class ServerGamePacketListenerImplMixin implements ServerReplay$PackTracker {
	// We need to keep track of what pack a player has...
	// We don't really care if the player accepts / declines them, we'll record them anyway.
	@Unique @Nullable private ClientboundResourcePackPacket replay$pack = null;

	@Inject(
		method = "send(Lnet/minecraft/network/protocol/Packet;Lio/netty/util/concurrent/GenericFutureListener;)V",
		at = @At("HEAD")
	)
	private void onSendPacket(
		Packet<?> packet,
		@Nullable GenericFutureListener<?> packetSendListener,
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
