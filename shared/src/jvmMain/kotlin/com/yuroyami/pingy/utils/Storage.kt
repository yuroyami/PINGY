package com.yuroyami.pingy.utils

import java.io.File

actual fun pingyDataStoreDir(): String =
    File(System.getProperty("user.home"), ".pingy").apply { mkdirs() }.absolutePath
