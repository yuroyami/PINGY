package com.yuroyami.pingy.utils

/**
 * JVM desktop actuals: the same JNI path Android uses, compiled for the host at
 * build time and extracted from the jar on first use.
 *
 * Windows exposes no SOCK_DGRAM + IPPROTO_ICMP, so the library is simply absent
 * there. [icmpTransportAvailable] reports that honestly instead of letting the
 * engine manufacture a packet loss every 200 ms forever.
 */

actual fun openIcmpSocket(ipv4: String): Int = NativeIcmpPing.openSocket(ipv4)

actual fun icmpSendProbe(fd: Int, session: Long, seq: Int, payloadSize: Int): Long =
    NativeIcmpPing.sendProbe(fd, session, seq, payloadSize)

actual fun icmpAwaitReply(fd: Int, session: Long, budgetMs: Int): Long =
    NativeIcmpPing.awaitReply(fd, session, budgetMs)

actual fun closeIcmpSocket(fd: Int) {
    NativeIcmpPing.closeSocket(fd)
}

actual fun icmpTransportAvailable(): Boolean = NativeIcmpPing.loaded

actual fun resolveHostToIpv4(host: String): String? = NativeIcmpPing.resolveHost(host)
