package com.yuroyami.pingy.android

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.yuroyami.pingy.ui.ScreenUI

class AppActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        /** Further changing UI and Theme */
        window.statusBarColor = Color.LightGray.toArgb()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        /** Jetpack Compose */
        setContent {
//            val isDark = isSystemInDarkTheme()
//            val view = LocalView.current
//            val systemBarColor = Color.Transparent.toArgb()
//            LaunchedEffect(isDark) {
//                val window = (view.context as Activity).window
//                WindowCompat.setDecorFitsSystemWindows(window, false)
//                window.statusBarColor = systemBarColor
//                window.navigationBarColor = systemBarColor
//                WindowCompat.getInsetsController(window, window.decorView).apply {
//                    isAppearanceLightStatusBars = isDark
//                    isAppearanceLightNavigationBars = isDark
//                }
//            }
            ScreenUI()
        }
    }
}

