package com.yuroyami.pingy.ui.main.components

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Text colour against the surfaces it actually lands on.
 *
 * The RTT scale darkens at both ends, which is right for bars and wrong for
 * text: a sub-millisecond LAN reading and a badly degraded one were the two
 * least readable numbers on screen.
 */
class GraphPaletteTest {

    private val surfaces = listOf(
        "statistics strip" to StatsSurfaceColor,
        "settings deck" to SettingsSurfaceColor,
        "cockpit" to Color(0xFF0B0E13),
    )

    @Test
    fun every_rtt_in_the_scale_is_readable_on_every_surface() {
        for ((name, surface) in surfaces) {
            for (ping in 0..2_000) {
                val ratio = contrastRatio(readablePingTextColor(ping, surface), surface)
                assertTrue(
                    ratio >= 4.5,
                    "$ping ms reads at ${(ratio * 100).toInt() / 100.0} to 1 on the $name",
                )
            }
        }
    }

    @Test
    fun the_static_chrome_tokens_are_readable_too() {
        for ((name, surface) in surfaces) {
            listOf(
                "label" to StatsLabelColor,
                "sub" to StatsSubColor,
                "dim" to StatsDimColor,
                "body" to SettingsTextColor,
            ).forEach { (token, color) ->
                val ratio = contrastRatio(color, surface)
                assertTrue(ratio >= 4.5, "$token on the $name reads at ${(ratio * 100).toInt() / 100.0} to 1")
            }
        }
    }

    @Test
    fun a_colour_that_already_passes_is_left_alone() {
        // Around 200 ms the scale is bright yellow-green and needs no help.
        assertTrue(readablePingTextColor(200) == calcPingColor(200))
    }

    @Test
    fun contrast_of_a_colour_with_itself_is_one() {
        assertTrue(contrastRatio(Color.White, Color.White) in 0.999..1.001)
        assertTrue(contrastRatio(Color.White, Color.Black) in 20.9..21.1)
    }
}
