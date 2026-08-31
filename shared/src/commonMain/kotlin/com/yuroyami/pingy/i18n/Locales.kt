package com.yuroyami.pingy.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Locale registry.
 *
 * Lyricist's runtime API is used deliberately instead of its KSP processor:
 * annotation processors lag new Kotlin releases, and a translation catalogue is
 * not worth coupling to that. Adding a language means adding a file and one
 * entry here.
 *
 * Keys are BCP-47 language tags. Lyricist matches the device tag against them,
 * falling back to [DefaultLanguageTag] when nothing fits.
 */
const val DefaultLanguageTag: String = "en"

val PingyLocales: Map<String, Strings> = mapOf(
    "en" to EnStrings,
    "ar" to ArStrings,
    "de" to DeStrings,
    "es" to EsStrings,
    "fa" to FaStrings,
    "fr" to FrStrings,
    "he" to HeStrings,
    "hi" to HiStrings,
    "id" to IdStrings,
    "it" to ItStrings,
    "ja" to JaStrings,
    "ko" to KoStrings,
    "nl" to NlStrings,
    "pl" to PlStrings,
    "pt" to PtStrings,
    "ru" to RuStrings,
    "tr" to TrStrings,
    "uk" to UkStrings,
    "vi" to ViStrings,
    "zh" to ZhHansStrings,
    "zh-Hant" to ZhHantStrings,
)

/** Languages written right to left. Used to assert mirrored layout in tests. */
val RtlLanguageTags: Set<String> = setOf("ar", "fa", "he")

/**
 * The active string set.
 *
 * `staticCompositionLocalOf` rather than `compositionLocalOf`: the value changes
 * only when the device language changes, so there is no reason to pay for
 * fine-grained invalidation on every read.
 */
val LocalStrings: ProvidableCompositionLocal<Strings> =
    staticCompositionLocalOf { EnStrings }

/** Shorthand for `LocalStrings.current` at call sites. */
val strings: Strings
    @Composable get() = LocalStrings.current
