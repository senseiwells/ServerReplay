package me.senseiwells.replay.compat.voicechat

import de.maxhenkel.voicechat.api.VoicechatApi
import de.maxhenkel.voicechat.api.VoicechatConnection
import de.maxhenkel.voicechat.api.VoicechatPlugin
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
import java.util.concurrent.CompletableFuture

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
    private val cache = WeakHashMap<SoundPacket, CompletableFuture<Packet<ClientCommonPacketListener>>>()

    private lateinit var decoder: OpusDecoder

    override fun getPluginId(): String {
        return ServerReplay.MOD_ID
    }

    override fun initialize(api: VoicechatApi) {
        this.decoder = api.createDecoder()
    }

    override fun registerEvents(registration: EventRegistration) {
        registration.registerEvent(LocationalSoundPacketEvent::class.java, this::onLocationalSoundPacket)
        registration.registerEvent(EntitySoundPacketEvent::class.java, this::onEntitySoundPacket)
        registration.registerEvent(StaticSoundPacketEvent::class.java, this::onStaticSoundPacket)
        registration.registerEvent(MicrophonePacketEvent::class.java, this::onMicrophonePacketEvent)
    }

    private fun onLocationalSoundPacket(event: LocationalSoundPacketEvent) {
        val packet = event.packet
        this.recordForReceiver(event, this.cache.getOrPut(packet) {
            this.createPacketFuture(LOCATIONAL_ID, packet.sender, packet.opusEncodedData) {
                writeDouble(packet.position.x)
                writeDouble(packet.position.y)
                writeDouble(packet.position.z)
                writeFloat(packet.distance)
            }
        })
    }

    private fun onEntitySoundPacket(event: EntitySoundPacketEvent) {
        val packet = event.packet
        this.recordForReceiver(event, this.cache.getOrPut(packet) {
            this.createPacketFuture(ENTITY_ID, packet.sender, packet.opusEncodedData) {
                writeBoolean(packet.isWhispering)
                writeFloat(packet.distance)
            }
        })
    }

    private fun onStaticSoundPacket(event: StaticSoundPacketEvent) {
        val packet = event.packet
        this.recordForReceiver(event, this.cache.getOrPut(packet) {
            this.createPacketFuture(STATIC_ID, packet.sender, packet.opusEncodedData)
        })
    }

    private fun onMicrophonePacketEvent(event: MicrophonePacketEvent) {
        val connection = event.senderConnection ?: return
        val player = connection.getServerPlayer() ?: return
        val inGroup = connection.isInGroup

        val lazyEntityPacket = lazy {
            this.createPacketFuture(ENTITY_ID, player.uuid, event.packet.opusEncodedData) {
                writeBoolean(event.packet.isWhispering)
                writeFloat(event.voicechat.voiceChatDistance.toFloat())
            }
        }

        val playerRecorder = PlayerRecorders.get(player)
        if (playerRecorder != null) {
            val future = if (!inGroup) {
                this.createPacketFuture(STATIC_ID, player.uuid, event.packet.opusEncodedData)
            } else {
                lazyEntityPacket.value
            }
            future.thenApplyAsync(playerRecorder::record, player.server)
        }

        if (!inGroup) {
            val dimension = player.level().dimension()
            val chunkPos = player.chunkPosition()
            lazyEntityPacket.value.thenApplyAsync({
                for (recorder in ChunkRecorders.containing(dimension, chunkPos)) {
                    recorder.record(it)
                }
            }, player.server)
        }
    }

    private fun createPacketFuture(
        id: ResourceLocation,
        sender: UUID,
        encoded: ByteArray,
        additional: FriendlyByteBuf.() -> Unit = { }
    ): CompletableFuture<Packet<ClientCommonPacketListener>> {
        return CompletableFuture.supplyAsync {
            val buf = PacketByteBufs.create()
            buf.writeShort(VERSION)
            buf.writeUUID(sender)
            buf.writeByteArray(shortsToBytes(this.decoder.decode(encoded)))
            additional(buf)
            ServerPlayNetworking.createS2CPacket(id, buf)
        }
    }

    private fun <T: SoundPacket> recordForReceiver(
        event: PacketEvent<T>,
        future: CompletableFuture<Packet<ClientCommonPacketListener>>
    ) {
        val player = event.receiverConnection?.getServerPlayer() ?: return
        val recorder = PlayerRecorders.get(player) ?: return
        future.thenApplyAsync(recorder::record, player.server)
    }

    private fun VoicechatConnection.getServerPlayer(): ServerPlayer? {
        return this.player.player as? ServerPlayer
    }

    private fun shortsToBytes(shorts: ShortArray): ByteArray {
        val data = ByteArray(shorts.size * 2)
        for (i in shorts.indices) {
            val split = shortToBytes(shorts[i])
            data[i * 2] = split[0]
            data[i * 2 + 1] = split[1]
        }
        return data
    }

    private fun shortToBytes(s: Short): ByteArray {
        return byteArrayOf((s.toInt() and 0xFF).toByte(), ((s.toInt() shr 8) and 0xFF).toByte())
    }
}