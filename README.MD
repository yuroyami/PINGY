<p align="center">
  <img src="https://github.com/yuroyami/PINGY/blob/master/androidApp/src/main/ic_launcher-playstore.png" width="160" alt="Pingy">
</p>

<h1 align="center">Pingy</h1>

<p align="center">
  <b>A live network ping visualizer for Android & iOS.</b><br>
  <sub>Pretty graphs. Real packets. No nonsense.</sub>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Android-8.0+-a4c639?logo=android&logoColor=white" alt="Android 8.0+">
  <img src="https://img.shields.io/badge/iOS-16.0+-555?logo=apple&logoColor=white" alt="iOS 16.0+">
  <img src="https://img.shields.io/badge/Kotlin_Multiplatform-7F52FF?logo=kotlin&logoColor=white" alt="Kotlin Multiplatform">
  <img src="https://img.shields.io/badge/Compose_Multiplatform-4285F4?logo=jetpackcompose&logoColor=white" alt="Compose Multiplatform">
  <img src="https://img.shields.io/badge/License-AGPL--3.0-blue" alt="License">
</p>

---

## Download

<table align="center">
  <tr>
    <th></th>
    <th>Recommended</th>
    <th>Store</th>
  </tr>
  <tr>
    <td><b>Android</b></td>
    <td>
      <a href="https://github.com/yuroyami/PINGY/releases/latest">
        <img src="https://raw.githubusercontent.com/flocke/andOTP/master/assets/badges/get-it-on-github.png" width="140" alt="GitHub Releases">
      </a>
    </td>
    <td>
      <a href="https://play.google.com/store/apps/details?id=com.yuroyami.pingy">
        <img src="https://i0.wp.com/reefcentral.pt/wp-content/uploads/2019/04/get-on-google-play.png?fit=564%2C168&ssl=1" width="140" alt="Google Play">
      </a>
    </td>
  </tr>
  <tr>
    <td><b>iOS</b></td>
    <td>
      <a href="https://github.com/yuroyami/PINGY/releases/latest">
        <img src="https://github.com/CelloSerenity/altdirect/blob/main/assets/png/Download_Blue.png?raw=true" width="140" alt="Download IPA">
      </a>
    </td>
    <td>
      <a href="https://apps.apple.com/app/pingy">
        <img src="https://developer.apple.com/assets/elements/icons/download-on-the-app-store/download-on-the-app-store.svg" width="140" alt="App Store">
      </a>
    </td>
  </tr>
</table>

---

## Features

- **Real-time ping graph** — smooth canvas rendering of round-trip times over a rolling time window, repainted every frame via `withFrameMillis` (not just on state change).
- **Chameleon pinglette** — the newest bar colorizes millisecond-precisely as the in-flight probe ages, snaps to its final color the moment the reply lands, and vanishes instantly on timeout.
- **Multiple panels at once** — monitor several hosts side by side (1.1.1.1, 8.8.8.8, your router, a game server, whatever). Each panel owns its own socket.
- **Live-tunable per panel** — packet size (16–480 bytes, applied to the very next probe), graph roof, zoom curve, time window (1s–30s), and canvas height. Every slider is wired through a `StateFlow` the engine subscribes to.
- **Adaptive cadence by default** — the next probe fires the instant the previous one returned (or times out at 3 s). No artificial floor, no sleep schedule.
- **Live stats** — sent/lost counters, lowest/highest seen, packet loss visible at a glance.
- **Domain or IP** — ping anything resolvable; DNS is paid once per panel lifetime, not per probe.
- **Quick presets** — dropdown of common hosts (Cloudflare, Google, Quad9, OpenDNS, LAN gateway, etc.).
- **Zero telemetry** — no analytics, no crash reporter, no phone-home. The only traffic the app emits is the packets you asked for.

---

## Platform Support

| Target | Status |
|:---|:---|
| **Android** | 8.0 (API 26) and above |
| **iOS** | 16.0 and above |
| **Desktop (JVM)** | Library only — the ICMP engine compiles and runs, no UI shell yet. macOS & Linux (x86_64 / arm64); Windows is unsupported (no unprivileged ICMP in Winsock). |

---

## Why this repo is interesting

