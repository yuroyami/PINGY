package com.yuroyami.pingy.utils

import java.net.Inet4Address
import java.net.InetAddress

/**
 * JVM (desktop) [openIcmpSocket] actual: same JNI path as Android, just
 * compiled against the host OS's toolchain at build time and extracted from
 * the jar at first use. See [NativeIcmpPing] for the runtime loader.
 *
 * On Windows the native lib doesn't exist (Winsock has no unprivileged ICMP
 * path), so [NativeIcmpPing.openSocket] returns `-1` without attempting a
 * syscall, and the engine treats that as a transient open failure.
 */
actual fun openIcmpSocket(ipv4: String): Int = NativeIcmpPing.openSocket(ipv4)

/** JVM [icmpSendProbe] actual: fire-and-forget JNI send. */
actual fun icmpSendProbe(fd: Int, seq: Int, payloadSize: Int): Long =
    NativeIcmpPing.sendProbe(fd, seq, payloadSize)

/** JVM [icmpAwaitReply] actual: blocks in poll(2) on the engine's own
 * single-lane dispatcher, which exists precisely for this call. */
actual fun icmpAwaitReply(fd: Int, budgetMs: Int): Long =
    NativeIcmpPing.awaitReply(fd, budgetMs)

/** JVM [closeIcmpSocket] actual — no-op when the lib isn't loaded. */
actual fun closeIcmpSocket(fd: Int) {
    NativeIcmpPing.closeSocket(fd)
}

/**
 * JVM [resolveHostToIpv4] actual: identical to the Android implementation —
 * InetAddress is JDK-standard and works off whatever resolver the host OS
 * provides (macOS `mDNSResponder`, Linux `nsswitch.conf`, etc.).
 */
actual fun resolveHostToIpv4(host: String): String? = try {
    InetAddress.getAllByName(host)
        .firstOrNull { it is Inet4Address }
        ?.hostAddress
} catch (_: Throwable) {
    null
}
