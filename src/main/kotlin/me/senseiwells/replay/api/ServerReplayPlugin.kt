package me.senseiwells.replay.api

import me.senseiwells.replay.chunk.ChunkRecorder
import me.senseiwells.replay.player.PlayerRecorder

/**
 * This interface can be implemented to send additional packets
 * to replay recorders when a replay is started.
 *
 * This is intended for use if your mod sends custom packets to the
 * client that are usually sent after the player has logged in - these
 * would normally be recorded however if a replay is started with the
 * `/replay` command the recorder does not know this, and so they need
 * to be manually resent.
 */
interface ServerReplayPlugin {
    /**
     * This method is called **only** for when replays are started
     * with the `/replay` command, this allows you to send any
     * additional packets that would've been sent when a player logs on.
     *
     * This is called after all the packets for joining a server have
     * been sent, this includes all the chunk and entity packets.
     *
     * @param recorder The [PlayerRecorder] that has just started.
     */
    fun onPlayerReplayStart(recorder: PlayerRecorder) {
        
    }

    /**
     * This method is called for every chunk recording that is started.
     * This allows you to send any additional packets that would've
     * been sent if a player were to log on and start recording.
     * This may, for example, include any resource packs packets.
     *
     * This is called after all the packets for joining a server have
     * been sent, this includes all the chunk and entity packets.
     *
     * @param recorder The [ChunkRecorder] that has just started.
     */
    fun onChunkReplayStart(recorder: ChunkRecorder) {

    }
}