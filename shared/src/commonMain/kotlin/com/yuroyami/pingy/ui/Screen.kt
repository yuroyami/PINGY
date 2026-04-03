package com.yuroyami.pingy.ui

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavKey
import com.yuroyami.pingy.ui.main.MainScreenUI
import kotlinx.serialization.Serializable

sealed interface Screen : NavKey {

    @Composable
    fun UI()

    /**
     * The main screen of the app.
     */
    @Serializable
    data object Main : Screen {
        @Composable
        override fun UI() {
            MainScreenUI()
        }
    }

}