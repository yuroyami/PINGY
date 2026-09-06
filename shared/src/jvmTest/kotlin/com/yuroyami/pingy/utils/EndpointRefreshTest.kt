package com.yuroyami.pingy.utils

import com.yuroyami.pingy.logic.PingEvent
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Recovering from an address that stopped answering.
 *
 * A hostname resolves once and the answer was cached until a socket error.
 * Silence produces no socket error, so a target whose address had moved stayed
 * red forever while the engine kept probing the dead one.
 */
class EndpointRefreshTest {

    @Test
    fun sustained_silence_asks_dns_again() = runBlocking {
        // Never replies, so every probe reaps as a timeout.
        val transport = ScriptedTransport(idle = { budget -> Thread.sleep(budget.toLong()) })
        val endpoints = mutableListOf<String>()
        val engine = PingEngine("example.invalid", 32, 1_000L, transport)
        engine.start { if (it is PingEvent.Endpoint) endpoints += it.ipv4 }
        try {
            // Sends at 0s, 1s, 2s ...; each times out three seconds later, so
            // the fifth consecutive timeout lands around eight seconds in.
            delay(11_000)
            assertTrue(
                transport.resolves >= 2,
                "silence never triggered a second lookup (resolves=${transport.resolves})",
            )
            assertEquals(transport.resolves, endpoints.size, "every resolution should be announced")
        } finally {
            engine.stopAndJoin()
        }
    }

    @Test
    fun a_healthy_target_is_resolved_once() = runBlocking {
        val transport = ScriptedTransport(idle = { budget -> Thread.sleep(budget.toLong()) })
        transport.replyTo = { _, sendUsec -> sendUsec + 500 }
        val endpoints = mutableListOf<String>()
        val engine = PingEngine("example.invalid", 32, 100L, transport)
        engine.start { if (it is PingEvent.Endpoint) endpoints += it.ipv4 }
        try {
            delay(1_500)
            assertEquals(1, transport.resolves, "a replying target must not be looked up again")
            assertEquals(listOf("192.0.2.1"), endpoints)
        } finally {
            engine.stopAndJoin()
        }
    }
}
