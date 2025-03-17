package me.senseiwells.replay.mixin.flashback;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ClientboundLevelChunkPacketData.BlockEntityInfo.class)
public interface BlockEntityInfoAccessor {
    @Accessor
    int getPackedXZ();

    @Accessor
    int getY();

    @Accessor
    BlockEntityType<?> getType();

    @Accessor
    @Nullable CompoundTag getTag();
}
