@file:JvmName("PingEngineCommon")

package com.yuroyami.pingy.utils

import kotlin.jvm.JvmName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.concurrent.Volatile

/**
 * Hard ceiling before the engine declares a probe lost and feeds `null` to
 * the UI. Also the upper bound of the chameleon's colorize sweep — the same
 * 3-second deadline ties the engine, the visual and the user-facing
 * definition of "void" together.
 *
 * Why 3s: short enough that a dead panel snaps back quickly once the
 * network recovers (you don't stare at a stalled probe for a minute), long
 * enough that a genuinely slow link (mobile dropping down to 2G, a distant
 * server over a congested path) still registers as a valid reading.
 */
const val PING_TIMEOUT_MS: Int = 3000

/**
 * Perform exactly one ICMP echo probe against [host] and return the round-trip
 * time in milliseconds, or `null` on any failure (unresolvable host, socket
 * error, timeout, malformed reply).
 *
 * Platform actuals:
 *  - Android / JVM → unprivileged `SOCK_DGRAM + IPPROTO_ICMP` via a small JNI
 *                    library compiled from `shared/native/icmp_ping.c`.
 *  - iOS         → the same BSD socket code path via Kotlin/Native cinterop —
 *                    a single C entry point (`do_ping_once_c` in
 *                    `shared/src/nativeInterop/cinterop/IcmpPing.def`) runs
 *                    the whole send/poll/recv/timestamp dance inside C so the
 *                    measurement window stays free of K/N transitions.
 *
 * All three platforms therefore measure an honest-to-goodness network RTT
 * with microsecond-grade precision — no subprocess fork, no `ping -i` floor,
 * no shell. Interchangeable across Android and iOS.
 */
expect suspend fun pingOnce(host: String, timeoutMs: Int, payloadSize: Int): Double?

/**
 * Resolve a hostname to an IPv4 dotted string once, via the platform resolver.
 * Returns `null` if the host is unresolvable. If the input already looks like
 * an IPv4 literal, actuals are free to shortcut and return it unchanged.
 *
 * Exists so [PingEngine] can pay the DNS cost once and then hand a numeric IP
 * to every subsequent probe — `getaddrinfo` per ping was visibly dilating the
 * cadence on hostname targets (each reply still measured the right RTT, but
 * the wall-clock gap between bars included the lookup).
 */
expect fun resolveHostToIpv4(host: String): String?

/**
 * A live, long-running ping engine. Spawns a coroutine that fires [pingOnce]
 * against [host] back-to-back and reports each result (RTT in ms, or `null`
 * for a confirmed lost/timed-out probe) via the callback passed to [start].
 *
 * [intervalMs] == 0 → "fire the next probe the instant the previous one
 * returned" (adaptive mode; the effective cadence equals the RTT). Positive
 * values sleep between probes. Interval changes via [updateInterval] are
 * picked up at the next sleep point with no loop restart.
 *
 * Lifecycle: construct, `start(cb)` once, `stop()` once. The engine is
 * single-use by design — `stop()` cancels the owning scope, and the same
 * instance cannot be resumed.
 */
class PingEngine(
    val host: String,
    packetSize: Int,
    intervalMs: Long,
) {
    @Volatile private var _packetSize: Int = packetSize
    @Volatile private var _intervalMs: Long = intervalMs

    /** Resolved IP for [host] — cached once on first iteration so the hot
     *  loop hits the numeric path in the native resolver (`inet_pton`) and
     *  skips `getaddrinfo` altogether. Falls back to the original string
     *  when resolution fails, so a dead DNS doesn't wedge the engine. */
    @Volatile private var cachedTarget: String? = null

    // IO so the blocking native syscall doesn't squat Default-pool workers.
    // SupervisorJob so a thrown exception inside the loop doesn't poison sibling
    // scopes — though the loop body traps everything via runCatching anyway.
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun start(onPingResult: (Double?) -> Unit) {
        scope.launch {
            while (isActive) {
                val target = cachedTarget ?: run {
                    val resolved = runCatching { resolveHostToIpv4(host) }.getOrNull() ?: host
                    cachedTarget = resolved
                    resolved
                }
                // withTimeoutOrNull is the hard cap that overrules the process.
                // The native call gets PING_TIMEOUT_MS too, but if for any reason
                // it fails to honor it, we still unblock the loop at the deadline
                // (+ a tiny margin so the native timeout gets to fire naturally
                // in the common case).
                val rtt = runCatching {
                    withTimeoutOrNull(PING_TIMEOUT_MS + 200L) {
                        pingOnce(target, timeoutMs = PING_TIMEOUT_MS, payloadSize = _packetSize)
                    }
                }.getOrNull()
                if (!isActive) break
                onPingResult(rtt)
                val iv = _intervalMs
                if (iv > 0L) delay(iv)
            }
        }
    }

    fun stop() {
        scope.cancel()
    }

    fun updateInterval(intervalMs: Long) {
        _intervalMs = intervalMs
    }

    fun updatePacketSize(packetSize: Int) {
        _packetSize = packetSize
    }
}
