# Third-party notices

Pingy is distributed under the GNU Affero General Public License version 3. It
also incorporates the following runtime component families. The versions are
the direct roots pinned by the build; their platform and transitive modules use
the same listed license unless their own packaged metadata states otherwise.
Gradle dependency metadata remains enabled in Android artifacts so release
tooling can inspect the exact resolved module graph.

| Component family | Version | Project/copyright steward | License text |
|:--|:--|:--|:--|
| Kotlin standard library | 2.3.20 | Kotlin Team / JetBrains | [Apache 2.0](licenses/APACHE-2.0.txt) |
| Kotlin Coroutines | 1.11.0 | JetBrains Team / JetBrains | [Apache 2.0](licenses/APACHE-2.0.txt) |
| Compose Multiplatform UI, Foundation, Resources | 1.11.1 | JetBrains and Android Open Source Project contributors | [Apache 2.0](licenses/APACHE-2.0.txt) |
| Compose Material 3 | 1.9.0 | Android Open Source Project contributors | [Apache 2.0](licenses/APACHE-2.0.txt) |
| Compose Material Icons Extended | 1.7.3 | Android Open Source Project contributors | [Apache 2.0](licenses/APACHE-2.0.txt) |
| Jetpack Lifecycle | 2.10.0 | Android Open Source Project contributors | [Apache 2.0](licenses/APACHE-2.0.txt) |
| AndroidX Activity Compose | 1.13.0 | Android Open Source Project contributors | [Apache 2.0](licenses/APACHE-2.0.txt) |
| AndroidX AppCompat | 1.7.1 | Android Open Source Project contributors | [Apache 2.0](licenses/APACHE-2.0.txt) |
| AndroidX Core | 1.18.0 | Android Open Source Project contributors | [Apache 2.0](licenses/APACHE-2.0.txt) |
| AndroidX Core Splashscreen | 1.2.0 | Android Open Source Project contributors | [Apache 2.0](licenses/APACHE-2.0.txt) |
| Multiplatform Settings | 1.3.0 | Russell Wolf and contributors | [Apache 2.0](licenses/APACHE-2.0.txt) |
| Kermit | 2.1.0 | Touchlab and contributors | [Apache 2.0](licenses/APACHE-2.0.txt) |
| Android desugar JDK libraries | 2.1.5 | Android Open Source Project and OpenJDK contributors | [GPL v2 with Classpath Exception](licenses/GPL-2.0-WITH-CLASSPATH-EXCEPTION.txt) |

## Embedded graphics and image libraries

Compose Multiplatform resolves Skiko `0.144.6`. Its Apple framework links the
Skia `m144-22f58c9fd4` binary bundle and the libraries below. These are not
represented as separate Gradle modules, so they are inventoried explicitly
from Skiko's native linker configuration and Skia's pinned `DEPS` revisions.
The copied license files come from those exact upstream revisions.

| Embedded component | Pinned source revision | Project/copyright steward | License text |
|:--|:--|:--|:--|
| Skiko | 0.144.6 | JetBrains and contributors | [Apache 2.0](licenses/APACHE-2.0.txt) |
| Skia | m144 / `22f58c9fd43d55bde818821c04b48fda5d7ec939` | Google and contributors | [BSD 3-Clause](licenses/SKIA-BSD-3-CLAUSE.txt) |
| HarfBuzz | `08b52ae2e44931eef163dbad71697f911fadc323` | HarfBuzz contributors | [Old MIT](licenses/HARFBUZZ-OLD-MIT.txt) |
| ICU | `364118a1d9da24bb5b770ac3d762ac144d6da5a4` | Unicode, Inc. and contributors | [Unicode License v3 and bundled notices](licenses/ICU-UNICODE-3.0.txt) |
| libpng | `49363adcfaf098748d7a4c8c624ad8c45a8c3a86` | PNG Reference Library authors | [PNG Reference Library License v2](licenses/LIBPNG-2.0.txt) |
| libjpeg-turbo | `e14cbfaa85529d47f9f55b0f104a579c1061f9ad` | Independent JPEG Group, libjpeg-turbo contributors, and others | [IJG and BSD-style licenses](licenses/LIBJPEG-TURBO.txt) |
| libwebp | `845d5476a866141ba35ac133f856fa62f0b7445f` | Google and contributors | [BSD 3-Clause](licenses/WEBP-BSD-3-CLAUSE.txt) |
| Wuffs | `e3f919ccfe3ef542cfc983a82146070258fb57f8` | Google and contributors | [Apache 2.0](licenses/WUFFS-APACHE-2.0.txt) |
| Expat | `8e49998f003d693213b538ef765814c7d21abada` | Expat maintainers | [MIT](licenses/EXPAT-MIT.txt) |
| zlib | `646b7f569718921d7d4b5b8e22572ff6c76f2596` | Jean-loup Gailly, Mark Adler, and contributors | [zlib License](licenses/ZLIB.txt) |
| PIEX | `bb217acdca1cc0c16b704669dd6f91a1b509c406` | Google and contributors | [Apache 2.0](licenses/PIEX-APACHE-2.0.txt) |
| Adobe DNG SDK | `dbe0a676450d9b8c71bf00688bb306409b779e90` | Adobe Systems Incorporated | [DNG SDK License Agreement](licenses/ADOBE-DNG-SDK.txt) |

This software is based in part on the work of the Independent JPEG Group.

The complete license texts above are immutable files in the source distribution
and are packaged into both Android and iOS app bundles. Project-specific AGPL
terms are in [LICENSE](LICENSE). Build and test tools such as Gradle, the Android
Gradle Plugin, AndroidX Test, CocoaPods, and Xcode are not linked into or
redistributed inside the application.

Android, AndroidX, Jetpack, Kotlin, and Compose are trademarks of their
respective owners. Their inclusion does not imply endorsement of Pingy.
