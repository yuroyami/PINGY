package com.yuroyami.pingy.i18n

/**
 * Every user-visible string in Pingy.
 *
 * A plain data class rather than a code-generated catalogue: Lyricist's runtime
 * API needs no annotation processor, which keeps translations independent of
 * KSP's lag behind new Kotlin releases.
 *
 * Rules for adding entries:
 * - Anything a person reads belongs here, including accessibility descriptions.
 *   A screen reader user is reading strings too.
 * - Prefer a lambda over interpolation at the call site, so translators can move
 *   the placeholder to wherever their grammar puts it.
 * - Keep abbreviations that are drawn inside the graph (`AVG`, `JIT`) short;
 *   they sit in a fixed-width readout.
 */
data class Strings(
    // Header and cockpit
    val addTarget: String,
    val targetFieldLabel: String,
    val targetFieldDescription: String,
    val presetTargets: String,
    val cyclePanelLayout: String,
    val layoutStack: String,
    val layoutGrid: String,
    val layoutPages: String,
    val switchAllToBars: String,
    val switchAllToRidge: String,
    val allPanelsBars: String,
    val allPanelsRidge: String,

    // Add results
    val targetAdded: (String) -> String,
    val targetAlreadyMonitored: (String) -> String,
    val panelLimitReached: String,

    // Panel controls
    val switchPanelStyle: String,
    val panelSettings: String,
    val removeTarget: (String) -> String,
    val remember: String,
    val willReturnNextLaunch: (String) -> String,
    val willNotReturnNextLaunch: (String) -> String,
    val longPressToReset: (String) -> String,

    // Settings sliders
    val settingInterval: String,
    val settingPacket: String,
    val settingAttack: String,
    val settingWindow: String,
    val settingRoof: String,
    val settingHeight: String,
    val intervalAdaptive: String,
    val valueLinear: String,

    // Statistics readout
    val statAverage: String,
    val statJitter: String,
    val statLoss: String,
    val statGone: String,
    val statLow: String,
    val statHigh: String,

    // Empty and error states
    val firstRunTitle: String,
    val firstRunBody: String,
    val emptyTitle: String,
    val emptyBody: String,
    val storeUnreadable: String,
    val skippedRecords: (Int) -> String,

    // Local faults, shown instead of pretending the target lost packets
    val faultResolveFailed: String,
    val faultSocketOpenFailed: String,
    val faultSendFailed: String,
    val faultSocketLost: String,
    val faultUnsupportedPlatform: String,

    // Target validation
    val rejectEmpty: String,
    val rejectCredentials: String,
    val rejectPort: String,
    val rejectIpv6: String,
    val rejectTooLong: String,
    val rejectIllegalCharacters: String,
    val rejectNotAHost: String,

    // Accessibility summary of the graph
    val a11yLatestTimedOut: String,
    val a11yLatestRtt: (Int) -> String,
    val a11yNoReading: String,
    val a11yOverLastSeconds: (Long) -> String,
    val a11yNoProbesSent: String,
    val a11ySentLost: (Int, Int) -> String,
    val a11yAverage: (Int) -> String,
    val a11yBest: (Int) -> String,
    val a11yWorst: (Int) -> String,
    val a11yExcludedFaults: (Int) -> String,

    // Status, recovery and destructive actions
    val loading: String,
    val undo: String,
    val panelRemoved: (String) -> String,
    val resetPanelSettings: String,
    val settingsWereReset: String,
    val retry: String,
    val close: String,

    // Menu and pager state, spoken to assistive technology
    val currentlySelected: String,
    val pagePosition: (Int, Int) -> String,
    val previousPanel: String,
    val nextPanel: String,

    // Contextual help for the statistics, which are otherwise bare jargon
    val whatDoTheseMean: String,
    val helpAverage: String,
    val helpJitter: String,
    val helpLoss: String,
    val helpGone: String,

    // About and legal
    val back: String,
    val aboutTagline: String,
    val aboutTech: String,
    val privacyPolicy: String,
    val licenseAndNotices: String,
    val openSourceLicenses: String,
    val sourceCode: String,
    val legalTitle: String,
)
