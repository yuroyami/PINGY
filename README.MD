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

- **Real-time ping graph** &mdash; smooth canvas rendering of round-trip times over a rolling time window
- **Multiple panels at once** &mdash; monitor several hosts side by side (1.1.1.1, 8.8.8.8, your router, a game server, whatever)
- **Configurable per panel** &mdash; packet size, interval (down to "as fast as the reply arrives"), graph roof, zoom curve, time window, and canvas height
- **Live stats** &mdash; sent/lost counters, lowest/highest seen, packet loss visible at a glance
- **Domain or IP** &mdash; ping anything resolvable
- **Quick presets** &mdash; dropdown of common hosts (Cloudflare, Google, Quad9, OpenDNS, LAN gateway, etc.)
- **No network data collection** &mdash; packets go only where you tell them to, nothing phones home

---

## Platform Support

| Target | Status |
|:---|:---|
| **Android** | 8.0 (API 26) and above |
| **iOS** | 16.0 and above |
| **Desktop (JVM)** | Experimental |
| **Web (JS)** | Experimental |

Under the hood each platform uses a native ping engine:

| Platform | Engine |
|:---|:---|
| **Android** | `/system/bin/ping` via a long-lived process |
| **iOS** | `SimplePing` (ICMP via raw socket) |
| **JVM** | `InetAddress.isReachable` / `ping` subprocess |
| **JS** | HTTP round-trip fallback |

---

## Architecture

Built with **Kotlin Multiplatform** and **Compose Multiplatform** &mdash; one shared codebase, four targets.

| Layer | Technology |
|:---|:---|
| **UI** | Compose Multiplatform (Material 3) |
| **State** | Kotlin `StateFlow` + a bounded ring buffer per panel |
| **Ping engine** | `expect`/`actual` per platform |
| **Navigation** | Navigation 3 |

The ring buffer caps each panel at a few thousand samples so long monitoring sessions don't blow up memory, and interval changes are debounced so dragging a slider doesn't thrash the underlying ping process.

---

## FAQ

**Why is my ping "lost" / negative?**
> A null/negative value means the probe timed out or the system couldn't reach the host. The app counts it as a lost packet and keeps going.

**Why does Android ask for storage permission?**
> Only for saving exported data in future versions. Nothing is uploaded anywhere.

**Why doesn't the web build match the mobile version feature-for-feature?**
> Browsers don't let JavaScript send real ICMP packets. The JS target uses an HTTP round-trip as a best-effort substitute, so absolute numbers differ from native ping.

**Android says "App not installed" when updating?**
> Uninstall the old version first, then install the new APK.

---

## Building

**Requirements:** Android Studio 2025.3.1+ (and Xcode 16.3+ for iOS).

Clone the repo and open it in Android Studio &mdash; no additional configuration needed. The iOS app is in `iosApp/` and opens in Xcode via the `.xcworkspace` once `pod install` has run.

---

## Feedback

[Open an issue](https://github.com/yuroyami/PINGY/issues/new) for bugs or feature requests, or start a [discussion](https://github.com/yuroyami/PINGY/discussions) for general questions.

---

<p align="center">
  <sub>Licensed under <a href="https://www.gnu.org/licenses/agpl-3.0.en.html">AGPL-3.0</a></sub>
</p>
