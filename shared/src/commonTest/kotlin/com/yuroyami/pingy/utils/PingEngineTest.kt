package com.yuroyami.pingy.utils

import com.yuroyami.pingy.logic.LocalFault
import com.yuroyami.pingy.logic.Ping
import com.yuroyami.pingy.logic.PingEvent
import com.yuroyami.pingy.logic.PingKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Engine behaviour against real sockets.
 *
 * These run on every target, which matters: on Kotlin/Native the
 * timeout-reaping path can throw an uncaught `ConcurrentModificationException`
 * that the JVM never sees, because `LinkedHashMap` is a typealias for the
 * common `HashMap` there and its entry references go stale the moment the map
 * changes. Running the same test on the iOS simulator is what makes this a
 * real guard rather than a hopeful comment.
 */
class PingEngineTest {

    /**
     * Real sockets on a real clock.
     *
     * [runTest] drives a virtual scheduler, so a `withTimeoutOrNull` under it
     * expires instantly in virtual time while the engine has not yet touched the
     * network. The waiting therefore happens on a real dispatcher.
     */
    private suspend fun collectEvents(
        host: String,
        count: Int,
        timeoutMs: Long,
        wanted: (PingEvent) -> Boolean = { it is PingEvent.Resolved || it is PingEvent.Fault },
    ): List<PingEvent> = withContext(Dispatchers.Default) {
        val channel = Channel<PingEvent>(Channel.UNLIMITED)
        val engine = PingEngine(host = host, packetSize = 32, intervalMs = 0L)
        val started = engine.start { channel.trySend(it) }
        assertTrue(started, "engine refused to start")

        val out = mutableListOf<PingEvent>()
        withTimeoutOrNull(timeoutMs) {
            while (out.count(wanted) < count) out.add(channel.receive())
        }
        engine.stopAndJoin()
        out
    }

    /** Verdicts and faults only, the shape the older tests were written against. */
    private suspend fun collect(host: String, count: Int, timeoutMs: Long): List<Ping> =
        collectEvents(host, count, timeoutMs)
            .filterIsInstance<PingEvent.Probe>()
            .filter { it !is PingEvent.Sent }
            .map { it.ping }

    @Test
    fun every_probe_is_announced_at_send_time_before_its_verdict() = runTest(timeout = 90.seconds) {
        if (!icmpTransportAvailable()) return@runTest
        val events = collectEvents("127.0.0.1", count = 3, timeoutMs = 15_000)
        val verdicts = events.filterIsInstance<PingEvent.Resolved>()
        assertTrue(verdicts.isNotEmpty(), "expected verdicts, got $events")
        for (verdict in verdicts) {
            val sentIndex = events.indexOfFirst { it is PingEvent.Sent && it.seq == verdict.seq }
            assertTrue(sentIndex >= 0, "verdict for seq ${verdict.seq} had no send announcement")
            assertTrue(sentIndex < events.indexOf(verdict), "send must precede its verdict")
            val sent = events[sentIndex] as PingEvent.Sent
            assertEquals(PingKind.PENDING, sent.ping.kind)
            // Same probe, same slot: the verdict carries the send moment.
            assertEquals(sent.ping.timestamp, verdict.ping.timestamp)
        }
    }

    @Test
    fun a_black_hole_probe_resolves_to_a_timeout_under_its_own_seq() = runTest(timeout = 90.seconds) {
        if (!icmpTransportAvailable()) return@runTest
        val events = collectEvents("192.0.2.1", count = 1, timeoutMs = 25_000) { it is PingEvent.Resolved }
        val verdict = events.filterIsInstance<PingEvent.Resolved>().firstOrNull()
            ?: return@runTest  // a local fault (no route) is a legitimate outcome here
        assertEquals(PingKind.TIMEOUT, verdict.ping.kind)
        assertTrue(events.any { it is PingEvent.Sent && it.seq == verdict.seq })
    }

    @Test
    fun loopback_produces_real_replies() = runTest(timeout = 90.seconds) {
        if (!icmpTransportAvailable()) return@runTest
        val pings = collect("127.0.0.1", count = 3, timeoutMs = 15_000)
        val replies = pings.filter { it.kind == PingKind.REPLY }
        assertTrue(replies.isNotEmpty(), "expected at least one reply, got $pings")
        replies.forEach {
            val rtt = it.rttMs
            assertTrue(rtt != null && rtt >= 0.0 && rtt < PING_TIMEOUT_MS, "implausible RTT $rtt")
        }
    }

    @Test
    fun sub_millisecond_rtt_is_not_floored_to_zero() = runTest(timeout = 90.seconds) {
        if (!icmpTransportAvailable()) return@runTest
        val replies = collect("127.0.0.1", count = 5, timeoutMs = 15_000)
            .filter { it.kind == PingKind.REPLY }
        if (replies.isEmpty()) return@runTest
        // Loopback is well under a millisecond. Rounding at capture time turned
        // every one of these into 0 and collapsed jitter with them.
        assertTrue(
            replies.any { (it.rttMs ?: 0.0) > 0.0 },
            "every loopback RTT came back as exactly 0.0; precision was lost",
        )
    }

    @Test
    fun an_unresolvable_host_is_a_local_fault_not_packet_loss() = runTest(timeout = 90.seconds) {
        // The whole point of the typed outcome: this device failed, the target
        // did not. Reporting it as loss made the loss percentage meaningless.
        val pings = collect("pingy.invalid.", count = 1, timeoutMs = 20_000)
        assertTrue(pings.isNotEmpty(), "engine reported nothing at all")
        val first = pings.first()
        assertEquals(PingKind.LOCAL_FAULT, first.kind)
        assertEquals(LocalFault.RESOLVE_FAILED, first.fault)
        assertFalse(first.isLoss, "a DNS failure must never count as packet loss")
        assertFalse(first.wasSent, "no probe left the device")
    }

    @Test
    fun timeout_reaping_does_not_kill_the_engine() = runTest(timeout = 90.seconds) {
        if (!icmpTransportAvailable()) return@runTest
        // 192.0.2.1 is TEST-NET-1 (RFC 5737): routable syntax, never answers.
        // Every probe must therefore reap as a timeout. A reaper that reads a
        // stale map entry here throws out of the coroutine on Kotlin/Native and
        // takes the whole process down on the very first timeout.
        val pings = collect("192.0.2.1", count = 2, timeoutMs = 25_000)
        assertTrue(pings.isNotEmpty(), "engine died before reporting anything")
        assertTrue(
            pings.all { it.kind == PingKind.TIMEOUT || it.kind == PingKind.LOCAL_FAULT },
            "unexpected replies from a black-hole address: $pings",
        )
        // Surviving to report a second event is the real check here.
        assertTrue(pings.size >= 2, "engine stopped after the first timeout")
    }

    @Test
    fun start_is_not_repeatable() = runTest(timeout = 90.seconds) {
        val engine = PingEngine(host = "127.0.0.1", packetSize = 32, intervalMs = 1_000L)
        assertTrue(engine.start { }, "first start should be accepted")
        assertFalse(engine.start { }, "a second start must not run a second loop")
        engine.stopAndJoin()
    }

    @Test
    fun stop_is_idempotent_and_joinable() = runTest(timeout = 90.seconds) {
        val engine = PingEngine(host = "127.0.0.1", packetSize = 32, intervalMs = 1_000L)
        engine.start { }
        engine.stop()
        engine.stop()
        engine.stopAndJoin()
    }
}
