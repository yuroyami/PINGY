@file:JvmName("PingEngineCommon")

package com.yuroyami.pingy.utils

import kotlin.jvm.JvmName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.concurrent.Volatile
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

/**
 * Hard ceiling before a probe is declared lost and fed to the UI as `null`.
 * Also the upper bound of the chameleon's colorize sweep — the same
 * 3-second deadline ties the engine, the visual and the user-facing
 * definition of "void" together.
 *
 * Why 3s: it answers "how slow can a reply be before we stop believing this
 * packet will ever return". A genuinely slow link (mobile dropping down to
 * 2G, a distant server over a congested path) still registers as a valid
 * reading instead of a fake loss. It does NOT pace the probing: the
 * pipelined schedule keeps sending straight through an unresolved probe.
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
 * Build + send one echo request stamped with [seq]. Returns the send moment
 * in native monotonic MICROSECONDS (>= 0), or `-1` on a socket-level
 * failure. Never blocks on the network: sending is fire-and-forget, and
 * replies are collected separately by [icmpAwaitReply].
 */
expect fun icmpSendProbe(fd: Int, seq: Int, payloadSize: Int): Long

/**
 * Block up to [budgetMs] for the NEXT echo reply on the socket, whichever
 * outstanding probe it answers. Returns [AWAIT_SOCK_ERR], [AWAIT_NOTHING],
 * or a packed value: `(recvUsec & 47-bit mask) << 16 | seq`. The engine
 * matches `seq` against its outstanding table and diffs the two native
 * timestamps for the RTT, so the measured window never crosses the
 * Kotlin↔C boundary.
 */
expect fun icmpAwaitReply(fd: Int, budgetMs: Int): Long

/** Close a socket previously returned by [openIcmpSocket]. Idempotent on `-1`. */
expect fun closeIcmpSocket(fd: Int)

/** [icmpAwaitReply]: socket died (caller should close+reopen the fd). */
const val AWAIT_SOCK_ERR: Long = -1L

/** [icmpAwaitReply]: no valid reply within the budget; socket still healthy. */
const val AWAIT_NOTHING: Long = -2L

/** [icmpSendProbe]: send failed at the socket level. */
const val SEND_SOCK_ERR: Long = -1L

/** Mask for the 47 usec bits carried by a packed [icmpAwaitReply] result. */
private const val USEC_MASK: Long = (1L shl 47) - 1

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

/** Backoff after a socket-level error, so a rejecting firewall (EPERM on
 * send, admin-prohibited ICMP) throttles to 5 attempts/sec instead of an
 * unbounded open/send/close spin. */
private const val SOCK_ERR_BACKOFF_MS = 200L

/** Retry cadence while the host refuses to resolve. Failures are never
 * cached: a DNS hiccup at panel start must not poison the panel forever. */
private const val RESOLVE_RETRY_MS = 1_000L

/** Adaptive mode's watchdog: a reply pulls the next send immediately, but
 * silence never stalls the schedule longer than this. This is what keeps
 * the engine sampling straight through an outage instead of going blind
 * for a whole timeout per probe. */
private const val ADAPTIVE_WATCHDOG_MS = 250L

/** Ceiling on unanswered probes tracked at once. At the watchdog cadence a
 * full 3s outage keeps ~12 in flight; the cap is a safety net, not a knob.
 * When it is hit, send ticks are skipped until something resolves. */
private const val MAX_OUTSTANDING = 64

