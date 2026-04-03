import androidx.compose.ui.window.ComposeUIViewController
import com.yuroyami.pingy.ui.main.ScreenUI
import platform.UIKit.UIViewController

fun MainViewController(): UIViewController = ComposeUIViewController { ScreenUI() }
