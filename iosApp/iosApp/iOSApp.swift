import SwiftUI
import shared

@main
struct iOSApp: App {
    // The idle timer is deliberately left alone. Disabling it here kept the
    // display awake for the entire lifetime of the app, with no user control
    // and no disclosure, which is a battery and thermal cost the product never
    // asked permission for.
    init() {}
    
    @Environment(\.scenePhase) private var scenePhase

    var body: some Scene {
        WindowGroup {
            // The dark surface deliberately extends edge to edge, otherwise the
            // system paints a white band above the status bar. Safe areas are
            // honoured inside Compose instead, which is where the layout lives:
            // see `systemBarsPadding()` and the navigation-bar inset in
            // MainScreenUI.
            MainScreen().ignoresSafeArea(.all)
        }
        // Monitoring follows the foreground. Nothing used to stop the engines
        // when the app was backgrounded, so probes kept streaming and the radio
        // kept waking with the app out of sight.
        // Single-parameter closure: the two-parameter form of onChange is
        // iOS 17+, and this app targets iOS 16.
        .onChange(of: scenePhase) { phase in
            switch phase {
            case .active:     PingyLifecycleBridge().resume()
            case .background: PingyLifecycleBridge().pause()
            default:          break
            }
        }
    }
}


struct MainScreen: UIViewControllerRepresentable {

    func makeUIViewController(context: Context) -> UIViewController {
        MainKt.MainViewController()
    }
    
    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
