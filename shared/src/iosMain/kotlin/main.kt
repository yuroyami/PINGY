@file:Suppress("UNUSED")
package com.yuroyami.pingy

import androidx.compose.ui.window.ComposeUIViewController
import com.yuroyami.pingy.ui.adam.AdamScreenUI
import platform.UIKit.UIViewController

fun MainViewController(): UIViewController = ComposeUIViewController { AdamScreenUI() }
