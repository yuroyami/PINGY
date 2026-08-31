package com.yuroyami.pingy.utils

import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * JVM desktop counterpart to the Android bridge: same JNI symbols, same C
 * source, compiled per host by the `buildJvmNative` Gradle task and packaged as
 * a jar resource.
 *
 * On first use the matching library is extracted to a temp file and loaded.
 * `Files.createTempFile` creates it atomically at owner-only permissions, so
 * there is no symlink race, but the extracted bytes are still not verified
 * against an expected hash. Ship a signed, bundled library rather than relying
 * on this path for release builds.
 *
 * Supported hosts, matching what the C accepts via SOCK_DGRAM + IPPROTO_ICMP:
 *   - macOS x86_64 / arm64
 *   - Linux x86_64 / arm64 (needs `net.ipv4.ping_group_range` to include the
 *     invoking gid, which most distributions leave permissive)
 *
 * Windows is unsupported: Winsock has no unprivileged ICMP datagram socket.
 */
internal object NativeIcmpPing {

    /** False on Windows, on an unknown architecture, or if extraction fails. */
    val loaded: Boolean = runCatching { loadNative() }
        .onFailure { loggyw("NativeIcmpPing: no unprivileged ICMP on this host: ${it.message}") }
        .isSuccess

    fun openSocket(ipv4: String): Int = if (loaded) nativeOpenSocket(ipv4) else -1

    fun sendProbe(fd: Int, session: Long, seq: Int, payloadSize: Int): Long =
        if (loaded) nativeSendProbe(fd, session, seq, payloadSize) else -1L

    fun awaitReply(fd: Int, session: Long, budgetMs: Int): Long =
        if (loaded) nativeAwaitReply(fd, session, budgetMs) else -1L

    fun closeSocket(fd: Int) {
        if (loaded && fd >= 0) nativeCloseSocket(fd)
    }

    fun resolveHost(host: String): String? =
        if (loaded) runCatching { nativeResolveHost(host) }.getOrNull() else null

    @JvmStatic external fun nativeOpenSocket(ipv4: String): Int
    @JvmStatic external fun nativeSendProbe(fd: Int, session: Long, seq: Int, payloadSize: Int): Long
    @JvmStatic external fun nativeAwaitReply(fd: Int, session: Long, budgetMs: Int): Long
    @JvmStatic external fun nativeCloseSocket(fd: Int)
    @JvmStatic external fun nativeResolveHost(host: String): String?

    private fun loadNative() {
        val os = System.getProperty("os.name").lowercase()
        val arch = System.getProperty("os.arch").lowercase()
        val (dir, ext) = when {
            os.contains("mac") || os.contains("darwin") -> "darwin" to "dylib"
            os.contains("linux") -> "linux" to "so"
            else -> error("unsupported OS for unprivileged ICMP: $os")
        }
        val archDir = when {
            arch.contains("aarch64") || arch.contains("arm64") -> "arm64"
            arch.contains("x86_64") || arch.contains("amd64") -> "x86_64"
            else -> error("unsupported architecture: $arch")
        }
        val resourcePath = "/native/$dir-$archDir/libpingy_icmp.$ext"
        val stream = NativeIcmpPing::class.java.getResourceAsStream(resourcePath)
            ?: error("native library not on the classpath: $resourcePath")
        val tmp = Files.createTempFile("libpingy_icmp-", ".$ext")
        tmp.toFile().deleteOnExit()
        stream.use { Files.copy(it, tmp, StandardCopyOption.REPLACE_EXISTING) }
        System.load(tmp.toAbsolutePath().toString())
    }
}
