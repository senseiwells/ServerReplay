package me.senseiwells.replay.config.serialization

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.TripleSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.serializer
import net.minecraft.world.phys.Vec3

object Vec3Serializer: KSerializer<Vec3> {
    private val inner = ListSerializer(serializer<Double>())

    override val descriptor: SerialDescriptor = this.inner.descriptor

    override fun deserialize(decoder: Decoder): Vec3 {
        val list = this.inner.deserialize(decoder)
        if (list.size != 3) {
            throw SerializationException("Vec3 didn't contain exactly 3 doubles!")
        }
        return Vec3(list[0], list[1], list[2])
    }

    override fun serialize(encoder: Encoder, value: Vec3) {
        this.inner.serialize(encoder, listOf(value.x, value.y, value.z))
    }
}