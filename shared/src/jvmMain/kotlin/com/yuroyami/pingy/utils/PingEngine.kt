package com.yuroyami.pingy.utils

import java.net.Inet4Address
import java.net.InetAddress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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

/** JVM [pingOnSocket] actual — delegates to the JNI, on the IO dispatcher. */
actual suspend fun pingOnSocket(
    fd: Int,
    timeoutMs: Int,
    payloadSize: Int,
): Double = withContext(Dispatchers.IO) {
    NativeIcmpPing.pingOnSocket(fd, timeoutMs, payloadSize)
}

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
