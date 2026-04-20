package com.yuroyami.pingy.utils

import java.net.Inet4Address
import java.net.InetAddress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Android [openIcmpSocket] actual: thin wrapper around the JNI entry that
 * does `socket(AF_INET, SOCK_DGRAM, IPPROTO_ICMP)` + `connect()`.
 */
actual fun openIcmpSocket(ipv4: String): Int = NativeIcmpPing.nativeOpenSocket(ipv4)

/**
 * Android [pingOnSocket] actual: delegates to the JNI `nativePingOnSocket`.
 * The native call blocks its thread in `poll(2)` up to [timeoutMs], so we
 * hop to the IO dispatcher.
 */
actual suspend fun pingOnSocket(
    fd: Int,
    timeoutMs: Int,
    payloadSize: Int,
): Double = withContext(Dispatchers.IO) {
    NativeIcmpPing.nativePingOnSocket(fd, timeoutMs, payloadSize)
}

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
