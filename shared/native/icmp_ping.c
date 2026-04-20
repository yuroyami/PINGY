/*
 * Unprivileged ICMP echo ("ping") via SOCK_DGRAM + IPPROTO_ICMP, exposed to the
 * JVM / Android as a single JNI entry point.
 *
 * Works on:
 *   - Android (Linux kernel, ping_group_range normally permissive for app UIDs)
 *   - macOS   (Darwin; the kernel assigns a per-socket ICMP id and demuxes replies)
 *   - Linux   (same ping_group_range trick as Android)
 *
 * Does NOT work on Windows (Winsock doesn't expose DGRAM+IPPROTO_ICMP — use
 * IcmpSendEcho from icmp.dll via a separate codepath if you ever need it).
 *
 * Returned value from the JNI entry: the round-trip time in milliseconds as a
 * jdouble, or a negative sentinel for any failure (caller maps to "lost").
 */

#include <jni.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <netinet/ip.h>
#include <netinet/ip_icmp.h>
#include <netdb.h>
#include <arpa/inet.h>
#include <sys/time.h>
#include <time.h>
#include <poll.h>
#include <unistd.h>
#include <string.h>
#include <errno.h>
#include <stdint.h>

/* Sentinel returned to Kotlin when anything about the probe fails. */
static const jdouble PING_FAIL = -1.0;

/* BSD Internet checksum, same algorithm as the classic `in_cksum`. */
static uint16_t icmp_checksum(const uint8_t *buffer, int len) {
    int32_t sum = 0;
    const uint16_t *cursor = (const uint16_t *)buffer;
    int bytes_left = len;
    while (bytes_left > 1) { sum += *cursor++; bytes_left -= 2; }
    if (bytes_left == 1) {
        uint16_t last = 0;
        *((uint8_t *)&last) = *((const uint8_t *)cursor);
        sum += last;
    }
    sum = (sum >> 16) + (sum & 0xffff);
    sum += (sum >> 16);
    return (uint16_t)~sum;
}

/* Build an ICMP echo request into `buf`. Returns total packet size or -1. */
static int build_echo_request(void *buf, int buf_size, uint16_t id, uint16_t seq, int payload_size) {
    if (buf_size < (int)(sizeof(struct icmp) + payload_size)) return -1;
    struct icmp *hdr = (struct icmp *)buf;
    memset(hdr, 0, sizeof(struct icmp) + payload_size);
    hdr->icmp_type = ICMP_ECHO;
    hdr->icmp_code = 0;
    hdr->icmp_id = htons(id);
    hdr->icmp_seq = htons(seq);
    /* Fill payload with a recognizable pattern (0x07 — matches SPLPing's convention
     * so this engine is behaviorally interchangeable with the iOS-side one). */
    unsigned char *payload = (unsigned char *)buf + sizeof(struct icmp);
    for (int i = 0; i < payload_size; i++) payload[i] = 0x07;
    int total = (int)sizeof(struct icmp) + payload_size;
    hdr->icmp_cksum = 0;
    hdr->icmp_cksum = icmp_checksum((const uint8_t *)buf, total);
    return total;
}

/*
 * Peel a received datagram and tell the caller what ICMP type it carries.
 *
 * What shows up on an unprivileged SOCK_DGRAM+IPPROTO_ICMP socket differs
 * between kernels:
 *   - Linux delivers just the ICMP message (no IP header).
 *   - Darwin delivers the full IP datagram including the IPv4 header.
 * We detect which by looking at byte 0: ICMP_ECHOREPLY = 0, while an IPv4
 * header begins with 0x45 (version 4, header length 5). If it's the IP case,
 * skip the header.
 *
 * Return 0 on parseable reply (out_type valid), -1 on malformed buffer.
 */
static int parse_echo_reply(const uint8_t *buf, int buf_len, int *out_type) {
    if (buf_len < 8 || out_type == NULL) return -1;
    const uint8_t *icmp = buf;
    int icmp_len = buf_len;
    /* IPv4 header prefix detection: high nibble 4 (version) + low nibble >= 5 (IHL). */
    if ((buf[0] & 0xF0) == 0x40) {
        int ip_hdr_len = (buf[0] & 0x0F) << 2;
        if (ip_hdr_len < 20 || buf_len < ip_hdr_len + 8) return -1;
        icmp = buf + ip_hdr_len;
        icmp_len = buf_len - ip_hdr_len;
    }
    if (icmp_len < 8) return -1;
    *out_type = icmp[0];
    return 0;
}

/* Monotonic wall clock in microseconds. Prefer CLOCK_MONOTONIC where available. */
static int64_t monotonic_usec(void) {
#if defined(CLOCK_MONOTONIC)
    struct timespec ts;
    if (clock_gettime(CLOCK_MONOTONIC, &ts) == 0) {
        return (int64_t)ts.tv_sec * 1000000LL + (int64_t)ts.tv_nsec / 1000LL;
    }
#endif
    struct timeval tv;
    gettimeofday(&tv, NULL);
    return (int64_t)tv.tv_sec * 1000000LL + (int64_t)tv.tv_usec;
}

