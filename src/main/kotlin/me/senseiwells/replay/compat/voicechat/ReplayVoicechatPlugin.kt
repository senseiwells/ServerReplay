package me.senseiwells.replay.compat.voicechat

import de.maxhenkel.voicechat.api.VoicechatApi
import de.maxhenkel.voicechat.api.VoicechatConnection
import de.maxhenkel.voicechat.api.VoicechatPlugin
import de.maxhenkel.voicechat.api.audio.AudioConverter
import de.maxhenkel.voicechat.api.events.*
import de.maxhenkel.voicechat.api.opus.OpusDecoder
import de.maxhenkel.voicechat.api.packets.SoundPacket
import me.senseiwells.replay.ServerReplay
import me.senseiwells.replay.chunk.ChunkRecorders
import me.senseiwells.replay.player.PlayerRecorders
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.common.ClientCommonPacketListener
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import java.util.UUID
import java.util.WeakHashMap

@Suppress("unused")
object ReplayVoicechatPlugin: VoicechatPlugin {
    /**
     * Mod id of the replay voicechat mod, see [here](https://github.com/henkelmax/replay-voice-chat/blob/master/src/main/java/de/maxhenkel/replayvoicechat/ReplayVoicechat.java).
     */
    const val MOD_ID = "replayvoicechat"

    /**
     * Packet version for the voicechat mod, see [here](https://github.com/henkelmax/replay-voice-chat/blob/master/src/main/java/de/maxhenkel/replayvoicechat/net/AbstractSoundPacket.java#L10).
     */
    const val VERSION = 1

    private val LOCATIONAL_ID = ResourceLocation(MOD_ID, "locational_sound")
    private val ENTITY_ID = ResourceLocation(MOD_ID, "entity_sound")
    private val STATIC_ID = ResourceLocation(MOD_ID, "static_sound")

    // We don't want to constantly decode sound packets, when broadcasted to multiple players
    private val cache = WeakHashMap<SoundPacket, Packet<ClientCommonPacketListener>>()

    private lateinit var decoder: OpusDecoder

    override fun getPluginId(): String {
        return ServerReplay.MOD_ID
    }

    override fun initialize(api: VoicechatApi) {
        this.decoder = api.createDecoder()

        if (!ServerReplay.config.recordVoiceChat) {
            ServerReplay.logger.info("Not currently recording voice chat in replays, you must enabled this in the config")
        }
    }

    override fun registerEvents(registration: EventRegistration) {
        registration.registerEvent(LocationalSoundPacketEvent::class.java, this::onLocationalSoundPacket)
        registration.registerEvent(EntitySoundPacketEvent::class.java, this::onEntitySoundPacket)
        registration.registerEvent(StaticSoundPacketEvent::class.java, this::onStaticSoundPacket)
        registration.registerEvent(MicrophonePacketEvent::class.java, this::onMicrophonePacketEvent)
    }

    private fun onLocationalSoundPacket(event: LocationalSoundPacketEvent) {
        if (!ServerReplay.config.recordVoiceChat) {
            return
        }

        val packet = event.packet
        this.recordForReceiver(event, this.cache.getOrPut(packet) {
            this.createPacket(LOCATIONAL_ID, packet.sender, packet.opusEncodedData, event.voicechat.audioConverter) {
                writeDouble(packet.position.x)
                writeDouble(packet.position.y)
                writeDouble(packet.position.z)
                writeFloat(packet.distance)
            }
        })
    }

    private fun onEntitySoundPacket(event: EntitySoundPacketEvent) {
        if (!ServerReplay.config.recordVoiceChat) {
            return
        }

        val packet = event.packet
        this.recordForReceiver(event, this.cache.getOrPut(packet) {
            this.createPacket(ENTITY_ID, packet.sender, packet.opusEncodedData, event.voicechat.audioConverter) {
                writeBoolean(packet.isWhispering)
                writeFloat(packet.distance)
            }
        })
    }

    private fun onStaticSoundPacket(event: StaticSoundPacketEvent) {
        if (!ServerReplay.config.recordVoiceChat) {
            return
        }

        val packet = event.packet
        this.recordForReceiver(event, this.cache.getOrPut(packet) {
            this.createPacket(STATIC_ID, packet.sender, packet.opusEncodedData, event.voicechat.audioConverter)
        })
    }

    private fun onMicrophonePacketEvent(event: MicrophonePacketEvent) {
        if (!ServerReplay.config.recordVoiceChat) {
            return
        }

        val connection = event.senderConnection ?: return
        val player = connection.getServerPlayer() ?: return
        val converter = event.voicechat.audioConverter
        val inGroup = connection.isInGroup
        val time = System.currentTimeMillis()

        // We may need this for both the player and chunks
        val lazyEntityPacket = lazy {
            this.createPacket(ENTITY_ID, player.uuid, event.packet.opusEncodedData, converter) {
                writeBoolean(event.packet.isWhispering)
                writeFloat(event.voicechat.voiceChatDistance.toFloat())
            }
        }

        val playerRecorder = PlayerRecorders.get(player)
        if (playerRecorder != null) {
            val packet = if (!inGroup) {
                this.createPacket(STATIC_ID, player.uuid, event.packet.opusEncodedData, converter)
            } else {
                lazyEntityPacket.value
            }
            playerRecorder.record(packet, time)
        }

        if (!inGroup) {
            val dimension = player.level().dimension()
            val chunkPos = player.chunkPosition()
            for (recorder in ChunkRecorders.containing(dimension, chunkPos)) {
                recorder.record(lazyEntityPacket.value, time)
            }
        }
    }

    private fun createPacket(
        id: ResourceLocation,
        sender: UUID,
        encoded: ByteArray,
        converter: AudioConverter,
        additional: FriendlyByteBuf.() -> Unit = { }
    ): Packet<ClientCommonPacketListener> {
        // I previously had this running async, I changed it
        // to blocking as I thought it might be causing issues
        // with the timing of the replay playing back packets.
        // TODO: It doesn't seem to have fixed it, maybe re-implement?
        val buf = PacketByteBufs.create()
        buf.writeShort(VERSION)
        buf.writeUUID(sender)
        // We are forced to decode on the server-side since replay-voice-chat
        // reads the raw packet data when it reads the replay.
        buf.writeByteArray(converter.shortsToBytes(this.decoder.decode(encoded)))
        additional(buf)
        return ServerPlayNetworking.createS2CPacket(id, buf)
    }

    private fun <T: SoundPacket> recordForReceiver(
        event: PacketEvent<T>,
        packet: Packet<ClientCommonPacketListener>
    ) {
        val player = event.receiverConnection?.getServerPlayer() ?: return
        val recorder = PlayerRecorders.get(player) ?: return
        recorder.record(packet)
    }

    private fun VoicechatConnection.getServerPlayer(): ServerPlayer? {
        return this.player.player as? ServerPlayer
    }
}