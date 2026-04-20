package com.yuroyami.pingy.utils

import java.net.Inet4Address
import java.net.InetAddress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Android [pingOnce] actual: delegates to the NDK-compiled `libpingy_icmp.so`
 * via [NativeIcmpPing]. The native call blocks its thread inside `poll(2)`
 * up to [timeoutMs], so we hop to the IO dispatcher for it.
 */
actual suspend fun pingOnce(
    host: String,
    timeoutMs: Int,
    payloadSize: Int,
): Double? = withContext(Dispatchers.IO) {
    NativeIcmpPing.pingOnce(host, timeoutMs, payloadSize)
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
