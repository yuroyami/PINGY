/*
 * JNI surface for Pingy's unprivileged ICMP transport, used by Android (NDK)
 * and the JVM desktop target.
 *
 * All protocol logic lives in icmp_core.h, which the iOS cinterop back end
 * includes as well. This file only marshals JNI types; keeping it thin is what
 * stops the two platforms from drifting apart.
 *
 * Not supported on Windows: Winsock exposes no SOCK_DGRAM + IPPROTO_ICMP, so
 * the JVM loader simply reports the library as unavailable there.
 */

#include <jni.h>
#include "icmp_core.h"

JNIEXPORT jint JNICALL
Java_com_yuroyami_pingy_utils_NativeIcmpPing_nativeOpenSocket(
    JNIEnv *env, jclass klass, jstring jipv4
) {
    (void)klass;
    if (jipv4 == NULL) return -1;
    const char *ipv4 = (*env)->GetStringUTFChars(env, jipv4, NULL);
    if (ipv4 == NULL) return -1;
    const int fd = pingy_open_socket(ipv4);
    (*env)->ReleaseStringUTFChars(env, jipv4, ipv4);
    return (jint)fd;
}

JNIEXPORT jlong JNICALL
Java_com_yuroyami_pingy_utils_NativeIcmpPing_nativeSendProbe(
    JNIEnv *env, jclass klass, jint fd, jlong session, jint seq, jint payload_size
) {
    (void)env; (void)klass;
    return (jlong)pingy_send_probe((int)fd, (int64_t)session, (int)seq, (int)payload_size);
}

JNIEXPORT jlong JNICALL
Java_com_yuroyami_pingy_utils_NativeIcmpPing_nativeAwaitReply(
    JNIEnv *env, jclass klass, jint fd, jlong session, jint budget_ms
) {
    (void)env; (void)klass;
    return (jlong)pingy_await_reply((int)fd, (int64_t)session, (int)budget_ms);
}

JNIEXPORT void JNICALL
Java_com_yuroyami_pingy_utils_NativeIcmpPing_nativeCloseSocket(
    JNIEnv *env, jclass klass, jint fd
) {
    (void)env; (void)klass;
    pingy_close_socket((int)fd);
}

/*
 * Resolve on the native side so both platforms share one code path. Returns
 * the dotted IPv4 string, or NULL when the host does not resolve to IPv4.
 */
JNIEXPORT jstring JNICALL
Java_com_yuroyami_pingy_utils_NativeIcmpPing_nativeResolveHost(
    JNIEnv *env, jclass klass, jstring jhost
) {
    (void)klass;
    if (jhost == NULL) return NULL;
    const char *host = (*env)->GetStringUTFChars(env, jhost, NULL);
    if (host == NULL) return NULL;

    char ip[INET_ADDRSTRLEN];
    const int rc = pingy_resolve_host(host, ip, (int)sizeof(ip));
    (*env)->ReleaseStringUTFChars(env, jhost, host);

    return rc == 0 ? (*env)->NewStringUTF(env, ip) : NULL;
}
