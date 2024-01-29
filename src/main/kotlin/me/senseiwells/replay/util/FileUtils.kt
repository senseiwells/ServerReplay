package me.senseiwells.replay.util

import java.text.NumberFormat
import java.text.ParseException
import java.text.StringCharacterIterator
import java.util.*
import java.util.regex.Pattern
import kotlin.math.abs

object FileUtils {
    private const val KB = 1024L
    private const val MB = 1048576L
    private const val GB = 1073741824L
    private const val TB = 1099511627776L
    private val VALUE_PATTERN = Pattern.compile("([0-9]+([.,][0-9]+)?)\\s*(|K|M|G|T)B?", 2)

    fun parseSize(string: String, default: Long): Long {
        val matcher = VALUE_PATTERN.matcher(string)
        if (matcher.matches()) {
            try {
                val amount = matcher.group(1)
                val quantity = NumberFormat.getNumberInstance(Locale.ROOT).parse(amount).toDouble()
                val unit = matcher.group(3)
                if (unit == null || unit.isEmpty()) {
                    return quantity.toLong()
                } else if (unit.equals("K", ignoreCase = true)) {
                    return (quantity * KB).toLong()
                } else if (unit.equals("M", ignoreCase = true)) {
                    return (quantity * MB).toLong()
                } else if (unit.equals("G", ignoreCase = true)) {
                    return (quantity * GB).toLong()
                } else if (unit.equals("T", ignoreCase = true)) {
                    return (quantity * TB).toLong()
                }
            } catch (_: ParseException) {

            }
        }
        return default
    }

    fun formatSize(bytes: Long): String {
        val absB = if (bytes == Long.MIN_VALUE) Long.MAX_VALUE else abs(bytes.toDouble()).toLong()
        if (absB < KB) {
            return "$bytes B"
        }
        var value = absB
        val ci = StringCharacterIterator("KMGT")
        var i = 40
        while (i >= 0 && absB > 0xfffccccccccccccL shr i) {
            value = value shr 10
            ci.next()
            i -= 10
        }
        value *= java.lang.Long.signum(bytes).toLong()
        return String.format("%.1f %ciB", value / KB.toDouble(), ci.current())
    }
}