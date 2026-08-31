package com.yuroyami.pingy.ui.main.components

import com.yuroyami.pingy.PanelLayout
import com.yuroyami.pingy.logic.Ping
import com.yuroyami.pingy.utils.PING_TIMEOUT_MS
import kotlin.math.pow

/**
 * Pure geometry and ordering for the graph.
 *
 * Split out of PingGraph so it can be tested without a composition. None of it
 * touches Compose, the clock, or any panel state.
 */

/**
 * True when the list is already oldest-first, so the sort can be skipped.
 *
 * Uses the same comparator ordering as [PingTimeOrder] so the check and the
 * fallback sort can never disagree.
 */
internal fun List<Ping>.isOrderedByTime(): Boolean {
    for (i in 1 until size) {
        if (PingTimeOrder.compare(this[i - 1], this[i]) > 0) return false
    }
    return true
}

/** Oldest-first ordering for the visible window (timestamps ascending). */
internal val PingTimeOrder = Comparator<Ping> { a, b -> a.timestamp.compareTo(b.timestamp) }

/** Fast start, gentle landing: the shape of every glide in this file. */
internal fun easeOutCubic(t: Double): Double {
    val u = 1.0 - t.coerceIn(0.0, 1.0)
    return 1.0 - u * u * u
}

/** Exponential scaling to emphasize low ping values in the graph.
 * Uses the normalized formula: f * (1 - 2^(-x*z/f)) / (1 - 2^(-z))
 *
 * Normalizing by (1 - 2^(-z)) pins the roof to full height for EVERY zoom
 * factor and makes the family continuous: as z shrinks toward 0 the curve
 * settles onto the linear 1:1 line instead of collapsing toward zero height
 * (the old un-normalized formula squashed the whole graph to about a third
 * of its height at z=0.5, one notch away from linear). Higher factors push
 * low pings further up, which is what gamers care about.
 */
internal fun exponentialize(x: Float, f: Float, zoomFactor: Float): Double {
    val xc = x.toDouble().coerceIn(0.0, f.toDouble())
    if (zoomFactor <= 0.001f) {
        // Pure linear mapping.
        return xc
    }
    val z = zoomFactor.toDouble()
    val curved = 1.0 - 2.0.pow(-xc * z / f.toDouble())
    val fullScale = 1.0 - 2.0.pow(-z)
    return f.toDouble() * (curved / fullScale)
}

/**
 * The window actually drawn, in milliseconds.
 *
 * Grid cells are half as wide, so they show half the window to keep pixels per
 * millisecond constant. That is fine right up until the result drops below
 * [PING_TIMEOUT_MS], at which point a timeout can never be rendered at all, and
 * the graph quietly disagrees with the loss counter beside it.
 */
internal fun visibleWindowMs(timeframeMs: Long, layout: PanelLayout): Long {
    val scaled = if (layout == PanelLayout.GRID) timeframeMs / 2 else timeframeMs
    return scaled.coerceAtLeast(PING_TIMEOUT_MS.toLong())
}

/** Calculates a ping height on the current panel based on its value. */
internal fun calculatePingY(ping: Int, panelHeight: Float, pingMaxVal: Float, zoomFactor: Float): Float {
    return (exponentialize(ping.toFloat(), pingMaxVal, zoomFactor) * (panelHeight.toDouble() / pingMaxVal)).toFloat()
}
