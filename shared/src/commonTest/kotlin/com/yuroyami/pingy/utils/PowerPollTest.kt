package com.yuroyami.pingy.utils

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The power poll used to compare `now - Long.MIN_VALUE`, which overflows to a
 * negative number, so the battery and thermal floors were never applied.
 */
class PowerPollTest {

    @Test
    fun first_check_is_due_at_start() {
        assertTrue(powerCheckDue(nowMs = 0L, nextCheckAtMs = 0L))
    }

    @Test
    fun not_due_before_the_deadline() {
        assertFalse(powerCheckDue(nowMs = 14_999L, nextCheckAtMs = 15_000L))
    }

    @Test
    fun due_at_the_deadline() {
        assertTrue(powerCheckDue(nowMs = 15_000L, nextCheckAtMs = 15_000L))
    }
}
