package com.yuroyami.pingy.utils


/**
 * This function calculates the ICMP Ping to a specified host.
 *
 * @param host The host to ping.
 * @param packetSize The size of the packet to be sent in bytes.
 * @return The round-trip time in milliseconds, or null in case of an error or packet loss.
 */
expect suspend fun pingIcmp(host: String, ttl: Int = 64, packetSize: Int): Double?