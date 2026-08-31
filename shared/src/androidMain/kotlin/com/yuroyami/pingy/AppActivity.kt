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
     * Nothing used to stop the engines when the app left the screen, so probes
     * kept streaming, the radio kept waking and the battery kept draining with
     * the app out of sight and no way for the user to tell. Pending preference
     * edits are flushed here too, since the process may not come back.
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