/*
 * Protocol tests for shared/native/icmp_core.h.
 *
 * Built and run by `./gradlew :shared:nativeTest` with ASan and UBSan on, so
 * an alignment or bounds mistake in the packet code fails the build instead of
 * turning into a field report. Everything here is deterministic; the one part
 * that touches a real socket is skipped when the host forbids ping sockets.
 */

#include "icmp_core.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

static int failures = 0;

static void check(int ok, const char *what) {
    if (ok) {
        printf("  ok    %s\n", what);
    } else {
        printf("  FAIL  %s\n", what);
        failures++;
    }
}

/* Turn a freshly built request into the reply the kernel would hand back. */
static void make_reply(uint8_t *buf, int len) {
    buf[0] = ICMP_ECHOREPLY;
    pingy_put_u16(buf + 2, 0);
    pingy_put_u16(buf + 2, pingy_checksum(buf, len));
}

static void test_wire_size(void) {
    uint8_t buf[PINGY_ICMP_HDR + PINGY_MAX_PAYLOAD];
    const int n = pingy_build_request(buf, (int)sizeof(buf), 0xABCDEF0123456789ULL, 7, 32);
    check(n == 40, "a 32 byte payload builds a 40 byte packet, not sizeof(struct icmp)");
    check(buf[0] == ICMP_ECHO && buf[1] == 0, "type is echo request and code is zero");
    check(pingy_get_u16(buf + 6) == 7, "the header carries the sequence");
    check(memcmp(buf + PINGY_ICMP_HDR, "PGY1", 4) == 0, "the payload starts with the magic");
    check(pingy_get_u64(buf + PINGY_ICMP_HDR + 4) == 0xABCDEF0123456789ULL, "the session nonce is echoed into the payload");

    check(pingy_build_request(buf, (int)sizeof(buf), 1, 1, 15) < 0, "a payload under the identity block is refused");
    check(pingy_build_request(buf, (int)sizeof(buf), 1, 1, 481) < 0, "a payload over the wire maximum is refused");
    check(pingy_build_request(buf, 8, 1, 1, 32) < 0, "a buffer too small to hold the packet is refused");
}

static void test_checksum_alignment(void) {
    /* One byte in, so every 16 bit read the old implementation did would be
     * misaligned. UBSan traps on that; the bytewise version does not care. */
    uint8_t backing[64];
    uint8_t *odd = backing + 1;
    for (int i = 0; i < 41; i++) odd[i] = (uint8_t)(i * 7 + 3);

    uint8_t aligned[41];
    memcpy(aligned, odd, 41);

    check(pingy_checksum(odd, 40) == pingy_checksum(aligned, 40),
          "an odd aligned buffer checksums the same as an aligned one");
    check(pingy_checksum(odd, 41) == pingy_checksum(aligned, 41),
          "an odd byte count checksums the same at either alignment");
    check(pingy_checksum(aligned, 40) != pingy_checksum(aligned, 41),
          "the trailing odd byte actually takes part in the sum");
}

static void test_parser(void) {
    const uint64_t session = 0x1122334455667788ULL;
    uint8_t buf[PINGY_ICMP_HDR + PINGY_MAX_PAYLOAD];
    const int n = pingy_build_request(buf, (int)sizeof(buf), session, 42, 32);
    make_reply(buf, n);

    uint16_t seq = 0;
    check(pingy_parse_reply(buf, n, session, &seq) == 0 && seq == 42, "our own reply is accepted with its sequence");

    check(pingy_parse_reply(buf, n, session + 1, &seq) != 0, "a reply carrying a foreign session is refused");

    uint8_t bad_type[sizeof(buf)];
    memcpy(bad_type, buf, (size_t)n);
    bad_type[1] = 3;
    check(pingy_parse_reply(bad_type, n, session, &seq) != 0, "a non zero code is refused");

    uint8_t corrupt[sizeof(buf)];
    memcpy(corrupt, buf, (size_t)n);
    corrupt[20] ^= 0xFF;
    check(pingy_parse_reply(corrupt, n, session, &seq) != 0, "a corrupted body fails the checksum");

    for (int len = 0; len < n; len++) {
        if (pingy_parse_reply(buf, len, session, &seq) == 0) {
            check(0, "a truncated datagram was accepted");
            return;
        }
    }
    check(1, "every truncation of a valid reply is refused");

    uint8_t mismatch[sizeof(buf)];
    memcpy(mismatch, buf, (size_t)n);
    pingy_put_u16(mismatch + PINGY_ICMP_HDR + 12, 43);
    pingy_put_u16(mismatch + 2, 0);
    pingy_put_u16(mismatch + 2, pingy_checksum(mismatch, n));
    check(pingy_parse_reply(mismatch, n, session, &seq) != 0, "a header and payload sequence that disagree are refused");
}

static void test_resolution(void) {
    char ip[INET_ADDRSTRLEN];
    check(pingy_resolve_host("127.0.0.1", ip, (int)sizeof(ip)) == 0 && strcmp(ip, "127.0.0.1") == 0,
          "an IPv4 literal short circuits DNS");
    check(pingy_resolve_host("::1", ip, (int)sizeof(ip)) != 0, "an IPv6 literal is not resolvable on the IPv4 path");
}

static void test_socket_flags(void) {
    const int fd = pingy_open_socket("127.0.0.1");
    if (fd < 0) {
        printf("  skip  socket flags (unprivileged ICMP unavailable here, errno %d)\n", -fd);
        return;
    }
    const int fdflags = fcntl(fd, F_GETFD, 0);
    const int flflags = fcntl(fd, F_GETFL, 0);
    check((fdflags & FD_CLOEXEC) != 0, "the probe socket is close on exec");
    check((flflags & O_NONBLOCK) != 0, "the probe socket is non blocking");

    const int64_t sent = pingy_send_probe(fd, 0x5A5A5A5AL, 1, 32);
    check(sent > 0, "a probe to loopback leaves within the send budget");

    const int64_t packed = pingy_await_reply(fd, 0x5A5A5A5AL, 1000);
    check(packed >= 0, "loopback answers its own echo");
    if (packed >= 0) check((packed & 0xFFFF) == 1, "the reply carries the sequence we sent");

    pingy_close_socket(fd);
}

static void test_send_rejects_a_dead_descriptor(void) {
    check(pingy_send_probe(-1, 1, 1, 32) < 0, "sending on a closed descriptor fails rather than blocking");
    check(pingy_await_reply(-1, 1, 10) == PINGY_AWAIT_NOTHING, "waiting on a closed descriptor returns at once");
    check(pingy_open_socket(NULL) < 0, "a null address is refused");
}

int main(void) {
    printf("icmp_core protocol tests\n");
    test_wire_size();
    test_checksum_alignment();
    test_parser();
    test_resolution();
    test_socket_flags();
    test_send_rejects_a_dead_descriptor();

    if (failures == 0) {
        printf("PASS: all icmp_core assertions\n");
        return 0;
    }
    printf("FAIL: %d assertion(s)\n", failures);
    return 1;
}
