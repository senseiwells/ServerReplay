package me.senseiwells.replay.util

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object DateUtils {
    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd--HH-mm-ss")

    fun getFormattedDate(): String {
        return LocalDateTime.now().format(formatter)
    }
}