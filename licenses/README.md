# License provenance

This directory is the canonical license-text set packaged into both Pingy apps.
The Android copies under `androidApp/src/main/assets/legal/licenses` must remain
byte-for-byte identical; CI checks every `*.txt` file. The iOS project packages
this directory as a folder resource.

## Native graphics inventory

Compose Multiplatform `1.11.1` resolves Skiko `0.144.6`. That Skiko release
pins JetBrains Skia tag `m144-22f58c9fd4`, full commit
`22f58c9fd43d55bde818821c04b48fda5d7ec939`. The exact Apple arm64 bundle used
to audit the native archive is:

```text
https://github.com/JetBrains/skia/releases/download/m144-22f58c9fd4/Skia-m144-22f58c9fd4-ios-release-arm64.zip
SHA-256 995b795675795e8d8127f6ca882a1a3bdf72648ab2dd21134f758c01eaa3710c
```

The linked-library list comes from Skiko's native task configuration at tag
`v0.144.6`; pinned source revisions come from the Skia tag's `DEPS` file. The
archive's static-library members provide a second check that the documented
Skia, HarfBuzz, ICU, PNG, JPEG, WebP, Wuffs, Expat, zlib, PIEX, and DNG
components are present. `THIRD_PARTY_NOTICES.md` records those revisions and
maps each one to its license file here.

## Update procedure

When Compose or Skiko changes:

1. Resolve the exact Skiko version and its `dependencies.skia` tag.
2. Inspect Skiko's native linker list and the matching Skia binary archive;
   do not infer the inventory only from Gradle modules.
3. Read the matching Skia `DEPS` revisions and replace license texts directly
   from those exact upstream commits.
4. Update `THIRD_PARTY_NOTICES.md`, copy every license into the Android legal
   asset directory, and update this provenance record.
5. Build both distributables and verify the legal files inside the final APK,
   AAB, simulator app, and device app.
