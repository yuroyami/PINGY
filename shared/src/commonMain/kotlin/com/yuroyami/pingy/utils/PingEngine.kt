@file:JvmName("PingEngineCommon")

package com.yuroyami.pingy.utils

import com.yuroyami.pingy.logic.LocalFault
import com.yuroyami.pingy.logic.Ping
import com.yuroyami.pingy.logic.PingEvent
import kotlin.jvm.JvmName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.concurrent.Volatile
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource
import kotlin.uuid.Uuid

/**
 * Hard ceiling before a probe is declared lost. Ties the engine, the colour
 * sweep and the user-facing definition of "gone" to one number.
 */
const val PING_TIMEOUT_MS: Int = 3000

/**
 * Smallest gap between two probes to one target, in milliseconds.
 *
 * Adaptive mode fires the next probe as soon as the previous reply lands. With
 * no floor that is `1000 / RTT` packets per second, so a sub-millisecond LAN
 * gateway would be hit with roughly a thousand packets a second, forever,
 * unattended. At 10 ms the ceiling is 100 pps per panel, and any target with
 * an RTT above 10 ms (that is, anything off the local network) never reaches
 * the floor at all.
 */
const val MIN_PROBE_GAP_MS: Long = 10

/** Widest configurable interval. Guards the deadline arithmetic below. */
const val MAX_INTERVAL_MS: Long = 600_000

/** Clamp any interval, wherever it came from, into arithmetic-safe range. */
fun sanitizeIntervalMs(raw: Long): Long = when {
    raw <= 0L -> 0L                       // 0 means adaptive
    raw > MAX_INTERVAL_MS -> MAX_INTERVAL_MS
    else -> raw
}

/** Clamp a payload size to what the wire format accepts. */
fun sanitizePayloadSize(raw: Int): Int = raw.coerceIn(16, 480)

/**
 * Open an unprivileged ICMP socket connected to [ipv4]. Returns the platform
 * descriptor, or a negative errno on failure. Hostnames are refused; callers
 * resolve once via [resolveHostToIpv4].
 */
expect fun openIcmpSocket(ipv4: String): Int

/**
 * Send one probe stamped with [session] and [seq]. Returns the send moment in
 * native monotonic microseconds, or [SEND_SOCK_ERR].
 *
 * [session] is a per-engine random nonce written into the payload. Two sockets
 * connected to the same peer both receive that peer's replies, so without it a
 * second panel can consume and mis-time the first panel's reply.
 */
expect fun icmpSendProbe(fd: Int, session: Long, seq: Int, payloadSize: Int): Long

/**
 * Wait up to [budgetMs] for the next reply carrying [session]. Returns
 * [AWAIT_SOCK_ERR], [AWAIT_NOTHING], or `(recvUsec and 47-bit) shl 16 or seq`.
 * Replies belonging to another session are discarded without ending the wait.
 */
expect fun icmpAwaitReply(fd: Int, session: Long, budgetMs: Int): Long

/** Close a descriptor from [openIcmpSocket]. No-op on -1. */
expect fun closeIcmpSocket(fd: Int)

/** True when this platform has a working unprivileged ICMP transport. */
expect fun icmpTransportAvailable(): Boolean

/** [icmpAwaitReply]: the socket died; reopen it. */
const val AWAIT_SOCK_ERR: Long = -1L

/** [icmpAwaitReply]: nothing valid arrived in the budget; socket is healthy. */
const val AWAIT_NOTHING: Long = -2L

/** [icmpSendProbe]: send failed at the socket level. */
const val SEND_SOCK_ERR: Long = -1L

private const val USEC_MASK: Long = (1L shl 47) - 1

/** Resolve a host to an IPv4 literal, or null. IPv4 literals short-circuit. */
expect fun resolveHostToIpv4(host: String): String?

/** First backoff after a socket-level error, so a rejecting firewall cannot spin. */
private const val SOCK_ERR_BACKOFF_MS = 200L

/** Ceiling for the doubling backoff. A permanent refusal settles here. */
private const val SOCK_ERR_BACKOFF_MAX_MS = 6_400L

/** Retry cadence while a host refuses to resolve. Never cached as permanent. */
private const val RESOLVE_RETRY_MS = 1_000L

/** Backoff when the platform has no ICMP at all. Nothing will change soon. */
private const val UNSUPPORTED_RETRY_MS = 30_000L

