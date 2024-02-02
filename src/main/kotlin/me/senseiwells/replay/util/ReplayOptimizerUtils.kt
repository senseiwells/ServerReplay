package me.senseiwells.replay.util

import me.senseiwells.replay.config.ReplayConfig
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.*
import net.minecraft.network.protocol.login.ClientboundLoginCompressionPacket

object ReplayOptimizerUtils {
    private val IGNORED = setOf<Class<out Packet<*>>>(
        ClientboundBlockChangedAckPacket::class.java,
        ClientboundOpenBookPacket::class.java,
        ClientboundOpenScreenPacket::class.java,
        ClientboundUpdateRecipesPacket::class.java,
        ClientboundUpdateAdvancementsPacket::class.java,
        ClientboundSelectAdvancementsTabPacket::class.java,
        ClientboundSetCameraPacket::class.java,
        ClientboundHorseScreenOpenPacket::class.java,
        ClientboundContainerClosePacket::class.java,
        ClientboundContainerSetSlotPacket::class.java,
        ClientboundContainerSetDataPacket::class.java,
        ClientboundOpenSignEditorPacket::class.java,
        ClientboundAwardStatsPacket::class.java,
        ClientboundSetExperiencePacket::class.java,
        ClientboundPlayerAbilitiesPacket::class.java,
        ClientboundLoginCompressionPacket::class.java,

    )
    val SOUNDS = setOf<Class<out Packet<*>>>(
        ClientboundSoundPacket::class.java,
        ClientboundSoundEntityPacket::class.java
    )

    fun canIgnorePacket(packet: Packet<*>): Boolean {
        val type = packet::class.java
        if (ReplayConfig.ignoreLightPackets && type == ClientboundLightUpdatePacket::class.java) {
            return true
        }
        if (ReplayConfig.ignoreSoundPackets && SOUNDS.contains(type)) {
            return true
        }
        return IGNORED.contains(type)
    }
}