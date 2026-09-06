# Security policy

## Supported versions

Security fixes are applied to the latest released version and the current
`master` branch. Pre-release builds and older releases may not receive fixes.

## Reporting a vulnerability

Please do not open a public issue for a vulnerability that could put users at
risk. Email `younesaouameur@gmail.com` with:

- the affected version, platform, and device or simulator
- steps to reproduce it, plus what you expected and what you got instead
- the impact, and any proof-of-concept material
- a safe way to reach you

While testing, do not touch data or systems you do not own, do not disrupt
networks, and do not keep any data you come across.

You get a reply once the report has been read. After that you are kept in the
loop while it is assessed, and disclosure is coordinated with you once a fix
exists. There is no bounty.

For ordinary bugs and feature requests, use
<https://github.com/yuroyami/PINGY/issues>.

## Release key custody

The Android release key lives at `keystore/pingykey.jks` with its passwords in
`local.properties`. Both are ignored by Git and both must be mode 0600. The
build prints a warning if the keystore is readable by other accounts on the
machine.

Keep one encrypted backup of the key outside the working copy. Google Play ties
an app's identity to its signing key: losing it without Play App Signing
enrolment means publishing under a new application id, and every existing
install stops receiving updates.
