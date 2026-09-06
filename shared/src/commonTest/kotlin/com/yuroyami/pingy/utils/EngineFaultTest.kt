package com.yuroyami.pingy.utils

import com.yuroyami.pingy.logic.Ping
import com.yuroyami.pingy.logic.PingEvent
import com.yuroyami.pingy.logic.PingKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * What the engine does when the trouble is local.
 *
 * A dead socket used to resolve every probe in the air as TIMEOUT, whatever
 * their age, so a local failure was reported as the target dropping packets.
 */
class EngineFaultTest {

    private suspend fun firstVerdict(transport: IcmpTransport): Ping =
        withContext(Dispatchers.Default) {
            val events = Channel<PingEvent>(Channel.UNLIMITED)
            val seen = mutableListOf<PingEvent>()
            val engine = PingEngine("example.invalid", 32, 0L, transport)
            assertTrue(engine.start { events.trySend(it) }, "engine refused to start")
            val verdict = withTimeoutOrNull(5_000) {
                var found: PingEvent.Resolved? = null
                while (found == null) {
                    val event = events.receive()
                    seen += event
                    found = event as? PingEvent.Resolved
                }
                found
            }
            engine.stopAndJoin()
            assertNotNull(verdict, "no verdict arrived within 5 seconds; saw $seen").ping
        }

    @Test
    fun a_socket_error_right_after_a_send_is_interrupted_not_a_timeout() =
        runTest(timeout = 30.seconds) {
            val transport = ScriptedTransport(awaits = ArrayDeque(listOf(AWAIT_SOCK_ERR)))
            val verdict = firstVerdict(transport)
            assertEquals(
                PingKind.INTERRUPTED,
                verdict.kind,
                "a probe milliseconds old cannot have missed a 3 second deadline",
            )
            assertEquals(false, verdict.isLoss, "an interrupted probe is not packet loss")
            assertEquals(true, verdict.wasSent, "it did leave the device")
        }

    @Test
    fun power_state_is_read_on_the_first_loop_turn() = runTest(timeout = 30.seconds) {
        val transport = ScriptedTransport()
        transport.replyTo = { _, sendUsec -> sendUsec + 500 }
        firstVerdict(transport)
        assertTrue(transport.powerQueries >= 1, "power state was never queried")
    }

    @Test
    fun a_healthy_reply_still_comes_back_as_a_reply() = runTest(timeout = 30.seconds) {
        val transport = ScriptedTransport()
        transport.replyTo = { _, sendUsec -> sendUsec + 2_500 }
        val verdict = firstVerdict(transport)
        assertEquals(PingKind.REPLY, verdict.kind, "verdict was $verdict")
        assertEquals(2.5, verdict.rttMs)
    }
}
