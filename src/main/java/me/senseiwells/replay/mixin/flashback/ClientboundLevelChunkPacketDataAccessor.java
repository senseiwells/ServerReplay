package me.senseiwells.replay.mixin.flashback;

import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(ClientboundLevelChunkPacketData.class)
public interface ClientboundLevelChunkPacketDataAccessor {
    @Accessor
    byte[] getBuffer();

    @Accessor
    List<ClientboundLevelChunkPacketData.BlockEntityInfo> getBlockEntitiesData();
}
