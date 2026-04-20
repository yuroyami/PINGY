package com.yuroyami.pingy.utils

import com.yuroyami.pingy.native.icmp.do_ping_once_c
import com.yuroyami.pingy.native.icmp.resolve_host
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext

/**
 * iOS [pingOnce] actual: calls straight into the cinterop `do_ping_once_c`,
 * which does the entire resolve → socket → send → poll → recvfrom → timestamp
 * dance in one C function (mirror of the Android/JVM JNI path in
 * `shared/native/icmp_ping.c`). Keeping the hot loop inside C means the
 * measured window doesn't include Kotlin/Native ↔ C transitions or
 * `usePinned` bookkeeping, so the reported RTT reflects the network alone.
 *
 * Returns null on failure / timeout (native sentinel is -1.0).
 */
actual suspend fun pingOnce(
    host: String,
    timeoutMs: Int,
    payloadSize: Int,
): Double? = withContext(Dispatchers.IO) {
    val rtt = do_ping_once_c(host, timeoutMs, payloadSize)
    if (rtt < 0.0) null else rtt
}

/**
 * iOS [resolveHostToIpv4] actual: calls straight into the cinterop
 * `resolve_host`, which shortcuts IPv4 literals via `inet_pton` and otherwise
 * walks the system's `getaddrinfo`.
 */
actual fun resolveHostToIpv4(host: String): String? = memScoped {
    val buf = ByteArray(64)
    val rc = buf.usePinned { resolve_host(host, it.addressOf(0), buf.size) }
    if (rc != 0) return@memScoped null
    buf.decodeToString().trimEnd('\u0000').takeIf { it.isNotEmpty() }
}
