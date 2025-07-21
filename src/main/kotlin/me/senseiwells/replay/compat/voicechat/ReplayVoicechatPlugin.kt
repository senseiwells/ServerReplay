package me.senseiwells.replay.compat.voicechat

import de.maxhenkel.voicechat.Voicechat
import de.maxhenkel.voicechat.api.VoicechatApi
import de.maxhenkel.voicechat.api.VoicechatConnection
import de.maxhenkel.voicechat.api.VoicechatPlugin
import de.maxhenkel.voicechat.api.audio.AudioConverter
import de.maxhenkel.voicechat.api.events.*
import de.maxhenkel.voicechat.api.opus.OpusDecoder
import de.maxhenkel.voicechat.api.packets.SoundPacket
import de.maxhenkel.voicechat.net.*
import de.maxhenkel.voicechat.plugins.impl.VolumeCategoryImpl
import me.senseiwells.replay.ServerReplay
import me.senseiwells.replay.api.ServerReplayPlugin
import me.senseiwells.replay.api.ServerReplayPluginManager
import me.senseiwells.replay.recorder.ReplayRecorder
import me.senseiwells.replay.recorder.chunk.ChunkRecorder
import me.senseiwells.replay.recorder.chunk.ChunkRecorders
import me.senseiwells.replay.recorder.player.PlayerRecorder
import me.senseiwells.replay.recorder.player.PlayerRecorders
import net.minecraft.Util
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.common.ClientCommonPacketListener
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import java.util.*

object ReplayVoicechatPlugin: VoicechatPlugin, ServerReplayPlugin {
    /**
     * Mod id of the replay voicechat mod, see [here](https://github.com/henkelmax/replay-voice-chat/blob/master/src/main/java/de/maxhenkel/replayvoicechat/ReplayVoicechat.java).
     */
    const val MOD_ID = "replayvoicechat"

    /**
     * Packet version for the voicechat mod, see [here](https://github.com/henkelmax/replay-voice-chat/blob/master/src/main/java/de/maxhenkel/replayvoicechat/net/AbstractSoundPacket.java#L10).
     */
    const val VERSION = 1

    private val LOCATIONAL_TYPE = CustomPacketPayload.Type<VoicechatPayload>(
        ResourceLocation.fromNamespaceAndPath(MOD_ID, "locational_sound")
    )
    private val ENTITY_TYPE = CustomPacketPayload.Type<VoicechatPayload>(
        ResourceLocation.fromNamespaceAndPath(MOD_ID, "entity_sound")
    )
    private val STATIC_TYPE = CustomPacketPayload.Type<VoicechatPayload>(
        ResourceLocation.fromNamespaceAndPath(MOD_ID, "static_sound")
    )

    // We don't want to constantly decode sound packets, when broadcasted to multiple players
    private val cache = WeakHashMap<SoundPacket, Packet<ClientCommonPacketListener>>()

    private lateinit var decoder: OpusDecoder

    override fun getPluginId(): String {
        return ServerReplay.MOD_ID
    }

    override fun initialize(api: VoicechatApi) {
        this.decoder = api.createDecoder()

        @Suppress("DEPRECATION")
        ServerReplayPluginManager.registerPlugin(this)
    }

    override fun registerEvents(registration: EventRegistration) {
        registration.registerEvent(LocationalSoundPacketEvent::class.java, this::onLocationalSoundPacket)
        registration.registerEvent(EntitySoundPacketEvent::class.java, this::onEntitySoundPacket)
        registration.registerEvent(StaticSoundPacketEvent::class.java, this::onStaticSoundPacket)
        registration.registerEvent(MicrophonePacketEvent::class.java, this::onMicrophonePacket)

        registration.registerEvent(RegisterVolumeCategoryEvent::class.java, this::onRegisterCategory)
        registration.registerEvent(UnregisterVolumeCategoryEvent::class.java, this::onUnregisterCategory)
        registration.registerEvent(PlayerStateChangedEvent::class.java, this::onPlayerStateChanged)
    }

    private fun onLocationalSoundPacket(event: LocationalSoundPacketEvent) {
        if (!ServerReplay.config.recordVoiceChat) {
            return
        }

        val packet = event.packet
        this.recordForReceiver(event) {
            this.cache.getOrPut(packet) {
                this.createPacket(LOCATIONAL_TYPE, packet.sender, packet.opusEncodedData, event.voicechat.audioConverter) {
                    writeDouble(packet.position.x)
                    writeDouble(packet.position.y)
                    writeDouble(packet.position.z)
                    writeFloat(packet.distance)
                }
            }
        }
    }

    private fun onEntitySoundPacket(event: EntitySoundPacketEvent) {
        if (!ServerReplay.config.recordVoiceChat) {
            return
        }

        val packet = event.packet
        this.recordForReceiver(event) {
            this.cache.getOrPut(packet) {
                this.createPacket(ENTITY_TYPE, packet.sender, packet.opusEncodedData, event.voicechat.audioConverter) {
                    writeBoolean(packet.isWhispering)
                    writeFloat(packet.distance)
                }
            }
        }
    }

    private fun onStaticSoundPacket(event: StaticSoundPacketEvent) {
        if (!ServerReplay.config.recordVoiceChat) {
            return
        }

        val packet = event.packet
        this.recordForReceiver(event) {
            this.cache.getOrPut(packet) {
                this.createPacket(STATIC_TYPE, packet.sender, packet.opusEncodedData, event.voicechat.audioConverter)
            }
        }
    }

