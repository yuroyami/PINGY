package com.yuroyami.pingy.ui

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavKey
import com.yuroyami.pingy.ui.about.AboutScreenUI
import com.yuroyami.pingy.ui.about.LegalScreenUI
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

    /** A propos: logo, version, credits. Reached by tapping the wordmark. */
    @Serializable
    data object About : Screen {
        @Composable
        override fun UI() {
            AboutScreenUI()
        }
    }

    /**
     * License, notices and privacy policy.
     *
     * Store policy expects a privacy route reachable inside the app, and an
     * AGPL binary has to carry its license with it. Neither existed before.
     */
    @Serializable
    data object Legal : Screen {
        @Composable
        override fun UI() {
            LegalScreenUI()
        }
    }
}
