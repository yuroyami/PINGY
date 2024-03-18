package com.yuroyami.pingy.utils

/*********************************************************************************************************
 * These utilities are used in common code using common libraries and do not need to use expect/actuals  *
 *********************************************************************************************************/

import co.touchlab.kermit.Logger

/** Logs the [s] message to the platform's corresponding log output */
fun loggy(s: Any?) = Logger.e("Pingy") { s.toString() }

/** Looks for the smallest minimum ping in a ping list, except for -1 which equals an undefined ping.
 * We use our own implementation of 'List().min()' because Kotlin's min() will return -1 if it exists
 * in the list, while our implementation should ignore a -1 if found */
fun List<Int>.minPing(): Int? {
    var min: Int? = null
    for (p in this) {
        if (p > 0) {
            if (min == null || p < min) {
                min = p
            }
        }
    }
    return min
}