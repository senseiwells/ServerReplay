package me.senseiwells.replay.mixin.rejoin;

import me.senseiwells.replay.ducks.ServerReplay$PackTracker;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundResourcePackPopPacket;
import net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Mixin(ServerCommonPacketListenerImpl.class)
public class ServerCommonPacketListenerImplMixin implements ServerReplay$PackTracker {
	// We need to keep track of what packs a player has...
	// We don't really care if the player accepts / declines them, we'll record them anyway.
	@Unique private final Map<UUID, ClientboundResourcePackPushPacket> replay$packs = new ConcurrentHashMap<>();

	@Inject(
		method = "send(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketSendListener;)V",
		at = @At("HEAD")
	)
	private void onSendPacket(
		Packet<?> packet,
		@Nullable PacketSendListener packetSendListener,
		CallbackInfo ci
	) {
		if (packet instanceof ClientboundResourcePackPushPacket resources) {
			this.replay$packs.put(resources.id(), resources);
			return;
		}
		if (packet instanceof ClientboundResourcePackPopPacket resources) {
			Optional<UUID> uuid = resources.id();
			if (uuid.isPresent()) {
				this.replay$packs.remove(uuid.get());
			} else {
				this.replay$packs.clear();
			}
		}
	}

	@Override
	public void replay$addPacks(Collection<ClientboundResourcePackPushPacket> packs) {
		for (ClientboundResourcePackPushPacket packet : packs) {
			this.replay$packs.put(packet.id(), packet);
		}
	}

	@Override
	public Collection<ClientboundResourcePackPushPacket> replay$getPacks() {
		return this.replay$packs.values();
	}
}
