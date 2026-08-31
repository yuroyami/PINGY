package com.yuroyami.pingy

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.yuroyami.pingy.ui.adam.AdamScreenUI
import com.yuroyami.pingy.utils.applyActivityUiProperties
import com.yuroyami.pingy.utils.pingyAppContext
import kotlinx.coroutines.launch

class AppActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        pingyAppContext = applicationContext

        /* Tweaking some window UI elements */
        applyActivityUiProperties()

        /** Jetpack Compose */
        setContent {
            AdamScreenUI()
        }
    }

    /**
     * Monitoring is bound to the foreground.
     *
     * Engines stop here. Left running, they would keep streaming probes and
     * waking the radio while the app is off screen, draining the battery with
     * nothing on screen to hint at it. Pending preference edits are flushed
     * here too, since the process may not come back.
     */
    override fun onStop() {
        super.onStop()
        PingyLifecycle.viewmodel?.let { vm ->
            vm.flushNow()
            lifecycleScope.launch { vm.pauseMonitoring() }
        }
    }

    override fun onStart() {
        super.onStart()
        PingyLifecycle.viewmodel?.resumeMonitoring()
    }
}