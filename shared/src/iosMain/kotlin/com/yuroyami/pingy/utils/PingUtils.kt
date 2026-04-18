package com.yuroyami.pingy.utils

import com.yuroyami.pingy.native.icmp.*
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.UShortVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.posix.close

/**
 * Pure Kotlin/Native ICMP ping implementation using POSIX sockets via cinterop.
 * Replaces the SPLPing Objective-C library.
 *
 * Important Darwin quirk: with `SOCK_DGRAM + IPPROTO_ICMP` the kernel assigns a
 * unique ICMP identifier per socket and rewrites the id field on send / filters
 * replies on receive. We therefore do NOT match against our own `identifier`
 * value — we trust the kernel's demux and only verify that the reply is an
 * ECHOREPLY with a valid checksum (mirroring SPLPing's behavior).
 */
object PingUtils {

    private const val RECV_BUF_SIZE = 1024
    private const val IP_BUF_SIZE = 64
    private const val PACKET_BUF_SIZE = 512

    private const val ICMP_ECHOREPLY: Int = 0

    /**
     * Performs a single ICMP ping to the given host.
     *
     * @param host Hostname or IP address to ping.
     * @param timeoutMs Timeout in milliseconds for the ping response.
     * @param payloadSize Size of the ICMP payload in bytes.
     * @param identifier Identifier to place in the sent ICMP header (informational;
     *                   the kernel may overwrite it on Darwin).
     * @param sequenceNumber Sequence number for this ping.
     * @return Round-trip time in milliseconds, or null on failure/timeout.
     */
    fun pingOnce(
        host: String,
        timeoutMs: Int = 1000,
        payloadSize: Int = 32,
        identifier: Int = 0xABCD,
        sequenceNumber: Int = 1,
    ): Double? = memScoped {
        // 1. Resolve hostname to IPv4 dotted string
        val ipBuf = ByteArray(IP_BUF_SIZE)
        val resolved = ipBuf.usePinned { pinned ->
            resolve_host(host, pinned.addressOf(0), IP_BUF_SIZE)
        }
        if (resolved != 0) {
            loggy("PingUtils: host resolution failed for '$host' (gai/errno=$resolved)")
            return null
        }
        val ipAddress = ipBuf.decodeToString().trimEnd('\u0000')

        // 2. Open unprivileged ICMP socket
        val sockfd = create_icmp_socket()
        if (sockfd < 0) {
            loggy("PingUtils: create_icmp_socket failed (errno=${get_errno()}) host=$host")
            return null
        }

        try {
            // 3. Build the echo request
            val packetBuf = ByteArray(PACKET_BUF_SIZE)
            val packetSize = packetBuf.usePinned { pinned ->
                build_icmp_echo_request(
                    buf = pinned.addressOf(0),
                    buf_size = PACKET_BUF_SIZE,
                    identifier = identifier.toUShort(),
                    sequence_number = sequenceNumber.toUShort(),
                    payload_size = payloadSize
                )
            }
            if (packetSize < 0) {
                loggy("PingUtils: build_icmp_echo_request failed")
                return null
            }

            // 4. Send
            val sendTime = get_time_usec()
            val sent = packetBuf.usePinned { pinned ->
                send_icmp_packet(sockfd, ipAddress, pinned.addressOf(0), packetSize)
            }
            if (sent < 0) {
                loggy("PingUtils: send_icmp_packet failed (errno=${get_errno()}) host=$host ip=$ipAddress")
                return null
            }

            // 5. Receive. Loop over replies until we see a well-formed ECHOREPLY or timeout.
            val recvBuf = ByteArray(RECV_BUF_SIZE)
            val outId = alloc<UShortVar>()
            val outSeq = alloc<UShortVar>()
            val outType = alloc<UByteVar>()
            val outCode = alloc<UByteVar>()

            var remainingMs = timeoutMs
            while (remainingMs > 0) {
                val waitStart = get_time_usec()

                val received = recvBuf.usePinned { pinned ->
                    recv_icmp_packet(sockfd, pinned.addressOf(0), RECV_BUF_SIZE, remainingMs)
                }
                if (received < 0) return null // timeout or error

                val recvTime = get_time_usec()

                val parsed = recvBuf.usePinned { pinned ->
                    parse_icmp_echo_reply(
                        buf = pinned.addressOf(0),
                        buf_len = received,
                        out_id = outId.ptr,
                        out_seq = outSeq.ptr,
                        out_type = outType.ptr,
                        out_code = outCode.ptr
                    )
                }

                // parsed == 0 means well-formed + checksum valid.
                // On Darwin DGRAM+ICMP the kernel already demuxes replies to our socket,
                // so any ECHOREPLY we see is ours — no need to match id/seq.
                if (parsed == 0 && outType.value.toInt() == ICMP_ECHOREPLY) {
                    return (recvTime - sendTime).toDouble() / 1000.0
                }

                // Non-matching reply (wrong type, bad checksum, or too short):
                // adjust remaining timeout and try again.
                val elapsedMs = ((recvTime - waitStart) / 1000).toInt()
                remainingMs -= elapsedMs.coerceAtLeast(1)
            }

            null // timed out
        } finally {
            close(sockfd)
        }
    }
}
