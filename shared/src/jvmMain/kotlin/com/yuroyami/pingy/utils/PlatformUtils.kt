package com.yuroyami.pingy.utils

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.InetAddress


actual suspend fun pingIcmp(host: String, ttl: Int, packetSize: Int): Double? {
    return try {
        val pingCommand = "ping -c 1 -w 1 -s $packetSize $host"
        Runtime.getRuntime().exec(pingCommand).inputStream.use { inputStream ->
            val output = inputStream.reader().readText()
            if (output.isEmpty()) throw IOException()
            when {
                output.contains("100% packet loss") -> null
                else -> output.substringAfter("time=").substringBefore(" ms").trim().toDouble()
            }
        }
    } catch (e: IOException) {
        loggy(e.stackTraceToString())
        val inet = InetAddress.getByName(host)
        val mono1 = System.nanoTime()
        val reachable = inet.isReachable(990)
        val mono2 = System.nanoTime()
        if (reachable) {
            return (mono2 - mono1).toDouble().div(1_000_000)
        }
        null
    } catch (e: Exception) {
        loggy(e.stackTraceToString())
        null
    }
}

/**
 * JVM PingEngine: spawns a single long-running `ping` process
 * and continuously reads its stdout for results.
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

        val intervalSec = (_intervalMs.coerceAtLeast(200) / 1000.0)
        val cmd = "ping -i $intervalSec -s $_packetSize $host"

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
                    } else if (line.contains("100% packet loss")) {
                        onPingResult(null)
                    }
                }
            } catch (_: IOException) {
                // Process was destroyed
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
