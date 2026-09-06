package com.yuroyami.pingy.ui.main.components

import com.yuroyami.pingy.PanelLayout
import com.yuroyami.pingy.logic.Ping
import com.yuroyami.pingy.logic.PingKind
import com.yuroyami.pingy.logic.RingBuffer
import com.yuroyami.pingy.utils.PING_TIMEOUT_MS
import kotlin.math.pow
import kotlin.time.TimeSource

/**
 * Pure geometry and ordering for the graph.
 *
 * Split out of PingGraph so it can be tested without a composition. None of it
 * touches Compose, the clock, or any panel state.
 */

/**
 * Fill [out] with the drawable window, oldest first.
 *
 * The ring is in send order, so a newest-first walk sees ages that only grow.
 * The walk stops one entry past the horizon: that entry rides along as the
 * left anchor of the bar or slope bridging into view, so a span does not
 * vanish the frame its left end leaves the canvas. An age that DROPS means the
 * writer wrapped the ring underneath the walk, and the walk ends there.
 *
 * Entries younger than the frozen moment (negative age while scrubbing) are
 * skipped rather than drawn.
 */
internal fun gatherVisible(
    pings: RingBuffer<Ping>,
    frameNow: TimeSource.Monotonic.ValueTimeMark,
    freezeOffsetMs: Long,
    thresholdMs: Long,
    out: ArrayList<Ping>,
) {
    out.clear()
    var prevAge = Long.MIN_VALUE
    pings.forEachNewestFirst { p ->
        val age = (frameNow - p.timestamp).inWholeMilliseconds - freezeOffsetMs
        when {
            age < prevAge -> false
            age > thresholdMs -> { out.add(p); false }
            else -> {
                if (age >= 0) out.add(p)
                prevAge = age
                true
            }
        }
    }
    out.reverse()
}

/**
 * Collapse neighbours that share a pixel column, in place. Index 0 is the
 * off-canvas anchor and never folds. The worst news survives the fold: a loss
 * or fault over anything, a probe still in the air over a reply, and between
 * two replies the slower one.
 */
internal inline fun foldColumns(buf: ArrayList<Ping>, columnOf: (Ping) -> Int) {
    if (buf.size <= 1) return
    var write = 1
    var keptCol = columnOf(buf[0])
    for (read in 1 until buf.size) {
        val p = buf[read]
        val col = columnOf(p)
        if (col == keptCol) {
            val kept = buf[write - 1]
            if (outranksInFold(p, kept)) buf[write - 1] = p
        } else {
            buf[write] = p
            write++
            keptCol = col
        }
    }
    while (buf.size > write) buf.removeAt(buf.lastIndex)
}

@PublishedApi
internal fun outranksInFold(candidate: Ping, kept: Ping): Boolean {
    val c = foldRank(candidate)
    val k = foldRank(kept)
    if (c != k) return c > k
    return c == FOLD_REPLY && (candidate.rttMs ?: 0.0) > (kept.rttMs ?: 0.0)
}

private const val FOLD_REPLY = 1

private fun foldRank(p: Ping): Int = when (p.kind) {
    PingKind.REPLY -> FOLD_REPLY
    // Unknown outcomes outrank a reply but never a real loss: the gap that
    // survives a fold should be one we can actually stand behind.
    PingKind.PENDING, PingKind.INTERRUPTED -> 2
    PingKind.TIMEOUT, PingKind.LOCAL_FAULT -> 3
}

/**
 * In the ridge style a showing slot normally appears as the slope to its
 * showing neighbour. When that neighbour shows nothing (a loss, a fault), or
 * when this is the newest slot, the slot must paint itself level across its
 * own span instead, or a lone reply between two losses would leave no trace.
 */
internal inline fun fillsOwnSlot(visible: List<Ping>, index: Int, shows: (Ping) -> Boolean): Boolean =
    index + 1 >= visible.size || !shows(visible[index + 1])

/**
 * The age at which a probe's slot ends: the next probe's send, or now for the
 * newest. A bar spans exactly its own slot, so what the neighbouring probe
 * later turns out to be cannot change a width already on screen.
 */
internal inline fun slotEndAgeMs(visible: List<Ping>, index: Int, ageOf: (Ping) -> Long): Long =
    if (index + 1 < visible.size) ageOf(visible[index + 1]) else 0L

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
 * The same for every layout. The grid used to halve it to keep pixels per
 * millisecond constant, which silently changed the period being compared when
 * a person switched layout, and the Window slider went on showing the value
 * they had chosen. The grid is adaptive anyway, so cell width already varies
 * and pixels per millisecond were never actually constant.
 *
 * Floored at [PING_TIMEOUT_MS]: a loss lands at its send moment, one timeout in
 * the past, so a narrower window could never contain one.
 */
internal fun visibleWindowMs(timeframeMs: Long, layout: PanelLayout): Long =
    timeframeMs.coerceAtLeast(PING_TIMEOUT_MS.toLong())

/** Calculates a ping height on the current panel based on its value. */
internal fun calculatePingY(ping: Int, panelHeight: Float, pingMaxVal: Float, zoomFactor: Float): Float {
    return (exponentialize(ping.toFloat(), pingMaxVal, zoomFactor) * (panelHeight.toDouble() / pingMaxVal)).toFloat()
}
