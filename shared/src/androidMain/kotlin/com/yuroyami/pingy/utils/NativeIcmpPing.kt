package com.yuroyami.pingy.utils

/**
 * JNI bridge to `libpingy_icmp.so`, built from `shared/native/icmp_ping.c` and
 * `icmp_core.h` by the NDK (see `androidApp/src/main/cpp/CMakeLists.txt`).
 *
 * Why not exec `/system/bin/ping`? It enforces a ~200 ms floor on `-i` for
 * non-root callers, and every invocation pays a fork, linker and resolver
 * startup cost. A JNI-fronted SOCK_DGRAM + IPPROTO_ICMP socket avoids both and
 * reaches microsecond-grade RTT fidelity.
 *
 * Pipelined API: the engine holds one socket open for its whole lifetime and
 * keeps many probes in flight over it. Every probe carries the engine's session
 * nonce so replies cannot be attributed to the wrong panel.
 */
internal object NativeIcmpPing {

    /** False when the shared library is missing or refuses to load. */
    val loaded: Boolean = runCatching { System.loadLibrary("pingy_icmp") }
        .onFailure { loggye("NativeIcmpPing: failed to load libpingy_icmp", it) }
        .isSuccess

    /** File descriptor on success, -1 on any failure. */
    @JvmStatic
    external fun nativeOpenSocket(ipv4: String): Int

    /** Send timestamp in monotonic microseconds, or -1 on socket failure. */
    @JvmStatic
    external fun nativeSendProbe(fd: Int, session: Long, seq: Int, payloadSize: Int): Long

    /** See [com.yuroyami.pingy.utils.icmpAwaitReply] for the packed contract. */
    @JvmStatic
    external fun nativeAwaitReply(fd: Int, session: Long, budgetMs: Int): Long

    /** Safe to call with -1. */
    @JvmStatic
    external fun nativeCloseSocket(fd: Int)

    /** Dotted IPv4 string, or null when the host has no IPv4 record. */
    @JvmStatic
    external fun nativeResolveHost(host: String): String?
}
