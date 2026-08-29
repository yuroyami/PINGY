package com.yuroyami.pingy.utils

import android.annotation.SuppressLint
import android.content.Context

/** Application context, captured by AppActivity before any composition runs. */
@SuppressLint("StaticFieldLeak")
lateinit var pingyAppContext: Context

actual fun pingyDataStoreDir(): String = pingyAppContext.filesDir.absolutePath
