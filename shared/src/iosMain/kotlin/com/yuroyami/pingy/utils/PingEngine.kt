package com.yuroyami.pingy.utils

import com.yuroyami.pingy.native.icmp.resolve_host
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext

/**
 * iOS [pingOnce] actual: delegates to [PingUtils.pingOnce], which is a
 * pure Kotlin/Native + POSIX cinterop implementation (see
 * `shared/src/nativeInterop/cinterop/IcmpPing.def` and its matching
 * `PingUtils.kt`). Darwin's kernel demuxes ECHOREPLYs per-socket, so the
 * Kotlin layer only has to validate type + checksum.
 */
actual suspend fun pingOnce(
    host: String,
    timeoutMs: Int,
    payloadSize: Int,
): Double? = withContext(Dispatchers.IO) {
    PingUtils.pingOnce(
        host = host,
        timeoutMs = timeoutMs,
        payloadSize = payloadSize,
    )
}

/**
 * iOS [resolveHostToIpv4] actual: calls straight into the cinterop
 * `resolve_host` (same helper `PingUtils` uses), which shortcuts IPv4
 * literals via `inet_pton` and otherwise walks the system's `getaddrinfo`.
 */
actual fun resolveHostToIpv4(host: String): String? = memScoped {
    val buf = ByteArray(64)
    val rc = buf.usePinned { resolve_host(host, it.addressOf(0), buf.size) }
    if (rc != 0) return@memScoped null
    buf.decodeToString().trimEnd('\u0000').takeIf { it.isNotEmpty() }
}
