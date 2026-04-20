package com.yuroyami.pingy.utils

import java.net.Inet4Address
import java.net.InetAddress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext

/**
 * JVM (desktop) [pingOnce] actual: same JNI path as Android, just compiled
 * against the host OS's toolchain at build time and extracted from the jar
 * at first use. See [NativeIcmpPing] for the runtime loader and the
 * `buildJvmNative` Gradle task for the compile step.
 *
 * On Windows the native lib does not exist (Winsock has no unprivileged
 * ICMP path), so this returns null immediately via [NativeIcmpPing.pingOnce].
 */
actual suspend fun pingOnce(
    host: String,
    timeoutMs: Int,
    payloadSize: Int,
): Double? = withContext(Dispatchers.IO) {
    NativeIcmpPing.pingOnce(host, timeoutMs, payloadSize)
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
