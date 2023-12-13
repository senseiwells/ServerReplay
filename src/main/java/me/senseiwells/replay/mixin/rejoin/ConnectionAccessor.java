package me.senseiwells.replay.mixin.rejoin;

import net.minecraft.network.Connection;
import net.minecraft.network.PacketListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Connection.class)
public interface ConnectionAccessor {
	@Accessor("packetListener")
	void setPacketListener(PacketListener listener);
}
