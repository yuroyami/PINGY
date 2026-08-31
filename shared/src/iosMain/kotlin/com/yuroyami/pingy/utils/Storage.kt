package com.yuroyami.pingy.utils

import platform.Foundation.NSApplicationSupportDirectory
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
    val base = NSSearchPathForDirectoriesInDomains(
        NSApplicationSupportDirectory, NSUserDomainMask, true
    ).first() as String
    val dir = "$base/Pingy"
    NSFileManager.defaultManager.createDirectoryAtPath(dir, true, null, null)
    return dir
}