Most KMP projects stop at "share the ViewModel, write the UI twice." Pingy goes further — a few things below are uncommon enough to be worth calling out.

### One C source, three runtimes

`shared/native/icmp_ping.c` is the single source of truth for the ICMP echo path. Android's NDK compiles it into `libpingy_icmp.so` through CMake; the JVM target compiles the same file through a Gradle `Exec` task into `libpingy_icmp.{dylib,so}` and ships it inside the jar under `/native/{darwin,linux}-{arm64,x86_64}/`; iOS compiles the same syscalls via Kotlin/Native cinterop (`shared/src/nativeInterop/cinterop/IcmpPing.def`). Every platform walks an unprivileged `SOCK_DGRAM + IPPROTO_ICMP` socket by hand — no shelled-out `ping`, no `-i` interval floor, no `SUID` binary.

### Persistent socket, not socket-per-probe

Conventional wisdom says "open the ICMP socket, send one packet, close it." At 5 Hz over a monitoring session that's 18 000 `socket()`/`close()` pairs an hour, and on Darwin each `socket()` also assigns a per-socket ICMP id in the kernel. Pingy opens **one** socket per `PingEngine`, `connect()`s it to the resolved IPv4 — which pins the 4-tuple so subsequent `send()`s skip the per-packet route lookup — and reuses that fd until the panel is closed. A socket-level failure triggers a lazy close+reopen on the next iteration; a plain timeout doesn't touch the fd.

### Measurement window stays in C

Both the JNI and cinterop paths capture `send_us` and `recv_us` from the **same C function**. No Kotlin↔native boundary is crossed between those two timestamps — no `usePinned` calls, no coroutine context switches, no JNI argument marshalling. The reported RTT is network time and nothing else. Clock source is `CLOCK_MONOTONIC` so NTP nudges can't skew an in-flight probe.

### Single source of truth for app metadata

