package com.yuroyami.pingy

import com.yuroyami.pingy.i18n.EnStrings
import com.yuroyami.pingy.logic.PanelSpec
import com.yuroyami.pingy.logic.PingEvent
import com.yuroyami.pingy.logic.StoreApi
import com.yuroyami.pingy.logic.StoreLoad
import com.yuroyami.pingy.utils.ProbeEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** An engine that does nothing, so a view model test never touches a socket. */
internal class InertEngine : ProbeEngine {
    override fun start(onEvent: (PingEvent) -> Unit) = true
    override fun stop() = Unit
    override suspend fun stopAndJoin() = Unit
    override fun updateInterval(intervalMs: Long) = Unit
    override fun updatePacketSize(packetSize: Int) = Unit
}

/** A store whose first [failFirst] writes fail. */
internal class FlakyStore(private val failFirst: Int) : StoreApi {
    var saves = 0; private set
    var lastPanels: List<PanelSpec> = emptyList(); private set

    override suspend fun load(): StoreLoad = StoreLoad.Loaded(emptyList(), null, null, 0)

    override suspend fun save(
        panels: List<PanelSpec>,
        graphStyle: String,
        panelLayout: String,
    ): Boolean {
        saves++
        lastPanels = panels
        return saves > failFirst
    }

    override suspend fun quarantine(): String? = null
}

/** A store whose writes take a known amount of time to land. */
internal class SlowStore(private val writeMs: Long) : StoreApi {
    var saves = 0; private set

    override suspend fun load(): StoreLoad = StoreLoad.Loaded(emptyList(), null, null, 0)

    override suspend fun save(
        panels: List<PanelSpec>,
        graphStyle: String,
        panelLayout: String,
    ): Boolean {
        kotlinx.coroutines.delay(writeMs)
        saves++
        return true
    }

    override suspend fun quarantine(): String? = null
}

/** A store that will not answer until the test lets it. */
internal class GatedStore(private val panels: List<PanelSpec>) : StoreApi {
    val gate = kotlinx.coroutines.CompletableDeferred<Unit>()

    override suspend fun load(): StoreLoad {
        gate.await()
        return StoreLoad.Loaded(panels, null, null, 0)
    }

    override suspend fun save(
        panels: List<PanelSpec>,
        graphStyle: String,
        panelLayout: String,
    ): Boolean = true

    override suspend fun quarantine(): String? = null
}

/**
 * What happens when the disk refuses.
 *
 * The save result was discarded, so a failed write was invisible and nothing
 * tried again until the user happened to change something else.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SaveFailureTest {

    @BeforeTest
    fun useTestDispatcher() = Dispatchers.setMain(StandardTestDispatcher())

    @AfterTest
    fun restoreDispatcher() = Dispatchers.resetMain()

    @Test
    fun a_failed_save_is_retried_without_a_new_edit() = runTest {
        val store = FlakyStore(failFirst = 1)
        val vm = PingyViewmodel(store) { _, _, _ -> InertEngine() }
        try {
            advanceUntilIdle()
            vm.addPanel("1.1.1.1")
            advanceUntilIdle()

            assertTrue(store.saves >= 2, "the failed write was never retried (saves=${store.saves})")
            assertEquals(
                listOf("1.1.1.1"),
                store.lastPanels.map { it.ip },
                "the retry has to carry the newest state, not the one that failed",
            )
        } finally {
            vm.panels.toList().forEach { vm.removePanel(it) }
        }
    }

    @Test
    fun a_write_that_keeps_failing_ends_in_a_notice_with_a_retry() = runTest {
        val store = FlakyStore(failFirst = 99)
        val vm = PingyViewmodel(store) { _, _, _ -> InertEngine() }
        try {
            advanceUntilIdle()
            vm.addPanel("1.1.1.1")
            advanceUntilIdle()

            val notice = vm.notice.value
            assertTrue(notice != null, "nothing told the user their panels are not being saved")
            assertEquals(EnStrings.saveFailed, notice.text)
            assertTrue(notice.action != null, "the notice has to offer a retry")
        } finally {
            vm.panels.toList().forEach { vm.removePanel(it) }
        }
    }

    @Test
    fun flush_and_wait_does_not_return_until_the_write_lands() = runTest {
        val store = SlowStore(writeMs = 200)
        val vm = PingyViewmodel(store) { _, _, _ -> InertEngine() }
        try {
            advanceUntilIdle()
            val flush = async { vm.flushAndWait() }
            advanceTimeBy(150)
            assertTrue(!flush.isCompleted, "the flush returned before the disk had it")
            advanceUntilIdle()
            assertTrue(flush.await(), "the flush should report success")
            assertEquals(1, store.saves)
        } finally {
            vm.panels.toList().forEach { vm.removePanel(it) }
        }
    }

    @Test
    fun a_healthy_store_saves_once_and_says_nothing() = runTest {
        val store = FlakyStore(failFirst = 0)
        val vm = PingyViewmodel(store) { _, _, _ -> InertEngine() }
        try {
            advanceUntilIdle()
            vm.addPanel("1.1.1.1")
            advanceUntilIdle()
            assertEquals(1, store.saves)
        } finally {
            vm.panels.toList().forEach { vm.removePanel(it) }
        }
    }
}
