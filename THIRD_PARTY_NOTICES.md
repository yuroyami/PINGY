# Third-party notices

Pingy bundles the open-source components listed below. Every one of them is
licensed under the **Apache License, Version 2.0**; the full text is in
[licenses/APACHE-2.0.txt](licenses/APACHE-2.0.txt).

The component list is generated from the Android release runtime classpath
after Gradle resolves it, so it names what actually ships, not only what the
build files ask for directly. The desktop and iOS builds add the native
graphics stack listed under "Rendering engine". Regenerate all of it whenever
the dependency set changes.

Pingy itself is licensed under the GNU Affero General Public License v3.0; see
[LICENSE](LICENSE) and [NOTICE](NOTICE).

## Components

### AndroidX (Android Open Source Project)

Apache-2.0. 128 modules across 41 groups:

- `androidx.activity`: `activity`, `activity-compose`, `activity-ktx`
- `androidx.annotation`: `annotation`, `annotation-experimental`, `annotation-jvm`
- `androidx.appcompat`: `appcompat`, `appcompat-resources`
- `androidx.arch.core`: `core-common`, `core-runtime`
- `androidx.autofill`: `autofill`
- `androidx.collection`: `collection`, `collection-jvm`, `collection-ktx`
- `androidx.compose.animation`: `animation`, `animation-android`, `animation-core`, `animation-core-android`
- `androidx.compose.foundation`: `foundation`, `foundation-android`, `foundation-layout`, `foundation-layout-android`
- `androidx.compose.material`: `material-icons-core`, `material-icons-core-android`, `material-icons-extended`, `material-icons-extended-android`, `material-ripple`, `material-ripple-android`
- `androidx.compose.material3`: `material3`, `material3-android`
- `androidx.compose.runtime`: `runtime`, `runtime-android`, `runtime-annotation`, `runtime-annotation-android`, `runtime-retain`, `runtime-retain-android`, `runtime-saveable`, `runtime-saveable-android`
- `androidx.compose.ui`: `ui`, `ui-android`, `ui-geometry`, `ui-geometry-android`, `ui-graphics`, `ui-graphics-android`, `ui-text`, `ui-text-android`, `ui-unit`, `ui-unit-android`, `ui-util`, `ui-util-android`
- `androidx.concurrent`: `concurrent-futures`
- `androidx.core`: `core`, `core-ktx`, `core-splashscreen`, `core-viewtree`
- `androidx.cursoradapter`: `cursoradapter`
- `androidx.customview`: `customview`, `customview-poolingcontainer`
- `androidx.datastore`: `datastore-core`, `datastore-core-android`, `datastore-core-okio`, `datastore-core-okio-jvm`, `datastore-preferences-core`, `datastore-preferences-core-android`, `datastore-preferences-external-protobuf`, `datastore-preferences-proto`
- `androidx.documentfile`: `documentfile`
- `androidx.drawerlayout`: `drawerlayout`
- `androidx.dynamicanimation`: `dynamicanimation`
- `androidx.emoji2`: `emoji2`, `emoji2-views-helper`
- `androidx.fragment`: `fragment`
- `androidx.graphics`: `graphics-path`, `graphics-shapes`, `graphics-shapes-android`
- `androidx.interpolator`: `interpolator`
- `androidx.legacy`: `legacy-support-core-utils`
- `androidx.lifecycle`: `lifecycle-common`, `lifecycle-common-java8`, `lifecycle-common-jvm`, `lifecycle-livedata`, `lifecycle-livedata-core`, `lifecycle-livedata-core-ktx`, `lifecycle-process`, `lifecycle-runtime`, `lifecycle-runtime-android`, `lifecycle-runtime-compose`, `lifecycle-runtime-compose-android`, `lifecycle-runtime-ktx`, `lifecycle-runtime-ktx-android`, `lifecycle-viewmodel`, `lifecycle-viewmodel-android`, `lifecycle-viewmodel-compose`, `lifecycle-viewmodel-compose-android`, `lifecycle-viewmodel-ktx`, `lifecycle-viewmodel-navigation3`, `lifecycle-viewmodel-navigation3-android`, `lifecycle-viewmodel-savedstate`, `lifecycle-viewmodel-savedstate-android`
- `androidx.loader`: `loader`
- `androidx.localbroadcastmanager`: `localbroadcastmanager`
- `androidx.navigation3`: `navigation3-runtime`, `navigation3-runtime-android`, `navigation3-ui`, `navigation3-ui-android`
- `androidx.navigationevent`: `navigationevent`, `navigationevent-android`, `navigationevent-compose`, `navigationevent-compose-android`
- `androidx.print`: `print`
- `androidx.profileinstaller`: `profileinstaller`
- `androidx.resourceinspection`: `resourceinspection-annotation`
- `androidx.savedstate`: `savedstate`, `savedstate-android`, `savedstate-compose`, `savedstate-compose-android`, `savedstate-ktx`
- `androidx.startup`: `startup-runtime`
- `androidx.tracing`: `tracing`, `tracing-android`
- `androidx.transition`: `transition`
- `androidx.vectordrawable`: `vectordrawable`, `vectordrawable-animated`
- `androidx.versionedparcelable`: `versionedparcelable`
- `androidx.viewpager`: `viewpager`
- `androidx.window`: `window`, `window-core`, `window-core-android`

