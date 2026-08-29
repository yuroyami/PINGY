package com.yuroyami.pingy.logic

import kotlin.time.TimeSource

/** One probe result. [timestamp] is a value-class mark so the draw pass can
 * compute thousands of ages per frame from a single clock read. */
data class Ping(
    val value: Int?,
    val timestamp: TimeSource.Monotonic.ValueTimeMark
)
