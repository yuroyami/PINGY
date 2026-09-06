package com.yuroyami.pingy.i18n

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Translation completeness.
 *
 * The `Strings` data class already makes a missing entry a compile error, so
 * these tests cover what the type system cannot: an entry that was copied from
 * English and never translated, a placeholder that got dropped in translation,
 * and a locale registered under a tag nothing will ever match.
 */
class LocalesTest {

    private val en = EnStrings

    /** Every catalogue value, paired with the field name, via the data class. */
    private fun fields(s: Strings): Map<String, String> = mapOf(
        "addTarget" to s.addTarget,
        "targetFieldLabel" to s.targetFieldLabel,
        "targetFieldDescription" to s.targetFieldDescription,
        "presetTargets" to s.presetTargets,
        "cyclePanelLayout" to s.cyclePanelLayout,
        "layoutStack" to s.layoutStack,
        "layoutGrid" to s.layoutGrid,
        "layoutPages" to s.layoutPages,
        "switchAllToBars" to s.switchAllToBars,
        "switchAllToRidge" to s.switchAllToRidge,
        "panelLimitReached" to s.panelLimitReached,
        "switchPanelStyle" to s.switchPanelStyle,
        "panelSettings" to s.panelSettings,
        "settingInterval" to s.settingInterval,
        "settingPacket" to s.settingPacket,
        "settingWindow" to s.settingWindow,
        "settingRoof" to s.settingRoof,
        "settingHeight" to s.settingHeight,
        "firstRunTitle" to s.firstRunTitle,
        "firstRunBody" to s.firstRunBody,
        "emptyTitle" to s.emptyTitle,
        "storeUnreadable" to s.storeUnreadable,
        "faultResolveFailed" to s.faultResolveFailed,
        "faultSocketOpenFailed" to s.faultSocketOpenFailed,
        "faultUnsupportedPlatform" to s.faultUnsupportedPlatform,
        "rejectEmpty" to s.rejectEmpty,
        "rejectCredentials" to s.rejectCredentials,
        "rejectPort" to s.rejectPort,
        "rejectIpv6" to s.rejectIpv6,
        "rejectNotAHost" to s.rejectNotAHost,
        "back" to s.back,
        "aboutTagline" to s.aboutTagline,
        "privacyPolicy" to s.privacyPolicy,
        "licenseAndNotices" to s.licenseAndNotices,
        "sourceCode" to s.sourceCode,
        "legalTitle" to s.legalTitle,
        "helpPending" to s.helpPending,
        "inspectReply" to s.inspectReply,
        "inspectTimeout" to s.inspectTimeout,
        "inspectInFlight" to s.inspectInFlight,
        "inspectInterrupted" to s.inspectInterrupted,
        "inspectUnobserved" to s.inspectUnobserved,
        "inspectSamples" to s.inspectSamples,
        "a11yLatestFault" to s.a11yLatestFault,
        "a11yLatestInterrupted" to s.a11yLatestInterrupted,
        "paused" to s.paused,
        "saveFailed" to s.saveFailed,
        "storeRetry" to s.storeRetry,
        "storeStartFresh" to s.storeStartFresh,
        "openAbout" to s.openAbout,
        "reduceMotionSetting" to s.reduceMotionSetting,
    )

    @Test
    fun english_is_registered_and_is_the_fallback() {
        assertTrue(DefaultLanguageTag in PingyLocales)
        assertEquals(EnStrings, PingyLocales.getValue(DefaultLanguageTag))
    }

    @Test
    fun every_locale_has_a_wellformed_tag() {
        PingyLocales.keys.forEach { tag ->
            assertTrue(tag.isNotBlank(), "blank language tag")
            assertTrue(
                Regex("^[a-z]{2,3}(-[A-Z][a-z]{3})?(-[A-Z]{2})?$").matches(tag),
                "`$tag` is not a BCP-47 tag Lyricist will match",
            )
        }
    }

    @Test
    fun no_locale_is_blank_anywhere() {
        PingyLocales.forEach { (tag, s) ->
            fields(s).forEach { (name, value) ->
                assertTrue(value.isNotBlank(), "$tag.$name is blank")
            }
        }
    }

    @Test
    fun translations_are_not_copied_english() {
        // A handful of entries legitimately match English: proper nouns and
        // borrowed technical terms. Everything else being identical means the
        // file was never actually translated.
        val allowedIdentical = setOf(
            "settingInterval", "settingPacket", "settingWindow", "settingHeight",
            "targetFieldLabel", "privacyPolicy", "back", "legalTitle", "emptyTitle",
        )
        val enFields = fields(en)
        PingyLocales.filterKeys { it != "en" }.forEach { (tag, s) ->
            val same = fields(s).count { (name, value) ->
                name !in allowedIdentical && value == enFields[name]
            }
            assertTrue(
                same <= 4,
                "locale `$tag` has $same entries identical to English; likely untranslated",
            )
        }
    }

    @Test
    fun placeholders_survive_translation() {
        // A translator dropping the interpolated value produces a sentence with
        // a hole in it, which the type system cannot catch.
        PingyLocales.forEach { (tag, s) ->
            assertTrue(s.targetAdded("HOST").contains("HOST"), "$tag.targetAdded lost its host")
            assertTrue(s.removeTarget("HOST").contains("HOST"), "$tag.removeTarget lost its host")
            assertTrue(
                s.targetAlreadyMonitored("HOST").contains("HOST"),
                "$tag.targetAlreadyMonitored lost its host",
            )
            assertTrue(s.skippedRecords(7).contains("7"), "$tag.skippedRecords lost its count")
            assertTrue(s.a11yLatestRtt(42).contains("42"), "$tag.a11yLatestRtt lost its value")
            assertTrue(s.a11yOverLastSeconds(5).contains("5"), "$tag.a11yOverLastSeconds lost its value")
            val sentLost = s.a11ySentLost(11, 3)
            assertTrue(sentLost.contains("11") && sentLost.contains("3"), "$tag.a11ySentLost lost a value")
            assertTrue(s.a11yExcludedFaults(9).contains("9"), "$tag.a11yExcludedFaults lost its count")
        }
    }

    @Test
    fun right_to_left_languages_are_registered() {
        RtlLanguageTags.forEach { tag ->
            assertTrue(tag in PingyLocales, "RTL language `$tag` is declared but not registered")
        }
    }

    @Test
    fun statistics_labels_stay_short_enough_for_the_readout() {
        // The stats strip is a single fixed-height line. A long word there
        // pushes the numbers off screen rather than wrapping.
        PingyLocales.forEach { (tag, s) ->
            listOf(
                "statAverage" to s.statAverage,
                "statJitter" to s.statJitter,
                "statLoss" to s.statLoss,
                "statGone" to s.statGone,
                "statRange" to s.statRange,
            ).forEach { (name, value) ->
                assertTrue(value.length <= 10, "$tag.$name is ${value.length} chars: `$value`")
            }
        }
    }
}