/** Adaptive mode's watchdog: silence never stalls the schedule longer than this. */
private const val ADAPTIVE_WATCHDOG_MS = 250L

/**
 * Longest single `poll` wait. Also the worst-case stop latency.
 *
 * The loop is the only owner of its descriptor. Closing it from another thread
 * to interrupt the wait was both a use-after-close race and unreliable: on
 * Darwin a cross-thread `close()` did not wake a blocked `poll()` at all. A
 * bounded wait lets the loop notice cancellation by itself, which needs no
 * cross-thread signalling.
 */
private const val MAX_POLL_SLICE_MS = 250L

/** How often the engine re-reads battery and thermal state. */
private const val POWER_POLL_MS = 15_000L

/** Plain comparison on purpose: subtracting a MIN_VALUE sentinel overflowed and never fired. */
internal fun powerCheckDue(nowMs: Long, nextCheckAtMs: Long): Boolean = nowMs >= nextCheckAtMs

/** Ceiling on unanswered probes tracked at once. */
private const val MAX_OUTSTANDING = 64

/**
 * A live pipelined ping engine. One socket, one event loop, many probes in
 * flight.
 *
 * Lifecycle is explicit: construct, [start] once, [stop] (or [stopAndJoin])
 * once. A second [start] is refused rather than silently running two loops over
 * one descriptor.
 *
 * Descriptor ownership is single-owner. The loop opens, uses and closes its own
 * socket; nothing else touches it.
 */
