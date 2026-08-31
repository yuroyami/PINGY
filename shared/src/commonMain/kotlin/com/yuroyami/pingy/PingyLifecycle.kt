package com.yuroyami.pingy

import kotlin.concurrent.Volatile

/**
 * The bridge platform shells use to pause and resume monitoring.
 *
 * Each shell owns its own foreground signal (Android activity callbacks, an iOS
 * scene phase, a desktop window listener), but they all need the same view
 * model. Registering it once here keeps that wiring in one place instead of
 * every shell reaching for a different global.
 */
object PingyLifecycle {
    @Volatile
    var viewmodel: PingyViewmodel? = null
        internal set

    /**
     * Write pending preference edits now.
     *
     * Exposed here rather than making shells reach for the view model directly:
     * `PingyViewmodel` extends `androidx.lifecycle.ViewModel`, which is an
     * implementation dependency of this module and therefore not on a shell's
     * compile classpath. A shell should not need that type just to say "I am
     * about to go away".
     */
    fun flushPendingWrites() {
        viewmodel?.flushNow()
    }

    /** Stop every engine and release its socket. Safe before the UI exists. */
    suspend fun pauseMonitoring() {
        viewmodel?.pauseMonitoring()
    }

    /** Restart exactly what [pauseMonitoring] stopped. */
    fun resumeMonitoring() {
        viewmodel?.resumeMonitoring()
    }
}
