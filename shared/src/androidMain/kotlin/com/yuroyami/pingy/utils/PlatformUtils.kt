package com.yuroyami.pingy.utils

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader

/**
 * Android PingEngine: spawns a single long-running `/system/bin/ping` process
 * and continuously reads its stdout for results. One process per panel.
 */
actual class PingEngine actual constructor(
    actual val host: String,
    packetSize: Int,
    intervalMs: Long,
) {
    private val _packetSize = packetSize
    @Volatile private var _intervalMs = intervalMs

    private var process: Process? = null
    private var readJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    actual fun start(onPingResult: (Double?) -> Unit) {
        stop()

        // -i interval in seconds (minimum 0.2 on most Android), -s packet size
        // Continuous ping mode (no -c flag) so the process stays alive
        val intervalSec = (_intervalMs.coerceAtLeast(200) / 1000.0)
        val cmd = "/system/bin/ping -i $intervalSec -s $_packetSize $host"

        try {
            process = Runtime.getRuntime().exec(cmd)
        } catch (e: IOException) {
            loggy("Failed to start ping process: ${e.message}")
            return
        }

        val reader = BufferedReader(InputStreamReader(process!!.inputStream))

        readJob = scope.launch {
            try {
                while (isActive) {
                    val line = reader.readLine() ?: break
                    val rtt = parsePingLine(line)
                    if (rtt != null) {
                        onPingResult(rtt)
                    } else if (line.contains("100% packet loss") || line.contains("Request timeout")) {
                        onPingResult(null)
                    }
                }
            } catch (_: IOException) {
                // Process was destroyed, expected on stop()
            }
        }
    }

    actual fun stop() {
        readJob?.cancel()
        readJob = null
        process?.destroyForcibly()
        process = null
    }

    actual fun updateInterval(intervalMs: Long) {
        _intervalMs = intervalMs
    }

    private fun parsePingLine(line: String): Double? {
        if (!line.contains("time=")) return null
        return try {
            line.substringAfter("time=").substringBefore(" ms").trim().toDouble()
        } catch (_: NumberFormatException) {
            null
        }
    }
}
