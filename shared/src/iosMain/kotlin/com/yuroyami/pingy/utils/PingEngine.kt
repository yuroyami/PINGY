package com.yuroyami.pingy.utils

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
 */
actual class PingEngine actual constructor(
    actual val host: String,
    packetSize: Int,
    intervalMs: Long,
) {
    private val _packetSize = packetSize
    private var _intervalMs = intervalMs

    private var job: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    actual fun start(onPingResult: (Double?) -> Unit) {
        stop()
        job = scope.launch {
            while (isActive) {
                val result = PingUtils.pingOnce(
                    host = host,
                    timeoutMs = 1000,
                    payloadSize = _packetSize,
                )
                onPingResult(result)
                delay(_intervalMs)
            }
        }
    }

    actual fun stop() {
        job?.cancel()
        job = null
    }

    actual fun updateInterval(intervalMs: Long) {
        _intervalMs = intervalMs
    }
}
