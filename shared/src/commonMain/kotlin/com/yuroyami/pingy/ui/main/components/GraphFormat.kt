package com.yuroyami.pingy.ui.main.components

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Number and duration formatting for the readout.
 *
 * Deliberately locale-independent: these render inside a fixed-width instrument
 * panel where a grouping separator or a different decimal mark would break the
 * layout, and the values are technical rather than prose.
 */

internal fun formatTimeframe(ms: Long): String {
    val totalSec = ms / 1000
    return when {
        totalSec < 60 -> "${totalSec}s"
        totalSec < 3600 -> {
            val m = totalSec / 60
            val s = totalSec % 60
            if (s == 0L) "${m}m" else "${m}m ${s}s"
        }
        else -> "${totalSec / 3600}h"
    }
}

/** Sub-second-precision age for the scrub chip, e.g. "0.4s" / "2.3s" / "1m 5s". */
internal fun formatShortAge(ms: Long): String = if (ms < 60_000) {
    val tenths = (ms / 100).coerceAtLeast(0)
    "${tenths / 10}.${tenths % 10}s"
} else {
    formatTimeframe(ms)
}

/**
 * Format an RTT for display. Sub-millisecond values keep two decimals so a LAN
 * target does not read as a flat "0ms"; anything above 10 ms rounds to a whole
 * millisecond, which is all the precision a reader can use.
 */
internal fun formatRtt(ms: Double): String = when {
    ms < 1.0 -> {
        val hundredths = (ms * 100).roundToInt()
        "0.${(hundredths % 100).toString().padStart(2, '0')}"
    }
    ms < 10.0 -> formatFloat1(ms.toFloat())
    else -> ms.roundToInt().toString()
}

/** One-decimal formatter without depending on platform `Locale` / `String.format`. */
internal fun formatFloat1(value: Float): String {
    val negative = value < 0f
    val absTenths = (abs(value) * 10f).roundToInt()
    val whole = absTenths / 10
    val frac = absTenths % 10
    val sign = if (negative && (whole != 0 || frac != 0)) "-" else ""
    return "$sign$whole.$frac"
}