    private fun onMicrophonePacket(event: MicrophonePacketEvent) {
        if (!ServerReplay.config.recordVoiceChat) {
            return
        }

        val connection = event.senderConnection ?: return
        val player = connection.getServerPlayer() ?: return
        val server = player.level().server
        val converter = event.voicechat.audioConverter
        val inGroup = connection.isInGroup

        // We may need this for both the player and chunks
        val lazyEntityPacket = lazy {
            this.createPacket(ENTITY_TYPE, player.uuid, event.packet.opusEncodedData, converter) {
                writeBoolean(event.packet.isWhispering)
                writeFloat(event.voicechat.voiceChatDistance.toFloat())
            }
        }

        server.execute {
            val playerRecorder = PlayerRecorders.get(player)
            if (playerRecorder != null) {
                val packet = if (!inGroup) {
                    this.createPacket(STATIC_TYPE, player.uuid, event.packet.opusEncodedData, converter)
                } else {
                    lazyEntityPacket.value
                }
                playerRecorder.record(packet)
            }

            if (!inGroup) {
                val dimension = player.level().dimension()
                val chunkPos = player.chunkPosition()
                for (recorder in ChunkRecorders.containing(dimension, chunkPos)) {
                    recorder.record(lazyEntityPacket.value)
                }
            }
        }
    }

    private fun onRegisterCategory(event: RegisterVolumeCategoryEvent) {
        val server = Voicechat.SERVER.server?.server ?: return
        server.execute {
            val category = event.volumeCategory
            if (category is VolumeCategoryImpl) {
                val packet = AddCategoryPacket(category).toClientboundPacket()
                for (recorder in ChunkRecorders.recorders()) {
                    recorder.record(packet)
                }
            }
        }
    }

    private fun onUnregisterCategory(event: UnregisterVolumeCategoryEvent) {
        val server = Voicechat.SERVER.server?.server ?: return
        server.execute {
            val packet = RemoveCategoryPacket(event.volumeCategory.id).toClientboundPacket()
            for (recorder in ChunkRecorders.recorders()) {
                recorder.record(packet)
            }
        }
    }

    private fun onPlayerStateChanged(event: PlayerStateChangedEvent) {
        val voicechat = Voicechat.SERVER.server ?: return
        val server = voicechat.server ?: return
        server.execute {
            val state = voicechat.playerStateManager.getState(event.playerUuid)
            if (state != null) {
                val packet = PlayerStatePacket(state).toClientboundPacket()
                for (recorder in ChunkRecorders.recorders()) {
                    recorder.record(packet)
                }
            }
        }
    }

    private fun createPacket(
        type: CustomPacketPayload.Type<*>,
        sender: UUID,
        encoded: ByteArray,
        converter: AudioConverter,
        additional: FriendlyByteBuf.() -> Unit = { }
    ): Packet<ClientCommonPacketListener> {
        // We are forced to decode on the server-side since replay-voice-chat
        // reads the raw packet data when it reads the replay.
        val raw = converter.shortsToBytes(this.decoder.decode(encoded))
        val payload = VoicechatPayload.of(type) { buf ->
            buf.writeShort(VERSION)
            buf.writeUUID(sender)
            buf.writeByteArray(raw)
            additional(buf)
        }
        return ClientboundCustomPayloadPacket(payload)
    }

    private fun <T: SoundPacket> recordForReceiver(
        event: PacketEvent<T>,
        packet: () -> Packet<ClientCommonPacketListener>
    ) {
        val player = event.receiverConnection?.getServerPlayer() ?: return
        player.server!!.execute {
            val recorder = PlayerRecorders.get(player)
            recorder?.record(packet())
        }
    }

    private fun VoicechatConnection.getServerPlayer(): ServerPlayer? {
        return this.player.player as? ServerPlayer
    }

    override fun onPlayerReplayStart(recorder: PlayerRecorder) {
        this.recordAdditionalPackets(recorder)
        val server = Voicechat.SERVER.server
        val player = recorder.getPlayerOrThrow()
        if (server != null && server.hasSecret(player.uuid)) {
            // I mean, do we really need to specify the secret? Might as well...
            val secret = server.getSecret(player.uuid)
            val packet = SecretPacket(player, secret, server.port, Voicechat.SERVER_CONFIG)
            recorder.record(packet.toClientboundPacket())
        }
    }

    override fun onChunkReplayStart(recorder: ChunkRecorder) {
        this.recordAdditionalPackets(recorder)
        val server = Voicechat.SERVER.server
        if (server != null) {
            val player = recorder.getDummyPlayer()
            // The chunks aren't sending any voice data so doesn't need a secret
            val packet = SecretPacket(player, Util.NIL_UUID, server.port, Voicechat.SERVER_CONFIG)
            recorder.record(packet.toClientboundPacket())
        }
    }

    private fun recordAdditionalPackets(recorder: ReplayRecorder) {
        val server = Voicechat.SERVER.server
        if (server != null) {
            val states = server.playerStateManager.states.associateBy { it.uuid }
            recorder.record(PlayerStatesPacket(states).toClientboundPacket())
            for (group in server.groupManager.groups.values) {
                recorder.record(AddGroupPacket(group.toClientGroup()).toClientboundPacket())
            }
            for (category in server.categoryManager.categories) {
                recorder.record(AddCategoryPacket(category).toClientboundPacket())
            }
        }
    }

    private fun de.maxhenkel.voicechat.net.Packet<*>.toClientboundPacket(): Packet<ClientCommonPacketListener> {
        return ClientboundCustomPayloadPacket(this)
    }
}