class PingEngine(
    val host: String,
    packetSize: Int,
    intervalMs: Long,
    private val transport: IcmpTransport = PlatformTransport,
) : ProbeEngine {
    private enum class State { IDLE, RUNNING, STOPPED }

    @Volatile private var state: State = State.IDLE
    @Volatile private var _packetSize: Int = sanitizePayloadSize(packetSize)
    @Volatile private var _intervalMs: Long = sanitizeIntervalMs(intervalMs)

    /** Set when the cadence changes, so the loop can re-anchor its deadline. */
    @Volatile private var intervalChanged: Boolean = false

    /** Per-session identity. Every probe carries it; every reply must return it. */
    private val session: Long = Uuid.random().toLongs { hi, _ -> hi }

    /** Resolved IPv4, cached after first success. Cleared on socket errors. */
    @Volatile private var cachedTarget: String? = null

    // Shared bounded lane rather than a private view of the IO pool per engine.
    private val scope = CoroutineScope(PingDispatchers.engine + SupervisorJob())
    private var loop: Job? = null

    /**
     * Start probing. [onEvent] hears every probe twice: once as
     * [PingEvent.Sent] the moment it leaves, once as [PingEvent.Resolved] when
     * its reply lands or its deadline passes. Both carry the send moment, so
     * the listener can fill the slot it claimed instead of appending.
     *
     * Every timestamp comes from one millisecond grid, so events are in send
     * order and a [PingEvent.Fault] never lands between two sends out of turn.
     *
     * Returns false when the engine has already been started or stopped, so a
     * double start is a visible no-op rather than a second competing loop.
     */
    override fun start(onEvent: (PingEvent) -> Unit): Boolean {
        if (state != State.IDLE) return false
        state = State.RUNNING

        loop = scope.launch {
            val epoch = TimeSource.Monotonic.markNow()
            fun nowMs() = epoch.elapsedNow().inWholeMilliseconds
            fun markAt(ms: Long) = epoch + ms.milliseconds

            // seq -> [native send usec, kotlin send ms]. Insertion order is send
            // order, so the first entry is always the oldest probe.
            val outstanding = LinkedHashMap<Int, LongArray>()
            var nextSeq = 1
            var fd = -1
            var nextSendAtMs = 0L
            var lastSentAtMs = -1L

            // Re-read power state occasionally rather than per probe: the query
            // crosses into platform services and the state changes slowly.
            var powerFloorMs = MIN_PROBE_GAP_MS
            var nextPowerCheckAtMs = 0L   // 0 means "on the first turn"

            var openFailures = 0

            fun resolve(seq: Int, ping: Ping) = onEvent(PingEvent.Resolved(seq, ping))
            fun fault(kind: LocalFault, code: Int = 0) =
                onEvent(PingEvent.Fault(Ping.localFault(kind, markAt(nowMs()), code)))

            /**
             * Settle every probe still in the air.
             *
             * Only the ones whose deadline really passed are losses. The rest
             * left the device and we simply stopped being able to watch them,
             * which says nothing about the target.
             */
            fun flushOutstanding(now: Long) {
                outstanding.forEach { (seq, entry) ->
                    val sentAtMs = entry[1]
                    val verdict = if (now - sentAtMs >= PING_TIMEOUT_MS) Ping.timeout(markAt(sentAtMs))
                                  else Ping.interrupted(markAt(sentAtMs))
                    resolve(seq, verdict)
                }
                outstanding.clear()
            }

            suspend fun dropSocket(kind: LocalFault) {
                flushOutstanding(nowMs())
                if (fd >= 0) transport.close(fd)
                fd = -1
                cachedTarget = null
                fault(kind)
                delay(SOCK_ERR_BACKOFF_MS)
            }

            try {
                if (!transport.available()) {
                    // A permanent condition, not a transient one. Report once and
                    // idle instead of manufacturing a loss every 200 ms forever.
                    while (isActive) {
                        fault(LocalFault.UNSUPPORTED_PLATFORM)
                        delay(UNSUPPORTED_RETRY_MS)
                    }
                    return@launch
                }

                while (isActive) {
                    if (fd < 0) {
                        val target = cachedTarget
                            ?: runCatching { transport.resolve(host) }.getOrNull()?.also { cachedTarget = it }
                        // Platform resolution blocks and cannot be interrupted,
                        // so cancellation can only take effect here, on the way
                        // out. Without this a stopped engine still opened a
                        // socket and put one more probe on the wire.
                        if (!isActive) break
                        if (target == null) {
                            fault(LocalFault.RESOLVE_FAILED)
                            delay(RESOLVE_RETRY_MS)
                            continue
                        }
                        val opened = runCatching { transport.open(target) }.getOrDefault(-1)
                        if (!isActive) break
                        if (opened < 0) {
                            // Carry the errno so the UI can say "permission
                            // denied" instead of a generic failure, and double
                            // the wait so a device that always refuses is not
                            // asked five times a second forever.
                            fault(LocalFault.SOCKET_OPEN_FAILED, code = -opened)
                            cachedTarget = null
                            val wait = (SOCK_ERR_BACKOFF_MS shl openFailures.coerceAtMost(5))
                                .coerceAtMost(SOCK_ERR_BACKOFF_MAX_MS)
                            openFailures++
                            delay(wait)
                            continue
                        }
                        openFailures = 0
                        fd = opened
                        nextSendAtMs = nowMs()
                    }

                    var now = nowMs()

                    if (powerCheckDue(now, nextPowerCheckAtMs)) {
                        nextPowerCheckAtMs = now + POWER_POLL_MS
                        powerFloorMs = runCatching { transport.powerState() }
                            .getOrDefault(PowerState.NORMAL).probeGapFloorMs
                    }

                    // A cadence change has to move the pending deadline too.
                    // Storing the new interval alone meant a shorter setting
                    // still waited out whatever the old one had scheduled.
                    // Anchored on the last real send, so shortening cannot
                    // fire a burst and lengthening cannot bring a send forward.
                    if (intervalChanged) {
                        intervalChanged = false
                        val iv = _intervalMs
                        val gap = (if (iv > 0L) iv else ADAPTIVE_WATCHDOG_MS)
                            .coerceAtLeast(powerFloorMs)
                        if (lastSentAtMs >= 0L) nextSendAtMs = lastSentAtMs + gap
                    }

                    if (now >= nextSendAtMs) {
                        if (!isActive) break
                        if (outstanding.size < MAX_OUTSTANDING) {
                            val seq = nextSeq
                            nextSeq = (nextSeq + 1) and 0xFFFF
                            val sendUsec = runCatching {
                                transport.send(fd, session, seq, _packetSize)
                            }.getOrDefault(SEND_SOCK_ERR)
                            if (sendUsec < 0) {
                                if (!isActive) break
                                dropSocket(LocalFault.SEND_FAILED)
                                continue
                            }
                            // Stamp the send moment AFTER the syscall returns, so
                            // the x position reflects when the packet actually left.
                            val sentMs = nowMs()
                            lastSentAtMs = sentMs
                            outstanding[seq] = longArrayOf(sendUsec, sentMs)
                            onEvent(PingEvent.Sent(seq, Ping.pending(markAt(sentMs))))
                        }
                        val iv = _intervalMs
                        val gap = if (iv > 0L) iv else ADAPTIVE_WATCHDOG_MS
                        // Widen the floor under battery saver or thermal
                        // pressure. Sampling slows rather than stopping, so the
                        // graph never invents an outage the target did not have.
                        nextSendAtMs = now + gap.coerceAtLeast(powerFloorMs)
                    }

                    val oldestDeadline = outstanding.values.firstOrNull()
                        ?.let { it[1] + PING_TIMEOUT_MS } ?: Long.MAX_VALUE
                    val budget = (minOf(nextSendAtMs, oldestDeadline) - now)
                        .coerceAtMost(MAX_POLL_SLICE_MS)
                    if (budget > 0) {
                        val packed = runCatching { transport.await(fd, session, budget.toInt()) }
                            .getOrDefault(AWAIT_SOCK_ERR)
                        if (!isActive) break
                        when {
                            packed == AWAIT_SOCK_ERR -> { dropSocket(LocalFault.SOCKET_LOST); continue }

                            packed == AWAIT_NOTHING -> Unit

                            else -> {
                                val seq = (packed and 0xFFFF).toInt()
                                val recvUsec = (packed ushr 16) and USEC_MASK
                                // Read the entry BEFORE removing it. Kotlin/Native
                                // invalidates a map entry reference the moment the
                                // backing map changes, and reading it afterwards
                                // threw an uncaught ConcurrentModificationException
                                // that killed the whole app on the first timeout.
                                val entry = outstanding.remove(seq)
                                if (entry != null) {
                                    val sendUsec = entry[0] and USEC_MASK
                                    val sentAtMs = entry[1]
                                    val rttMs = ((recvUsec - sendUsec) and USEC_MASK) / 1000.0
                                    // A reply older than the timeout is not a
                                    // measurement. The process can be suspended
                                    // between send and receive, which on mobile
                                    // is routine; without this guard the whole
                                    // suspension is reported as round-trip
                                    // time, and a 26-second "RTT" then poisons
                                    // the average and the range.
                                    if (rttMs > PING_TIMEOUT_MS) {
                                        resolve(seq, Ping.timeout(markAt(sentAtMs)))
                                    } else {
                                        resolve(seq, Ping.reply(rttMs, markAt(sentAtMs)))
                                    }
                                    // Adaptive mode pulls the next send forward only
                                    // once the pipeline is drained, otherwise every
                                    // watchdog probe ratchets the rate upward.
                                    if (_intervalMs <= 0L && outstanding.isEmpty()) {
                                        nextSendAtMs = nowMs() + powerFloorMs
                                    }
                                }
                                // else: a late reply for an already-reaped probe.
                            }
                        }
                    }

                    // Reap expired probes, oldest first.
                    now = nowMs()
                    val iterator = outstanding.entries.iterator()
                    while (iterator.hasNext()) {
                        val entry = iterator.next()
                        val seq = entry.key            // read both before remove()
                        val sentAtMs = entry.value[1]
                        if (now - sentAtMs >= PING_TIMEOUT_MS) {
                            iterator.remove()
                            resolve(seq, Ping.timeout(markAt(sentAtMs)))
                        } else break
                    }
                }
            } finally {
                // Settle the pipeline before letting go, so a stopped engine
                // never leaves a probe pending in the history forever.
                flushOutstanding(nowMs())
                // Sole owner: nothing else ever closes this descriptor.
                if (fd >= 0) transport.close(fd)
            }
        }
        return true
    }

    /**
     * Stop probing. Idempotent.
     *
     * The loop notices cancellation and closes its own socket within
     * [MAX_POLL_SLICE_MS] of the current platform call returning. Name
     * resolution is the exception: it blocks uninterruptibly, so a stuck
     * resolver delays the exit by however long it takes to answer. Nothing is
     * opened or sent in the meantime. Use [stopAndJoin] to observe the exit.
     */
    override fun stop() {
        if (state == State.STOPPED) return
        state = State.STOPPED
        scope.cancel()
    }

    /**
     * [stop], then suspend until the loop has actually released its socket.
     *
     * Cancellation propagates. Swallowing it here let a cancelled caller
     * believe the socket was released when the loop was still holding it.
     */
    override suspend fun stopAndJoin() {
        val job = loop
        stop()
        job?.join()
    }

    override fun updateInterval(intervalMs: Long) {
        _intervalMs = sanitizeIntervalMs(intervalMs)
        intervalChanged = true
    }

    override fun updatePacketSize(packetSize: Int) {
        _packetSize = sanitizePayloadSize(packetSize)
    }
}
