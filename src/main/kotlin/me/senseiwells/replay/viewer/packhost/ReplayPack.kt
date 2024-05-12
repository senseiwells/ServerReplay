package me.senseiwells.replay.viewer.packhost

import com.replaymod.replaystudio.replay.ReplayFile
import java.io.InputStream

class ReplayPack(
    hash: String,
    private val replay: ReplayFile
): ReadablePack {
    override val name: String = hash

    override fun stream(): InputStream {
        return this.replay.getResourcePack(this.name).orNull()
            ?: throw IllegalStateException("ReplayPack ${this.name} doesn't exist")
    }
}