/*
 * Resolve `host` (literal IPv4 or DNS name) into a dotted string in ip_buf.
 * Returns 0 on success, non-zero on failure.
 */
static int resolve_ipv4(const char *host, char *ip_buf, size_t ip_buf_size) {
    struct in_addr a;
    if (inet_pton(AF_INET, host, &a) == 1) {
        if (inet_ntop(AF_INET, &a, ip_buf, (socklen_t)ip_buf_size) == NULL) return -1;
        return 0;
    }
    struct addrinfo hints;
    struct addrinfo *result = NULL;
    memset(&hints, 0, sizeof(hints));
    hints.ai_family = AF_INET;
    /* NB: leave ai_socktype / ai_protocol at 0 — Darwin's getaddrinfo refuses
     * unusual combinations like (SOCK_DGRAM + IPPROTO_ICMP) with a NULL service. */
    int gai = getaddrinfo(host, NULL, &hints, &result);
    if (gai != 0 || result == NULL) {
        if (result) freeaddrinfo(result);
        return (gai != 0) ? gai : -1;
    }
    const struct sockaddr_in *sa = (const struct sockaddr_in *)result->ai_addr;
    if (inet_ntop(AF_INET, &sa->sin_addr, ip_buf, (socklen_t)ip_buf_size) == NULL) {
        freeaddrinfo(result);
        return -1;
    }
    freeaddrinfo(result);
    return 0;
}

static jdouble do_ping_once(const char *host, int timeout_ms, int payload_size) {
    if (host == NULL || payload_size < 0 || payload_size > 480 || timeout_ms <= 0) {
        return PING_FAIL;
    }

    char ip_addr[INET_ADDRSTRLEN + 1];
    memset(ip_addr, 0, sizeof(ip_addr));
    if (resolve_ipv4(host, ip_addr, sizeof(ip_addr)) != 0) return PING_FAIL;

    int sockfd = socket(AF_INET, SOCK_DGRAM, IPPROTO_ICMP);
    if (sockfd < 0) return PING_FAIL;

    uint8_t packet[512];
    int packet_size = build_echo_request(packet, sizeof(packet), 0xABCD, 1, payload_size);
    if (packet_size < 0) { close(sockfd); return PING_FAIL; }

    struct sockaddr_in dest;
    memset(&dest, 0, sizeof(dest));
    dest.sin_family = AF_INET;
    if (inet_pton(AF_INET, ip_addr, &dest.sin_addr) != 1) {
        close(sockfd);
        return PING_FAIL;
    }

    int64_t send_us = monotonic_usec();
    if (sendto(sockfd, packet, packet_size, 0, (struct sockaddr *)&dest, sizeof(dest)) < 0) {
        close(sockfd);
        return PING_FAIL;
    }

    uint8_t recv_buf[1024];
    int remaining_ms = timeout_ms;
    /* Loop because the socket may receive stray replies from other outstanding
     * probes or non-echo ICMP messages (unreachable, time-exceeded). Keep reading
     * until we either get a matching ECHOREPLY or the budget is gone. */
    while (remaining_ms > 0) {
        int64_t wait_start_us = monotonic_usec();
        struct pollfd pfd;
        pfd.fd = sockfd;
        pfd.events = POLLIN;
        pfd.revents = 0;
        int pr = poll(&pfd, 1, remaining_ms);
        if (pr <= 0) { close(sockfd); return PING_FAIL; }

        ssize_t n = recvfrom(sockfd, recv_buf, sizeof(recv_buf), 0, NULL, NULL);
        if (n < 0) { close(sockfd); return PING_FAIL; }

        int type = -1;
        if (parse_echo_reply(recv_buf, (int)n, &type) == 0 && type == ICMP_ECHOREPLY) {
            int64_t recv_us = monotonic_usec();
            close(sockfd);
            double rtt_ms = (double)(recv_us - send_us) / 1000.0;
            if (rtt_ms < 0.0) rtt_ms = 0.0;
            return (jdouble)rtt_ms;
        }

        int64_t now_us = monotonic_usec();
        int elapsed_ms = (int)((now_us - wait_start_us) / 1000);
        if (elapsed_ms < 1) elapsed_ms = 1;
        remaining_ms -= elapsed_ms;
    }
    close(sockfd);
    return PING_FAIL;
}

/*
 * JNI entry for Kotlin object `com.yuroyami.pingy.utils.NativeIcmpPing`.
 * The Kotlin side declares the method `@JvmStatic external fun nativePingOnce(...)`
 * so this is a static JNI with `jclass` as the second parameter.
 */
JNIEXPORT jdouble JNICALL
Java_com_yuroyami_pingy_utils_NativeIcmpPing_nativePingOnce(
    JNIEnv *env,
    jclass klass,
    jstring jhost,
    jint timeout_ms,
    jint payload_size
) {
    (void)klass;
    if (jhost == NULL) return PING_FAIL;
    const char *host = (*env)->GetStringUTFChars(env, jhost, NULL);
    if (host == NULL) return PING_FAIL;
    jdouble result = do_ping_once(host, (int)timeout_ms, (int)payload_size);
    (*env)->ReleaseStringUTFChars(env, jhost, host);
    return result;
}
