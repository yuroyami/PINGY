package com.yuroyami.pingy.theme

import androidx.compose.ui.graphics.Color

/** Contains the Material Color properties that we're using in our app.
 *
 * A material theme consists of two color palettes: Primary (A) and Secondary (B),
 * each with a main color (neutral to the dark-light theming), a lighter shade,
 * and a darker shade. */
object Paletting {

    /** Primary Palette Colors */
    val A_MAIN_COLOR = Color(0, 193, 213)
    val A_LIGHT_COLOR = Color(100, 244, 255)
    val A_DARK_COLOR = Color(0, 144, 169)

    /** Primary Palette friendly colors */
    val A_COMPLEMENTARY = Color(169, 25, 0) /* Complements it in the color wheel */
    val A_ANALOGOUS_1 = Color(0, 169, 110)  /* Analogous (neighbour) to the left */
    val A_ANALOGOUS_2 = Color(0, 59, 169)   /* Analogous to the right */
    val A_TRIADIC_1 = Color(169, 0, 144)    /* 2nd part of the triad with the main color */
    val A_TRIADIC_2 = Color(25, 0, 169)     /* 3rd part of the triad with the main color */

    /** Secondary Palette Colors (mostly white tbh) */
    val B_MAIN_COLOR = Color(250, 250, 250)
    val B_LIGHT_COLOR = Color(255, 255, 255)
    val B_DARK_COLOR = Color(199, 199, 199)

    /** Sgn Shade */
    val SGN = Color(92, 255, 127)
    val SGN2 = Color(92, 200, 90)

    /** Cockpit chrome shades shared by the header, chips, and panel decks. */
    val COCKPIT_BG = Color(0xFF0B0E13)
    val STRIP_BG = Color(0xFF131820)
    val STRIP_BORDER = Color(0xFF2A3442)
    val CHIP_BG = Color(0xFF10141B)
    val LOST_RED = Color(0xFFFF5252)
}

/**
 * Maps a ping RTT (ms) to the color it carries everywhere in the app: bars,
 * slopes, readouts, stat values, and the live dots on target chips.
 *
 * Anchors (interpolated in HSV, log-scale on ping): ≤10ms ocean blue, ~40ms
 * teal, ~200ms yellow-green, ~650ms orange, ~1s magenta, ≥2s deep purple.
 * Hue rides log(ping) because perceived latency is roughly logarithmic, and
 * brightness peaks near yellow so the warning zone never renders olive.
 */
fun pingColor(ping: Int): Color {
    val clamped = ping.coerceAtLeast(0).coerceAtMost(2000)
    val t = if (clamped <= 1) 0.0
            else (kotlin.math.log10(clamped.toDouble()) / kotlin.math.log10(2000.0)).coerceIn(0.0, 1.0)

    val hueDeg = piecewiseLinear(
        t,
        0.00, 225.0,
        0.50, 165.0,
        0.70, 95.0,
        0.85, 25.0,
        1.00, -60.0,
    ).let { ((it % 360.0) + 360.0) % 360.0 }

    val v = if (t <= 0.70) 0.65 + (t / 0.70) * 0.35
            else           1.00 - ((t - 0.70) / 0.30) * 0.50

    return hsvColor(hueDeg, 0.88, v)
}

/** Piecewise-linear interpolation through (x, y) anchor pairs sorted by x. */
private fun piecewiseLinear(x: Double, vararg xy: Double): Double {
    val n = xy.size / 2
    if (x <= xy[0]) return xy[1]
    if (x >= xy[(n - 1) * 2]) return xy[(n - 1) * 2 + 1]
    for (i in 0 until n - 1) {
        val x0 = xy[i * 2]; val y0 = xy[i * 2 + 1]
        val x1 = xy[(i + 1) * 2]; val y1 = xy[(i + 1) * 2 + 1]
        if (x in x0..x1) {
            val f = (x - x0) / (x1 - x0)
            return y0 + f * (y1 - y0)
        }
    }
    return xy[(n - 1) * 2 + 1]
}

/** HSV → sRGB. h in degrees [0,360), s/v in [0,1]. */
private fun hsvColor(h: Double, s: Double, v: Double): Color {
    val c = v * s
    val hh = (h / 60.0)
    val x = c * (1.0 - kotlin.math.abs((hh % 2.0) - 1.0))
    val m = v - c
    val (r0, g0, b0) = when {
        hh < 1.0 -> Triple(c, x, 0.0)
        hh < 2.0 -> Triple(x, c, 0.0)
        hh < 3.0 -> Triple(0.0, c, x)
        hh < 4.0 -> Triple(0.0, x, c)
        hh < 5.0 -> Triple(x, 0.0, c)
        else     -> Triple(c, 0.0, x)
    }
    return Color(
        red = (r0 + m).toFloat().coerceIn(0f, 1f),
        green = (g0 + m).toFloat().coerceIn(0f, 1f),
        blue = (b0 + m).toFloat().coerceIn(0f, 1f),
    )
}
