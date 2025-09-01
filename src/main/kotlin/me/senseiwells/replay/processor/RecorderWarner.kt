package me.senseiwells.replay.processor

import me.senseiwells.replay.ServerReplay
import net.casual.arcade.commands.success
import net.casual.arcade.replay.io.ReplayFormat
import net.casual.arcade.utils.EnumUtils
import net.minecraft.commands.CommandSourceStack

object RecorderWarner {
    private var warned = EnumUtils.emptySet<ReplayFormat>()

    internal fun output(source: CommandSourceStack, format: ReplayFormat) {
        if (this.warned.add(format)) {
            format.warn { message -> source.success(message, true) }
        }
    }

    internal fun output(consumer: (String) -> Unit) {
        ServerReplay.config.defaultReplayFormat.warn(consumer)
    }
}