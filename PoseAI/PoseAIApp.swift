import SwiftUI

@main
struct PoseAIApp: App {
    // 用 AppStorage 持久化「是否已看过引导」，只展示一次
    @AppStorage("hasSeenOnboarding") private var hasSeenOnboarding: Bool = false

    // MARK: - StoreKit 2 内购管理器（全局单例注入）
    @StateObject private var storeManager = StoreManager()

    var body: some Scene {
        WindowGroup {
            ContentView()
                .preferredColorScheme(.dark) // 强制深色模式，摄像头场景下更美观
                .environmentObject(storeManager) // 注入到整个视图树
                .fullScreenCover(isPresented: .init(
                    get: { !hasSeenOnboarding },
                    set: { if !$0 { hasSeenOnboarding = true } }
                )) {
                    OnboardingView(isPresented: .init(
                        get: { !hasSeenOnboarding },
                        set: { if !$0 { hasSeenOnboarding = true } }
                    ))
                }
        }
        .modelContainer(for: [ShootingRecord.self, CustomPlan.self])
    }
}
