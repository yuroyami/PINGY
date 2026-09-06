package com.yuroyami.pingy.ui.main.components

import com.yuroyami.pingy.i18n.Strings
import com.yuroyami.pingy.logic.Ping
import com.yuroyami.pingy.logic.RingBuffer
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.time.TimeSource

/**
 * Windowed statistics and their spoken equivalent.
 *
 * Kept apart from the drawing so the arithmetic that produces the loss and
 * outage figures can be read, and tested, without a canvas in the way.
 */

/**
 * Spoken equivalent of the graph.
 *
 * Everything the picture conveys, in one sentence: what is being monitored, the
 * newest reading, the window it covers, and the same aggregates rendered beside
 * it. Local faults are named rather than folded into loss, matching what the
 * numbers now do.
 */
internal fun buildGraphSummary(
    ip: String,
    latest: Int?,
    lost: Boolean,
    stats: WindowStats,
    windowMs: Long,
    s: Strings,
): String = buildString {
    append(ip).append(". ")
    when {
        lost -> append(s.a11yLatestTimedOut).append(' ')
        latest != null -> append(s.a11yLatestRtt(latest)).append(' ')
        else -> append(s.a11yNoReading).append(' ')
    }
    append(s.a11yOverLastSeconds(windowMs / 1000)).append(' ')
    if (stats.count == 0) {
        append(s.a11yNoProbesSent)
    } else {
        append(s.a11ySentLost(stats.count, stats.lost))
        stats.avg?.let { append(s.a11yAverage(it.roundToInt())) }
        stats.min?.let { append(s.a11yBest(it.roundToInt())) }
        stats.max?.let { append(s.a11yWorst(it.roundToInt())) }
    }
    if (stats.localFaults > 0) {
        append(". ").append(s.a11yExcludedFaults(stats.localFaults))
    }
    if (stats.interrupted > 0) {
        append(". ").append(s.a11yInterrupted(stats.interrupted))
    }
    append(".")
}

/**
 * Stats over the visible timeframe.
 *
 * [count] counts probes that left the device and got a verdict, so it is the
 * honest denominator for [lost]. Probes still in the air are not counted yet.
 * [localFaults] is reported separately because a DNS or socket failure says
 * nothing about the target.
 *
 * [gonePct] is time-based loss over [coveredMs], the span actually observed,
 * rather than over the whole window. Values stay in milliseconds as doubles;
 * rounding happens only at the point of display.
 */
internal data class WindowStats(
    val count: Int,
    val lost: Int,
    val localFaults: Int,
    /** Probes that left but whose observation was cut short. Not loss. */
    val interrupted: Int,
    /** Probes still in the air. Sent, but with nothing to say yet. */
    val pending: Int,
    val avg: Double?,
    val min: Double?,
    val max: Double?,
    /** Mean absolute successive difference between consecutive replies. */
    val jitter: Double?,
    val gonePct: Float?,
    val coveredMs: Long,
) {
    companion object {
        val EMPTY = WindowStats(0, 0, 0, 0, 0, null, null, null, null, null, 0L)
    }
}

/** One pass over the ring, newest-first, stopping at the window's horizon.
 *
 * Jitter pairs only consecutive replies. A loss breaks adjacency, so the
 * wobble number never spans a gap. A probe still in the air is skipped
 * without breaking it, since it has said nothing yet.
 *
 * GONE is slot attribution: every probe owns the stretch from its own send
 * to the next send (the newest owns up to now), and a slot counts as gone
 * when its probe was lost. Slots whose probe is still in the air, or whose
 * entry is a local fault, are unknown territory and enter neither side. */
internal fun computeWindowStats(
    pings: RingBuffer<Ping>,
    windowMs: Long,
    now: TimeSource.Monotonic.ValueTimeMark = TimeSource.Monotonic.markNow(),
): WindowStats {
    // The ring is in send order; an age that drops means the writer lapped
    // this walk, and the walk ends there.
    //
    // The walk keeps one sample past the horizon, exactly as the canvas does.
    // Its slot still covers part of the window, and dropping it censored every
    // completed timeout at the shortest setting: a verdict only arrives three
    // seconds after its send, by which time the send itself is off the edge.
    val window = ArrayList<Ping>(256)
    var prevAge = Long.MIN_VALUE
    pings.forEachNewestFirst { p ->
        val age = (now - p.timestamp).inWholeMilliseconds
        when {
            age < prevAge -> false
            age > windowMs -> {
                window.add(p)
                false
            }
            else -> {
                if (age >= 0) window.add(p)
                prevAge = age
                true
            }
        }
    }
    window.reverse()

    var count = 0
    var lost = 0
    var localFaults = 0
    var interrupted = 0
    var pending = 0
    var sum = 0.0
    var valid = 0
    var min = Double.MAX_VALUE
    var max = -Double.MAX_VALUE
    var jitterSum = 0.0
    var jitterCount = 0
    var olderValue = 0.0
    var olderWasValid = false
    var spanTotal = 0L
    var spanGone = 0L

    for (i in window.indices) {
        val p = window[i]

        // A local fault means no probe ever left this device. It is not evidence
        // about the target, so it enters neither the sent count, the loss count,
        // nor the time-based outage share.
        if (p.isLocalFault) {
            localFaults++
            continue
        }
        // Still in the air: nothing to count yet.
        if (p.isPending) {
            pending++
            continue
        }
        // Sent, then the socket died under it. That is our failure to observe,
        // not the target's failure to answer.
        if (p.isInterrupted) {
            interrupted++
            continue
        }
        // Monitoring was off from here to the next send. Unknown territory.
        if (p.isUnobserved) continue

        // Each probe owns the stretch from its own send to the next send, and
        // the oldest one is clipped to the horizon rather than dropped, so the
        // share moves continuously as it crosses the edge instead of jumping.
        val age = (now - p.timestamp).inWholeMilliseconds.coerceAtMost(windowMs)
        val slotEnd = if (i + 1 < window.size) (now - window[i + 1].timestamp).inWholeMilliseconds else 0L
        val span = age - slotEnd
        if (span <= 0) continue

        count++
        val v = p.rttMs
        val isLost = p.isLoss

        spanTotal += span
        if (isLost) spanGone += span
        if (isLost || v == null) {
            lost++
            olderWasValid = false
        } else {
            sum += v
            valid++
            if (v < min) min = v
            if (v > max) max = v
            if (olderWasValid) {
                jitterSum += abs(olderValue - v)
                jitterCount++
            }
            olderValue = v
            olderWasValid = true
        }
    }
    return WindowStats(
        count = count,
        lost = lost,
        localFaults = localFaults,
        interrupted = interrupted,
        pending = pending,
        avg = if (valid > 0) sum / valid else null,
        min = if (valid > 0) min else null,
        max = if (valid > 0) max else null,
        jitter = if (jitterCount > 0) jitterSum / jitterCount else null,
        gonePct = if (spanTotal > 0) spanGone * 100f / spanTotal else null,
        // Only the time we actually watched. Unknown stretches are in neither.
        coveredMs = spanTotal,
    )
}
