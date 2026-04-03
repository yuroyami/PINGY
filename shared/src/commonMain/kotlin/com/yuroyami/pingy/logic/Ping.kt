package com.yuroyami.pingy.logic

import kotlin.time.TimeMark

data class Ping(
    val value: Int?,
    val timestamp: TimeMark
)