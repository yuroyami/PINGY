package com.yuroyami.pingy.ui.main.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.yuroyami.pingy.theme.pingColor

/** Colour mapping for the readout. The RTT scale itself lives in the theme. */

/** Packet loss severity on the shared color scale: 0% reads healthy teal, 5%+ reads magenta. */
internal fun lossColor(percent: Float): Color = when {
    percent <= 0.05f -> calcPingColor(40)
    percent < 1f -> calcPingColor(300)
    percent < 5f -> calcPingColor(650)
    else -> calcPingColor(1_200)
}

/** The app-wide RTT color scale lives in the theme; the graph just speaks it. */
internal fun calcPingColor(ping: Int): Color = pingColor(ping)

// Readout surface and text colours, shared by the graph and its sheets.
internal val FizzleColor = Color(0xFFFF5252)
internal val PeakLineColor = Color(0xFFE2E8EF)
// Control deck: dark instrument surfaces with dim chrome around glowing values.
// Settings sit on a slightly lifted shade so the mode flip registers without
// ever leaving the chassis.
internal val StatsSurfaceColor = Color(0xFF14181E)
internal val SettingsSurfaceColor = Color(0xFF1B212B)
internal val StatsLabelColor = Color(0xFF7C8794)
// Was #5E6874 at 3.41:1 against the cockpit background, below the 4.5:1
// minimum for body text. #7C8794 measures 5.29:1.
internal val StatsSubColor = Color(0xFF7C8794)
// Was #5A6470 at 3.21:1. #78838F measures 4.79:1.
internal val StatsDimColor = Color(0xFF78838F)
internal val SettingsTextColor = Color(0xFFC9D2DD)
