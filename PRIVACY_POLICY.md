# Pingy Privacy Policy

_Last updated: August 31, 2026_

Pingy is a network latency monitor built by **yuroyami**. This policy describes
what the app actually does, read straight from its source code. "We" means the
developer.

## The short version

There is no Pingy account, no Pingy server, and no analytics, advertising,
tracking or crash-reporting service in the app. We never receive your targets,
your measurements, your settings or your usage.

The app does still put packets on the network, because that is the entire job of
a ping tool. Below is the complete list of what it sends.

## Network traffic the app generates

**ICMP echo requests ("pings") to the hosts you add.** This is the app's whole
purpose. Each packet goes to the address you chose. Nothing is sent anywhere
else.

Pingy does not start monitoring on its own. On first launch it monitors nothing
until you add a target. Targets you choose to remember are restored the next
time you open the app, and monitoring resumes for them.

**DNS lookups.** If you enter a name such as `example.com` rather than a numeric
address, your device asks your configured DNS resolver to translate it. That
lookup happens through the operating system and is visible to whoever runs your
resolver, exactly as it would be for any other app. Numeric addresses skip this
entirely.

Anyone who can watch your network can see these packets: the host you are
pinging, your network provider, your VPN provider if you use one, and whoever
runs the network you are on. Any ping tool works this way. There is no way to
send a probe and hide it from the network carrying it.

## Data stored on your device

Pingy keeps a small preferences file in its own private storage:

- the targets you asked it to remember
- per-target display settings such as interval, packet size and graph scale
- the app-wide graph style and panel layout

Measurements themselves are held in memory only and are lost when the app
closes. Nothing is uploaded.

Where the file lives, and what happens to it, differs by platform:

- **Android**: app-private storage, with system backup disabled. Deleting the
  app removes it.
- **iOS**: Application Support. Deleting the app removes it. This folder is
  included in normal iCloud and iTunes device backups, so a copy of your
  remembered targets can travel with your backup.
- **Desktop**: `~/.pingy`, restricted to your user account. This is outside the
  application itself, so it stays behind after you delete the app. Remove that
  folder by hand if you want it gone.

Removing a target, or switching "Remember" off for it, drops it from the file
at the next save.

## Diagnostic logs

The app writes diagnostic messages to the operating system's local log (Logcat
on Android, the unified log on Apple platforms) when something fails, such as a
name that will not resolve or a socket that will not open. These messages can
include the target you entered. They stay on your device, we cannot read them,
and nothing transmits them anywhere.

## Third-party code

Pingy is built with open-source libraries including Jetpack Compose, Kotlin
coroutines, AndroidX DataStore, kotlinx.serialization and Kermit. They are
compiled into the app.

None of them is an analytics, advertising, attribution or tracking service, and
none of them sends data off your device on our behalf. The full list with
licenses ships with the app and is in `THIRD_PARTY_NOTICES.md`.

## Permissions

- **Android**: `INTERNET`, required to send probes. That is the only one.
- **iOS**: local network access, requested by the system the first time you
  monitor a device on your own network, such as your router.

No permission is used for anything other than the features described here.

## Children

Pingy is a technical utility. There are no social features, no user accounts,
and no way to post anything. It is not aimed at children, and it collects nothing
from anyone.

## Changes

If this policy changes, the file is updated and the date at the top changes with
it.

## Contact

**yuroyami**
Email: `younesaouameur@gmail.com`

---

_The only thing Pingy sends is the probes you asked for._
