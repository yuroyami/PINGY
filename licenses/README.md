# Where these licenses come from

These are the master copies of every license text that ships inside Pingy.
The Android build copies them into the packaged assets at build time, and CI
checks the copies inside the APK against these files. The iOS and desktop
artifacts do not ship them yet.

## Native graphics inventory

Compose Multiplatform `1.11.0-beta02` resolves Skiko `0.144.5`. The inventory
below was taken against Skia tag `m144-22f58c9fd4`, full commit
`22f58c9fd43d55bde818821c04b48fda5d7ec939`. The Apple arm64 bundle that was
opened and inspected to produce this inventory is:

```text
https://github.com/JetBrains/skia/releases/download/m144-22f58c9fd4/Skia-m144-22f58c9fd4-ios-release-arm64.zip
SHA-256 995b795675795e8d8127f6ca882a1a3bdf72648ab2dd21134f758c01eaa3710c
```

Two sources feed this list. The linked libraries come from Skiko's native task
configuration, and the pinned source revisions come from the
Skia tag's `DEPS` file. Reading the static libraries inside the archive is a
third, independent check that Skia, HarfBuzz, ICU, PNG, JPEG, WebP, Wuffs,
Expat, zlib, PIEX, and DNG are all really in there.
`THIRD_PARTY_NOTICES.md` records those revisions and points each one at its
license file here.

## Update procedure

When Compose or Skiko changes:

1. Resolve the exact Skiko version and its `dependencies.skia` tag.
2. Inspect Skiko's native linker list and the matching Skia binary archive.
   Do not work out the inventory from Gradle modules alone.
3. Read the matching Skia `DEPS` revisions, then replace the license texts
   straight from those exact upstream commits.
4. Update `THIRD_PARTY_NOTICES.md`, copy every license into the Android legal
   asset directory, and update this file.
5. Build both distributables and verify the legal files inside the final APK,
   AAB, simulator app, and device app.
