package com.yuroyami.pingy.utils

import java.io.IOException

actual suspend fun pingIcmp(host: String, ttl: Int, packetSize: Int): Double? {
    return try {
        val pingCommand = "/system/bin/ping -c 1 -w 1 -t $ttl -s $packetSize $host"
        Runtime.getRuntime().exec(pingCommand).inputStream.use { inputStream ->
            val output = inputStream.reader().readText()
            when {
                output.contains("100% packet loss") -> null
                else -> output.substringAfter("time=").substringBefore(" ms").trim().toDouble()
            }
        }
    } catch (e: IOException) {
        loggy(e.stackTraceToString())
        null
    }
}