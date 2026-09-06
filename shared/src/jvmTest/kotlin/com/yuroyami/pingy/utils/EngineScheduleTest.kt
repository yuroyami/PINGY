package com.yuroyami.pingy.utils

import com.yuroyami.pingy.logic.PingEvent
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Changing the cadence of a running engine.
 *
 * The setting was stored but the pending deadline was not recomputed, so
 * shortening the interval still waited out whatever the previous one had
 * scheduled.
 */
class EngineScheduleTest {

    @Test
    fun shortening_the_interval_reschedules_instead_of_waiting_out_the_old_deadline() = runBlocking {
        val transport = ScriptedTransport(idle = { budget -> Thread.sleep(budget.toLong()) })
        transport.replyTo = { _, sendUsec -> sendUsec + 500 }
        val sends = Channel<Unit>(Channel.UNLIMITED)

        val engine = PingEngine("example.invalid", 32, 60_000L, transport)
        assertTrue(engine.start { if (it is PingEvent.Sent) sends.trySend(Unit) })
        try {
            sends.receive()          // the first probe leaves
            delay(300)               // and its reply has landed by now

            engine.updateInterval(50)

            val second = withTimeoutOrNull(1_000) { sends.receive() }
            assertNotNull(second, "no second probe within a second of shortening the interval")
        } finally {
            engine.stopAndJoin()
        }
    }

    @Test
    fun lengthening_the_interval_does_not_fire_a_burst() = runBlocking {
        val transport = ScriptedTransport(idle = { budget -> Thread.sleep(budget.toLong()) })
        transport.replyTo = { _, sendUsec -> sendUsec + 500 }
        val engine = PingEngine("example.invalid", 32, 50L, transport)
        engine.start { }
        try {
            delay(300)
            engine.updateInterval(10_000)
            val before = transport.sends
            delay(600)
            assertTrue(
                transport.sends - before <= 1,
                "a longer interval sent ${transport.sends - before} probes in 600 ms",
            )
        } finally {
            engine.stopAndJoin()
        }
    }
}
