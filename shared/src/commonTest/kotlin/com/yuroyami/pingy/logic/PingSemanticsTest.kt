package com.yuroyami.pingy.logic

import com.yuroyami.pingy.utils.MAX_INTERVAL_MS
import com.yuroyami.pingy.utils.sanitizeIntervalMs
import com.yuroyami.pingy.utils.sanitizePayloadSize
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.TimeSource

/**
 * The distinction the loss metric depends on: a timeout is evidence about the
 * target, a local fault is evidence about this device, and only the first
 * belongs in a loss percentage.
 */
class PingSemanticsTest {

    private val now get() = TimeSource.Monotonic.markNow()

    @Test
    fun a_reply_is_not_loss_and_keeps_sub_millisecond_precision() {
        val p = Ping.reply(0.42, now)
        assertFalse(p.isLoss)
        assertFalse(p.isLocalFault)
        assertTrue(p.wasSent)
        assertEquals(0.42, assertNotNull(p.rttMs))
    }

    @Test
    fun a_timeout_is_loss_and_was_sent() {
        val p = Ping.timeout(now)
        assertTrue(p.isLoss)
        assertFalse(p.isLocalFault)
        assertTrue(p.wasSent, "a timed-out probe did leave the device")
    }

    @Test
    fun local_faults_are_never_loss_and_never_counted_as_sent() {
        // DNS failure, no socket, no route, unsupported platform: none of these
        // sent a packet, so none of them says anything about the target.
        for (fault in LocalFault.entries) {
            val p = Ping.localFault(fault, now)
            assertFalse(p.isLoss, "$fault must not count as packet loss")
            assertFalse(p.wasSent, "$fault never sent a probe")
            assertTrue(p.isLocalFault)
            assertEquals(fault, p.fault)
        }
    }

    @Test
    fun display_rounding_happens_at_the_edge_not_at_capture() {
        // Rounding at capture floored every LAN reading to 0 and collapsed
        // jitter with it.
        assertEquals(0, Ping.reply(0.4, now).value)
        assertEquals(1, Ping.reply(0.6, now).value)
        assertEquals(0.4, Ping.reply(0.4, now).rttMs)
    }

    @Test
    fun interval_sanitiser_keeps_deadline_arithmetic_safe() {
        assertEquals(0L, sanitizeIntervalMs(0L))
        assertEquals(0L, sanitizeIntervalMs(-1L))
        assertEquals(MAX_INTERVAL_MS, sanitizeIntervalMs(Long.MAX_VALUE))
        assertEquals(250L, sanitizeIntervalMs(250L))
        // The property that actually matters: no sanitised interval can make
        // `now + interval` overflow.
        for (raw in listOf(Long.MIN_VALUE, -1L, 0L, 1L, MAX_INTERVAL_MS, Long.MAX_VALUE)) {
            val safe = sanitizeIntervalMs(raw)
            assertTrue(safe >= 0 && Long.MAX_VALUE - safe > 0, "unsafe interval from $raw")
        }
    }

    @Test
    fun payload_sanitiser_matches_the_wire_format_bounds() {
        assertEquals(16, sanitizePayloadSize(Int.MIN_VALUE))
        assertEquals(16, sanitizePayloadSize(0))
        assertEquals(480, sanitizePayloadSize(Int.MAX_VALUE))
        assertEquals(64, sanitizePayloadSize(64))
    }
}
