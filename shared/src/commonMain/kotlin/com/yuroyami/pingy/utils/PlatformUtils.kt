package com.yuroyami.pingy.utils


/**
 * This function calculates the ICMP Ping to a specified host.
 *
 * @param host The host to ping.
 * @param packetSize The size of the packet to be sent in bytes.
 * @return The round-trip time in milliseconds, or null in case of an error or packet loss.
 */
expect suspend fun pingIcmp(host: String, ttl: Int = 64, packetSize: Int): Double?

/**
 * A continuous ping engine that spawns a single long-lived resource (e.g. a process)
 * and emits ping results over time. Tied to the lifecycle of a PingPanel.
 *
 * On platforms where a persistent approach is beneficial (Android/JVM — long-running process),
 * the engine keeps a single process alive. On other platforms (iOS, JS), it falls back to
 * repeated single-shot pings internally.
 */
expect class PingEngine(host: String, packetSize: Int, intervalMs: Long) {
    val host: String

    /** Start the engine. Results are delivered via [onPingResult]. */
    fun start(onPingResult: (Double?) -> Unit)

    /** Stop the engine and release resources. */
    fun stop()

    /** Update the ping interval without restarting. */
    fun updateInterval(intervalMs: Long)
}
