package com.yuroyami.pingy.ui.main.components

import com.yuroyami.pingy.PanelLayout
import com.yuroyami.pingy.logic.Ping
import com.yuroyami.pingy.logic.LocalFault
import com.yuroyami.pingy.logic.RingBuffer
import com.yuroyami.pingy.utils.PING_TIMEOUT_MS
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

/**
 * The arithmetic behind the numbers beside the graph.
 *
 * These were unreachable while they lived inside a 1,500-line composable as
 * private helpers, which is the practical reason the statistics were wrong for
 * so long without anyone noticing.
 */
class GraphStatsTest {

    private val now = TimeSource.Monotonic.markNow()

    /** A ring holding samples at the given ages, newest last. */
    private fun ringOf(vararg samples: Pair<Long, Ping?>): RingBuffer<Ping> {
        val ring = RingBuffer<Ping>(64)
        samples.sortedByDescending { it.first }.forEach { (ageMs, template) ->
            val at = now - ageMs.milliseconds
            ring.add(
                when {
                    template == null -> Ping.timeout(at)
                    else -> template.copy(timestamp = at)
                }
            )
        }
        return ring
    }

    private fun reply(ms: Double) = Ping.reply(ms, now)

    @Test
    fun local_faults_are_excluded_from_the_loss_denominator() {
        val ring = RingBuffer<Ping>(64)
        ring.add(Ping.localFault(LocalFault.RESOLVE_FAILED, now - 900.milliseconds))
        ring.add(Ping.localFault(LocalFault.RESOLVE_FAILED, now - 600.milliseconds))
        ring.add(Ping.reply(12.0, now - 300.milliseconds))

        val stats = computeWindowStats(ring, 5_000)
        // Two DNS failures and one good reply: one probe was sent, none lost.
        assertEquals(1, stats.count, "only sent probes belong in the denominator")
        assertEquals(0, stats.lost)
        assertEquals(2, stats.localFaults)
    }

    @Test
    fun a_timeout_is_loss_and_a_reply_is_not() {
        val ring = ringOf(900L to reply(10.0), 600L to null, 300L to reply(20.0))
        val stats = computeWindowStats(ring, 5_000)
        assertEquals(3, stats.count)
        assertEquals(1, stats.lost)
        assertEquals(0, stats.localFaults)
    }

    @Test
    fun outage_share_is_measured_over_observed_coverage_only() {
        // Two failures 600 ms apart inside a 5 s window. The app has only been
        // watching for 900 ms, so it must not claim five seconds of outage.
        val ring = ringOf(900L to null, 300L to null)
        val stats = computeWindowStats(ring, 5_000)
        assertTrue(stats.coveredMs <= 1_000, "claimed ${stats.coveredMs} ms of coverage")
        val gone = assertNotNull(stats.gonePct)
        assertTrue(gone <= 100f)
    }

    @Test
    fun averages_keep_sub_millisecond_resolution() {
        val ring = ringOf(300L to reply(0.2), 200L to reply(0.4), 100L to reply(0.6))
        val avg = assertNotNull(computeWindowStats(ring, 5_000).avg)
        assertTrue(avg > 0.0 && avg < 1.0, "average collapsed to $avg")
    }

    @Test
    fun jitter_is_the_mean_absolute_successive_difference() {
        // 10, 14, 12 -> |14-10| = 4, |12-14| = 2, mean 3.
        val ring = ringOf(300L to reply(10.0), 200L to reply(14.0), 100L to reply(12.0))
        assertEquals(3.0, assertNotNull(computeWindowStats(ring, 5_000).jitter), 0.0001)
    }

    @Test
    fun jitter_does_not_pair_across_a_loss() {
        val ring = ringOf(400L to reply(10.0), 300L to null, 200L to reply(50.0))
        // The only two replies are separated by a loss, so no adjacent pair exists.
        assertNull(computeWindowStats(ring, 5_000).jitter)
    }

    @Test
    fun an_empty_window_reports_nothing_rather_than_zero() {
        val stats = computeWindowStats(RingBuffer(8), 5_000)
        assertEquals(0, stats.count)
        assertNull(stats.avg)
        assertNull(stats.min)
        assertNull(stats.max)
        assertNull(stats.gonePct)
    }

    @Test
    fun the_drawn_window_never_falls_below_the_timeout() {
        // A loss lands at its send moment, one timeout in the past. A window
        // narrower than that could never render one.
        assertTrue(visibleWindowMs(1_000, PanelLayout.COLUMN) >= PING_TIMEOUT_MS)
        assertTrue(visibleWindowMs(5_000, PanelLayout.GRID) >= PING_TIMEOUT_MS)
        // Above the floor, the grid still halves as intended.
        assertEquals(10_000, visibleWindowMs(20_000, PanelLayout.GRID))
        assertEquals(20_000, visibleWindowMs(20_000, PanelLayout.COLUMN))
    }
}
