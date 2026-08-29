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
 * iOS-side Kotlin/Native cinterop implementation (see `IcmpPing.def`) and
 * gets us to the same microsecond-grade RTT fidelity.
 *
 * Android's kernel has `net.ipv4.ping_group_range` set to include the
 * `inet` gid, so app UIDs are allowed to open these sockets without any
 * special permission.
 *
 * Pipelined API — the engine holds the socket open for its whole lifetime
 * and keeps many probes in flight over it:
 *  - [nativeOpenSocket]  → socket() + connect() to the resolved IPv4, once.
 *  - [nativeSendProbe]   → build + send one seq-stamped request, no waiting.
 *  - [nativeAwaitReply]  → poll()/recv() the next reply, whoever it answers.
 *  - [nativeCloseSocket] → close(), once at engine shutdown.
 */
internal object NativeIcmpPing {
    init {
        System.loadLibrary("pingy_icmp")
    }

    /** Returns the file descriptor on success, or `-1` on any failure. */
    @JvmStatic
    external fun nativeOpenSocket(ipv4: String): Int

    /** Send timestamp in monotonic usec (>= 0), or -1 on socket failure. */
    @JvmStatic
    external fun nativeSendProbe(fd: Int, seq: Int, payloadSize: Int): Long

    /** See [com.yuroyami.pingy.utils.icmpAwaitReply] for the packed contract. */
    @JvmStatic
    external fun nativeAwaitReply(fd: Int, budgetMs: Int): Long

    /** Safe to call with `-1`; no-op in that case. */
    @JvmStatic
    external fun nativeCloseSocket(fd: Int)
}
