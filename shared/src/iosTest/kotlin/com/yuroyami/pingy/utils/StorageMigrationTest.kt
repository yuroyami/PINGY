package com.yuroyami.pingy.utils

import com.yuroyami.pingy.logic.STORE_FILE_NAME
import okio.FileSystem
import okio.Path.Companion.toPath
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The store moved from Documents to Application Support without a migration,
 * so an upgrade reported a first run and left the old file orphaned.
 */
class StorageMigrationTest {

    private val fs = FileSystem.SYSTEM
    private val fm = NSFileManager.defaultManager

    private fun documentsPath() =
        (NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true)
            .first() as String) + "/" + STORE_FILE_NAME

    private fun supportPath() =
        (NSSearchPathForDirectoriesInDomains(NSApplicationSupportDirectory, NSUserDomainMask, true)
            .first() as String) + "/Pingy/" + STORE_FILE_NAME

    /** The simulator host may not have created these container folders yet. */
    private fun writeStore(path: String, contents: String) {
        val p = path.toPath()
        p.parent?.let { fs.createDirectories(it) }
        fs.write(p) { writeUtf8(contents) }
    }

    @AfterTest
    fun cleanUp() {
        listOf(documentsPath(), supportPath()).forEach { fs.delete(it.toPath(), mustExist = false) }
    }

    @Test
    fun an_old_documents_store_moves_into_application_support() {
        fs.delete(supportPath().toPath(), mustExist = false)
        writeStore(documentsPath(), "legacy-bytes")

        pingyDataStoreDir()

        assertFalse(fm.fileExistsAtPath(documentsPath()), "the old file must not be left behind")
        assertTrue(fm.fileExistsAtPath(supportPath()), "the store must be in Application Support")
        assertEquals("legacy-bytes", fs.read(supportPath().toPath()) { readUtf8() })
    }

    @Test
    fun a_newer_store_is_never_overwritten_by_the_old_one() {
        writeStore(documentsPath(), "old")
        writeStore(supportPath(), "current")

        pingyDataStoreDir()

        assertEquals("current", fs.read(supportPath().toPath()) { readUtf8() })
    }
}
