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
}
