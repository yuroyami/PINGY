package com.yuroyami.pingy.ui.main.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.yuroyami.pingy.theme.pingColor
import kotlin.math.pow

/** Colour mapping for the readout. The RTT scale itself lives in the theme. */

/** WCAG minimum for normal-size body text. */
private const val MIN_TEXT_CONTRAST = 4.5

/** WCAG relative luminance of an sRGB colour. */
internal fun luminance(c: Color): Double {
    fun channel(v: Float): Double {
        val d = v.toDouble()
        return if (d <= 0.04045) d / 12.92 else ((d + 0.055) / 1.055).pow(2.4)
    }
    return 0.2126 * channel(c.red) + 0.7152 * channel(c.green) + 0.0722 * channel(c.blue)
}

/** WCAG contrast ratio between two opaque colours, from 1 to 21. */
internal fun contrastRatio(a: Color, b: Color): Double {
    val la = luminance(a)
    val lb = luminance(b)
    return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
}

/**
 * The RTT hue, lightened just far enough to be readable on [surface].
 *
 * The scale darkens deliberately at both ends, which is right for bars against
 * the canvas and wrong for text: an excellent LAN reading and a badly degraded
 * one were the two hardest numbers to read, exactly when they matter. Bars keep
 * the raw hue; only text goes through here.
 */
internal fun readablePingTextColor(ping: Int, surface: Color = StatsSurfaceColor): Color {
    val base = calcPingColor(ping)
    if (contrastRatio(base, surface) >= MIN_TEXT_CONTRAST) return base
    var t = 0.05f
    while (t < 1f) {
        val candidate = lerp(base, Color.White, t)
        if (contrastRatio(candidate, surface) >= MIN_TEXT_CONTRAST) return candidate
        t += 0.05f
    }
    return Color.White
}

/** Packet loss severity on the shared color scale: 0% reads healthy teal, 5%+ reads magenta. */
internal fun lossColor(percent: Float): Color = when {
    percent <= 0.05f -> readablePingTextColor(40)
    percent < 1f -> readablePingTextColor(300)
    percent < 5f -> readablePingTextColor(650)
    else -> readablePingTextColor(1_200)
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
// Measured against every surface these sit on, not just the cockpit: #7C8794
// was 4.43:1 on the settings deck and #78838F was 4.19:1, both under the 4.5:1
// minimum for body text. #8A95A3 measures 5.32:1 at its worst.
internal val StatsLabelColor = Color(0xFF8A95A3)
internal val StatsSubColor = Color(0xFF8A95A3)
internal val StatsDimColor = Color(0xFF8A95A3)
internal val SettingsTextColor = Color(0xFFC9D2DD)
