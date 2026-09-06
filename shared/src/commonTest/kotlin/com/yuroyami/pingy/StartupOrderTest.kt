package com.yuroyami.pingy

import com.yuroyami.pingy.logic.PanelSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * What happens when the store is slow.
 *
 * Restoration always started probing, whatever had happened while the read was
 * in flight, because a pause that ran before any panel existed had nothing to
 * record and left no trace for the restore to consult.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StartupOrderTest {

    @BeforeTest
    fun useTestDispatcher() = Dispatchers.setMain(StandardTestDispatcher())

    @AfterTest
    fun restoreDispatcher() = Dispatchers.resetMain()

    @Test
    fun going_to_the_background_before_the_load_finishes_keeps_panels_stopped() = runTest {
        val store = GatedStore(listOf(PanelSpec(ip = "1.1.1.1")))
        val engines = mutableListOf<InertEngine>()
        val vm = PingyViewmodel(store) { _, _, _ -> InertEngine().also(engines::add) }
        try {
            advanceUntilIdle()
            vm.pauseMonitoring()          // the app left the foreground first
            store.gate.complete(Unit)     // and only then did the store answer
            advanceUntilIdle()

            assertEquals(1, vm.panels.size, "the saved panel should still be restored")
            assertFalse(
                vm.panels.single().wantsToRun,
                "a restored panel must not start probing while the app is in the background",
            )
            assertEquals(0, engines.size, "no engine should have been built at all")

            vm.resumeMonitoring()
            advanceUntilIdle()
            assertTrue(vm.panels.single().wantsToRun, "coming back has to start it")
        } finally {
            vm.panels.toList().forEach { vm.removePanel(it) }
        }
    }

    @Test
    fun a_normal_foreground_start_probes_right_away() = runTest {
        val store = GatedStore(listOf(PanelSpec(ip = "1.1.1.1")))
        val vm = PingyViewmodel(store) { _, _, _ -> InertEngine() }
        try {
            store.gate.complete(Unit)
            advanceUntilIdle()
            assertTrue(vm.panels.single().wantsToRun)
        } finally {
            vm.panels.toList().forEach { vm.removePanel(it) }
        }
    }
}
