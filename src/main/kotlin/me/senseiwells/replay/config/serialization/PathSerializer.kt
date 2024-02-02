package me.senseiwells.replay.config.serialization

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.nio.file.InvalidPathException
import java.nio.file.Path
import kotlin.io.path.pathString

object PathSerializer: KSerializer<Path> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("PathSerializer", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Path) {
        encoder.encodeString(value.pathString)
    }

    override fun deserialize(decoder: Decoder): Path {
        try {
            return Path.of(decoder.decodeString())
        } catch (e: InvalidPathException) {
            throw SerializationException("Invalid path")
        }
    }
}