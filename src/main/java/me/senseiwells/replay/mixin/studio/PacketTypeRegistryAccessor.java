package me.senseiwells.replay.mixin.studio;

import com.replaymod.replaystudio.lib.viaversion.api.protocol.packet.State;
import com.replaymod.replaystudio.lib.viaversion.api.protocol.version.ProtocolVersion;
import com.replaymod.replaystudio.protocol.PacketType;
import com.replaymod.replaystudio.protocol.PacketTypeRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.Map;

@Deprecated
@Mixin(value = PacketTypeRegistry.class, remap = false)
public interface PacketTypeRegistryAccessor {
    @Invoker("<init>")
    static PacketTypeRegistry create(ProtocolVersion version, State state) {
        throw new AssertionError();
    }

    @Mutable
    @Accessor
    void setVersion(ProtocolVersion version);

    @Accessor
    Map<Integer, PacketType> getTypeForId();

    @Accessor
    Map<PacketType, Integer> getIdForType();
}
