<p align="center">
  <img src="https://github.com/yuroyami/PINGY/blob/master/androidApp/src/main/ic_launcher-playstore.png" width="120" alt="Pingy">
</p>

<h1 align="center">Pingy</h1>

<p align="center">Ping graphs that redraw as fast as the replies come back.</p>

<p align="center">
  <img src="https://img.shields.io/badge/Android-8.0+-a4c639?logo=android&logoColor=white" alt="Android 8.0+">
  <img src="https://img.shields.io/badge/iOS-16.0+-555?logo=apple&logoColor=white" alt="iOS 16.0+">
  <img src="https://img.shields.io/badge/Kotlin_Multiplatform-7F52FF?logo=kotlin&logoColor=white" alt="Kotlin Multiplatform">
  <img src="https://img.shields.io/badge/Compose_Multiplatform-4285F4?logo=jetpackcompose&logoColor=white" alt="Compose Multiplatform">
  <img src="https://img.shields.io/badge/License-AGPL--3.0-blue" alt="License">
</p>

<p align="center">
  <a href="https://github.com/yuroyami/PINGY/releases">Releases</a> ·
  <a href="PRIVACY_POLICY.md">Privacy</a> ·
  <a href="LICENSE">License</a>
</p>

<p align="center"><sub>Not yet published to the App Store or Google Play.</sub></p>

---

Type a host. `1.1.1.1`, `google.com`, your router, a game server. Pingy sends real
ICMP packets and paints each reply as a bar in a rolling graph. A bar that is still
waiting shifts color as the probe gets older, locks to its final shade when the reply
lands, and disappears the moment the probe times out.

## Features

- **Many hosts at once**: one panel per host, up to 24 side by side.
- **Live sliders**: packet size (16 to 480 bytes), time window (1 to 30 s), graph
  ceiling, graph height, zoom curve. Every change applies to the very next probe.
  Nothing restarts.
- **Adaptive cadence**: the next probe leaves as soon as the previous reply arrives,
  or when it times out at 3 s. A 10 ms floor stops a sub-millisecond LAN target from
  being hit a thousand times a second. Faster links give you more samples per second.
- **Rolling stats**: average, jitter, loss, dead-time share, and the low-to-high
  range, all on screen at once. Attempts that never left your device (no DNS
  answer, no route, no socket) are reported on their own and never counted as
  packet loss.
- **Domain or IP**: a name is resolved once per panel, not once per probe.
- **Quick presets**: Cloudflare, Google, Quad9, OpenDNS, your LAN gateway.
- **No telemetry**: no analytics, no crash reporter, no ads, no account.

## Platform Support

| Target | Status |
|:---|:---|
| **Android** | 8.0 (API 26) and above |
| **iOS** | 16.0 and above |
| **Desktop (JVM)** | macOS and Linux. A Compose Desktop app builds and runs; `packageDistributionForCurrentOS` produces a DMG or DEB. Windows is out: Winsock has no unprivileged ICMP socket, so there is no MSI target. |

## Privacy

No telemetry, no analytics, no crash reporter, no ads, no account, no backend.

Pingy sends ICMP probes to the hosts you add, plus a DNS lookup when you type a name
instead of a numeric address. It monitors nothing until you ask it to, and nothing
leaves your device beyond those probes. Targets you mark as remembered are stored
locally in app-private storage. Full details in
[PRIVACY_POLICY.md](PRIVACY_POLICY.md).

Android asks for one permission, `INTERNET`. No
credentials or keystore files are ever committed: `local.properties`, `*.jks`, and
the `keystore/` directory are all `.gitignore`d.

## Building

**Requirements**

- Android Studio 2025.3.1+ (Kotlin 2.4, Gradle 9.4)
- JDK 21 (the Gradle toolchain fetches it for you)
- Xcode 26+ for iOS. Compose Multiplatform 1.11 links against `UIUtilities`,
  which the Xcode 16.x SDKs do not ship. The simulator target is arm64 only, so
  building for a simulator needs an Apple Silicon Mac.
- CMake 3.22.1+ (the NDK manages it on Android)

**Android**

```
./gradlew :androidApp:assembleDebug
```

Release signing reads three keys from `local.properties`, which is `.gitignore`d:
`keystore.keyAlias`, `keystore.keyPassword`, `keystore.storePassword`. Debug
builds use the automatic debug key and need no keystore.

**iOS**

```
open iosApp/iosApp.xcodeproj
```

No CocoaPods step. A Gradle build phase inside the Xcode project builds and embeds
the Kotlin framework, so opening the project and pressing Run is enough.

## Tests

```
./gradlew :shared:jvmTest :shared:iosSimulatorArm64Test
```

The engine suite runs against real loopback sockets on both the JVM and
Kotlin/Native, because a socket bug that shows up on one runtime can be
completely invisible on the other.

## Feedback

[Open an issue](https://github.com/yuroyami/PINGY/issues/new) or [start a discussion](https://github.com/yuroyami/PINGY/discussions).

---

<p align="center">
  <sub>Licensed under <a href="https://www.gnu.org/licenses/agpl-3.0.en.html">AGPL-3.0</a></sub>
</p>
