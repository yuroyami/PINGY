package com.yuroyami.pingy.ui.main.components

import com.yuroyami.pingy.logic.Ping
import com.yuroyami.pingy.logic.PingKind
import com.yuroyami.pingy.logic.RingBuffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

/**
 * The geometry rules behind the graph, checked without a canvas.
 *
 * The one that matters most: a probe's bar spans its own slot, from its send
 * to the next send. What the neighbouring probe later turns out to be cannot
 * change that width, which is what stopped green history from turning blank
 * three seconds after the fact.
 */
class GraphMathTest {

    private val now = TimeSource.Monotonic.markNow()
    private fun ago(ms: Long) = now - ms.milliseconds
    private fun ageOf(p: Ping) = (now - p.timestamp).inWholeMilliseconds

    @Test
    fun a_slot_ends_at_the_next_send_no_matter_how_the_neighbour_resolves() {
        val visible = arrayListOf(
            Ping.reply(20.0, ago(1_000)),
            Ping.pending(ago(750)),
            Ping.reply(30.0, ago(0)),
        )
        assertEquals(750L, slotEndAgeMs(visible, 0, ::ageOf))
        // The neighbour times out three seconds later. Same slot, same width.
        visible[1] = Ping.timeout(ago(750))
        assertEquals(750L, slotEndAgeMs(visible, 0, ::ageOf))
    }

    @Test
    fun the_newest_slot_runs_to_now() {
        val visible = arrayListOf(Ping.reply(20.0, ago(1_000)), Ping.pending(ago(300)))
        assertEquals(0L, slotEndAgeMs(visible, 1, ::ageOf))
    }

    @Test
    fun a_reply_next_to_a_loss_still_paints_its_own_slot_in_the_ridge_style() {
        // On a choking link most replies stand alone between losses. If they
        // only showed through a slope to a showing neighbour, the ridge would
        // stay blank while the loss counter said one in four got through.
        val shows: (Ping) -> Boolean = { it.kind == PingKind.REPLY }
        val alone = listOf(Ping.reply(20.0, ago(1_000)), Ping.timeout(ago(750)), Ping.reply(30.0, ago(500)))
        assertEquals(true, fillsOwnSlot(alone, 0, shows))
        val paired = listOf(Ping.reply(20.0, ago(1_000)), Ping.reply(30.0, ago(750)))
        assertEquals(false, fillsOwnSlot(paired, 0, shows), "a slope to the neighbour covers it")
        assertEquals(true, fillsOwnSlot(paired, 1, shows), "the newest runs level to now")
    }

    @Test
    fun gather_returns_the_window_oldest_first_with_one_anchor_beyond_it() {
        val ring = RingBuffer<Ping>(16)
        listOf(8_000L, 6_000L, 4_000L, 2_000L, 500L).forEach { ring.add(Ping.reply(1.0, ago(it))) }
        val out = ArrayList<Ping>()
        gatherVisible(ring, now, freezeOffsetMs = 0L, thresholdMs = 5_000L, out = out)
        // 8000 is two entries past the horizon and stays out; 6000 rides along
        // as the anchor so the bar bridging into view keeps its left edge.
        assertEquals(listOf(6_000L, 4_000L, 2_000L, 500L), out.map(::ageOf))
    }

    @Test
    fun gather_stops_at_an_age_drop_because_that_is_the_writer_lapping_it() {
        // Newest-first, ages must only grow. An older slot holding a younger
        // entry means the ring wrapped underneath the walk.
        val ring = RingBuffer<Ping>(16)
        ring.add(Ping.reply(1.0, ago(500)))
        ring.add(Ping.reply(1.0, ago(4_000)))
        val out = ArrayList<Ping>()
        gatherVisible(ring, now, freezeOffsetMs = 0L, thresholdMs = 5_000L, out = out)
        assertEquals(listOf(4_000L), out.map(::ageOf))
    }

    @Test
    fun gather_skips_entries_younger_than_the_frozen_moment() {
        val ring = RingBuffer<Ping>(16)
        ring.add(Ping.reply(1.0, ago(2_000)))
        ring.add(Ping.reply(1.0, ago(100)))
        val out = ArrayList<Ping>()
        // Frozen one second ago: the 100 ms entry was born after the freeze.
        gatherVisible(ring, now, freezeOffsetMs = 1_000L, thresholdMs = 5_000L, out = out)
        assertEquals(listOf(2_000L), out.map(::ageOf))
    }

    @Test
    fun fold_keeps_the_worst_news_in_a_shared_pixel_column() {
        // Column 0 is the anchor and is never folded. Everything else here
        // shares column 7.
        val col: (Ping) -> Int = { p -> if (ageOf(p) > 9_000L) 0 else 7 }

        val lossWins = arrayListOf(
            Ping.reply(1.0, ago(10_000)),
            Ping.reply(10.0, ago(300)), Ping.timeout(ago(200)), Ping.reply(50.0, ago(100)),
        )
        foldColumns(lossWins, col)
        assertEquals(listOf(PingKind.REPLY, PingKind.TIMEOUT), lossWins.map { it.kind })

        val pendingBeatsReply = arrayListOf(
            Ping.reply(1.0, ago(10_000)),
            Ping.reply(50.0, ago(300)), Ping.pending(ago(200)),
        )
        foldColumns(pendingBeatsReply, col)
        assertEquals(listOf(PingKind.REPLY, PingKind.PENDING), pendingBeatsReply.map { it.kind })

        val worstRtt = arrayListOf(
            Ping.reply(1.0, ago(10_000)),
            Ping.reply(10.0, ago(300)), Ping.reply(50.0, ago(200)), Ping.reply(20.0, ago(100)),
        )
        foldColumns(worstRtt, col)
        assertEquals(listOf(1.0, 50.0), worstRtt.map { it.rttMs })
    }
}
