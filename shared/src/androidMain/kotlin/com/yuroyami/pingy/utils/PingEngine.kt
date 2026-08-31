package com.yuroyami.pingy.utils

/** Android actuals: thin JNI hops into `libpingy_icmp.so`. */

actual fun openIcmpSocket(ipv4: String): Int = NativeIcmpPing.nativeOpenSocket(ipv4)

actual fun icmpSendProbe(fd: Int, session: Long, seq: Int, payloadSize: Int): Long =
    NativeIcmpPing.nativeSendProbe(fd, session, seq, payloadSize)

/** Blocks in poll(2) on the engine's own single-lane dispatcher. */
actual fun icmpAwaitReply(fd: Int, session: Long, budgetMs: Int): Long =
    NativeIcmpPing.nativeAwaitReply(fd, session, budgetMs)

actual fun closeIcmpSocket(fd: Int) {
    NativeIcmpPing.nativeCloseSocket(fd)
}

/**
 * Android's `net.ipv4.ping_group_range` normally admits app UIDs, so the only
 * real question is whether the native library loaded at all.
 */
actual fun icmpTransportAvailable(): Boolean = NativeIcmpPing.loaded

/**
 * Resolution happens natively so Android and iOS share one code path, including
 * the IPv4-literal short circuit. Returns null when the host has no IPv4 record.
 */
actual fun resolveHostToIpv4(host: String): String? =
    runCatching { NativeIcmpPing.nativeResolveHost(host) }.getOrNull()
