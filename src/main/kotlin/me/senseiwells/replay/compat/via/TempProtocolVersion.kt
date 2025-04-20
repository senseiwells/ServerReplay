package me.senseiwells.replay.compat.via

import com.replaymod.replaystudio.lib.viaversion.api.protocol.version.ProtocolVersion
import net.minecraft.SharedConstants

@Deprecated("Temporary, until ReplayStudio/ViaVersion is updated")
object TempProtocolVersion {
    @JvmField
    val v1_21_5: ProtocolVersion = ProtocolVersion.register(SharedConstants.getProtocolVersion(), "1.21.5")

    @JvmStatic
    fun noop() {

    }
}