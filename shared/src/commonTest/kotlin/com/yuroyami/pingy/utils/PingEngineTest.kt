package com.yuroyami.pingy.utils

import com.yuroyami.pingy.logic.LocalFault
import com.yuroyami.pingy.logic.Ping
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
 * These run on every target, which matters: the timeout-reaping path threw an
 * uncaught `ConcurrentModificationException` on Kotlin/Native only, because
 * `LinkedHashMap` is a typealias for the common `HashMap` there and its entry
 * references are invalidated the moment the map changes. Running the same test
 * on the iOS simulator is what makes that a regression test rather than a
 * hopeful comment.
 */
class PingEngineTest {

    /**
     * Real sockets on a real clock.
     *
     * [runTest] drives a virtual scheduler, so a `withTimeoutOrNull` under it
     * expires instantly in virtual time while the engine has not yet touched the
     * network. The waiting therefore happens on a real dispatcher.
     */
    private suspend fun collect(
        host: String,
        count: Int,
        timeoutMs: Long,
    ): List<Ping> = withContext(Dispatchers.Default) {
        val channel = Channel<Ping>(Channel.UNLIMITED)
        val engine = PingEngine(host = host, packetSize = 32, intervalMs = 0L)
        val started = engine.start { channel.trySend(it) }
        assertTrue(started, "engine refused to start")

        val out = mutableListOf<Ping>()
        withTimeoutOrNull(timeoutMs) {
            while (out.size < count) out.add(channel.receive())
        }
        engine.stopAndJoin()
        out
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
        // Every probe must therefore reap as a timeout. On Kotlin/Native the old
        // reaper read an invalidated map entry here and threw out of the
        // coroutine, terminating the process on the very first timeout.
        val pings = collect("192.0.2.1", count = 2, timeoutMs = 25_000)
        assertTrue(pings.isNotEmpty(), "engine died before reporting anything")
        assertTrue(
            pings.all { it.kind == PingKind.TIMEOUT || it.kind == PingKind.LOCAL_FAULT },
            "unexpected replies from a black-hole address: $pings",
        )
        // Surviving to report a second event is the actual regression check.
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
