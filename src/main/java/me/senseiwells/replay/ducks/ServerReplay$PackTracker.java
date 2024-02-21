package me.senseiwells.replay.ducks;

import net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket;

import java.util.Collection;

public interface ServerReplay$PackTracker {
	void replay$addPacks(Collection<ClientboundResourcePackPushPacket> packs);

	Collection<ClientboundResourcePackPushPacket> replay$getPacks();
}
