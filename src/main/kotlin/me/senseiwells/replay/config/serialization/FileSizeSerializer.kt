package me.senseiwells.replay.config.serialization

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import me.senseiwells.replay.util.FileSize

object FileSizeSerializer: KSerializer<FileSize> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("FileSize", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: FileSize) {
        encoder.encodeString(value.raw)
    }

    override fun deserialize(decoder: Decoder): FileSize {
        return FileSize(decoder.decodeString())
    }
}