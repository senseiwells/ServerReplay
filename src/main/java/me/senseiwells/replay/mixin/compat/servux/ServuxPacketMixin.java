package me.senseiwells.replay.mixin.compat.servux;

import fi.dy.masa.servux.network.packet.ServuxEntitiesPacket;
import fi.dy.masa.servux.network.packet.ServuxLitematicaPacket;
import fi.dy.masa.servux.network.packet.ServuxStructuresPacket;
import fi.dy.masa.servux.network.packet.ServuxTweaksPacket;
import me.senseiwells.replay.api.network.RecordablePayload;
import net.minecraft.network.FriendlyByteBuf;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(
    value = {
        ServuxEntitiesPacket.Payload.class,
        ServuxLitematicaPacket.Payload.class,
        ServuxStructuresPacket.Payload.class,
        ServuxTweaksPacket.Payload.class
    },
    remap = false
)
@SuppressWarnings("AddedMixinMembersNamePattern")
public class ServuxPacketMixin implements RecordablePayload {
    @Override
    public boolean shouldRecord() {
        return false;
    }

    @Override
    public void record(@NotNull FriendlyByteBuf buf) {

    }
}
