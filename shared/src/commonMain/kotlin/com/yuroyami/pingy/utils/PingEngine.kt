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
 * Open an unprivileged ICMP socket and `connect()` it to the given IPv4
 * literal. Returns the platform file descriptor (Int) on success, or `-1`
 * on any failure. Hostnames are intentionally not accepted — callers
 * resolve once via [resolveHostToIpv4] and feed the numeric result here.
 *
 * The point of holding the socket open: per-probe `socket() + close()` is
 * real kernel work (Darwin also allocates a per-socket ICMP id on every
 * create), and sendto() on an unconnected DGRAM socket re-resolves the
 * route every packet. Opening once + connecting once means subsequent
 * probes are pure send/poll/recv with no kernel-side setup.
 */
expect fun openIcmpSocket(ipv4: String): Int

/**
 * Perform exactly one ICMP echo probe over a socket previously returned by
 * [openIcmpSocket]. The raw native return value uses this sentinel contract:
 *
 *   result >= 0.0  — success, RTT in ms.
 *   [PING_RESULT_SOCK_ERR] (-1.0) — socket-level failure; caller must close+reopen.
 *   [PING_RESULT_TIMEOUT]  (-2.0) — timeout / no matching reply; socket still healthy.
 *
 * Returning a `Double` (not `Double?`) deliberately — the engine branches on
 * the sentinel to decide between "lost-but-keep-socket" and "socket-died-reopen".
 */
expect suspend fun pingOnSocket(fd: Int, timeoutMs: Int, payloadSize: Int): Double

/** Close a socket previously returned by [openIcmpSocket]. Idempotent on `-1`. */
expect fun closeIcmpSocket(fd: Int)

/** Native sentinel: socket died (caller should close+reopen the fd). */
const val PING_RESULT_SOCK_ERR: Double = -1.0

/** Native sentinel: no matching reply within the budget (socket still healthy). */
const val PING_RESULT_TIMEOUT: Double = -2.0

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
 * A live, long-running ping engine. Spawns a coroutine that fires probes
 * against [host] back-to-back and reports each result (RTT in ms, or `null`
 * for a confirmed lost/timed-out probe) via the callback passed to [start].
 *
 * Single socket per engine: on first iteration we resolve the host once,
 * open one ICMP socket, `connect()` it, and reuse it for every probe until
 * [stop]. A socket-level error (send/poll/recv failure) triggers a close+
 * reopen on the next iteration; a plain timeout does not.
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
            var fd = -1
            try {
                while (isActive) {
                    // Lazy (re)open. cachedTarget resolves the host at most once
                    // per engine lifetime; the fd is recreated after a socket-level
                    // error or not at all if probes keep succeeding / only timing out.
                    if (fd < 0) {
                        val target = cachedTarget ?: run {
                            val resolved = runCatching { resolveHostToIpv4(host) }.getOrNull() ?: host
                            cachedTarget = resolved
                            resolved
                        }
                        fd = runCatching { openIcmpSocket(target) }.getOrDefault(-1)
                        if (fd < 0) {
                            // Could be a transient resource issue (EMFILE, ENOBUFS) or a
                            // bad IP. Emit one lost sample, back off briefly, retry.
                            if (!isActive) break
                            onPingResult(null)
                            delay(200)
                            continue
                        }
                    }

                    // withTimeoutOrNull is the hard cap that overrules the native
                    // call — it should honor its own PING_TIMEOUT_MS but if poll(2)
                    // ever stalls we still unblock the loop (+ tiny margin so the
                    // native timeout fires naturally in the common case).
                    val raw = runCatching {
                        withTimeoutOrNull(PING_TIMEOUT_MS + 200L) {
                            pingOnSocket(fd, timeoutMs = PING_TIMEOUT_MS, payloadSize = _packetSize)
                        } ?: PING_RESULT_SOCK_ERR // outer timeout → assume the fd is wedged
                    }.getOrDefault(PING_RESULT_SOCK_ERR)

                    if (!isActive) break

                    when {
                        raw >= 0.0 -> onPingResult(raw)
                        raw == PING_RESULT_TIMEOUT -> onPingResult(null)
                        else -> {
                            // Socket-level error: report lost, close fd, reopen next iter.
                            onPingResult(null)
                            closeIcmpSocket(fd)
                            fd = -1
                        }
                    }

                    val iv = _intervalMs
                    if (iv > 0L) delay(iv)
                }
            } finally {
                if (fd >= 0) closeIcmpSocket(fd)
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
