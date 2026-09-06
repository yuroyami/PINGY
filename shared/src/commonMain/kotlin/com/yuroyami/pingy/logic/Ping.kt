package com.yuroyami.pingy.logic

import com.yuroyami.pingy.i18n.Strings
import kotlin.math.roundToInt
import kotlin.time.TimeSource

/**
 * Why a sample exists. The distinction matters: a timeout says something about
 * the target, a local fault says something about *us*, and folding the second
 * into the first is what made the old loss percentage untrustworthy.
 */
enum class PingKind {
    /** A probe was sent and no verdict has arrived yet. Resolved in place. */
    PENDING,

    /** A probe was sent and its reply came back. [Ping.rttMs] is set. */
    REPLY,

    /** A probe was sent and never answered within the timeout. Counts as loss. */
    TIMEOUT,

    /**
     * A probe was sent, then the local socket died before its deadline. We
     * stopped being able to observe it, so nothing is known about the target.
     * Never counts as loss.
     */
    INTERRUPTED,

    /**
     * No probe left this device: DNS failed, the socket would not open, the
     * send failed, or the platform has no ICMP transport. Never counts as
     * loss, because nothing was ever measured.
     */
    LOCAL_FAULT,

    /**
     * A marker written when monitoring stops. The stretch from here to the
     * next send was never watched, so it is neither healthy nor lost.
     */
    UNOBSERVED,
}

/** What went wrong locally, so the UI can explain it instead of drawing a gap. */
enum class LocalFault {
    RESOLVE_FAILED,
    SOCKET_OPEN_FAILED,
    SEND_FAILED,
    SOCKET_LOST,
    UNSUPPORTED_PLATFORM;

    /** The localized sentence to show for this fault. */
    fun message(s: Strings): String = when (this) {
        RESOLVE_FAILED -> s.faultResolveFailed
        SOCKET_OPEN_FAILED -> s.faultSocketOpenFailed
        SEND_FAILED -> s.faultSendFailed
        SOCKET_LOST -> s.faultSocketLost
        UNSUPPORTED_PLATFORM -> s.faultUnsupportedPlatform
    }
}

/**
 * One probe. [timestamp] is the moment the probe was *sent*, which is the
 * honest x position for a pipelined sampler: sends are evenly scheduled while
 * completions arrive in bursts.
 *
 * A probe enters the history as [PingKind.PENDING] the moment it leaves, and
 * its verdict later replaces that entry in the same slot. The history is
 * therefore always in send order, and nothing already drawn ever moves.
 *
 * [rttMs] keeps sub-millisecond precision. Rounding at capture time floored
 * every LAN measurement and collapsed jitter to zero; rounding now happens
 * only in [value], at the point of display.
 */
data class Ping(
    val rttMs: Double?,
    val kind: PingKind,
    val timestamp: TimeSource.Monotonic.ValueTimeMark,
    val fault: LocalFault? = null,
    /** Platform errno behind a local fault, or 0 when there is none to give. */
    val faultCode: Int = 0,
) {
    /** Rounded RTT for drawing and readouts. Null unless this is a reply. */
    val value: Int? get() = rttMs?.roundToInt()

    /** A sent-but-unanswered probe. The only thing that counts as packet loss. */
    val isLoss: Boolean get() = kind == PingKind.TIMEOUT

    /** True when this device could not probe at all. Excluded from statistics. */
    val isLocalFault: Boolean get() = kind == PingKind.LOCAL_FAULT

    /** True while the probe is in the air. Excluded from statistics until resolved. */
    val isPending: Boolean get() = kind == PingKind.PENDING

    /** True when we lost the ability to observe a probe that really left. */
    val isInterrupted: Boolean get() = kind == PingKind.INTERRUPTED

    /** True for the gap marker written when monitoring stopped. */
    val isUnobserved: Boolean get() = kind == PingKind.UNOBSERVED

    /** Whether a probe actually left the device, so it belongs in denominators. */
    val wasSent: Boolean
        get() = kind != PingKind.LOCAL_FAULT && kind != PingKind.UNOBSERVED

    companion object {
        fun pending(sentAt: TimeSource.Monotonic.ValueTimeMark) =
            Ping(null, PingKind.PENDING, sentAt)

        fun reply(rttMs: Double, sentAt: TimeSource.Monotonic.ValueTimeMark) =
            Ping(rttMs, PingKind.REPLY, sentAt)

        fun timeout(sentAt: TimeSource.Monotonic.ValueTimeMark) =
            Ping(null, PingKind.TIMEOUT, sentAt)

        fun interrupted(sentAt: TimeSource.Monotonic.ValueTimeMark) =
            Ping(null, PingKind.INTERRUPTED, sentAt)

        /** Marks the moment monitoring stopped. Nothing after it was watched. */
        fun unobserved(at: TimeSource.Monotonic.ValueTimeMark) =
            Ping(null, PingKind.UNOBSERVED, at)

        fun localFault(fault: LocalFault, at: TimeSource.Monotonic.ValueTimeMark, code: Int = 0) =
            Ping(null, PingKind.LOCAL_FAULT, at, fault, code)
    }
}

/**
 * What the engine tells its listener. One [Sent] per probe, later exactly one
 * [Resolved] carrying the same [seq]; [Fault] stands alone because no probe
 * left the device. [Endpoint] is news about the target rather than a probe.
 */
sealed interface PingEvent {

    /** Everything that concerns one probe and belongs in the history. */
    sealed interface Probe : PingEvent {
        val ping: Ping
    }

    /** A probe left. [ping] is [PingKind.PENDING], stamped with the send moment. */
    data class Sent(val seq: Int, override val ping: Ping) : Probe

    /** The probe announced under [seq] got its verdict: a reply or a timeout. */
    data class Resolved(val seq: Int, override val ping: Ping) : Probe

    /** A local failure. Appended as its own entry, never tied to a probe. */
    data class Fault(override val ping: Ping) : Probe

    /**
     * The address this engine is probing now, announced after every
     * resolution. A hostname can move, and a measurement is only meaningful
     * next to the endpoint that produced it.
     */
    data class Endpoint(val ipv4: String) : PingEvent
}
