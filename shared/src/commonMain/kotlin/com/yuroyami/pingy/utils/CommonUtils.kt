package com.yuroyami.pingy.utils

/*********************************************************************************************************
 * These utilities are used in common code using common libraries and do not need to use expect/actuals  *
 *********************************************************************************************************/

import co.touchlab.kermit.Logger

private const val TAG = "Pingy"

/** Informational log — expected events (startup, connectivity state, etc). */
fun loggy(s: Any?) = Logger.i(tag = TAG) { s.toString() }

/** Warning — something unexpected but recoverable (packet loss, transient socket errors). */
fun loggyw(s: Any?) = Logger.w(tag = TAG) { s.toString() }

/** Error — a failure the user will probably notice (engine aborted, permission denied). */
fun loggye(s: Any?, throwable: Throwable? = null) =
    Logger.e(throwable = throwable, tag = TAG) { s.toString() }
