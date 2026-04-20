package com.yuroyami.pingy.utils

/**
 * JNI bridge to `libpingy_icmp.so`, the unprivileged ICMP shim compiled from
 * `shared/native/icmp_ping.c` by the Android NDK toolchain (see
 * `androidApp/src/main/cpp/CMakeLists.txt`).
 *
 * Why ship our own native lib instead of exec'ing `/system/bin/ping`?
 * Android's stock ping enforces a ~200ms floor on the `-i` interval for
 * non-root callers, and every single-shot invocation pays a fork + linker
 * + DNS-resolver startup cost on top. Going direct via a JNI-fronted
 * SOCK_DGRAM+IPPROTO_ICMP socket is behaviorally interchangeable with the
 * iOS-side Kotlin/Native implementation in `PingUtils.kt` and gets us to
 * the same microsecond-grade RTT fidelity.
 *
 * Android's kernel has `net.ipv4.ping_group_range` set to include the
 * `inet` gid, so app UIDs are allowed to open these sockets without any
 * special permission.
 */
internal object NativeIcmpPing {
    init {
        System.loadLibrary("pingy_icmp")
    }

    /**
     * Returns the RTT in milliseconds, or a negative sentinel (-1.0) on any
     * failure (resolution, socket, send, poll, timeout, malformed reply).
     * Blocks the calling thread for up to [timeoutMs].
     */
    @JvmStatic
    external fun nativePingOnce(host: String, timeoutMs: Int, payloadSize: Int): Double

    /** Null-on-failure wrapper over [nativePingOnce]. */
    fun pingOnce(host: String, timeoutMs: Int, payloadSize: Int): Double? {
        val rtt = nativePingOnce(host, timeoutMs, payloadSize)
        return if (rtt < 0.0) null else rtt
    }
}
