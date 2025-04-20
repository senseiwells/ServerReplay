package me.senseiwells.replay.writer.flashback

import net.minecraft.network.FriendlyByteBuf
import net.minecraft.world.phys.Vec2
import net.minecraft.world.phys.Vec3

class EntityMovement(
    val id: Int,
    val position: Vec3,
    val rotation: Vec2,
    val headRot: Float,
    val onGround: Boolean
) {
    fun write(buf: FriendlyByteBuf) {
        buf.writeVarInt(this.id)
        buf.writeVec3(this.position)
        buf.writeFloat(this.rotation.y)
        buf.writeFloat(this.rotation.x)
        buf.writeFloat(this.headRot)
        buf.writeBoolean(this.onGround)
    }

    companion object {
        fun size(): Int {
            return 4 + 3 * 8 + 2 * 4 + 4 + 1
        }

        fun read(buffer: FriendlyByteBuf): EntityMovement {
            val id = buffer.readVarInt()
            val position = buffer.readVec3()
            val yaw = buffer.readFloat()
            val pitch = buffer.readFloat()
            val headYaw = buffer.readFloat()
            val grounded = buffer.readBoolean()
            return EntityMovement(id, position, Vec2(pitch, yaw), headYaw, grounded)
        }
    }
}