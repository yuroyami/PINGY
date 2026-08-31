package com.yuroyami.pingy.utils

import com.yuroyami.pingy.native.icmp.close_icmp_socket
import com.yuroyami.pingy.native.icmp.icmp_await_reply
import com.yuroyami.pingy.native.icmp.icmp_send_probe
import com.yuroyami.pingy.native.icmp.open_icmp_socket_connected
import com.yuroyami.pingy.native.icmp.resolve_host
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned

/**
 * iOS actuals: cinterop calls into the same `shared/native/icmp_core.h` that
 * the Android and JVM JNI wrapper includes, so the wire format, checksum and
 * reply parser cannot drift between platforms.
 */

actual fun openIcmpSocket(ipv4: String): Int = open_icmp_socket_connected(ipv4)

actual fun icmpSendProbe(fd: Int, session: Long, seq: Int, payloadSize: Int): Long =
    icmp_send_probe(fd, session, seq, payloadSize)

actual fun icmpAwaitReply(fd: Int, session: Long, budgetMs: Int): Long =
    icmp_await_reply(fd, session, budgetMs)

actual fun closeIcmpSocket(fd: Int) {
    close_icmp_socket(fd)
}

/** Darwin always exposes unprivileged ICMP to app processes. */
actual fun icmpTransportAvailable(): Boolean = true

actual fun resolveHostToIpv4(host: String): String? {
    val buf = ByteArray(64)
    val rc = buf.usePinned { resolve_host(host, it.addressOf(0), buf.size) }
    if (rc != 0) return null
    val end = buf.indexOf(0).let { if (it < 0) buf.size else it }
    return buf.decodeToString(0, end).takeIf { it.isNotEmpty() }
}
