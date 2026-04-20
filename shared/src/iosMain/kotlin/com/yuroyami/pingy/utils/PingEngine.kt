package com.yuroyami.pingy.utils

import com.yuroyami.pingy.native.icmp.close_icmp_socket
import com.yuroyami.pingy.native.icmp.open_icmp_socket_connected
import com.yuroyami.pingy.native.icmp.ping_once_on_socket
import com.yuroyami.pingy.native.icmp.resolve_host
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext

/**
 * iOS [openIcmpSocket] actual: single cinterop call into
 * `open_icmp_socket_connected` — a BSD `socket(AF_INET, SOCK_DGRAM,
 * IPPROTO_ICMP)` followed by `connect()`, both in C. Returns the fd or -1.
 */
actual fun openIcmpSocket(ipv4: String): Int = open_icmp_socket_connected(ipv4)

/**
 * iOS [pingOnSocket] actual: single cinterop call into `ping_once_on_socket`,
 * which does build → send → poll → recv → timestamp entirely in C. Keeps
 * the measured window free of K/N ↔ C transitions and ByteArray pinning.
 */
actual suspend fun pingOnSocket(
    fd: Int,
    timeoutMs: Int,
    payloadSize: Int,
): Double = withContext(Dispatchers.IO) {
    ping_once_on_socket(fd, timeoutMs, payloadSize)
}

/** iOS [closeIcmpSocket] actual — idempotent on negative fds. */
actual fun closeIcmpSocket(fd: Int) {
    close_icmp_socket(fd)
}

/**
 * iOS [resolveHostToIpv4] actual: calls straight into the cinterop
 * `resolve_host`, which shortcuts IPv4 literals via `inet_pton` and
 * otherwise walks the system's `getaddrinfo`.
 */
actual fun resolveHostToIpv4(host: String): String? = memScoped {
    val buf = ByteArray(64)
    val rc = buf.usePinned { resolve_host(host, it.addressOf(0), buf.size) }
    if (rc != 0) return@memScoped null
    buf.decodeToString().trimEnd('\u0000').takeIf { it.isNotEmpty() }
}
