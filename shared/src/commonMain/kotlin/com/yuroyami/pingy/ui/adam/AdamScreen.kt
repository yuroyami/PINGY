package com.yuroyami.pingy.ui.adam

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import cafe.adriel.lyricist.ProvideStrings
import cafe.adriel.lyricist.rememberStrings
import com.yuroyami.pingy.PingyViewmodel
import com.yuroyami.pingy.i18n.DefaultLanguageTag
import com.yuroyami.pingy.i18n.LocalStrings
import com.yuroyami.pingy.i18n.PingyLocales
import com.yuroyami.pingy.i18n.RtlLanguageTags
import com.yuroyami.pingy.ui.Screen

/** Provides access to the global [PingyViewmodel] instance shared across the app. */
val LocalViewmodel = compositionLocalOf<PingyViewmodel> { error("No Viewmodel provided yet") }

@Composable
fun AdamScreenUI() {
    val vm = viewModel(
        key = "pingyVM",
        modelClass = PingyViewmodel::class,
        factory = viewModelFactory { initializer { PingyViewmodel() } }
    )

    val lyricist = rememberStrings(PingyLocales, DefaultLanguageTag)

    // Compose derives layout direction from the platform locale on Android, but
    // not uniformly elsewhere. Deriving it from the language actually in use
    // keeps Arabic, Persian and Hebrew mirrored on every target.
    val layoutDirection =
        if (lyricist.languageTag.substringBefore('-') in RtlLanguageTags) LayoutDirection.Rtl
        else LayoutDirection.Ltr

    // The view model raises notices before any UI exists, so it cannot read the
    // CompositionLocal. Keep it in step with whatever locale is active.
    vm.strings = PingyLocales[lyricist.languageTag]
        ?: PingyLocales[lyricist.languageTag.substringBefore('-')]
        ?: PingyLocales.getValue(DefaultLanguageTag)

    ProvideStrings(lyricist, LocalStrings) {
        CompositionLocalProvider(
            LocalViewmodel provides vm,
            LocalLayoutDirection provides layoutDirection,
        ) {
            MaterialTheme {
                NavDisplay(
                    backStack = vm.backstack,
                    onBack = {
                        if (vm.backstack.size > 1) vm.backstack.removeAt(vm.backstack.lastIndex)
                    },
                    entryDecorators = listOf(
                        rememberSaveableStateHolderNavEntryDecorator(),
                        rememberViewModelStoreNavEntryDecorator()
                    ),
                    entryProvider = entryProvider {
                        entry<Screen.Main> { it.UI() }
                        entry<Screen.About> { it.UI() }
                        entry<Screen.Legal> { it.UI() }
                    }
                )
            }
        }
    }
}
