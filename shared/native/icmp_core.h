/*
 * Single source of truth for Pingy's unprivileged ICMP echo transport.
 *
 * Both back ends include this header, so the wire format, checksum, parser and
 * socket lifecycle exist exactly once:
 *   - shared/native/icmp_ping.c        JNI wrapper for Android and the JVM
 *   - src/nativeInterop/cinterop/IcmpPing.def   cinterop wrapper for iOS
 *
 * Everything is `static inline` and JNI-free on purpose: the header must stay
 * compilable by the NDK, the host `cc`, and Kotlin/Native's cinterop clang.
 *
 * Wire format
 * -----------
 * An Echo request is an 8-byte ICMP header followed by our payload. The header
 * is written as exactly 8 bytes; `sizeof(struct icmp)` is 28 on both Darwin and
 * Linux because of the error-message union, and using it silently prepends 20
 * zero bytes to every "payload".
 *
 *   offset  size  field
 *   0       1     type (8 = echo request, 0 = echo reply)
 *   1       1     code (0)
 *   2       2     checksum
 *   4       2     identifier   (rewritten by the kernel; never matched on)
 *   6       2     sequence
 *   8       4     magic "PGY1"
 *   12      8     session nonce, big endian
 *   20      2     sequence echo
 *   22      2     reserved (0)
 *   24..    n     0x07 filler
 *
 * The session nonce is what makes a reply attributable. Two sockets connected
 * to the same peer both receive that peer's replies, so sequence-only matching
 * lets one panel consume and mis-time another panel's reply. Each engine
 * session draws a random nonce, stamps it into every request, and refuses any
 * reply that does not carry it back.
 */

#ifndef PINGY_ICMP_CORE_H
#define PINGY_ICMP_CORE_H

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
#include <fcntl.h>
#include <string.h>
#include <errno.h>
#include <stdint.h>

/* Wire-accurate Echo header size. Deliberately not sizeof(struct icmp). */
#define PINGY_ICMP_HDR 8

/* Identity block written at the start of every payload. */
#define PINGY_IDENT_LEN 16
#define PINGY_MIN_PAYLOAD PINGY_IDENT_LEN
#define PINGY_MAX_PAYLOAD 480

/* icmp_await_reply return sentinels. */
#define PINGY_AWAIT_SOCK_ERR (-1)
#define PINGY_AWAIT_NOTHING  (-2)

/* ---------------------------------------------------------------- checksum */

/*
 * RFC 1071 Internet checksum, read one byte at a time.
 *
 * The previous implementation cast the buffer to `uint16_t*` and dereferenced
 * it. That is undefined behaviour for any buffer that is not 2-byte aligned,
 * and UBSan traps on it. Accumulating bytes costs nothing measurable for the
 * <=512-byte packets involved here and is valid for every alignment.
 */
static inline uint16_t pingy_checksum(const uint8_t *data, int len) {
    uint32_t sum = 0;
    int i = 0;
    for (; i + 1 < len; i += 2) {
        sum += (uint32_t)(((uint32_t)data[i] << 8) | (uint32_t)data[i + 1]);
    }
    if (i < len) sum += (uint32_t)data[i] << 8;
    while (sum >> 16) sum = (sum & 0xFFFFu) + (sum >> 16);
    return (uint16_t)(~sum & 0xFFFFu);
}

/* --------------------------------------------------------------- big endian */

static inline void pingy_put_u16(uint8_t *p, uint16_t v) {
    p[0] = (uint8_t)(v >> 8);
    p[1] = (uint8_t)(v & 0xFF);
}

static inline uint16_t pingy_get_u16(const uint8_t *p) {
    return (uint16_t)(((uint16_t)p[0] << 8) | (uint16_t)p[1]);
}

static inline void pingy_put_u64(uint8_t *p, uint64_t v) {
    int i;
    for (i = 0; i < 8; i++) p[i] = (uint8_t)(v >> (56 - 8 * i));
}

static inline uint64_t pingy_get_u64(const uint8_t *p) {
    uint64_t v = 0;
    int i;
    for (i = 0; i < 8; i++) v = (v << 8) | (uint64_t)p[i];
    return v;
}

/* ------------------------------------------------------------------- clock */

