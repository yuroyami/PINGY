package com.yuroyami.pingy.utils

import com.yuroyami.pingy.logic.Ping
import com.yuroyami.pingy.logic.PingKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.TimeSource

/**
 * A reply cannot legitimately be older than the timeout.
 *
 * On mobile the process is suspended routinely, and a reply matched after a
 * suspension reports the whole suspended interval as round-trip time. Observed
 * live on the simulator as a 26-second "RTT" in the range readout, which then
 * dragged the average with it.
 */
class RttPlausibilityTest {

    private val now = TimeSource.Monotonic.markNow()

    @Test
    fun the_timeout_is_the_upper_bound_of_a_believable_reply() {
        // Whatever the engine reports as a REPLY has to fit inside the window
        // the product defines as "still worth believing".
        val believable = Ping.reply(PING_TIMEOUT_MS - 1.0, now)
        assertEquals(PingKind.REPLY, believable.kind)
        assertTrue((believable.rttMs ?: 0.0) < PING_TIMEOUT_MS)
    }

    @Test
    fun a_sample_beyond_the_timeout_belongs_to_the_timeout_bucket() {
        // The engine converts these rather than emitting them as replies; this
        // pins the semantic the conversion relies on.
        val suspended = Ping.timeout(now)
        assertTrue(suspended.isLoss)
        assertEquals(null, suspended.rttMs)
        assertTrue(suspended.wasSent)
    }

    @Test
    fun display_formatting_never_prints_a_raw_double() {
        // "35.567–26866.845ms" shipped to the screen because a Double was
        // interpolated straight into the range readout.
        listOf(0.4, 0.95, 1.0, 9.99, 10.0, 42.6, 1234.5).forEach { ms ->
            val shown = formatRttForTest(ms)
            assertTrue(
                shown.count { it == '.' } <= 1,
                "unexpected precision in `$shown` for $ms",
            )
            assertTrue(shown.length <= 7, "`$shown` is too wide for the readout")
        }
    }

    /** Mirrors the display rule in the graph package, which is internal there. */
    private fun formatRttForTest(ms: Double): String = when {
        ms < 1.0 -> {
            val hundredths = (ms * 100).toInt()
            "0.${(hundredths % 100).toString().padStart(2, '0')}"
        }
        ms < 10.0 -> {
            val tenths = (ms * 10).toInt()
            "${tenths / 10}.${tenths % 10}"
        }
        else -> ms.toInt().toString()
    }
}
