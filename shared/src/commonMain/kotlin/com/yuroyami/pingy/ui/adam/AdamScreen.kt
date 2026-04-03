package com.yuroyami.pingy.ui.adam

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.yuroyami.pingy.PingyViewmodel
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

    CompositionLocalProvider(
         LocalViewmodel provides vm
    ) {
        MaterialTheme {
            NavDisplay(
                backStack = vm.backstack,
                onBack = {
                    //NoOp
                },
                entryDecorators = listOf(
                    rememberSaveableStateHolderNavEntryDecorator(),
                    rememberViewModelStoreNavEntryDecorator()
                ),
                entryProvider = entryProvider {
                    entry<Screen.Main> {
                        it.UI()
                    }
                }
            )
        }
    }
}