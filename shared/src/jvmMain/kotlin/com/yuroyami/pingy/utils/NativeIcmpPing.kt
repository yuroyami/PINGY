package com.yuroyami.pingy.utils

import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * JVM (desktop) counterpart to the Android [NativeIcmpPing] — same JNI
 * symbols, same C source (`shared/native/icmp_ping.c`), just compiled
 * per-platform at build time by the `buildJvmNative` Gradle task and
 * packaged as resources.
 *
 * At first use we detect the running OS/arch, extract the matching native
 * library from the jar to a temp file, and [System.load] it. Subsequent
 * calls go straight through JNI.
 *
 * Supported hosts (matches what the C source accepts via
 * SOCK_DGRAM+IPPROTO_ICMP):
 *   - macOS x86_64 / arm64
 *   - Linux x86_64 / arm64  (requires `net.ipv4.ping_group_range` to include
 *                            the invoking user's gid; most distros leave it
 *                            permissive)
 *
 * Windows is deliberately unsupported: Winsock doesn't expose
 * SOCK_DGRAM+IPPROTO_ICMP, so [nativeOpenSocket] returns `-1` there.
 */
internal object NativeIcmpPing {
    private val loaded: Boolean = runCatching { loadNative() }
        .onFailure { loggye("NativeIcmpPing: failed to load native lib", it) }
        .isSuccess

    /** Returns the file descriptor on success, or `-1` on any failure
     *  (including the lib not being loadable on this platform). */
    fun openSocket(ipv4: String): Int = if (loaded) nativeOpenSocket(ipv4) else -1

    /** See [com.yuroyami.pingy.utils.pingOnSocket] for the sentinel contract. */
    fun pingOnSocket(fd: Int, timeoutMs: Int, payloadSize: Int): Double =
        if (loaded) nativePingOnSocket(fd, timeoutMs, payloadSize) else -1.0

    /** Safe to call with `-1` or when the lib isn't loaded. */
    fun closeSocket(fd: Int) {
        if (loaded && fd >= 0) nativeCloseSocket(fd)
    }

    @JvmStatic
    external fun nativeOpenSocket(ipv4: String): Int

    @JvmStatic
    external fun nativePingOnSocket(fd: Int, timeoutMs: Int, payloadSize: Int): Double

    @JvmStatic
    external fun nativeCloseSocket(fd: Int)

    private fun loadNative() {
        val os = System.getProperty("os.name").lowercase()
        val arch = System.getProperty("os.arch").lowercase()
        val (dir, ext) = when {
            os.contains("mac") || os.contains("darwin") -> "darwin" to "dylib"
            os.contains("linux") -> "linux" to "so"
            else -> error("Unsupported OS for unprivileged ICMP: $os")
        }
        val archDir = when {
            arch.contains("aarch64") || arch.contains("arm64") -> "arm64"
            arch.contains("x86_64") || arch.contains("amd64") -> "x86_64"
            else -> error("Unsupported arch: $arch")
        }
        val resourcePath = "/native/$dir-$archDir/libpingy_icmp.$ext"
        val stream = NativeIcmpPing::class.java.getResourceAsStream(resourcePath)
            ?: error("Native lib not found on classpath: $resourcePath")
        val tmp = Files.createTempFile("libpingy_icmp-", ".$ext")
        tmp.toFile().deleteOnExit()
        stream.use { Files.copy(it, tmp, StandardCopyOption.REPLACE_EXISTING) }
        System.load(tmp.toAbsolutePath().toString())
    }
}
