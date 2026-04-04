package com.yuroyami.pingy.utils

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.w3c.xhr.XMLHttpRequest
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

actual suspend fun pingIcmp(host: String, ttl: Int, packetSize: Int): Double? {
    return try {
        val pingCommand = "ping -c 1 -w 1 -T $ttl -s $packetSize $host"
        val result = suspendCoroutine<String?> { continuation ->
            val xhr = XMLHttpRequest()
            xhr.open("GET", "/ping?cmd=$pingCommand")
            xhr.onreadystatechange = {
                if (xhr.readyState == 4.toShort()) {
                    if (xhr.status == 200.toShort()) {
                        continuation.resume(xhr.responseText)
                    } else {
                        continuation.resumeWithException(Exception("Failed to perform ping"))
                    }
                }
            }
            xhr.send()
        }
        if (result != null) {
            if (result.contains("100% packet loss")) {
                null
            } else {
                result.substringAfter("time=").substringBefore(" ms").trim().toDoubleOrNull()
            }
        } else {
            null
        }
    } catch (e: dynamic) {
        loggy(e.stackTrace?.toString() ?: e.toString())
        null
    }
}

/**
 * JS PingEngine: performs repeated single-shot pings via XHR to the backend.
 */
actual class PingEngine actual constructor(
    actual val host: String,
    packetSize: Int,
    intervalMs: Long,
) {
    private val _packetSize = packetSize
    private var _intervalMs = intervalMs

    private var job: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)

    actual fun start(onPingResult: (Double?) -> Unit) {
        stop()
        job = scope.launch {
            while (isActive) {
                val result = pingIcmp(host = host, packetSize = _packetSize)
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
