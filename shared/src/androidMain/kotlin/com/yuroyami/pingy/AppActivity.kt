package com.yuroyami.pingy

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.yuroyami.pingy.ui.adam.AdamScreenUI

class AppActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        /** Further changing UI and Theme */
        window.statusBarColor = Color.LightGray.toArgb()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        /** Jetpack Compose */
        setContent {
            AdamScreenUI()
        }
    }
}