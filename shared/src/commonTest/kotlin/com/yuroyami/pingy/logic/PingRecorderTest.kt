package com.yuroyami.pingy.logic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

/**
 * One slot per probe, written at send time and resolved in place.
 *
 * This is the contract the graph relies on: the ring is always in send order,
 * and a verdict never moves anything, it only fills the slot its probe already
 * owns.
 */
class PingRecorderTest {

    private val epoch = TimeSource.Monotonic.markNow()
    private fun at(ms: Long) = epoch + ms.milliseconds

    @Test
    fun a_verdict_replaces_its_pending_entry_in_place() {
        val ring = RingBuffer<Ping>(16)
        val recorder = PingRecorder(ring)
        recorder.accept(PingEvent.Sent(1, Ping.pending(at(0))))
        recorder.accept(PingEvent.Sent(2, Ping.pending(at(250))))
        recorder.accept(PingEvent.Resolved(1, Ping.reply(12.0, at(0))))

        val snap = ring.snapshot()
        assertEquals(2, snap.size, "a verdict must not add an entry")
        assertEquals(PingKind.REPLY, snap[0].kind)
        assertEquals(PingKind.PENDING, snap[1].kind)
    }

    @Test
    fun a_late_timeout_lands_in_its_own_slot_and_the_ring_stays_in_send_order() {
        // Probe 3 answers first, probe 1 times out last. The old buffer would
        // have appended [reply 3, timeout 1] and left the graph to re-sort.
        val ring = RingBuffer<Ping>(16)
        val recorder = PingRecorder(ring)
        recorder.accept(PingEvent.Sent(1, Ping.pending(at(0))))
        recorder.accept(PingEvent.Sent(2, Ping.pending(at(250))))
        recorder.accept(PingEvent.Sent(3, Ping.pending(at(500))))
        recorder.accept(PingEvent.Resolved(3, Ping.reply(40.0, at(500))))
        recorder.accept(PingEvent.Resolved(1, Ping.timeout(at(0))))

        val snap = ring.snapshot()
        assertEquals(listOf(PingKind.TIMEOUT, PingKind.PENDING, PingKind.REPLY), snap.map { it.kind })
        for (i in 1 until snap.size) {
            assertTrue(snap[i - 1].timestamp <= snap[i].timestamp, "ring left send order at $i")
        }
    }

    @Test
    fun a_fault_is_appended_as_its_own_entry() {
        val ring = RingBuffer<Ping>(16)
        val recorder = PingRecorder(ring)
        recorder.accept(PingEvent.Sent(1, Ping.pending(at(0))))
        recorder.accept(PingEvent.Fault(Ping.localFault(LocalFault.SOCKET_LOST, at(10))))
        assertEquals(listOf(PingKind.PENDING, PingKind.LOCAL_FAULT), ring.snapshot().map { it.kind })
    }

    @Test
    fun a_verdict_for_an_unknown_probe_is_ignored() {
        val ring = RingBuffer<Ping>(16)
        val recorder = PingRecorder(ring)
        recorder.accept(PingEvent.Sent(1, Ping.pending(at(0))))
        recorder.accept(PingEvent.Resolved(99, Ping.reply(1.0, at(0))))
        assertEquals(listOf(PingKind.PENDING), ring.snapshot().map { it.kind })
    }

    @Test
    fun a_verdict_is_applied_once() {
        val ring = RingBuffer<Ping>(16)
        val recorder = PingRecorder(ring)
        recorder.accept(PingEvent.Sent(1, Ping.pending(at(0))))
        recorder.accept(PingEvent.Resolved(1, Ping.reply(5.0, at(0))))
        recorder.accept(PingEvent.Resolved(1, Ping.timeout(at(0))))
        assertEquals(listOf(PingKind.REPLY), ring.snapshot().map { it.kind })
    }
}
