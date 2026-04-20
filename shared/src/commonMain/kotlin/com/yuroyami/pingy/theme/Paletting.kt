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
}
