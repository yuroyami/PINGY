<p align="center">
  <img src="https://github.com/yuroyami/PINGY/blob/master/androidApp/src/main/ic_launcher-playstore.png" width="120" alt="Pingy">
</p>

<h1 align="center">Pingy</h1>

<p align="center">Live network latency, visualized.</p>

<p align="center">
  <img src="https://img.shields.io/badge/Android-8.0+-a4c639?logo=android&logoColor=white" alt="Android 8.0+">
  <img src="https://img.shields.io/badge/iOS-16.0+-555?logo=apple&logoColor=white" alt="iOS 16.0+">
  <img src="https://img.shields.io/badge/Kotlin_Multiplatform-7F52FF?logo=kotlin&logoColor=white" alt="Kotlin Multiplatform">
  <img src="https://img.shields.io/badge/Compose_Multiplatform-4285F4?logo=jetpackcompose&logoColor=white" alt="Compose Multiplatform">
  <img src="https://img.shields.io/badge/License-AGPL--3.0-blue" alt="License">
</p>

<p align="center">
  <a href="https://apps.apple.com/app/pingy">App Store</a> ·
  <a href="https://play.google.com/store/apps/details?id=com.yuroyami.pingy">Google Play</a> ·
  <a href="https://github.com/yuroyami/PINGY/releases/latest">GitHub Releases</a>
</p>

---

Type a host — `1.1.1.1`, `google.com`, your router, a game server. Pingy sends real ICMP packets and paints each reply as a bar in a rolling graph. The in-flight bar shifts color as the probe ages, snaps to its final shade when the reply lands, and vanishes the moment a packet times out.

## Features

- **Multi-host monitoring** — one panel per host, as many as you want side by side.
- **Live sliders** — packet size (16–480 bytes), time window (1–30 s), graph roof, graph height, zoom curve. Every change hits the very next probe, no restart.
- **Adaptive cadence** — the next probe fires the moment the previous reply returns (or times out at 3 s). Fast links yield more samples per second, not the same handful.
- **Rolling stats** — sent, lost, lowest, highest, packet-loss percentage, always on screen.
- **Domain or IP** — DNS is paid once per panel, never per probe.
- **Quick presets** — Cloudflare, Google, Quad9, OpenDNS, your LAN gateway.
- **No telemetry** — no analytics, no crash reporter, no ads, no account.

## Platform Support

| Target | Status |
|:---|:---|
| **Android** | 8.0 (API 26) and above |
| **iOS** | 16.0 and above |
| **Desktop (JVM)** | Library only — engine compiles on macOS and Linux, no UI shell yet. Windows unsupported (Winsock doesn't expose unprivileged ICMP). |

## Privacy

No telemetry, no analytics, no crash reporter, no ads, no account. The only network traffic Pingy emits is the ICMP probes you explicitly start. Android permissions are limited to `INTERNET` and `ACCESS_NETWORK_STATE`. No credentials or keystore files are ever committed — `local.properties`, `*.jks`, and the `keystore/` directory are all `.gitignore`d.

## Building

**Requirements**

- Android Studio 2025.3.1+ (Kotlin 2.2, Gradle 9)
- JDK 21 (auto-provisioned by the Gradle toolchain)
- Xcode 16.3+ for iOS
- CMake 3.22.1+ (NDK-managed on Android)

**Android**

```
./gradlew :androidApp:assembleDebug
```

Release signing reads from `local.properties` (which is `.gitignore`d) — `keystore.keyAlias`, `keystore.keyPassword`, `keystore.storePassword`. Unsigned debug builds don't need a keystore.

**iOS**

```
cd iosApp && pod install
open iosApp.xcworkspace
```

## Feedback

[Open an issue](https://github.com/yuroyami/PINGY/issues/new) or [start a discussion](https://github.com/yuroyami/PINGY/discussions).

---

<p align="center">
  <sub>Licensed under <a href="https://www.gnu.org/licenses/agpl-3.0.en.html">AGPL-3.0</a></sub>
</p>
