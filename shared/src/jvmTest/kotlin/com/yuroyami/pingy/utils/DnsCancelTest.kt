package com.yuroyami.pingy.utils

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Stopping an engine while its resolver is stuck.
 *
 * Platform name resolution is a blocking call that cannot be interrupted, so
 * cancellation cannot take effect until it returns. What it must never do is
 * let the stopped engine carry on and open a socket or put a probe on the wire
 * once the answer finally arrives.
 */
class DnsCancelTest {

    @Test
    fun nothing_opens_or_sends_after_stop_even_when_dns_returns_late() = runBlocking {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val base = ScriptedTransport()
        val blockingResolver = object : IcmpTransport by base {
            override fun resolve(host: String): String? {
                entered.countDown()
                release.await()
                return base.resolve(host)
            }
        }

        val engine = PingEngine("slow.example", 32, 0L, blockingResolver)
        assertTrue(engine.start { }, "engine refused to start")
        assertTrue(entered.await(5, TimeUnit.SECONDS), "the engine never reached the resolver")

        val join = launch { engine.stopAndJoin() }
        delay(300)
        assertFalse(join.isCompleted, "the join cannot finish while the resolver is stuck")

        release.countDown()
        join.join()

        assertEquals(0, base.opens, "a stopped engine must not open a socket")
        assertEquals(0, base.sends, "a stopped engine must not send a probe")
    }

    @Test
    fun a_join_that_is_cancelled_says_so_instead_of_reporting_success() = runBlocking {
        val release = CountDownLatch(1)
        val base = ScriptedTransport()
        val blockingResolver = object : IcmpTransport by base {
            override fun resolve(host: String): String? {
                release.await()
                return base.resolve(host)
            }
        }
        val engine = PingEngine("slow.example", 32, 0L, blockingResolver)
        engine.start { }

        val finished = withTimeoutOrNull(200) { engine.stopAndJoin() }
        release.countDown()

        assertNull(finished, "stopAndJoin claimed to finish while the loop still held its socket")
    }

    @Test
    fun a_resolver_that_answers_before_stop_still_probes_normally() = runBlocking {
        val base = ScriptedTransport()
        base.replyTo = { _, sendUsec -> sendUsec + 1_000 }
        val engine = PingEngine("example.invalid", 32, 0L, base)
        engine.start { }
        delay(300)
        engine.stopAndJoin()
        assertTrue(base.sends > 0, "a live engine should have probed")
        assertEquals(1, base.opens)
    }
}
