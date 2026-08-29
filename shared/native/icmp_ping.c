/*
 * Unprivileged ICMP echo ("ping") via SOCK_DGRAM + IPPROTO_ICMP, exposed to the
 * JVM / Android as three JNI entries that let callers keep a socket alive across
 * probes instead of opening+closing one per probe.
 *
 * Works on:
 *   - Android (Linux kernel, ping_group_range normally permissive for app UIDs)
 *   - macOS   (Darwin; the kernel assigns a per-socket ICMP id and demuxes replies)
 *   - Linux   (same ping_group_range trick as Android)
 *
 * Does NOT work on Windows (Winsock doesn't expose DGRAM+IPPROTO_ICMP — use
 * IcmpSendEcho from icmp.dll via a separate codepath if you ever need it).
 *
 * Why persist the socket? Two reasons:
 *   1) socket() + close() per probe is real work on both kernels (Darwin also
 *      allocates a per-socket ICMP id on every create), and a 5 Hz ping stream
 *      pays that cost twelve times a second.
 *   2) sendto() on an unconnected DGRAM socket re-resolves the route every
 *      packet. connect()ing once pins the 4-tuple so subsequent send()s skip
 *      the route lookup.
 *
 * Pipelined contract: nativeSendProbe fires one seq-stamped request and
 * returns its send timestamp; nativeAwaitReply hands back the next reply's
 * (timestamp, seq) pair. The Kotlin engine keeps the outstanding table and
 * issues per-probe verdicts, so many probes can be in flight at once.
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
static int parse_echo_reply(const uint8_t *buf, int buf_len, int *out_type, uint16_t *out_seq) {
    if (buf_len < 8 || out_type == NULL || out_seq == NULL) return -1;
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
    uint16_t seq_be;
    memcpy(&seq_be, icmp + 6, sizeof(uint16_t));
    *out_seq = ntohs(seq_be);
    return 0;
}

/* Sequence numbers are supplied by the Kotlin engine, which keeps an
 * outstanding-probes table and matches replies to sends by seq. The id field
 * stays fixed: both kernels rewrite it for unprivileged ICMP sockets, so
 * matching on it would be meaningless. */

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
 * JNI: open an unprivileged ICMP socket and `connect()` it to an IPv4 literal.
 * Resolution lives on the Kotlin side (cached once per engine), so this
 * entry point deliberately refuses hostnames — caller must pass a dotted
 * IPv4 string. Returns the fd on success, -1 on any failure.
 *
 * Connecting is what saves us the per-packet route lookup on subsequent
 * send()s — the kernel caches the route in the socket's dst_cache.
 */
JNIEXPORT jint JNICALL
Java_com_yuroyami_pingy_utils_NativeIcmpPing_nativeOpenSocket(
    JNIEnv *env,
    jclass klass,
    jstring jipv4
) {
    (void)klass;
    if (jipv4 == NULL) return -1;
    const char *ipv4 = (*env)->GetStringUTFChars(env, jipv4, NULL);
    if (ipv4 == NULL) return -1;

    int fd = socket(AF_INET, SOCK_DGRAM, IPPROTO_ICMP);
    if (fd < 0) {
        (*env)->ReleaseStringUTFChars(env, jipv4, ipv4);
        return -1;
    }

    struct sockaddr_in dest;
    memset(&dest, 0, sizeof(dest));
    dest.sin_family = AF_INET;
    int parsed = inet_pton(AF_INET, ipv4, &dest.sin_addr);
    (*env)->ReleaseStringUTFChars(env, jipv4, ipv4);
    if (parsed != 1) { close(fd); return -1; }

    if (connect(fd, (struct sockaddr *)&dest, sizeof(dest)) < 0) {
        close(fd);
        return -1;
    }
    return (jint)fd;
}

/*
 * JNI: build + send one echo request carrying the caller-supplied sequence
 * number. Returns the send timestamp in monotonic MICROSECONDS (>= 0), or
 * -1 on any socket-level failure. The engine owns the outstanding table;
 * this call never waits for anything.
 */
JNIEXPORT jlong JNICALL
Java_com_yuroyami_pingy_utils_NativeIcmpPing_nativeSendProbe(
    JNIEnv *env,
    jclass klass,
    jint jfd,
    jint jseq,
    jint payload_size
) {
    (void)env;
    (void)klass;
    int fd = (int)jfd;
    if (fd < 0 || payload_size < 0 || payload_size > 480) return (jlong)-1;

    uint8_t packet[512];
    int packet_size = build_echo_request(packet, sizeof(packet), 0xABCD,
                                         (uint16_t)(jseq & 0xFFFF), payload_size);
    if (packet_size < 0) return (jlong)-1;

    int64_t send_us = monotonic_usec();
    /* send(), not sendto(): the socket is connected, so the kernel's
     * dst_cache is warm and we skip the per-packet route lookup. */
    if (send(fd, packet, packet_size, 0) < 0) return (jlong)-1;
    return (jlong)send_us;
}

/*
 * JNI: wait up to budget_ms for the NEXT echo reply on the socket,
 * whichever outstanding probe it answers. Returns:
 *   -1  socket-level failure (caller closes + reopens)
 *   -2  nothing valid arrived within the budget
 *   else ((recv_usec & 47-bit mask) << 16) | reply_seq
 * The engine matches reply_seq against its outstanding table and computes
 * the RTT from the two native timestamps; 47 bits of microseconds wrap
 * every ~4.5 years of uptime, far beyond any live window. Non-echo ICMP
 * traffic is skipped without burning the whole budget.
 */
JNIEXPORT jlong JNICALL
Java_com_yuroyami_pingy_utils_NativeIcmpPing_nativeAwaitReply(
    JNIEnv *env,
    jclass klass,
    jint jfd,
    jint budget_ms
) {
    (void)env;
    (void)klass;
    int fd = (int)jfd;
    if (fd < 0 || budget_ms <= 0) return (jlong)-2;

    uint8_t recv_buf[1024];
    int remaining_ms = budget_ms;
    while (remaining_ms > 0) {
        int64_t wait_start_us = monotonic_usec();
        struct pollfd pfd;
        pfd.fd = fd;
        pfd.events = POLLIN;
        pfd.revents = 0;
        int pr = poll(&pfd, 1, remaining_ms);
        if (pr < 0) return (jlong)-1;
        if (pr == 0) return (jlong)-2;

        ssize_t n = recv(fd, recv_buf, sizeof(recv_buf), 0);
        if (n < 0) return (jlong)-1;

        int type = -1;
        uint16_t reply_seq = 0;
        if (parse_echo_reply(recv_buf, (int)n, &type, &reply_seq) == 0 &&
            type == ICMP_ECHOREPLY) {
            int64_t recv_us = monotonic_usec();
            return (jlong)(((recv_us & 0x7FFFFFFFFFFFLL) << 16) | (jlong)reply_seq);
        }

        int64_t now_us = monotonic_usec();
        int elapsed_ms = (int)((now_us - wait_start_us) / 1000);
        if (elapsed_ms < 1) elapsed_ms = 1;
        remaining_ms -= elapsed_ms;
    }
    return (jlong)-2;
}

/*
 * JNI: close a socket previously returned by nativeOpenSocket. Idempotent
 * on negative fds (no-op), so callers can call it unconditionally in their
 * lifecycle finaliser.
 */
JNIEXPORT void JNICALL
Java_com_yuroyami_pingy_utils_NativeIcmpPing_nativeCloseSocket(
    JNIEnv *env,
    jclass klass,
    jint jfd
) {
    (void)env;
    (void)klass;
    if (jfd >= 0) close((int)jfd);
}