### Cafe Adriel

Apache-2.0. 1 module in 1 group:

- `cafe.adriel.lyricist`: `lyricist` (with its `lyricist-core` and per-platform
  artifacts). Supplies the runtime translation lookup for the 21 language
  catalogues.

### Compose Multiplatform (JetBrains)

Apache-2.0. 18 modules across 7 groups:

- `org.jetbrains.compose.animation`: `animation`, `animation-core`
- `org.jetbrains.compose.components`: `components-resources`, `components-resources-android`
- `org.jetbrains.compose.foundation`: `foundation`, `foundation-layout`
- `org.jetbrains.compose.material`: `material-icons-core`, `material-icons-extended`, `material-ripple`
- `org.jetbrains.compose.material3`: `material3`
- `org.jetbrains.compose.runtime`: `runtime`, `runtime-saveable`
- `org.jetbrains.compose.ui`: `ui`, `ui-geometry`, `ui-graphics`, `ui-text`, `ui-unit`, `ui-util`

### Compose Multiplatform AndroidX ports (JetBrains)

Apache-2.0. 11 modules across 4 groups:

- `org.jetbrains.androidx.lifecycle`: `lifecycle-common`, `lifecycle-runtime`, `lifecycle-runtime-compose`, `lifecycle-viewmodel`, `lifecycle-viewmodel-compose`, `lifecycle-viewmodel-navigation3`, `lifecycle-viewmodel-savedstate`
- `org.jetbrains.androidx.navigation3`: `navigation3-ui`
- `org.jetbrains.androidx.navigationevent`: `navigationevent-compose`
- `org.jetbrains.androidx.savedstate`: `savedstate`, `savedstate-compose`

### Google

Apache-2.0. 1 module in 1 group:

- `com.google.guava`: `listenablefuture`

### JSpecify

Apache-2.0. 1 module in 1 group:

- `org.jspecify`: `jspecify`

### JetBrains

Apache-2.0. 1 module in 1 group:

- `org.jetbrains`: `annotations`

### Kotlin (JetBrains)

Apache-2.0. 2 modules in 1 group:

- `org.jetbrains.kotlin`: `kotlin-stdlib`, `kotlin-stdlib-common`

### Square

Apache-2.0. 2 modules in 1 group:

- `com.squareup.okio`: `okio`, `okio-jvm`

### Touchlab

Apache-2.0. 4 modules in 1 group:

- `co.touchlab`: `kermit`, `kermit-android`, `kermit-core`, `kermit-core-android`

### kotlinx (JetBrains)

Apache-2.0. 13 modules in 1 group:

- `org.jetbrains.kotlinx`: `atomicfu`, `atomicfu-jvm`, `kotlinx-coroutines-android`, `kotlinx-coroutines-bom`, `kotlinx-coroutines-core`, `kotlinx-coroutines-core-jvm`, `kotlinx-datetime`, `kotlinx-datetime-jvm`, `kotlinx-serialization-bom`, `kotlinx-serialization-core`, `kotlinx-serialization-core-jvm`, `kotlinx-serialization-json`, `kotlinx-serialization-json-jvm`

### Rendering engine (desktop and iOS)

Compose Multiplatform `1.11.0-beta02` resolves Skiko `0.144.5`, which is not on
the Android classpath and therefore not in the list above. Skiko is Apache-2.0
and embeds Skia together with HarfBuzz, ICU, libpng, libjpeg-turbo, WebP,
Wuffs, Expat, zlib, PIEX and the Adobe DNG SDK. Their license texts are in
`licenses/`, one file per component.

## Native code

`shared/native/icmp_core.h` and `shared/native/icmp_ping.c` are original work,
covered by Pingy's own license. They use only the platform C library and the
POSIX socket API. No third-party C code is copied in.

## Fonts and artwork

**Inter**, version 3.019 (git `0a5106e0b`). Copyright 2020 The Inter Project
Authors. SIL Open Font License 1.1, full text in `licenses/OFL-1.1.txt`.

The bundled file is the **Medium** weight despite its `Inter-Regular.otf`
filename; the name table inside it reads `Inter-Medium:2021:0a5106e0b`. The
design was tuned on that weight, so the file keeps its name.

```text
shared/src/commonMain/composeResources/font/Inter-Regular.otf
SHA-256 99dab2bdcb613c4c8264000a94351d1227f74dc95a86d1249493aeee0c0179c4
```

Application icons and the wordmark are original work by the Pingy author.
