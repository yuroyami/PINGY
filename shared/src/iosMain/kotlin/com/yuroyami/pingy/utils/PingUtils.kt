package com.yuroyami.pingy.utils

import com.yuroyami.pingy.native.icmp.*
import kotlinx.cinterop.UShortVar
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.posix.close

/**
 * Pure Kotlin/Native ICMP ping implementation using POSIX sockets via cinterop.
 * Replaces the SPLPing Objective-C library entirely.
 */
object PingUtils {

    private const val RECV_BUF_SIZE = 1024
    private const val IP_BUF_SIZE = 64
    private const val PACKET_BUF_SIZE = 512

    /**
     * Performs a single ICMP ping to the given host.
     *
     * @param host Hostname or IP address to ping.
     * @param timeoutMs Timeout in milliseconds for the ping response.
     * @param payloadSize Size of the ICMP payload in bytes.
     * @param identifier Unique identifier for this ping session.
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
        // Resolve hostname to IP
        val ipBuf = ByteArray(IP_BUF_SIZE)
        val resolved = ipBuf.usePinned { pinned ->
            resolve_host(host, pinned.addressOf(0), IP_BUF_SIZE)
        }
        if (resolved != 0) return null

        val ipAddress = ipBuf.decodeToString().trimEnd('\u0000')

        // Create ICMP socket
        val sockfd = create_icmp_socket()
        if (sockfd < 0) return null

        try {
            // Build ICMP echo request
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
            if (packetSize < 0) return null

            // Record send time
            val sendTime = get_time_usec()

            // Send packet
            val sent = packetBuf.usePinned { pinned ->
                send_icmp_packet(sockfd, ipAddress, pinned.addressOf(0), packetSize)
            }
            if (sent < 0) return null

            // Receive reply (loop to skip non-matching replies)
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

                // Parse the reply
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

                if (parsed == 0
                    && outType.value.toInt() == 0 // ICMP_ECHOREPLY
                    && outId.value.toInt() == identifier
                    && outSeq.value.toInt() == sequenceNumber
                ) {
                    // Success — return RTT in milliseconds
                    return (recvTime - sendTime).toDouble() / 1000.0
                }

                // Not our reply, adjust remaining timeout and try again
                val elapsed = ((recvTime - waitStart) / 1000).toInt()
                remainingMs -= elapsed.coerceAtLeast(1)
            }

            null // timed out waiting for matching reply
        } finally {
            close(sockfd)
        }
    }
}
