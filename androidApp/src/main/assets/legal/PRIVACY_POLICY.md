# Pingy Privacy Policy

_Last updated: August 31, 2026_

Pingy is a network latency monitor built by **yuroyami**. This policy describes
what the app actually does, based on its source code. "We" means the developer.

## The short version

There is no Pingy account, no Pingy server, and no analytics, advertising,
tracking or crash-reporting service in the app. We never receive your targets,
your measurements, your settings or your usage.

That is not the same as saying the app generates no network traffic. It is a
network tool. What follows is the complete list.

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

Anyone who can observe your network can see these packets: the host you are
pinging, your network provider, your VPN provider if you use one, and any
operator of the network you are on. That is inherent to sending network probes
and is true of any ping tool.

## Data stored on your device

Pingy keeps a small preferences file in its own private storage:

- the targets you asked it to remember;
- per-target display settings such as interval, packet size and graph scale;
- the app-wide graph style and panel layout.

Measurements themselves are held in memory only and are lost when the app
closes. Nothing is uploaded. On Android this file lives in app-private storage
with backup disabled. On iOS it lives in Application Support. On desktop it
lives in `~/.pingy`, restricted to your user account.

Deleting the app removes this file. Removing a target, or turning off "Remember"
for it, removes it from the file at the next save.

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

- **Android**: `INTERNET` and `ACCESS_NETWORK_STATE`. `INTERNET` is required to
  send probes. `ACCESS_NETWORK_STATE` is used to describe the connection you are
  on.
- **iOS**: local network access, requested by the system the first time you
  monitor a device on your own network, such as your router.

No permission is used for anything other than the features described here.

## Children

Pingy is a technical utility with no social features, no user accounts and no
content submission. It is not directed at children and collects nothing from
anyone.

## Changes

If this policy changes, the file is updated and the date at the top changes with
it.

## Contact

**yuroyami**
Email: `younesaouameur@gmail.com`

---

_Pingy sends the probes you ask for, and nothing else._
