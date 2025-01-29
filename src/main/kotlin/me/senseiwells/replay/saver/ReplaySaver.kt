package me.senseiwells.replay.saver

import me.senseiwells.replay.ServerReplay
import me.senseiwells.replay.recorder.ReplayRecorder
import net.minecraft.network.ProtocolInfo
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.Packet
import net.minecraft.world.phys.Vec2
import net.minecraft.world.phys.Vec3
import java.nio.file.Path
import java.util.concurrent.CompletableFuture

interface ReplaySaver {
    val recorder: ReplayRecorder
    val path: Path
    val closed: Boolean

    val markers: Int
        get() = 0

    fun prePacketRecord(packet: Packet<*>): Boolean

    fun writePacket(
        packet: Packet<*>,
        protocol: ProtocolInfo<*>,
        timestamp: Long,
        offThread: Boolean
    ): CompletableFuture<Int?>

    fun postPacketRecord(packet: Packet<*>)

    fun writeMarker(name: String?, position: Vec3, rotation: Vec2, timestamp: Int) {

    }

    fun getRawRecordingSize(): Long

    @Deprecated("Getting the compressed recording size is computationally expensive")
    fun getCompressedRecordingSize(force: Boolean): CompletableFuture<Long>

    fun close(duration: Int, save: Boolean): CompletableFuture<Long>

    companion object {
        val ReplaySaver.name
            get() = this.recorder.getName()

        fun ReplaySaver.broadcastToOps(message: Component) {
            if (ServerReplay.config.notifyAdminsOfStatus) {
                this.recorder.server.execute {
                    this.recorder.server.playerList.players.filter {
                        this.recorder.server.playerList.isOp(it.gameProfile)
                    }.forEach { it.sendSystemMessage(message) }
                }
            }
        }

        fun ReplaySaver.broadcastToOpsAndConsole(message: String) {
            this.broadcastToOps(Component.literal(message))
            ServerReplay.logger.info(message)
        }

        fun ReplaySaver.broadcastToOpsAndConsole(message: Component) {
            this.broadcastToOps(message)
            ServerReplay.logger.info(message.string)
        }
    }
}