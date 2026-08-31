package com.yuroyami.pingy.utils

/**
 * Desktop exposes no portable battery or thermal signal, and a machine running
 * a desktop app is usually not the constrained case this exists for.
 */
actual fun currentPowerState(): PowerState = PowerState.NORMAL