/* Monotonic microseconds. CLOCK_MONOTONIC so NTP steps cannot skew an RTT. */
static inline int64_t pingy_now_usec(void) {
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

/* ------------------------------------------------------------ build / parse */

/*
 * Write one Echo request into `buf`. Returns the total packet size, or -1 if
 * the buffer is too small or the payload size is out of range.
 */
static inline int pingy_build_request(
    uint8_t *buf, int buf_size, uint64_t session, uint16_t seq, int payload_size
) {
    if (payload_size < PINGY_MIN_PAYLOAD || payload_size > PINGY_MAX_PAYLOAD) return -1;
    const int total = PINGY_ICMP_HDR + payload_size;
    if (buf_size < total) return -1;

    memset(buf, 0, (size_t)total);
    buf[0] = ICMP_ECHO;                 /* type */
    buf[1] = 0;                         /* code */
    pingy_put_u16(buf + 2, 0);          /* checksum, filled in below */
    pingy_put_u16(buf + 4, 0);          /* identifier: the kernel rewrites it */
    pingy_put_u16(buf + 6, seq);

    uint8_t *pl = buf + PINGY_ICMP_HDR;
    pl[0] = 'P'; pl[1] = 'G'; pl[2] = 'Y'; pl[3] = '1';
    pingy_put_u64(pl + 4, session);
    pingy_put_u16(pl + 12, seq);
    pingy_put_u16(pl + 14, 0);
    /* Filler pattern for the remainder. */
    if (payload_size > PINGY_IDENT_LEN) {
        memset(pl + PINGY_IDENT_LEN, 0x07, (size_t)(payload_size - PINGY_IDENT_LEN));
    }

    pingy_put_u16(buf + 2, pingy_checksum(buf, total));
    return total;
}

/*
 * Accept a datagram only if it is an Echo reply that carries our session nonce.
 *
 * Kernels differ in what they hand an unprivileged SOCK_DGRAM+IPPROTO_ICMP
 * socket: Linux delivers the bare ICMP message, Darwin prepends the full IPv4
 * header. Both shapes are handled, rather than one back end assuming each.
 *
 * Returns 0 and fills *out_seq on an accepted reply, or -1 for anything else
 * (too short, wrong type or code, bad checksum, foreign or missing identity).
 * Rejection is never fatal; the caller just keeps waiting.
 */
static inline int pingy_parse_reply(
    const uint8_t *buf, int len, uint64_t session, uint16_t *out_seq
) {
    if (buf == NULL || out_seq == NULL || len < PINGY_ICMP_HDR) return -1;

    const uint8_t *icmp = buf;
    int icmp_len = len;

    /* IPv4 prefix detection: version nibble 4 and IHL >= 5. Echo reply type is
     * 0, so a bare ICMP message can never be mistaken for an IP header here. */
    if ((buf[0] & 0xF0) == 0x40) {
        const int ihl = (buf[0] & 0x0F) << 2;
        if (ihl < 20 || len < ihl + PINGY_ICMP_HDR) return -1;
        icmp = buf + ihl;
        icmp_len = len - ihl;
    }
    if (icmp_len < PINGY_ICMP_HDR + PINGY_IDENT_LEN) return -1;
    if (icmp[0] != ICMP_ECHOREPLY || icmp[1] != 0) return -1;

    /* Checksum over the whole ICMP message with the checksum field zeroed. */
    const uint16_t got = pingy_get_u16(icmp + 2);
    uint32_t sum = 0;
    int i = 0;
    for (; i + 1 < icmp_len; i += 2) {
        uint32_t w = ((uint32_t)icmp[i] << 8) | (uint32_t)icmp[i + 1];
        if (i == 2) w = 0; /* the checksum field itself */
        sum += w;
    }
    if (i < icmp_len) sum += (uint32_t)icmp[i] << 8;
    while (sum >> 16) sum = (sum & 0xFFFFu) + (sum >> 16);
    if ((uint16_t)(~sum & 0xFFFFu) != got) return -1;

    const uint8_t *pl = icmp + PINGY_ICMP_HDR;
    if (pl[0] != 'P' || pl[1] != 'G' || pl[2] != 'Y' || pl[3] != '1') return -1;
    if (pingy_get_u64(pl + 4) != session) return -1;   /* someone else's probe */

    const uint16_t hdr_seq = pingy_get_u16(icmp + 6);
    if (pingy_get_u16(pl + 12) != hdr_seq) return -1;  /* header/payload disagree */

    *out_seq = hdr_seq;
    return 0;
}

/* ---------------------------------------------------------------- lifecycle */

/*
 * Open an unprivileged ICMP socket and connect() it to an IPv4 literal.
 * Hostnames are refused; the Kotlin engine resolves once and caches.
 *
 * connect() pins the 4-tuple, which skips the per-packet route lookup and, on
 * Darwin, also filters out replies from other peers.
 *
 * Returns the descriptor, or a NEGATIVE ERRNO. A caller that only sees -1
 * cannot tell "this device forbids ping sockets" from "there is no route",
 * which are very different things to show a person.
 */
static inline int pingy_open_socket(const char *ipv4) {
    if (ipv4 == NULL) return -EINVAL;

    int fd = socket(AF_INET, SOCK_DGRAM, IPPROTO_ICMP);
    if (fd < 0) return errno > 0 ? -errno : -1;

    /* Close-on-exec: a forked child must not inherit a live probe socket. */
    int flags = fcntl(fd, F_GETFD, 0);
    if (flags >= 0) (void)fcntl(fd, F_SETFD, flags | FD_CLOEXEC);

    /* Non-blocking: a blocking send or recv can park the engine's thread past
     * the poll deadline it advertises, where it cannot notice cancellation. */
    int fl = fcntl(fd, F_GETFL, 0);
    if (fl >= 0) (void)fcntl(fd, F_SETFL, fl | O_NONBLOCK);

    struct sockaddr_in dest;
    memset(&dest, 0, sizeof(dest));
    dest.sin_family = AF_INET;
    if (inet_pton(AF_INET, ipv4, &dest.sin_addr) != 1) { close(fd); return -EINVAL; }
    if (connect(fd, (struct sockaddr *)&dest, sizeof(dest)) < 0) {
        const int err = errno;
        close(fd);
        return err > 0 ? -err : -1;
    }
    return fd;
}

static inline void pingy_close_socket(int fd) {
    if (fd >= 0) close(fd);
}

/* Longest we will wait for a full send buffer to drain. Matches the engine's
 * poll slice, so a send cannot sit outside the stop latency it promises. */
#define PINGY_SEND_BUDGET_MS 250

/*
 * Send one request. Returns its monotonic send time in microseconds, or -1.
 * The timestamp is taken as late as possible, immediately before the send()
 * that actually succeeds.
 *
 * The socket is non-blocking, so a full send buffer surfaces as EAGAIN. We wait
 * for writability against an absolute deadline instead of blocking forever.
 */
static inline int64_t pingy_send_probe(
    int fd, int64_t session, int seq, int payload_size
) {
    if (fd < 0) return -1;

    uint8_t packet[PINGY_ICMP_HDR + PINGY_MAX_PAYLOAD];
    const int n = pingy_build_request(
        packet, (int)sizeof(packet), (uint64_t)session,
        (uint16_t)(seq & 0xFFFF), payload_size
    );
    if (n < 0) return -1;

    const int64_t deadline_us = pingy_now_usec() + PINGY_SEND_BUDGET_MS * 1000LL;
    for (;;) {
        const int64_t send_us = pingy_now_usec();
        const ssize_t w = send(fd, packet, (size_t)n, 0);
        if (w >= 0) return send_us;
        if (errno == EINTR) continue;
        if (errno != EAGAIN && errno != EWOULDBLOCK) return -1;

        const int64_t now_us = pingy_now_usec();
        if (now_us >= deadline_us) return -1;
        int wait_ms = (int)((deadline_us - now_us + 999) / 1000);
        if (wait_ms <= 0) wait_ms = 1;

        struct pollfd pfd;
        pfd.fd = fd;
        pfd.events = POLLOUT;
        pfd.revents = 0;
        const int pr = poll(&pfd, 1, wait_ms);
        if (pr < 0 && errno == EINTR) continue;
        if (pr <= 0) return -1;
        if (pfd.revents & (POLLERR | POLLNVAL)) return -1;
    }
}

/*
 * Wait up to budget_ms for the next reply belonging to this session.
 *
 * Returns PINGY_AWAIT_SOCK_ERR, PINGY_AWAIT_NOTHING, or
 * ((recv_usec & 47-bit) << 16) | seq. The packed form is always non-negative,
 * so it can never collide with either sentinel.
 *
 * Deadline is absolute: EINTR and rejected foreign packets resume the wait on
 * the remaining time instead of restarting or aborting it.
 */
static inline int64_t pingy_await_reply(int fd, int64_t session, int budget_ms) {
    if (fd < 0 || budget_ms <= 0) return PINGY_AWAIT_NOTHING;

    const int64_t deadline_us = pingy_now_usec() + (int64_t)budget_ms * 1000LL;
    uint8_t recv_buf[1024];

    for (;;) {
        const int64_t now_us = pingy_now_usec();
        if (now_us >= deadline_us) return PINGY_AWAIT_NOTHING;
        int wait_ms = (int)((deadline_us - now_us + 999) / 1000);
        if (wait_ms <= 0) wait_ms = 1;

        struct pollfd pfd;
        pfd.fd = fd;
        pfd.events = POLLIN;
        pfd.revents = 0;

        const int pr = poll(&pfd, 1, wait_ms);
        if (pr < 0) {
            if (errno == EINTR) continue;   /* a signal is not a socket failure */
            return PINGY_AWAIT_SOCK_ERR;
        }
        if (pr == 0) return PINGY_AWAIT_NOTHING;

        /* poll() reports error conditions even when they were not requested. */
        if (pfd.revents & (POLLERR | POLLNVAL)) return PINGY_AWAIT_SOCK_ERR;
        if ((pfd.revents & (POLLIN | POLLHUP)) == 0) continue;

        ssize_t n;
        do { n = recv(fd, recv_buf, sizeof(recv_buf), 0); } while (n < 0 && errno == EINTR);
        if (n < 0) {
            if (errno == EAGAIN || errno == EWOULDBLOCK) continue;
            return PINGY_AWAIT_SOCK_ERR;
        }
        if (n == 0) return PINGY_AWAIT_SOCK_ERR;

        uint16_t seq = 0;
        if (pingy_parse_reply(recv_buf, (int)n, (uint64_t)session, &seq) == 0) {
            const int64_t recv_us = pingy_now_usec();
            return (int64_t)(((recv_us & 0x7FFFFFFFFFFFLL) << 16) | (int64_t)seq);
        }
        /* Not ours. Keep waiting on the remaining budget. */
    }
}

/* --------------------------------------------------------------- resolution */

/*
 * Resolve a hostname to an IPv4 dotted string. Returns 0 on success, or a
 * non-zero getaddrinfo code. IPv4 literals bypass DNS entirely.
 */
static inline int pingy_resolve_host(const char *hostname, char *ip_buf, int ip_buf_size) {
    if (hostname == NULL || ip_buf == NULL || ip_buf_size < INET_ADDRSTRLEN) return -1;

    struct in_addr addr4;
    if (inet_pton(AF_INET, hostname, &addr4) == 1) {
        return inet_ntop(AF_INET, &addr4, ip_buf, (socklen_t)ip_buf_size) ? 0 : -1;
    }

    /* ai_socktype and ai_protocol stay 0: Darwin's getaddrinfo rejects
     * (SOCK_DGRAM + IPPROTO_ICMP) when `service` is NULL and fails every
     * lookup, including numeric ones. */
    struct addrinfo hints;
    struct addrinfo *result = NULL;
    memset(&hints, 0, sizeof(hints));
    hints.ai_family = AF_INET;

    const int rc = getaddrinfo(hostname, NULL, &hints, &result);
    if (rc != 0 || result == NULL) return rc != 0 ? rc : -1;

    struct sockaddr_in *a = (struct sockaddr_in *)(void *)result->ai_addr;
    const int ok = inet_ntop(AF_INET, &a->sin_addr, ip_buf, (socklen_t)ip_buf_size) ? 0 : -1;
    freeaddrinfo(result);
    return ok;
}

#endif /* PINGY_ICMP_CORE_H */
