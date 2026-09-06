package com.yuroyami.pingy.logic

import kotlinx.coroutines.runBlocking
import okio.FileSystem
import okio.Path.Companion.toPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Getting out of an unreadable store.
 *
 * A failed read blocks saving so the old file is never clobbered, which is
 * right. What was missing was any way back: a user could build a whole cockpit
 * that was silently never saved.
 */
class StoreQuarantineTest {

    private val fs = FileSystem.SYSTEM

    @Test
    fun a_broken_store_is_moved_aside_intact_and_reading_starts_over() = runBlocking {
        val path = PingyStore.storeFilePath.toPath()
        path.parent?.let { fs.createDirectories(it) }
        // Other tests in this process may hold a reader that has already cached
        // the file, so start it over before staging a broken one.
        PingyStore.quarantine()
        fs.write(path) { writeUtf8("this is definitely not a protobuf") }

        assertIs<StoreLoad.Failed>(PingyStore.load(), "a corrupt file has to read as a failure")

        val movedTo = assertNotNull(PingyStore.quarantine(), "nothing was moved aside")
        val backup = "${path.parent}/$movedTo".toPath()

        assertFalse(fs.exists(path), "the broken file is still in the way")
        assertTrue(fs.exists(backup), "the old bytes were not kept")
        assertEquals(
            "this is definitely not a protobuf",
            fs.read(backup) { readUtf8() },
            "quarantine must preserve the file byte for byte",
        )

        assertIs<StoreLoad.NotInitialized>(
            PingyStore.load(),
            "after quarantine the app should start over rather than stay broken",
        )

        assertTrue(PingyStore.save(listOf(PanelSpec(ip = "1.1.1.1")), "PINGLETTES", "COLUMN", false))
        val reloaded = assertIs<StoreLoad.Loaded>(PingyStore.load())
        assertEquals(listOf("1.1.1.1"), reloaded.panels.map { it.ip })

        fs.delete(backup, mustExist = false)
        fs.delete(path, mustExist = false)
    }

    @Test
    fun quarantining_nothing_reports_nothing_moved() = runBlocking {
        val path = PingyStore.storeFilePath.toPath()
        PingyStore.quarantine()
        fs.delete(path, mustExist = false)
        assertEquals(null, PingyStore.quarantine())
    }
}
