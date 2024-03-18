package com.yuroyami.pingy.logic

import kotlin.time.TimeSource

data class Ping(
    val value: Int?,
    val timestamp: TimeSource.Monotonic.ValueTimeMark
)