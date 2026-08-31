package com.yuroyami.pingy

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Objective-C visible handle so the SwiftUI shell can bind monitoring to the
 * scene phase. Swift cannot call a Kotlin `object` or a suspend function
 * directly, so this exposes plain instance methods over [PingyLifecycle].
 */
class PingyLifecycleBridge {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    /** Stop every engine and release its socket. Safe before the VM exists. */
    fun pause() {
        val vm = PingyLifecycle.viewmodel ?: return
        vm.flushNow()
        scope.launch { vm.pauseMonitoring() }
    }

    /** Restart exactly what [pause] stopped. */
    fun resume() {
        PingyLifecycle.viewmodel?.resumeMonitoring()
    }
}
