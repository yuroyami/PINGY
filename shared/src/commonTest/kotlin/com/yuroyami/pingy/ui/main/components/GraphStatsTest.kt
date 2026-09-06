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
    fun a_timeout_that_just_expired_is_still_counted_at_the_minimum_window() {
        // A verdict only lands three seconds after its send, by which point the
        // send is past a three second horizon. Dropping the boundary sample hid
        // every loss on a failing link at the shortest setting.
        val ring = ringOf(3_010L to null, 10L to Ping.pending(now))
        val stats = computeWindowStats(ring, PING_TIMEOUT_MS.toLong(), now)
        assertEquals(1, stats.count, "the expired timeout has to stay visible")
        assertEquals(1, stats.lost)
        assertEquals(1, stats.pending)
    }

    @Test
    fun the_slot_crossing_the_left_edge_is_clipped_rather_than_dropped() {
        // Sent 5.1 s ago and lost, so four of the five visible seconds are dark.
        val ring = ringOf(5_100L to null, 1_000L to reply(1.0))
        val stats = computeWindowStats(ring, 5_000, now)
        assertEquals(2, stats.count)
        assertEquals(1, stats.lost)
        assertEquals(80f, assertNotNull(stats.gonePct), 0.5f)
        assertEquals(5_000L, stats.coveredMs)
    }

    @Test
    fun outage_share_moves_continuously_as_a_slot_leaves_the_window() {
        // Slide the same fixed history across the horizon: no cliff.
        var previous = -1f
        for (window in 4_000L..6_000L step 100L) {
            val ring = ringOf(5_100L to null, 1_000L to reply(1.0))
            val gone = assertNotNull(computeWindowStats(ring, window, now).gonePct)
            if (previous >= 0f) {
                assertTrue(
                    kotlin.math.abs(gone - previous) < 5f,
                    "outage jumped from $previous to $gone at window $window",
                )
            }
            previous = gone
        }
    }

    @Test
    fun interrupted_probes_enter_neither_loss_nor_outage() {
        val ring = ringOf(
            900L to reply(10.0),
            600L to Ping.interrupted(now),
            300L to reply(20.0),
        )
        val stats = computeWindowStats(ring, 5_000)
        assertEquals(2, stats.count, "an interrupted probe has no verdict to count")
        assertEquals(0, stats.lost)
        assertEquals(1, stats.interrupted)
        assertEquals(0f, stats.gonePct)
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
    fun pending_probes_have_no_verdict_yet_so_they_are_neither_sent_nor_lost() {
        val ring = ringOf(900L to reply(10.0), 600L to Ping.pending(now), 300L to reply(20.0))
        val stats = computeWindowStats(ring, 5_000)
        assertEquals(2, stats.count)
        assertEquals(0, stats.lost)
    }

    @Test
    fun each_verdict_owns_its_own_slot_forward_to_the_next_send() {
        // reply 900 -> 600 ok (300), timeout 600 -> 300 gone (300),
        // pending 300 -> 100 unknown (excluded), reply 100 -> now ok (100).
        // Known time 700 ms, gone 300 ms.
        val ring = ringOf(
            900L to reply(10.0),
            600L to null,
            300L to Ping.pending(now),
            100L to reply(20.0),
        )
        val gone = assertNotNull(computeWindowStats(ring, 5_000).gonePct)
        assertEquals(300f / 700f * 100f, gone, 1.5f)
    }

    @Test
    fun the_drawn_window_never_falls_below_the_timeout() {
        // A loss lands at its send moment, one timeout in the past. A window
        // narrower than that could never render one.
        assertTrue(visibleWindowMs(1_000, PanelLayout.COLUMN) >= PING_TIMEOUT_MS)
        assertTrue(visibleWindowMs(1_000, PanelLayout.GRID) >= PING_TIMEOUT_MS)
    }

    @Test
    fun the_chosen_window_is_the_same_in_every_layout() {
        // Switching layout must not change the period being compared, or the
        // Window setting stops describing what is on screen.
        PanelLayout.entries.forEach { layout ->
            assertEquals(20_000, visibleWindowMs(20_000, layout), "layout $layout changed the window")
            assertEquals(10_000, visibleWindowMs(10_000, layout), "layout $layout changed the window")
        }
    }
}
