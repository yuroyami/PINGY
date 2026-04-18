package com.yuroyami.pingy.utils

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader

/**
 * Android PingEngine: spawns a fresh `/system/bin/ping -c 1` process for every
 * individual ping. This sidesteps the ~200ms `-i` floor enforced by the
 * non-root Android ping binary and lets the user drive intervals as low as
 * the UI exposes.
 *
 * Loop model: send a single ping, wait for it to return (or time out), fire
 * the callback, then sleep for whatever time is left in the configured
 * interval. If the round trip (plus process-spawn overhead) is already longer
 * than the interval, the next ping fires immediately — giving a "ping as soon
 * as the previous one finished" stream naturally.
 */
actual class PingEngine actual constructor(
    actual val host: String,
    packetSize: Int,
    intervalMs: Long,
) {
    private val _packetSize = packetSize
    @Volatile private var _intervalMs = intervalMs

    private var job: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)
    private var currentCallback: ((Double?) -> Unit)? = null

    actual fun start(onPingResult: (Double?) -> Unit) {
        currentCallback = onPingResult
        startLoop()
    }

    private fun startLoop() {
        job?.cancel()
        val cb = currentCallback ?: return
        job = scope.launch {
            while (isActive) {
                val started = System.currentTimeMillis()
                val rtt = runSinglePing()
                cb(rtt)
                val elapsed = System.currentTimeMillis() - started
                val remaining = _intervalMs - elapsed
                if (remaining > 0) delay(remaining)
                // else: no delay — fire the next ping immediately.
            }
        }
    }

    private suspend fun runSinglePing(): Double? = withContext(Dispatchers.IO) {
        // -c 1 : send exactly one echo request
        // -W 1 : 1-second per-reply timeout (anything slower is packet loss)
        // -s N : payload size
        val cmd = arrayOf(
            "/system/bin/ping",
            "-c", "1",
            "-W", "1",
            "-s", _packetSize.toString(),
            host
        )
        var process: Process? = null
        val wallStartNanos = System.nanoTime()
        try {
            process = Runtime.getRuntime().exec(cmd)
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            var rtt: Double? = null
            while (true) {
                val line = reader.readLine() ?: break
                val r = parsePingLine(line)
                if (r != null) {
                    rtt = r
                    break
                }
            }
            process.waitFor()
            val wallMs = (System.nanoTime() - wallStartNanos) / 1_000_000.0
            if (rtt != null) {
                loggy("Android ping rtt=${"%.2f".format(rtt)}ms wall=${"%.2f".format(wallMs)}ms overhead=${"%.2f".format(wallMs - rtt)}ms")
            } else {
                loggy("Android ping lost wall=${"%.2f".format(wallMs)}ms")
            }
            rtt
        } catch (_: IOException) {
            null
        } finally {
            process?.destroyForcibly()
        }
    }

    actual fun stop() {
        currentCallback = null
        job?.cancel()
        job = null
    }

    actual fun updateInterval(intervalMs: Long) {
        if (_intervalMs == intervalMs) return
        _intervalMs = intervalMs
        // No restart needed — the loop re-reads _intervalMs on each iteration.
    }
}

private fun parsePingLine(line: String): Double? {
    if (!line.contains("time=")) return null
    return try {
        line.substringAfter("time=").substringBefore(" ms").trim().toDouble()
    } catch (_: NumberFormatException) {
        null
    }
}
