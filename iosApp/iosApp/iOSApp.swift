import SwiftUI
import shared

@main
struct iOSApp: App {
    init() {
        UIApplication.shared.isIdleTimerDisabled = true //Keep screen on
    }
    
    var body: some Scene {
        WindowGroup {
            MainScreen().ignoresSafeArea(.all)
        }
    }
}


struct MainScreen: UIViewControllerRepresentable {

    func makeUIViewController(context: Context) -> UIViewController {
        MainKt.MainViewController()
    }
    
    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
