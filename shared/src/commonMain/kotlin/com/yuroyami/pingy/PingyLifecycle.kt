package com.yuroyami.pingy

import kotlin.concurrent.Volatile

/**
 * The bridge platform shells use to pause and resume monitoring.
 *
 * Each shell owns its own signal for this: Android activity callbacks, an iOS
 * scene phase, and on desktop the window's minimized state. They all need the
 * same view model, so registering it once here keeps that wiring in one place
 * instead of every shell reaching for a different global.
 *
 * Desktop sleep and wake are not detected. A machine suspending shows up as an
 * ordinary gap in the history rather than a session boundary.
 */
object PingyLifecycle {
    @Volatile
    var viewmodel: PingyViewmodel? = null
        internal set

    /**
     * Write pending preference edits and wait for the disk. Returns whether
     * they landed, so a shell that is about to exit can decide what to do.
     *
     * Exposed here rather than making shells reach for the view model directly:
     * `PingyViewmodel` extends `androidx.lifecycle.ViewModel`, which is an
     * implementation dependency of this module and therefore not on a shell's
     * compile classpath. A shell should not need that type just to say "I am
     * about to go away".
     */
    suspend fun flushPendingWrites(): Boolean = viewmodel?.flushAndWait() ?: true

    /** Same, without waiting. For transitions that cannot block, like Android onStop. */
    fun flushPendingWritesAsync() {
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
