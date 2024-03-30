package me.senseiwells.replay.ducks;

import me.senseiwells.replay.viewer.ReplayViewer;
import net.minecraft.network.protocol.Packet;
import org.jetbrains.annotations.Nullable;

public interface ServerReplay$ReplayViewable {
	void replay$setReplayViewer(@Nullable ReplayViewer viewer);

	@Nullable ReplayViewer replay$getReplayViewer();

	void replay$sendReplayViewerPacket(Packet<?> packet);
}
