package me.senseiwells.replay.player

import com.mojang.datafixers.util.Pair
import net.minecraft.core.NonNullList
import net.minecraft.network.protocol.game.ClientboundAnimatePacket
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket
import net.minecraft.network.protocol.game.ClientboundRotateHeadPacket
import net.minecraft.network.protocol.game.ClientboundSetEntityLinkPacket
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.item.ItemStack
import kotlin.math.abs
import kotlin.math.roundToInt

class PlayerState(
    private val owner: PlayerRecorder
) {
    private val items = NonNullList.withSize(6, ItemStack.EMPTY)

    private var wasSleeping = false

    private var lastCorrection = 0
    private var lastVehicle = -1

    private var lastX: Double? = null
    private var lastY: Double? = null
    private var lastZ: Double? = null
    private var lastHeadYaw: Int? = null

    fun tick() {
        val player = this.owner.player

        var forced = false
        var lx: Double? = this.lastX
        var ly: Double? = this.lastY
        var lz: Double? = this.lastZ
        if (lx == null || ly == null || lz == null) {
            this.lastX = player.x
            this.lastY = player.y
            this.lastZ = player.z
            lx = player.x
            ly = player.y
            lz = player.z
            forced = true
        }
        if (++this.lastCorrection >= 100) {
            this.lastCorrection = 0
            forced = true
        }

        val dx = player.x - lx
        val dy = player.y - ly
        val dz = player.z - lz

        this.lastX = player.x
        this.lastY = player.y
        this.lastZ = player.z

        if (forced || abs(dx) > MAX_TRAVEL || abs(dy) > MAX_TRAVEL || abs(dz) > MAX_TRAVEL) {
             this.owner.record(ClientboundTeleportEntityPacket(player))
        } else {
            val newYaw = (player.yRot * 256.0f / 360.0f).toInt().toByte()
            val newPitch = (player.xRot * 256.0f / 360.0f).toInt().toByte()
            this.owner.record(ClientboundMoveEntityPacket.PosRot(
                player.id,
                (dx * 4096).roundToInt().toShort(),
                (dy * 4096).roundToInt().toShort(),
                (dz * 4096).roundToInt().toShort(),
                newYaw,
                newPitch,
                player.onGround()
            ))
        }

        val yaw = (player.yHeadRot * 256.0f / 360.0f).toInt()
        if (yaw != this.lastHeadYaw) {
            this.owner.record(ClientboundRotateHeadPacket(player, yaw.toByte()))
            this.lastHeadYaw = yaw
        }

        this.owner.record(ClientboundSetEntityMotionPacket(player.id, player.deltaMovement))

        if (player.swinging && player.swingTime == 0) {
            this.owner.record(ClientboundAnimatePacket(player, if (player.swingingArm == InteractionHand.MAIN_HAND) 0 else 3))
        }

        for (slot in EquipmentSlot.values()) {
            val stack = player.getItemBySlot(slot)
            val ordinal = slot.ordinal
            if (ItemStack.matches(this.items[ordinal], stack)) {
                val copy = stack.copy()
                this.items[ordinal] = copy
                this.owner.record(ClientboundSetEquipmentPacket(player.id, listOf(Pair.of(slot, copy))))
            }
        }

        val vehicle = player.vehicle
        val id = if (vehicle == null) -1 else vehicle.id
        if (this.lastVehicle != id) {
            this.lastVehicle = id
            this.owner.record(ClientboundSetEntityLinkPacket(player, vehicle))
        }

        if (!player.isSleeping && this.wasSleeping) {
            this.owner.record(ClientboundAnimatePacket(player, ClientboundAnimatePacket.WAKE_UP))
            this.wasSleeping = false
        }
    }

    companion object {
        private const val MAX_TRAVEL = 8.0
    }
}