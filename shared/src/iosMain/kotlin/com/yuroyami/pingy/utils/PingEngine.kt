package com.yuroyami.pingy.utils

import kotlin.concurrent.Volatile
import kotlin.time.TimeSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * iOS PingEngine: performs repeated single-shot ICMP pings via [PingUtils]
 * on a background coroutine with the configured interval.
 *
 * Interval changes take effect immediately: the current in-flight delay is
 * cancelled and the loop is restarted with the new interval.
 */
actual class PingEngine actual constructor(
    actual val host: String,
    packetSize: Int,
    intervalMs: Long,
) {
    private val _packetSize = packetSize

    @Volatile
    private var _intervalMs = intervalMs

    private var job: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)
    private var currentCallback: ((Double?) -> Unit)? = null

    actual fun start(onPingResult: (Double?) -> Unit) {
        currentCallback = onPingResult
        relaunchLoop()
    }

    private fun relaunchLoop() {
        val cb = currentCallback ?: return
        job?.cancel()
        job = scope.launch {
            while (isActive) {
                val wallStart = TimeSource.Monotonic.markNow()
                val result = PingUtils.pingOnce(
                    host = host,
                    timeoutMs = 1000,
                    payloadSize = _packetSize,
                )
                val wallMs = wallStart.elapsedNow().inWholeMicroseconds / 1000.0
                if (result != null) {
                    loggy("iOS ping rtt=${formatMs(result)}ms wall=${formatMs(wallMs)}ms overhead=${formatMs(wallMs - result)}ms")
                } else {
                    loggy("iOS ping lost wall=${formatMs(wallMs)}ms")
                }
                cb(result)
                delay(_intervalMs)
            }
        }
    }

    private fun formatMs(v: Double): String {
        val neg = v < 0
        val cents = kotlin.math.round(kotlin.math.abs(v) * 100.0).toLong()
        val prefix = if (neg) "-" else ""
        return "$prefix${cents / 100}.${(cents % 100).toString().padStart(2, '0')}"
    }

    actual fun stop() {
        currentCallback = null
        job?.cancel()
        job = null
    }

    actual fun updateInterval(intervalMs: Long) {
        if (_intervalMs == intervalMs) return
        _intervalMs = intervalMs
        // Cancel current delay so the new interval takes effect immediately.
        relaunchLoop()
    }
}
