package com.yuroyami.pingy.utils

import java.net.Inet4Address
import java.net.InetAddress

/**
 * Android [openIcmpSocket] actual: thin wrapper around the JNI entry that
 * does `socket(AF_INET, SOCK_DGRAM, IPPROTO_ICMP)` + `connect()`.
 */
actual fun openIcmpSocket(ipv4: String): Int = NativeIcmpPing.nativeOpenSocket(ipv4)

/** Android [icmpSendProbe] actual: fire-and-forget JNI send. */
actual fun icmpSendProbe(fd: Int, seq: Int, payloadSize: Int): Long =
    NativeIcmpPing.nativeSendProbe(fd, seq, payloadSize)

/** Android [icmpAwaitReply] actual: blocks in poll(2) on the engine's own
 * single-lane dispatcher, which exists precisely for this call. */
actual fun icmpAwaitReply(fd: Int, budgetMs: Int): Long =
    NativeIcmpPing.nativeAwaitReply(fd, budgetMs)

/** Android [closeIcmpSocket] actual — idempotent on negative fds. */
actual fun closeIcmpSocket(fd: Int) {
    NativeIcmpPing.nativeCloseSocket(fd)
}

/**
 * Android [resolveHostToIpv4] actual: uses [InetAddress.getAllByName] and
 * picks the first IPv4 entry (the native ICMP path is v4-only). Returns
 * null on any resolver error; the engine falls back to passing the original
 * host string through, which the native resolver will then fail on — same
 * observable outcome as a transient DNS hiccup.
 */
actual fun resolveHostToIpv4(host: String): String? = try {
    InetAddress.getAllByName(host)
        .firstOrNull { it is Inet4Address }
        ?.hostAddress
} catch (_: Throwable) {
    null
}
