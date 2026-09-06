package com.yuroyami.pingy.utils

import com.yuroyami.pingy.logic.STORE_FILE_NAME
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask

/**
 * Internal preferences belong in Application Support, not Documents.
 *
 * Documents is the user-visible container: it is exposed by Files.app when the
 * app ever enables file sharing, and it is backed up to iCloud by default.
 * Application Support is the documented home for data the user does not manage
 * directly. Apple's guidance is explicit about this split.
 */
actual fun pingyDataStoreDir(): String {
    val fm = NSFileManager.defaultManager
    val base = NSSearchPathForDirectoriesInDomains(
        NSApplicationSupportDirectory, NSUserDomainMask, true
    ).first() as String
    val dir = "$base/Pingy"
    fm.createDirectoryAtPath(dir, true, null, null)
    migrateLegacyStore(fm, dir)
    return dir
}

/**
 * Builds before this move kept the store in Documents, and nothing looked for
 * it there, so an upgrade reported a first run and lost the saved targets.
 * Moves it once; a store already in the new home always wins.
 */
internal fun migrateLegacyStore(fm: NSFileManager, newDir: String) {
    val documents = NSSearchPathForDirectoriesInDomains(
        NSDocumentDirectory, NSUserDomainMask, true
    ).first() as String
    val old = "$documents/$STORE_FILE_NAME"
    val new = "$newDir/$STORE_FILE_NAME"
    if (!fm.fileExistsAtPath(old) || fm.fileExistsAtPath(new)) return
    fm.moveItemAtPath(old, new, null)
}
