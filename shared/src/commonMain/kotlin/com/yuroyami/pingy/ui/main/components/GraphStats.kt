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
 * [count] counts probes that actually left the device, so it is the honest
 * denominator for [lost]. [localFaults] is reported separately because a DNS or
 * socket failure says nothing about the target.
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
 * Jitter pairs only consecutive valid samples — a loss breaks adjacency, so
 * the wobble number never spans a gap.
 *
 * GONE uses completion-gap attribution: each verdict owns the time span
 * back to the previous verdict (the oldest one owns its span back to the
 * window edge), and a span counts as gone when its verdict was a loss.
 * With pipelined probing this is honest in both directions: during a real
 * outage lost verdicts stream at the watchdog cadence and their spans tile
 * the whole dark stretch, while a single dropped packet resolves BETWEEN
 * two healthy replies and owns only milliseconds. The still-unresolved
 * stretch between the newest verdict and "now" belongs to nobody. */
internal fun computeWindowStats(pings: RingBuffer<Ping>, windowMs: Long): WindowStats {
    val now = TimeSource.Monotonic.markNow()

    // Gather the window, tolerant of send-time entries appended in
    // completion order (a reaped loss sits up to a timeout out of place),
    // then sort oldest-first so spans and jitter pair true time-neighbours.
    val window = ArrayList<Ping>(256)
    var prevAge = Long.MIN_VALUE
    pings.forEachNewestFirst { p ->
        val age = (now - p.timestamp).inWholeMilliseconds
        when {
            age + REORDER_SLACK_MS < prevAge -> return@forEachNewestFirst false
            age > windowMs + REORDER_SLACK_MS -> return@forEachNewestFirst false
            else -> {
                if (age in 0..windowMs) window.add(p)
                if (age > prevAge) prevAge = age
                true
            }
        }
    }
    if (!window.isOrderedByTime()) window.sortWith(PingTimeOrder)

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
    var olderAge = Long.MIN_VALUE
    var spanTotal = 0L
    var spanGone = 0L

    // The oldest sample in the window may be the oldest we HAVE, not the oldest
    // there was. Attributing everything back to the window edge invented outage
    // time that was never observed: two failures a second apart could report
    // 100% gone across a five second window the app had not even been running
    // for. Coverage starts at the oldest sample we actually hold.
    val oldestAge = window.firstOrNull()?.let { (now - it.timestamp).inWholeMilliseconds }
    val coveredMs = oldestAge?.coerceAtMost(windowMs) ?: 0L

    for (p in window) {
        val age = (now - p.timestamp).inWholeMilliseconds

        // A local fault means no probe ever left this device. It is not evidence
        // about the target, so it enters neither the sent count, the loss count,
        // nor the time-based outage share.
        if (p.isLocalFault) {
            localFaults++
            continue
        }

        count++
        val v = p.rttMs
        val isLost = p.isLoss

        // Each verdict owns the span back to its predecessor. The oldest owns
        // only back to the start of observed coverage.
        val span = if (olderAge == Long.MIN_VALUE) coveredMs - age else olderAge - age
        if (span > 0) {
            spanTotal += span
            if (isLost) spanGone += span
        }
        olderAge = age
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
