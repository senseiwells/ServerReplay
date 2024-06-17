package me.senseiwells.replay.ducks;

import me.senseiwells.replay.viewer.ReplayViewer;
import net.minecraft.network.protocol.Packet;
import org.jetbrains.annotations.Nullable;

public interface ServerReplay$ReplayViewable {
	void replay$startViewingReplay(ReplayViewer viewer);

	void replay$stopViewingReplay();

	@Nullable ReplayViewer replay$getViewingReplay();

	void replay$sendReplayViewerPacket(Packet<?> packet);
}
