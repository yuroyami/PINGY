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
    val avg: Double?,
    val min: Double?,
    val max: Double?,
    /** Mean absolute successive difference between consecutive replies. */
    val jitter: Double?,
    val gonePct: Float?,
    val coveredMs: Long,
) {
    companion object {
        val EMPTY = WindowStats(0, 0, 0, null, null, null, null, null, 0L)
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
internal fun computeWindowStats(pings: RingBuffer<Ping>, windowMs: Long): WindowStats {
    val now = TimeSource.Monotonic.markNow()

    // The ring is in send order; an age that drops means the writer lapped
    // this walk, and the walk ends there.
    val window = ArrayList<Ping>(256)
    var prevAge = Long.MIN_VALUE
    pings.forEachNewestFirst { p ->
        val age = (now - p.timestamp).inWholeMilliseconds
        when {
            age < prevAge -> false
            age > windowMs -> false
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

    // The oldest sample in the window may be the oldest we HAVE, not the oldest
    // there was. Attributing everything back to the window edge invented outage
    // time that was never observed: two failures a second apart could report
    // 100% gone across a five second window the app had not even been running
    // for. Coverage starts at the oldest sample we actually hold.
    val oldestAge = window.firstOrNull()?.let { (now - it.timestamp).inWholeMilliseconds }
    val coveredMs = oldestAge?.coerceAtMost(windowMs) ?: 0L

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
        if (p.isPending) continue

        count++
        val v = p.rttMs
        val isLost = p.isLoss

        val age = (now - p.timestamp).inWholeMilliseconds
        val slotEnd = if (i + 1 < window.size) (now - window[i + 1].timestamp).inWholeMilliseconds else 0L
        val span = age - slotEnd
        if (span > 0) {
            spanTotal += span
            if (isLost) spanGone += span
        }
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
        avg = if (valid > 0) sum / valid else null,
        min = if (valid > 0) min else null,
        max = if (valid > 0) max else null,
        jitter = if (jitterCount > 0) jitterSum / jitterCount else null,
        gonePct = if (spanTotal > 0) spanGone * 100f / spanTotal else null,
        coveredMs = coveredMs,
    )
}
