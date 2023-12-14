package me.senseiwells.replay.util

import org.apache.commons.lang3.mutable.MutableLong
import java.io.FilterOutputStream
import java.io.OutputStream

class CounterOutputStream(
    out: OutputStream,
    private val bytes: MutableLong
): FilterOutputStream(out) {
    override fun write(b: Int) {
        super.write(b)
        this.bytes.increment()
    }
}