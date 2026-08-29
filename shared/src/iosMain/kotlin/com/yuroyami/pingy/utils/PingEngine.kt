package com.yuroyami.pingy.utils

import com.yuroyami.pingy.native.icmp.close_icmp_socket
import com.yuroyami.pingy.native.icmp.icmp_await_reply
import com.yuroyami.pingy.native.icmp.icmp_send_probe
import com.yuroyami.pingy.native.icmp.open_icmp_socket_connected
import com.yuroyami.pingy.native.icmp.resolve_host
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned

/**
 * iOS [openIcmpSocket] actual: single cinterop call into
 * `open_icmp_socket_connected` — a BSD `socket(AF_INET, SOCK_DGRAM,
 * IPPROTO_ICMP)` followed by `connect()`, both in C. Returns the fd or -1.
 */
actual fun openIcmpSocket(ipv4: String): Int = open_icmp_socket_connected(ipv4)

/** iOS [icmpSendProbe] actual: fire-and-forget cinterop send. */
actual fun icmpSendProbe(fd: Int, seq: Int, payloadSize: Int): Long =
    icmp_send_probe(fd, seq, payloadSize)

/** iOS [icmpAwaitReply] actual: blocks in poll(2) on the engine's own
 * single-lane dispatcher, which exists precisely for this call. */
actual fun icmpAwaitReply(fd: Int, budgetMs: Int): Long =
    icmp_await_reply(fd, budgetMs)

/** iOS [closeIcmpSocket] actual — idempotent on negative fds. */
actual fun closeIcmpSocket(fd: Int) {
    close_icmp_socket(fd)
}

/**
 * iOS [resolveHostToIpv4] actual: calls straight into the cinterop
 * `resolve_host`, which shortcuts IPv4 literals via `inet_pton` and
 * otherwise walks the system's `getaddrinfo`.
 */
actual fun resolveHostToIpv4(host: String): String? {
    val buf = ByteArray(64)
    val rc = buf.usePinned { resolve_host(host, it.addressOf(0), buf.size) }
    if (rc != 0) return null
    return buf.decodeToString().trimEnd('\u0000').takeIf { it.isNotEmpty() }
}