The [`kmpSsot`](https://github.com/yuroyami/kmp-ssot) plugin (declared once in the root `build.gradle.kts`) owns `appName`, `versionName`, `bundleIdBase` and `javaVersion`. It propagates them into the Android manifest placeholders, the Gradle defaultConfig, the generated `BuildConfig`, the iOS `Info.plist`, and the pbxproj — so there is exactly one place in the repo to bump a version, and nothing drifts.

### Hard-cap timeout tied to the visual

`PING_TIMEOUT_MS = 3000` lives in the common engine. The withTimeoutOrNull hard cap, the C-side poll(2) budget, and the chameleon's color sweep all read from that same constant. Change it in one place and the engine, the UI, and the "definition of void" move together.

### Live state all the way down

Every slider writes into a `MutableStateFlow` that the engine collects. Packet size changes hit the very next probe (no debounce — the engine reads a `@Volatile` field each iteration). DNS results are cached per panel. Interval changes (when exposed) are debounced so slider drags don't thrash the sleep schedule. A ring buffer — bounded at 2 000 samples per panel — tracks history; an `AtomicLong` version counter triggers Compose recomposition without wrapping the buffer itself in a Flow.

---

## Architecture

Built with **Kotlin Multiplatform** and **Compose Multiplatform** — one shared codebase, three targets.

| Layer | Technology |
|:---|:---|
| **UI** | Compose Multiplatform (Material 3) |
| **State** | `MutableStateFlow` + bounded ring buffer per panel |
| **Ping engine** | `expect` / `actual` per platform, backed by JNI (Android/JVM) and cinterop (iOS) |
| **Navigation** | Navigation 3 |
| **Build metadata** | `kmpSsot` Gradle plugin + `com.github.gmazzo.buildconfig` |

### Engine contract

Three `expect` functions in `commonMain` drive the whole pipeline:

```kotlin
expect fun openIcmpSocket(ipv4: String): Int
expect suspend fun pingOnSocket(fd: Int, timeoutMs: Int, payloadSize: Int): Double
expect fun closeIcmpSocket(fd: Int)
```

`pingOnSocket` returns a sentinel `Double`:

| Return | Meaning |
|:---|:---|
| `≥ 0.0`  | RTT in milliseconds |
| `-1.0`   | Socket-level error — caller closes + reopens the fd |
| `-2.0`   | Budget exhausted, no matching reply — socket stays healthy |

The common loop inspects the sentinel and decides whether to keep or recycle the fd, so platform actuals stay dumb.

---

## FAQ

**Why is my ping "lost" / the bar blank?**
> A null value means the probe returned no valid ECHOREPLY within the 3-second hard cap, or the system couldn't reach the host. The app counts it as a lost packet and keeps going.

**Why does the Android app need no special permissions?**
> It only declares `INTERNET` (plus `ACCESS_NETWORK_STATE`). Android's `ping_group_range` kernel setting lets app UIDs open unprivileged ICMP sockets directly — no root, no storage permissions, no shell.

**Why does pinging `1.1.1.1` look different from `google.com` on the same network?**
> Endpoints respond with different latencies because of peering, not because of DNS. Pingy caches the DNS result once per panel, so after the first probe there's no `getaddrinfo` on the hot path. If the first probe against a hostname looks slower than later ones, that's the DNS tax you only pay once.

**Android says "App not installed" when updating?**
> Uninstall the old version first, then install the new APK. Happens when signatures don't match (debug vs. release, or between release channels).

**Windows?**
> Unsupported on purpose — Winsock doesn't expose `SOCK_DGRAM + IPPROTO_ICMP`. Wiring this app up to `IcmpSendEcho` from `icmp.dll` would need a separate codepath and hasn't been a priority.

---

## Building

**Requirements**
- Android Studio 2025.3.1+ (Kotlin 2.2, Gradle 9)
- Xcode 16.3+ (iOS build only)
- JDK 21 (toolchain auto-provisioned by Gradle if missing)
- CMake 3.22.1+ (NDK-managed on Android)

### GitHub Packages authentication

The `kmpSsot` Gradle plugin is hosted on GitHub Packages, which requires any GitHub account to authenticate — even for public packages (that's a GitHub policy, not a Pingy one). `settings.gradle.kts` reads credentials in this order:

1. `gpr.user` / `gpr.key` Gradle properties — most commonly placed in `~/.gradle/gradle.properties` (outside the repo).
2. `GITHUB_ACTOR` / `GITHUB_TOKEN` environment variables — what GitHub Actions injects automatically into CI jobs.

Nothing is committed to the repo. To build locally, create `~/.gradle/gradle.properties` (or append to it) with:

```properties
gpr.user=<your-github-username>
gpr.key=<classic-PAT-scoped-to-read:packages>
```

A classic PAT with the single scope `read:packages` is enough. Keep `~/.gradle/gradle.properties` at mode `600`.

### Android

```
./gradlew :androidApp:assembleDebug
```

Signing config is opt-in: if `keystore/pingykey.jks` doesn't exist, the app still builds (unsigned debug). Release signing reads from `local.properties` (which is `.gitignore`d) — `yuroyami.keyAlias`, `yuroyami.keyPassword`, `yuroyami.storePassword`.

### iOS

```
cd iosApp && pod install
open iosApp.xcworkspace
```

Then build from Xcode. The `shared` framework is produced by `:shared:podInstall` via `kotlin-cocoapods`; `kmpSsot`'s `syncIosConfig` task writes the version/bundleId/appName into the pbxproj before the framework is linked.

### Desktop (JVM)

```
./gradlew :shared:buildJvmNative
./gradlew :shared:jvmJar
```

The JVM target is library-only today — the jar ships the native ICMP shim and exposes `PingEngine`, but there's no UI shell. Useful if you want to embed the engine in a Swing/Compose Desktop harness of your own.

---

## Security & privacy

- No telemetry, no crash reporter, no ads, no analytics.
- No outbound traffic beyond the ICMP probes you explicitly start.
- `INTERNET` + `ACCESS_NETWORK_STATE` are the only Android permissions.
- No credentials or keystore files are committed. `local.properties`, `*.jks`, and the `keystore/` directory are all `.gitignore`d.

---

## Feedback

[Open an issue](https://github.com/yuroyami/PINGY/issues/new) for bugs or feature requests, or start a [discussion](https://github.com/yuroyami/PINGY/discussions) for general questions.

---

<p align="center">
  <sub>Licensed under <a href="https://www.gnu.org/licenses/agpl-3.0.en.html">AGPL-3.0</a></sub>
</p>
