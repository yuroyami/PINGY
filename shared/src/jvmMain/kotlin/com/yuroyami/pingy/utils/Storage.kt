package com.yuroyami.pingy.utils

import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission

/**
 * Desktop preference directory, owner-only.
 *
 * `mkdirs()` alone produced a 0755 directory holding a 0644 file, so every
 * other account on a shared machine could read the list of hosts being
 * monitored. On POSIX hosts the permissions are tightened explicitly; on
 * Windows the call is skipped because the attribute view does not exist there.
 */
actual fun pingyDataStoreDir(): String {
    val dir = File(System.getProperty("user.home"), ".pingy")
    dir.mkdirs()
    runCatching {
        val path = dir.toPath()
        if (path.fileSystem.supportedFileAttributeViews().contains("posix")) {
            Files.setPosixFilePermissions(
                path,
                setOf(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE,
                    PosixFilePermission.OWNER_EXECUTE,
                ),
            )
        }
    }
    return dir.absolutePath
}
