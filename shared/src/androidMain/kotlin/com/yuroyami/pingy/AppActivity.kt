package com.yuroyami.pingy

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.yuroyami.pingy.ui.adam.AdamScreenUI
import com.yuroyami.pingy.utils.applyActivityUiProperties
import com.yuroyami.pingy.utils.pingyAppContext

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
}