import androidx.compose.ui.window.ComposeUIViewController
import com.yuroyami.pingy.ui.ScreenUI
import platform.UIKit.UIViewController

fun MainViewController(): UIViewController = ComposeUIViewController { ScreenUI() }
