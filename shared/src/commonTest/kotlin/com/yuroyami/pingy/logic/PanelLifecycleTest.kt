package com.yuroyami.pingy.logic

import com.yuroyami.pingy.utils.ProbeEngine
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** An engine whose stop takes exactly as long as the test wants it to. */
private class FakeEngine : ProbeEngine {
    val stopGate = CompletableDeferred<Unit>()
    var started = false; private set

    override fun start(onEvent: (PingEvent) -> Unit): Boolean {
        started = true
        return true
    }

    override fun stop() {
        stopGate.complete(Unit)
    }

    override suspend fun stopAndJoin() {
        stopGate.await()
    }

    override fun updateInterval(intervalMs: Long) = Unit
    override fun updatePacketSize(packetSize: Int) = Unit
}

/**
 * Starting and stopping a panel.
 *
 * Coming back to the app before a slow stop finished used to leave the panel
 * dead: the resume saw an engine still installed and returned, then the stop
 * cleared it. Two engines could also briefly share the one history buffer.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PanelLifecycleTest {

    private fun panelWith(engines: MutableList<FakeEngine>, scheduler: kotlinx.coroutines.test.TestCoroutineScheduler) =
        PingPanel("127.0.0.1", StandardTestDispatcher(scheduler)) { _, _, _ ->
            FakeEngine().also(engines::add)
        }

    @Test
    fun a_resume_arriving_during_a_slow_stop_wins() = runTest {
        val engines = mutableListOf<FakeEngine>()
        val panel = panelWith(engines, testScheduler)
        try {
            panel.startPinging()
            runCurrent()
            assertTrue(panel.running.value, "the panel should be live after a start")

            val pause = launch { panel.stopPingingAndJoin() }
            runCurrent()
            assertFalse(pause.isCompleted, "the stop must still be waiting on the engine")

            panel.startPinging()          // the user came back before the stop finished
            engines[0].stopGate.complete(Unit)
            pause.join()
            runCurrent()

            assertTrue(panel.running.value, "the later intent has to win")
            assertEquals(2, engines.size, "the new engine starts only after the old one released")
        } finally {
            panel.close()
        }
    }

    @Test
    fun stopping_twice_is_idempotent() = runTest {
        val engines = mutableListOf<FakeEngine>()
        val panel = panelWith(engines, testScheduler)
        try {
            panel.startPinging()
            runCurrent()
            engines[0].stopGate.complete(Unit)

            panel.stopPingingAndJoin()
            panel.stopPingingAndJoin()
            runCurrent()

            assertFalse(panel.running.value)
            assertFalse(panel.wantsToRun)
            assertEquals(1, engines.size, "a second stop must not build anything")
        } finally {
            panel.close()
        }
    }

    @Test
    fun starting_twice_keeps_one_engine() = runTest {
        val engines = mutableListOf<FakeEngine>()
        val panel = panelWith(engines, testScheduler)
        try {
            panel.startPinging()
            panel.startPinging()
            runCurrent()
            assertEquals(1, engines.size, "one panel owns one engine")
            assertTrue(panel.wantsToRun)
        } finally {
            panel.close()
        }
    }
}
