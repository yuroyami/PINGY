package com.yuroyami.pingy.utils

import android.content.Context
import android.os.Build
import android.os.PowerManager

/**
 * Android reports battery saver through [PowerManager.isPowerSaveMode] and
 * thermal pressure through `getCurrentThermalStatus` on Q and above.
 */
actual fun currentPowerState(): PowerState = runCatching {
    val pm = pingyAppContext.getSystemService(Context.POWER_SERVICE) as PowerManager

    val thermal = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        when (pm.currentThermalStatus) {
            PowerManager.THERMAL_STATUS_NONE, PowerManager.THERMAL_STATUS_LIGHT -> PowerState.NORMAL
            PowerManager.THERMAL_STATUS_MODERATE -> PowerState.CONSTRAINED
            else -> PowerState.THROTTLED
        }
    } else {
        PowerState.NORMAL
    }

    if (thermal != PowerState.NORMAL) thermal
    else if (pm.isPowerSaveMode) PowerState.CONSTRAINED
    else PowerState.NORMAL
}.getOrDefault(PowerState.NORMAL)
