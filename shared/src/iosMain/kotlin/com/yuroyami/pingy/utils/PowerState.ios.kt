package com.yuroyami.pingy.utils

import platform.Foundation.NSProcessInfo
// Both live in Objective-C categories, so cinterop exposes them as extensions
// that have to be imported by name rather than reached through the class.
import platform.Foundation.NSProcessInfoThermalState
import platform.Foundation.isLowPowerModeEnabled
import platform.Foundation.thermalState

/**
 * iOS reports Low Power Mode and a four-level thermal state on `ProcessInfo`.
 *
 * Both properties come from Objective-C categories, so cinterop exposes them as
 * extensions that must be imported by name rather than reached through the
 * class.
 */
actual fun currentPowerState(): PowerState {
    val info = NSProcessInfo.processInfo
    return when (info.thermalState) {
        NSProcessInfoThermalState.NSProcessInfoThermalStateSerious,
        NSProcessInfoThermalState.NSProcessInfoThermalStateCritical -> PowerState.THROTTLED
        NSProcessInfoThermalState.NSProcessInfoThermalStateFair -> PowerState.CONSTRAINED
        else -> if (info.isLowPowerModeEnabled()) PowerState.CONSTRAINED else PowerState.NORMAL
    }
}
