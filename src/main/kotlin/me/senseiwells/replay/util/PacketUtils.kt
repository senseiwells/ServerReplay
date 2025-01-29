package me.senseiwells.replay.util

import io.netty.buffer.ByteBuf
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket
import net.minecraft.world.entity.Entity

fun ClientboundAddEntityPacket(entity: Entity): ClientboundAddEntityPacket {
    return ClientboundAddEntityPacket(
        entity.id,
        entity.uuid,
        entity.x,
        entity.y,
        entity.z,
        entity.xRot,
        entity.yRot,
        entity.type,
        0,
        entity.deltaMovement,
        entity.yHeadRot.toDouble()
    )
}

fun Packet<*>.getDebugName(): String {
    return if (this is ClientboundCustomPayloadPacket) {
        "CustomPayload(${this.payload.type().id})"
    } else {
        this.type().id.toString()
    }
}

fun ByteBuf.toByteArray(): ByteArray {
    val bytes = ByteArray(this.readableBytes())
    this.readBytes(bytes)
    return bytes
}