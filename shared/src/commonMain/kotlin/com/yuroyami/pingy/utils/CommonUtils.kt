package com.yuroyami.pingy.utils

/*********************************************************************************************************
 * These utilities are used in common code using common libraries and do not need to use expect/actuals  *
 *********************************************************************************************************/

import co.touchlab.kermit.Logger

/** Logs the [s] message to the platform's corresponding log output */
fun loggy(s: Any?) = Logger.e("Pingy") { s.toString() }