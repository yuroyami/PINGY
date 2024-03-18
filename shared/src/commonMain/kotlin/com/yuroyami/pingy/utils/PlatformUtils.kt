package com.yuroyami.pingy.utils

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp


/**
 * This function calculates the ICMP Ping to a specified host.
 *
 * @param host The host to ping.
 * @param packetSize The size of the packet to be sent in bytes.
 * @return The round-trip time in milliseconds, or null in case of an error or packet loss.
 */
expect suspend fun pingIcmp(host: String, ttl: Int = 64, packetSize: Int): Double?

expect fun generateTimestampMillis(): Long

/** Getting screen size info for UI-related calculations */
data class ScreenSizeInfo(val hPX: Int, val wPX: Int, val hDP: Dp, val wDP: Dp)
@Composable expect fun getScreenSizeInfo(): ScreenSizeInfo