/**
 * A live, long-running PIPELINED ping engine. One socket, one event loop,
 * many probes in flight:
 *
 *  - a send schedule fires seq-stamped probes and never waits for replies
 *    (interval > 0: fixed cadence; interval 0: each reply triggers the next
 *    send immediately, with a [ADAPTIVE_WATCHDOG_MS] watchdog so timeouts
 *    cannot stall the schedule),
 *  - the same loop collects whatever replies arrive and matches them to
 *    their probes by sequence number (per-probe verdicts, in resolution
 *    order),
 *  - probes older than [PING_TIMEOUT_MS] are reaped as losses without ever
 *    having blocked the schedule.
 *
 * This removes the classic one-outstanding-probe blindness: during an
 * outage the engine keeps sampling at the watchdog cadence, so loss
 * statistics reflect time truthfully, and a single dropped packet is
 * exposed as exactly that by the probes that follow it within milliseconds.
 *
 * A socket-level error flushes the outstanding probes as losses (they died
 * with the socket), backs off, re-resolves, and reopens. Each engine runs
 * on its own single-lane view of Dispatchers.IO — the loop blocks a thread
 * in poll(2), and parking those on the shared IO pool would let enough
 * panels starve every other IO consumer in the process.
 *
 * Lifecycle: construct, `start(cb)` once, `stop()` once. `stop()` also
 * closes the live fd directly, which wakes a blocked poll so the loop exits
 * now, not up to a full budget later.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PingEngine(
    val host: String,
    packetSize: Int,
    intervalMs: Long,
) {
    @Volatile private var _packetSize: Int = packetSize
    @Volatile private var _intervalMs: Long = intervalMs

    /** Resolved IP for [host] — cached after the first success so the hot
     *  loop hits the numeric path in the native resolver (`inet_pton`) and
     *  skips `getaddrinfo`. Cleared on socket errors to force a re-resolve. */
    @Volatile private var cachedTarget: String? = null

    /** The fd currently held by the loop, mirrored so [stop] can close it
     *  from outside and wake a blocked poll(). Cleared BEFORE the loop closes
     *  the fd itself, so stop() can never double-close a recycled number. */
    @Volatile private var liveFd: Int = -1

    /** Set by [stop] before it closes [liveFd]; tells the loop's finally
     *  block that the fd is already gone. */
    @Volatile private var closedByStop = false

    private val scope = CoroutineScope(Dispatchers.IO.limitedParallelism(1) + SupervisorJob())

    /**
     * [onPingResult] receives each probe's verdict together with the moment
     * the probe was SENT. Send time is the honest x-position for a pipelined
     * sampler: sends are evenly scheduled, while completions arrive in
     * bursts (overlapping replies, timeout reaps, socket flushes) that would
     * shimmer and displace gaps if plotted directly.
     */
    fun start(onPingResult: (Double?, TimeSource.Monotonic.ValueTimeMark) -> Unit) {
        scope.launch {
            val epoch = TimeSource.Monotonic.markNow()
            fun nowMs() = epoch.elapsedNow().inWholeMilliseconds
            fun markAt(ms: Long) = epoch + ms.milliseconds

            // seq -> [native send usec, kotlin send ms]. Insertion order is
            // send order, so the first entry is always the oldest probe.
            val outstanding = LinkedHashMap<Int, LongArray>()
            var nextSeq = 1
            var fd = -1
            var nextSendAtMs = 0L

            // Probes already in the air die with their socket: their replies
            // can only arrive on the fd we are about to close. Each keeps its
            // own historic send moment, so the losses spread out truthfully
            // instead of stacking on one instant.
            fun flushOutstandingAsLost() {
                outstanding.values.forEach { onPingResult(null, markAt(it[1])) }
                outstanding.clear()
            }

            suspend fun dropSocket() {
                flushOutstandingAsLost()
                liveFd = -1
                closeIcmpSocket(fd)
                fd = -1
                cachedTarget = null
                delay(SOCK_ERR_BACKOFF_MS)
            }

            try {
                while (isActive) {
                    // Lazy (re)open. Resolution failures are retried, never cached.
                    if (fd < 0) {
                        val target = cachedTarget ?: runCatching { resolveHostToIpv4(host) }
                            .getOrNull()
                            ?.also { cachedTarget = it }
                        if (target == null) {
                            if (!isActive) break
                            onPingResult(null, TimeSource.Monotonic.markNow())
                            delay(RESOLVE_RETRY_MS)
                            continue
                        }
                        fd = runCatching { openIcmpSocket(target) }.getOrDefault(-1)
                        if (fd < 0) {
                            if (!isActive) break
                            onPingResult(null, TimeSource.Monotonic.markNow())
                            delay(SOCK_ERR_BACKOFF_MS)
                            continue
                        }
                        if (!isActive) {
                            // stop() ran while we were opening; this fd is ours to clean.
                            closeIcmpSocket(fd)
                            fd = -1
                            break
                        }
                        liveFd = fd
                        nextSendAtMs = nowMs()
                    }

                    var now = nowMs()

                    // Send tick. The schedule advances even when the
                    // outstanding cap forces a skip, so a recovering socket
                    // resumes at the right cadence instead of bursting.
                    if (now >= nextSendAtMs) {
                        if (outstanding.size < MAX_OUTSTANDING) {
                            val seq = nextSeq
                            nextSeq = (nextSeq + 1) and 0xFFFF
                            val sendUsec = runCatching {
                                icmpSendProbe(fd, seq, _packetSize.coerceIn(0, 480))
                            }.getOrDefault(SEND_SOCK_ERR)
                            if (sendUsec < 0) {
                                if (!isActive) break
                                onPingResult(null, markAt(now))
                                dropSocket()
                                continue
                            }
                            outstanding[seq] = longArrayOf(sendUsec, now)
                        }
                        val iv = _intervalMs
                        nextSendAtMs = now + if (iv > 0L) iv else ADAPTIVE_WATCHDOG_MS
                    }

                    // Wait for whatever happens first: a reply lands, the next
                    // send tick arrives, or the oldest probe hits its deadline.
                    val oldestDeadline = outstanding.values.firstOrNull()
                        ?.let { it[1] + PING_TIMEOUT_MS } ?: Long.MAX_VALUE
                    val budget = (minOf(nextSendAtMs, oldestDeadline) - now)
                        .coerceAtMost(1_000L)
                    if (budget > 0) {
                        val packed = runCatching { icmpAwaitReply(fd, budget.toInt()) }
                            .getOrDefault(AWAIT_SOCK_ERR)
                        if (!isActive) break
                        when {
                            packed == AWAIT_SOCK_ERR -> {
                                onPingResult(null, TimeSource.Monotonic.markNow())
                                dropSocket()
                                continue
                            }

                            packed == AWAIT_NOTHING -> Unit

                            else -> {
                                val seq = (packed and 0xFFFF).toInt()
                                val recvUsec = (packed ushr 16) and USEC_MASK
                                val entry = outstanding.remove(seq)
                                if (entry != null) {
                                    val rttMs = ((recvUsec - (entry[0] and USEC_MASK)) and USEC_MASK) / 1000.0
                                    onPingResult(rttMs, markAt(entry[1]))
                                    // Adaptive mode: good news pulls the next send
                                    // forward, but ONLY once the pipeline is drained.
                                    // Pulling on every reply while others are still in
                                    // flight is a ratchet: each watchdog-added probe
                                    // keeps triggering its own successor forever, so
                                    // every timeout would permanently raise the rate.
                                    if (_intervalMs <= 0L && outstanding.isEmpty()) {
                                        nextSendAtMs = nowMs()
                                    }
                                }
                                // else: a late reply for a probe already reaped
                                // as lost. Its verdict stands; drop the packet.
                            }
                        }
                    }

                    // Reap expired probes, oldest first. Never blocks sending.
                    now = nowMs()
                    val iterator = outstanding.entries.iterator()
                    while (iterator.hasNext()) {
                        val entry = iterator.next()
                        if (now - entry.value[1] >= PING_TIMEOUT_MS) {
                            iterator.remove()
                            // The loss lands at its probe's true send moment,
                            // 3s in the past: exactly the spot the dissolving
                            // wall has just vacated on screen.
                            onPingResult(null, markAt(entry.value[1]))
                        } else break
                    }
                }
            } finally {
                liveFd = -1
                if (fd >= 0 && !closedByStop) closeIcmpSocket(fd)
            }
        }
    }

    fun stop() {
        closedByStop = true
        scope.cancel()
        val fd = liveFd
        liveFd = -1
        closeIcmpSocket(fd) // idempotent on -1; wakes a poll() blocked on this fd
    }

    fun updateInterval(intervalMs: Long) {
        _intervalMs = intervalMs
    }

    fun updatePacketSize(packetSize: Int) {
        _packetSize = packetSize
    }
}
