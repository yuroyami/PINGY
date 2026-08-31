package com.yuroyami.pingy.utils

/**
 * How hard the device is willing to work right now.
 *
 * Pingy is an unattended continuous sampler, which is exactly the kind of app
 * that should back off when the battery is low or the device is running hot.
 */
enum class PowerState {
    /** Normal operation. */
    NORMAL,

    /** Low power mode, or a mild thermal warning. Sample less aggressively. */
    CONSTRAINED,

    /** Serious thermal pressure. Sample as little as still useful. */
    THROTTLED;

    /**
     * Floor applied to the gap between probes to one target.
     *
     * Adaptive mode's normal floor is [MIN_PROBE_GAP_MS]; under pressure the
     * cadence widens rather than stopping, so the graph stays honest about the
     * target instead of showing a fake outage.
     */
    val probeGapFloorMs: Long
        get() = when (this) {
            NORMAL -> MIN_PROBE_GAP_MS
            CONSTRAINED -> 200L
            THROTTLED -> 1_000L
        }
}

/** Current power and thermal state, or [PowerState.NORMAL] where unknown. */
expect fun currentPowerState(): PowerState
