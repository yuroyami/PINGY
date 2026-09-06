package com.yuroyami.pingy.ui.main.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Display formatting for RTT values.
 *
 * The sub-millisecond branch rounded to hundredths and then always printed a
 * leading "0.", so 0.996 ms came out as "0.00": an almost-one-millisecond LAN
 * reading read as zero.
 */
class GraphFormatTest {

    @Test
    fun rounding_up_to_one_millisecond_carries() {
        assertEquals("1.0", formatRtt(0.996))
        assertEquals("0.99", formatRtt(0.994))
        assertEquals("0.00", formatRtt(0.0))
    }

    @Test
    fun output_never_goes_backwards_as_input_grows() {
        var prev = 0.0
        var x = 0.0
        while (x <= 12.0) {
            val v = formatRtt(x).toDouble()
            assertTrue(v >= prev, "formatRtt($x) = $v dropped below $prev")
            prev = v
            x += 0.001
        }
    }
}
