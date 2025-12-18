package me.senseiwells.replay.config.serialization

import com.mojang.serialization.Codec
import net.minecraft.server.permissions.PermissionLevel

internal object ExtraCodecs {
    val LENIENT_PERMISSION_LEVEL: Codec<PermissionLevel> = Codec.withAlternative(
        PermissionLevel.CODEC, Codec.INT.xmap(PermissionLevel::byId, PermissionLevel::id)
    )
}