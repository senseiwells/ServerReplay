package me.senseiwells.replay.config.serialization

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.time.Duration

object DurationSerializer: KSerializer<Duration> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("DurationSerializer", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): Duration {
        return Duration.parseOrNull(decoder.decodeString()) ?: Duration.ZERO
    }

    override fun serialize(encoder: Encoder, value: Duration) {
        encoder.encodeString(value.toString())
    }
}