package com.yuroyami.pingy

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * Undo used to rebuild a panel from its persisted spec alone. That spec carries
 * neither the Remember flag nor the panel's position, so undoing a removal
 * turned Remember back on and moved the panel to the end.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class UndoRememberTest {

    @BeforeTest
    fun isolateStorage() {
        System.setProperty("user.home", Files.createTempDirectory("pingy-undo-").toString())
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun restoreDispatcher() = Dispatchers.resetMain()

    @Test
    fun undo_keeps_remember_off_and_the_original_position() = runBlocking {
        val vm = PingyViewmodel()
        try {
            delay(200)   // let the store read finish
            vm.addPanel("1.1.1.1")
            vm.addPanel("8.8.8.8")
            vm.addPanel("9.9.9.9")

            val middle = vm.panels[1]
            middle.persistAcrossSessions.value = false

            val removed = vm.removePanel(middle)
            vm.restorePanel(removed)

            assertEquals(
                listOf("1.1.1.1", "8.8.8.8", "9.9.9.9"),
                vm.panels.map { it.ip },
                "the restored panel must go back where it was",
            )
            assertFalse(
                vm.panels[1].persistAcrossSessions.value,
                "Undo must not turn Remember back on",
            )
        } finally {
            vm.panels.toList().forEach { vm.removePanel(it) }
        }
    }

    @Test
    fun undo_keeps_remember_on_when_it_was_on() = runBlocking {
        val vm = PingyViewmodel()
        try {
            delay(200)
            vm.addPanel("1.1.1.1")
            val removed = vm.removePanel(vm.panels.single())
            vm.restorePanel(removed)
            assertEquals(true, vm.panels.single().persistAcrossSessions.value)
        } finally {
            vm.panels.toList().forEach { vm.removePanel(it) }
        }
    }
}
