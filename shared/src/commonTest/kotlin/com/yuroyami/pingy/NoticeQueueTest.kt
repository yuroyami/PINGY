package com.yuroyami.pingy

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
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * The one-line status slot.
 *
 * Every message replaced whatever was there, so a routine notice arriving a
 * second after a removal took away the only way to undo it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NoticeQueueTest {

    @BeforeTest
    fun useTestDispatcher() = Dispatchers.setMain(StandardTestDispatcher())

    @AfterTest
    fun restoreDispatcher() = Dispatchers.resetMain()

    private fun viewmodel() = PingyViewmodel(FlakyStore(failFirst = 0)) { _, _, _ -> InertEngine() }

    @Test
    fun a_plain_message_does_not_take_away_a_pending_undo() = runTest {
        val vm = viewmodel()
        advanceUntilIdle()
        vm.notify("removed", actionLabel = "Undo") { }
        val undo = assertNotNull(vm.notice.value)

        vm.notify("layout changed")

        assertEquals(undo.id, vm.notice.value?.id, "the undo was replaced by an unrelated message")
        assertNotNull(vm.notice.value?.action)
    }

    @Test
    fun the_held_message_appears_once_the_undo_is_done_with() = runTest {
        val vm = viewmodel()
        advanceUntilIdle()
        vm.notify("removed", actionLabel = "Undo") { }
        val undo = assertNotNull(vm.notice.value)
        vm.notify("layout changed")

        vm.dismissNotice(undo.id)

        assertEquals("layout changed", vm.notice.value?.text)
        vm.dismissNotice(assertNotNull(vm.notice.value).id)
        assertNull(vm.notice.value)
    }

    @Test
    fun one_reversible_action_still_replaces_another() = runTest {
        val vm = viewmodel()
        advanceUntilIdle()
        vm.notify("first", actionLabel = "Undo") { }
        vm.notify("second", actionLabel = "Undo") { }
        assertEquals("second", vm.notice.value?.text)
    }
}
