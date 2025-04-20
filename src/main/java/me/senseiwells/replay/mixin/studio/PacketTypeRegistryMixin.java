package me.senseiwells.replay.mixin.studio;

import com.replaymod.replaystudio.lib.viaversion.api.protocol.packet.State;
import com.replaymod.replaystudio.lib.viaversion.api.protocol.version.ProtocolVersion;
import com.replaymod.replaystudio.protocol.PacketType;
import com.replaymod.replaystudio.protocol.PacketTypeRegistry;
import me.senseiwells.replay.compat.via.TempProtocolVersion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

// TODO: Remove this when updating ReplayStudio
@Deprecated
@Mixin(value = PacketTypeRegistry.class, remap = false)
public class PacketTypeRegistryMixin {
    @Shadow private static Map<ProtocolVersion, EnumMap<State, PacketTypeRegistry>> forVersionAndState;

    @Inject(
        method = "<clinit>",
        at = @At("HEAD")
    )
    private static void preClinit(CallbackInfo ci) {
        TempProtocolVersion.noop();
    }

    @Inject(
        method = "<clinit>",
        at = @At("TAIL")
    )
    private static void postClinit(CallbackInfo ci) {
        // Big hack-fix to make this work...
        EnumMap<State, PacketTypeRegistry> forState = new EnumMap<>(State.class);
        for (State state : State.values()) {
            PacketTypeRegistry registry = PacketTypeRegistryAccessor.create(ProtocolVersion.v1_21_4, state);

            PacketTypeRegistryAccessor accessor = (PacketTypeRegistryAccessor) registry;

            if (state == State.PLAY) {
                Map<Integer, PacketType> typeForId = accessor.getTypeForId();
                Map<PacketType, Integer> idForType = accessor.getIdForType();
                Map<Integer, PacketType> copy = new HashMap<>(accessor.getTypeForId());

                typeForId.clear();
                idForType.clear();
                copy.forEach((id, type) -> {
                    if (id >= 0x02 && id <= 0x77) {
                        typeForId.put(id - 1, type);
                        idForType.put(type, id - 1);
                    } else {
                        typeForId.put(id, type);
                        idForType.put(type, id);
                    }
                });
            }

            accessor.setVersion(TempProtocolVersion.v1_21_5);
            forState.put(state, registry);
        }
        forVersionAndState.put(TempProtocolVersion.v1_21_5, forState);
    }
}
