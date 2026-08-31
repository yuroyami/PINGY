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
    /** A probe was sent and its reply came back. [Ping.rttMs] is set. */
    REPLY,

    /** A probe was sent and never answered within the timeout. Counts as loss. */
    TIMEOUT,

    /**
     * No probe left this device: DNS failed, the socket would not open, the
     * send failed, or the platform has no ICMP transport. Never counts as
     * loss, because nothing was ever measured.
     */
    LOCAL_FAULT,
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
 * One probe outcome. [timestamp] is the moment the probe was *sent*, which is
 * the honest x position for a pipelined sampler: sends are evenly scheduled
 * while completions arrive in bursts.
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
) {
    /** Rounded RTT for drawing and readouts. Null unless this is a reply. */
    val value: Int? get() = rttMs?.roundToInt()

    /** A sent-but-unanswered probe. The only thing that counts as packet loss. */
    val isLoss: Boolean get() = kind == PingKind.TIMEOUT

    /** True when this device could not probe at all. Excluded from statistics. */
    val isLocalFault: Boolean get() = kind == PingKind.LOCAL_FAULT

    /** Whether a probe actually left the device, so it belongs in denominators. */
    val wasSent: Boolean get() = kind != PingKind.LOCAL_FAULT

    companion object {
        fun reply(rttMs: Double, sentAt: TimeSource.Monotonic.ValueTimeMark) =
            Ping(rttMs, PingKind.REPLY, sentAt)

        fun timeout(sentAt: TimeSource.Monotonic.ValueTimeMark) =
            Ping(null, PingKind.TIMEOUT, sentAt)

        fun localFault(fault: LocalFault, at: TimeSource.Monotonic.ValueTimeMark) =
            Ping(null, PingKind.LOCAL_FAULT, at, fault)
    }
}
