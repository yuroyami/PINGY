# Pingy R8/ProGuard rules.
#
# Deliberately minimal: the app keeps no reflection-driven code.
#  - JNI is covered by proguard-android-optimize.txt's default
#    `-keepclasseswithmembernames class * { native <methods>; }`,
#    which preserves NativeIcmpPing and its native method names.
#  - Compose, kotlinx-coroutines, kermit and multiplatform-settings all ship
#    consumer rules in their AARs.
#
# Add project-specific keeps below as the app grows.
