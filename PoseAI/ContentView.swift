import SwiftUI
import SwiftData
import AVFoundation
import AVFAudio
import StoreKit

// MARK: - 品牌设计常量（参考图：黑白高对比 + 金黄点缀）
enum Design {
    static let accent = Color(red: 1.0, green: 0.84, blue: 0.25)         // #FFD53F 金黄
    static let accentGlow = Color(red: 1.0, green: 0.84, blue: 0.25).opacity(0.35)
    static let success = Color(red: 1.0, green: 0.84, blue: 0.25)       // 金黄
    static let successGlow = Color(red: 1.0, green: 0.84, blue: 0.25).opacity(0.35)
    static let danger = Color(red: 1.0, green: 0.35, blue: 0.35)         // 珊瑚红
    static let surface = Color.black.opacity(0.45)
    static let surfaceStrong = Color.black.opacity(0.65)
    static let border = Color.white.opacity(0.12)
    static let borderActive = Color.white.opacity(0.55)
    static let textPrimary = Color.white
    static let textSecondary = Color.white.opacity(0.50)
    static let overlayBg = Color.black.opacity(0.55)
    static let deepSpaceBlack = Color.black
    static let blur: Material = .ultraThinMaterial
    static let cornerCard: CGFloat = 16
    static let cornerBadge: CGFloat = 8
}

// MARK: - 拍摄模式（底部栏视觉状态）
enum CaptureMode: String, CaseIterable {
    case guide = "智能导拍"
    case photo = "照片"
    case portrait = "人像"
    case panorama = "全景"
}

struct ContentView: View {

    // MARK: - SwiftData
    @Environment(\.modelContext) private var modelContext
    @Query(sort: \ShootingRecord.createdAt, order: .reverse) private var recentRecords: [ShootingRecord]
    @Query(sort: \CustomPlan.createdAt, order: .reverse) private var storedCustomPlans: [CustomPlan]

    // MARK: - ViewModel
    @StateObject private var vm = ShootingViewModel()

    // MARK: - StoreKit
    @EnvironmentObject var storeManager: StoreManager

    // MARK: - UI 状态
    @State private var scanPulse: Bool = false
    @State private var scanRotation: Double = 0

    // MARK: - 保存照片记录
    private func savePhotoRecord(image: UIImage, filter: PhotoFilter) {
        Task {
            do {
                if let localId = try await PhotoAlbumUtil.shared.saveToAlbumAndGetIdentifier(image: image) {
                    await MainActor.run {
                        let record = ShootingRecord(
                            sceneRawValue: vm.scene.rawValue,
                            planId: vm.currentPlan?.id ?? "unknown",
                            planName: vm.currentPlan?.poseName ?? "未知",
                            matchScore: Int(vm.score),
                            localIdentifier: localId,
                            appliedFilterRawValue: filter == .original ? nil : filter.rawValue
                        )
                        modelContext.insert(record)
                    }
                }
            } catch {
                print("保存相册失败", error)
            }
        }
    }

    // MARK: - 倒计时覆盖层
    @ViewBuilder
    private var countdownOverlay: some View {
        if vm.countdown > 0 {
            Text("\(vm.countdown)")
                .font(.system(size: 130, weight: .ultraLight, design: .rounded))
                .foregroundColor(.white.opacity(0.9))
                .shadow(color: .black.opacity(0.35), radius: 24)
                .transition(.scale(scale: 1.4).combined(with: .opacity))
                .id("photo_\(vm.countdown)")
                .animation(.spring(response: 0.35, dampingFraction: 0.6), value: vm.countdown)
                .allowsHitTesting(false)
        }
    }

    @ViewBuilder
    private var recordCountdownOverlay: some View {
        if vm.isRecordingMode && vm.recordCountdown > 0 {
            Text("\(vm.recordCountdown)")
                .font(.system(size: 130, weight: .ultraLight, design: .rounded))
                .foregroundColor(Design.success.opacity(0.9))
                .shadow(color: .black.opacity(0.35), radius: 24)
                .transition(.scale(scale: 1.4).combined(with: .opacity))
                .id("record_\(vm.recordCountdown)")
                .animation(.spring(response: 0.35, dampingFraction: 0.6), value: vm.recordCountdown)
                .allowsHitTesting(false)
        }
    }

    // MARK: - Body
    var body: some View {
        ZStack {
            // 0. 摄像头层
            cameraLayer

            // 1. 沉浸模式手势层
            Color.clear
                .contentShape(Rectangle())
                .onTapGesture { vm.toggleImmersiveMode() }

            // 2. 构图辅助线（全屏三分法网格）
            if vm.isSceneReady && !vm.isImmersiveMode && vm.captureMode == .guide {
                CompositionGuideLines(composition: vm.currentPlan?.composition)
            }

            // 3. 场景扫描引导
            if !vm.isSceneReady {
                sceneScanningOverlay
            }

            // 4. 剪影引导（仅智能导拍模式）
            if vm.captureMode == .guide, let plan = vm.currentPlan {
                if vm.requiresProUnlock {
                    paywallTeaser
                } else {
                    if plan.secondaryPosePoints != nil {
                        let width = UIScreen.main.bounds.width
                        SilhouetteGuideOverlay(
                            isAligned: Binding(get: { vm.isReady }, set: { _ in }),
                            plan: plan,
                            bodyBoundingBox: vm.detectedPoses.indices.contains(0) ? vm.detectedPoses[0].bbox : nil,
                            forceOffset: -width * 0.18
                        )
                        .opacity(vm.isImmersiveMode ? 0.35 : 1.0)
                        .transition(.opacity.animation(.easeInOut(duration: 0.5)))
                        .animation(.easeInOut(duration: 0.4), value: vm.currentPlanIndex)

                        SilhouetteGuideOverlay(
                            isAligned: Binding(get: { vm.isReady }, set: { _ in }),
                            plan: plan,
                            bodyBoundingBox: vm.detectedPoses.indices.contains(1) ? vm.detectedPoses[1].bbox : nil,
                            forceOffset: width * 0.18
                        )
                        .opacity(vm.isImmersiveMode ? 0.35 : 1.0)
                        .transition(.opacity.animation(.easeInOut(duration: 0.5)))
                        .animation(.easeInOut(duration: 0.4), value: vm.currentPlanIndex)
                    } else {
                        SilhouetteGuideOverlay(
                            isAligned: Binding(get: { vm.isReady }, set: { _ in }),
                            plan: plan,
                            bodyBoundingBox: vm.detectedPoses.first?.bbox
                        )
                        .opacity(vm.isImmersiveMode ? 0.35 : 1.0)
                        .transition(.opacity.animation(.easeInOut(duration: 0.5)))
                        .animation(.easeInOut(duration: 0.4), value: vm.currentPlanIndex)
                    }
                }
            }

            // 5. AR 脚印（全身模式，仅导拍）
            if vm.captureMode == .guide, !vm.requiresProUnlock && !vm.isImmersiveMode,
               vm.isSceneReady, vm.currentPlan?.frameRatio == .fullBody {
                arFootprintsOverlay
            }

            // 6. 人脸对焦框
            if !vm.isImmersiveMode, let _ = vm.detectedPoses.first, vm.captureMode == .guide {
                faceFocusFrame
            }

            // 7. 顶部栏
            if !vm.isImmersiveMode {
                VStack(spacing: 0) {
                    topBar
                        .padding(.top, 54)
                        .padding(.horizontal, 16)
                    Spacer()
                }
                .transition(.move(edge: .top).combined(with: .opacity))
            }

            // 8. 左侧提示
            if !vm.isImmersiveMode && vm.isSceneReady && vm.captureMode == .guide {
                HStack {
                    leftGuidePanel
                        .padding(.leading, 12)
                        .padding(.top, 120)
                    Spacer()
                }
                .transition(.move(edge: .leading).combined(with: .opacity))
            }

            // 9. 构图提示浮层（仅导拍模式）
            if vm.captureMode == .guide, !vm.requiresProUnlock, vm.showCompositionTip, let plan = vm.currentPlan, !vm.isImmersiveMode {
                compositionTipOverlay(plan: plan)
            }

            // 10. AI 构图推荐（仅导拍模式展示）
            if let aiTip = vm.aiSuggestion, !vm.isImmersiveMode, vm.captureMode == .guide {
                aiAdvisorBanner(text: aiTip)
                    .gesture(
                        DragGesture().onEnded { val in
                            if val.translation.height < -10 {
                                withAnimation(.easeIn(duration: 0.3)) {
                                    vm.aiSuggestion = nil
                                }
                            }
                        }
                    )
            }

            // 11. 暗光提示 Banner
            if vm.isLowLight && vm.isSceneReady && !vm.isImmersiveMode {
                lowLightBanner
            }

            // 12. 底部区域（状态条 + 方案卡片 + 底栏）
            VStack(spacing: 0) {
                Spacer()
                bottomArea
            }

            // 13. 俯拍警告
            if vm.manager.devicePitch < -0.35 && !vm.isImmersiveMode {
                pitchWarningOverlay
            }

            // 14. 留白提醒
            if vm.showSpaceTip && vm.manager.devicePitch >= -0.35 && !vm.showCompositionTip && !vm.isImmersiveMode {
                spaceTipOverlay
            }

            // 14b. 全景模式横向移动提示
            if vm.captureMode == .panorama && !vm.isImmersiveMode {
                panoramaHintOverlay
            }

            // 15. 多机位角度仪表盘
            if let plan = vm.currentPlan, let multi = plan.multiAngles,
               vm.activeAngleIndex < multi.count, multi[vm.activeAngleIndex].requiredPitch != nil,
               !vm.isImmersiveMode {
                angleGuideOverlay(reqPitch: multi[vm.activeAngleIndex].requiredPitch!)
            }

            // 16. Vlog 提词器
            if !vm.displayVlogText.isEmpty && !vm.isImmersiveMode {
                vlogTextOverlay
            }

            // 17. 屏幕柔边补光带
            if vm.isLowLight && !vm.isImmersiveMode {
                RoundedRectangle(cornerRadius: 12)
                    .stroke(Color(red: 1.0, green: 0.95, blue: 0.88).opacity(0.35), lineWidth: 50)
                    .blur(radius: 30)
                    .ignoresSafeArea()
                    .allowsHitTesting(false)
                    .transition(.opacity.animation(.easeInOut(duration: 1.0)))
            }

            // 18. 快门闪光
            if vm.showShutterFlash {
                Color(red: 1.0, green: 0.95, blue: 0.88)
                    .ignoresSafeArea()
                    .opacity(0.9)
                    .transition(.opacity)
            }

            // 19. 倒计时大数字
            countdownOverlay
            recordCountdownOverlay
        }
        .ignoresSafeArea()
        .onAppear {
            vm.storeManager = storeManager
            vm.bind()
            vm.startScanTimeout()
            vm.customShootingPlans = storedCustomPlans.map { $0.asShootingPlan }
            startScanAnimations()
        }
        .onDisappear { vm.manager.stop() }
        .onReceive(NotificationCenter.default.publisher(for: UIApplication.willResignActiveNotification)) { _ in
            vm.manager.stop()
            vm.stopSpeaking()
        }
        .onReceive(NotificationCenter.default.publisher(for: UIApplication.didBecomeActiveNotification)) { _ in
            vm.evaluateCameraState()
        }
        .onChange(of: vm.isReviewingPhotos) { _ in vm.evaluateCameraState() }
        .onChange(of: vm.isReviewingVlog) { _ in vm.evaluateCameraState() }
        .onChange(of: vm.showPaywall) { _ in vm.evaluateCameraState() }
        .onChange(of: vm.showSessionGallery) { _ in vm.evaluateCameraState() }
        .onChange(of: vm.showGuide) { _ in vm.evaluateCameraState() }
        .onChange(of: storedCustomPlans) { newValue in
            vm.customShootingPlans = newValue.map { $0.asShootingPlan }
        }
        .sheet(isPresented: $vm.showGuide) {
            PoseGuideSheet(plan: vm.currentPlan, scene: vm.scene)
        }
        .fullScreenCover(isPresented: $vm.isReviewingPhotos) {
            PhotoPreviewView(images: vm.burstImages) { selectedImage, appliedFilter in
                savePhotoRecord(image: selectedImage, filter: appliedFilter)
                UINotificationFeedbackGenerator().notificationOccurred(.success)
                vm.isReviewingPhotos = false
                vm.checkAndRequestReview()
            } onRetake: {
                vm.isReviewingPhotos = false
            }
            .environmentObject(storeManager)
        }
        .fullScreenCover(isPresented: $vm.isReviewingVlog) {
            if let outputURL = vm.exportedVlogURL {
                VideoPreviewView(videoURL: outputURL) {
                    UINotificationFeedbackGenerator().notificationOccurred(.success)
                    vm.isReviewingVlog = false
                    vm.cleanupVlogTempFiles()
                    vm.checkAndRequestReview()
                } onRetake: {
                    vm.isReviewingVlog = false
                    vm.cleanupVlogTempFiles()
                }
            } else {
                Text("Vlog 分镜加载失败？")
            }
        }
        .sheet(isPresented: $vm.showSessionGallery) {
            HistoryGalleryView()
        }
        .fullScreenCover(isPresented: $vm.showPaywall) {
            PaywallView()
                .environmentObject(storeManager)
        }
        .sheet(isPresented: Binding(
            get: { vm.pointsToSave != nil },
            set: { if !$0 { vm.pointsToSave = nil } }
        )) {
            if let pts = vm.pointsToSave {
                SaveCustomPlanView(points: pts) {
                    vm.pointsToSave = nil
                    DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
                        vm.selectPlan(at: 0)
                    }
                }
            }
        }
    }

    // MARK: - 扫描动画启动（确保只执行一次）
    @State private var scanAnimationsStarted = false

    private func startScanAnimations() {
        guard !scanAnimationsStarted else { return }
        scanAnimationsStarted = true
        scanPulse = true
        withAnimation(.linear(duration: 1.6).repeatForever(autoreverses: false)) {
            scanRotation = 360
        }
    }

    // MARK: - 摄像头层
    private var cameraLayer: some View {
        Group {
            if vm.manager.authorizationStatus == .authorized {
                CameraPreview(manager: vm.manager).ignoresSafeArea()
            } else if vm.manager.authorizationStatus == .denied {
                permissionDeniedView
            } else {
                Color.black.ignoresSafeArea()
            }
        }
    }

    private var permissionDeniedView: some View {
        VStack(spacing: 28) {
            ZStack {
                Circle()
                    .fill(Design.danger.opacity(0.15))
                    .frame(width: 90, height: 90)
                Image(systemName: "camera.slash.fill")
                    .font(.system(size: 36, weight: .light))
                    .foregroundColor(Design.danger)
            }
            VStack(spacing: 8) {
                Text("需要摄像头权限")
                    .font(.system(size: 18, weight: .semibold))
                    .foregroundColor(.white)
                Text("PoseAI 需要访问摄像头\n才能实时检测姿势和场景")
                    .font(.system(size: 14))
                    .foregroundColor(Design.textSecondary)
                    .multilineTextAlignment(.center)
            }
            Button {
                if let url = URL(string: UIApplication.openSettingsURLString) {
                    UIApplication.shared.open(url)
                }
            } label: {
                HStack(spacing: 8) {
                    Image(systemName: "gear")
                        .font(.system(size: 15, weight: .medium))
                    Text("去设置中开启")
                        .font(.system(size: 15, weight: .semibold))
                }
                .foregroundColor(.black)
                .padding(.horizontal, 28)
                .padding(.vertical, 14)
                .background(Design.accent, in: Capsule())
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color.black)
    }

    // MARK: - 顶部信息栏（参考图风格）
    private var topBar: some View {
        HStack(spacing: 0) {
            // 左侧：返回/菜单按钮
            Button {
                vm.showGuide = true
            } label: {
                ZStack {
                    Circle()
                        .fill(Design.surface)
                        .frame(width: 38, height: 38)
                    Image(systemName: "chevron.left")
                        .font(.system(size: 16, weight: .semibold))
                        .foregroundColor(.white)
                }
            }

            Spacer()

            // 中央：AI 状态胶囊（丰富版：显示场景+方案信息）
            HStack(spacing: 8) {
                Circle()
                    .fill(vm.isSceneReady ? Design.success : Design.accent)
                    .frame(width: 8, height: 8)
                    .opacity(vm.isSceneReady ? 1.0 : (scanPulse ? 0.4 : 1.0))
                    .animation(vm.isSceneReady ? .none : Animation.easeInOut(duration: 0.8).repeatForever(autoreverses: true), value: scanPulse)

                if vm.isSceneReady, let plan = vm.currentPlan {
                    VStack(spacing: 1) {
                        Text(vm.scene.displayName)
                            .font(.system(size: 10, weight: .medium))
                            .foregroundColor(Color.white.opacity(0.6))
                        if let vlog = plan.vlogScript, vm.activeVlogClipIndex < vlog.clips.count {
                            HStack(spacing: 4) {
                                Circle()
                                    .fill(vm.isVlogRecording ? Color.red : Color.gray)
                                    .frame(width: 6, height: 6)
                                    .opacity(vm.isVlogRecording ? 1.0 : 0.5)
                                    .animation(vm.isVlogRecording ? Animation.easeInOut(duration: 0.6).repeatForever(autoreverses: true) : .default, value: vm.isVlogRecording)
                                Text("分镜 \(vm.activeVlogClipIndex + 1)/\(vlog.clips.count)")
                                    .font(.system(size: 12, weight: .bold))
                            }
                            .foregroundColor(Design.danger)
                        } else if let seq = plan.sequence, vm.activeSequenceIndex < seq.count {
                            Text("[\(vm.activeSequenceIndex + 1)/\(seq.count)] \(seq[vm.activeSequenceIndex].emoji) \(seq[vm.activeSequenceIndex].title)")
                                .font(.system(size: 12, weight: .bold))
                                .foregroundColor(Design.success)
                        } else if let multi = plan.multiAngles, vm.activeAngleIndex < multi.count {
                            Text("[\(vm.activeAngleIndex + 1)/\(multi.count)] 📷 \(multi[vm.activeAngleIndex].title)")
                                .font(.system(size: 12, weight: .bold))
                                .foregroundColor(Design.danger)
                        } else {
                            Text("\(plan.poseEmoji) \(plan.poseName)")
                                .font(.system(size: 12, weight: .bold))
                                .foregroundColor(.white)
                        }
                    }
                } else {
                    Text(vm.isSceneReady ? "AI 导拍中" : "识别场景中…")
                        .font(.system(size: 14, weight: .medium))
                        .foregroundColor(.white)
                }
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 6)
            .background(Design.surfaceStrong, in: Capsule())
            .overlay(Capsule().stroke(Design.border, lineWidth: 1))

            Spacer()

            // 右侧：分数环 + 倒计时 + 切换摄像头
            HStack(spacing: 10) {
                // 倒计时
                Button { vm.cycleTimer() } label: {
                    ZStack {
                        Circle()
                            .fill(vm.timerSeconds > 0 ? Design.accent.opacity(0.18) : Design.surface)
                            .frame(width: 36, height: 36)
                            .overlay(Circle().stroke(vm.timerSeconds > 0 ? Design.accent.opacity(0.5) : Design.border, lineWidth: 1))
                        if vm.timerSeconds == 0 {
                            Image(systemName: "timer")
                                .font(.system(size: 14, weight: .medium))
                                .foregroundColor(Design.textSecondary)
                        } else {
                            Text("\(vm.timerSeconds)s")
                                .font(.system(size: 12, weight: .bold))
                                .foregroundColor(Design.accent)
                        }
                    }
                }
                .accessibilityLabel("倒计时\(vm.timerSeconds)秒")

                // 分数环
                if vm.isSceneReady {
                    scoreRing
                }

                // 切换摄像头
                Button { vm.manager.isFront.toggle() } label: {
                    ZStack {
                        Circle()
                            .fill(Design.surface)
                            .frame(width: 38, height: 38)
                        Image(systemName: "arrow.triangle.2.circlepath.camera")
                            .font(.system(size: 16, weight: .medium))
                            .foregroundColor(.white)
                    }
                }
                .accessibilityLabel("切换\(vm.manager.isFront ? "后置" : "前置")摄像头")
            }
        }
        .animation(.spring(response: 0.45, dampingFraction: 0.75), value: vm.isSceneReady)
    }

    // MARK: - 分数环
    private var scoreRing: some View {
        ZStack {
            if vm.isReady {
                Circle()
                    .stroke(Design.successGlow, lineWidth: 10)
                    .frame(width: 46, height: 46)
                    .blur(radius: 6)
            }

            Circle()
                .stroke(Color.white.opacity(0.12), lineWidth: 3)
                .frame(width: 40, height: 40)

            Circle()
                .trim(from: 0, to: vm.score / 100)
                .stroke(
                    AngularGradient(
                        colors: vm.isReady ? [Design.success, Design.success.opacity(0.6)] : vm.scoreArcColors,
                        center: .center,
                        startAngle: .degrees(-90),
                        endAngle: .degrees(270)
                    ),
                    style: StrokeStyle(lineWidth: 3, lineCap: .round)
                )
                .frame(width: 40, height: 40)
                .rotationEffect(.degrees(-90))
                .animation(.linear(duration: 0.12), value: vm.score)

            Text("\(Int(vm.score))")
                .font(.system(size: 11, weight: .black, design: .rounded))
                .foregroundColor(.white)
        }
        .frame(width: 46, height: 46)
        .scaleEffect(vm.isReady ? 1.08 : 1.0)
        .animation(.spring(response: 0.3, dampingFraction: 0.55), value: vm.isReady)
        .accessibilityLabel("匹配度 \(Int(vm.score))%")
    }

    // MARK: - 左侧提示面板（参考图风格，动态状态）
    private var leftGuidePanel: some View {
        VStack(alignment: .leading, spacing: 10) {
            if let composition = vm.currentPlan?.composition {
                let aligned = vm.isReady
                HStack(spacing: 8) {
                    Image(systemName: aligned ? "checkmark.circle.fill" : composition.icon)
                        .font(.system(size: 12, weight: .medium))
                        .foregroundColor(aligned ? Design.success : .white.opacity(0.8))
                    Text(aligned ? "构图已对齐" : composition.voiceHint)
                        .font(.system(size: 12, weight: aligned ? .bold : .medium))
                        .foregroundColor(aligned ? Design.success : .white.opacity(0.85))
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 8)
                .background(Design.surfaceStrong, in: Capsule())
                .overlay(
                    Capsule().stroke(
                        aligned ? Design.success.opacity(0.6) : Design.border,
                        lineWidth: aligned ? 1.5 : 1
                    )
                )
                .animation(.spring(response: 0.25, dampingFraction: 0.6), value: aligned)
            }

            let isLevel = abs(vm.manager.deviceRoll) < 0.1
            HStack(spacing: 8) {
                Image(systemName: isLevel ? "level.fill" : "level")
                    .font(.system(size: 12, weight: .medium))
                    .foregroundColor(isLevel ? Design.success : .white.opacity(0.8))
                Text(isLevel ? "手机已水平" : "手机保持水平")
                    .font(.system(size: 12, weight: isLevel ? .bold : .medium))
                    .foregroundColor(isLevel ? Design.success : .white.opacity(0.85))
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 8)
            .background(Design.surfaceStrong, in: Capsule())
            .overlay(
                Capsule().stroke(
                    isLevel ? Design.success.opacity(0.6) : Design.border,
                    lineWidth: isLevel ? 1.5 : 1
                )
            )
            .animation(.spring(response: 0.25, dampingFraction: 0.6), value: isLevel)
        }
    }

    // MARK: - 人脸对焦框（黄色角括号，跟随检测到的人脸）
    private var faceFocusFrame: some View {
        GeometryReader { geo in
            let w = geo.size.width
            let h = geo.size.height
            let cornerLen: CGFloat = 14
            let thickness: CGFloat = 2.5
            let color = vm.isReady ? Design.success : Design.accent

            let (centerX, centerY, boxW, boxH) = resolveFaceBox(screenW: w, screenH: h)

            Canvas { ctx, _ in
                let corners: [(CGPoint, CGPoint, CGPoint)] = [
                    (CGPoint(x: centerX - boxW/2, y: centerY - boxH/2 + cornerLen),
                     CGPoint(x: centerX - boxW/2, y: centerY - boxH/2),
                     CGPoint(x: centerX - boxW/2 + cornerLen, y: centerY - boxH/2)),
                    (CGPoint(x: centerX + boxW/2 - cornerLen, y: centerY - boxH/2),
                     CGPoint(x: centerX + boxW/2, y: centerY - boxH/2),
                     CGPoint(x: centerX + boxW/2, y: centerY - boxH/2 + cornerLen)),
                    (CGPoint(x: centerX - boxW/2, y: centerY + boxH/2 - cornerLen),
                     CGPoint(x: centerX - boxW/2, y: centerY + boxH/2),
                     CGPoint(x: centerX - boxW/2 + cornerLen, y: centerY + boxH/2)),
                    (CGPoint(x: centerX + boxW/2 - cornerLen, y: centerY + boxH/2),
                     CGPoint(x: centerX + boxW/2, y: centerY + boxH/2),
                     CGPoint(x: centerX + boxW/2, y: centerY + boxH/2 - cornerLen))
                ]
                for (a, b, c) in corners {
                    var p = Path()
                    p.move(to: a); p.addLine(to: b); p.addLine(to: c)
                    ctx.stroke(p, with: .color(color), style: StrokeStyle(lineWidth: thickness, lineCap: .round))
                }
            }
        }
        .ignoresSafeArea()
        .allowsHitTesting(false)
        .animation(.easeInOut(duration: 0.25), value: vm.detectedPoses.first?.bbox)
    }

    private func resolveFaceBox(screenW: CGFloat, screenH: CGFloat) -> (CGFloat, CGFloat, CGFloat, CGFloat) {
        guard let pose = vm.detectedPoses.first, pose.bbox.height > 0.05 else {
            return (screenW / 2, screenH / 2 - 20, 80, 100)
        }
        let bbox = pose.bbox
        let headRatio: CGFloat = 0.18
        let faceW = bbox.width * screenW * 0.75
        let faceH = bbox.height * screenH * headRatio
        let faceX = bbox.midX * screenW
        let faceY = (bbox.minY + headRatio * 0.5) * screenH
        return (faceX, faceY, max(60, faceW), max(75, faceH))
    }

    // MARK: - 场景扫描引导
    private var sceneScanningOverlay: some View {
        VStack {
            Spacer()
            VStack(spacing: 28) {
                ZStack {
                    Circle()
                        .stroke(Design.accent.opacity(scanPulse ? 0.0 : 0.35), lineWidth: 1.5)
                        .frame(width: scanPulse ? 220 : 160)
                        .animation(.easeOut(duration: 1.8).repeatForever(autoreverses: false), value: scanPulse)

                    Circle()
                        .stroke(Design.accent.opacity(scanPulse ? 0.0 : 0.2), lineWidth: 1)
                        .frame(width: scanPulse ? 190 : 140)
                        .animation(.easeOut(duration: 1.8).delay(0.3).repeatForever(autoreverses: false), value: scanPulse)

                    RoundedRectangle(cornerRadius: 20)
                        .stroke(Design.accent.opacity(0.8), lineWidth: 2)
                        .frame(width: 140, height: 190)

                    ScanCornerLines()
                        .frame(width: 140, height: 190)

                    VStack(spacing: 10) {
                        ZStack {
                            Circle()
                                .trim(from: 0, to: 0.25)
                                .stroke(
                                    AngularGradient(colors: [Design.accent, .clear], center: .center),
                                    style: StrokeStyle(lineWidth: 2.5, lineCap: .round)
                                )
                                .frame(width: 44, height: 44)
                                .rotationEffect(.degrees(scanRotation))

                            Image(systemName: "viewfinder")
                                .font(.system(size: 22, weight: .ultraLight))
                                .foregroundColor(Design.accent.opacity(0.7))
                        }

                        Text("识别场景中…")
                            .font(.system(size: 13, weight: .semibold))
                            .foregroundColor(.white)
                    }
                }

                VStack(spacing: 6) {
                    Text("将镜头对准拍摄背景")
                        .font(.system(size: 14, weight: .medium))
                        .foregroundColor(.white.opacity(0.85))
                    Text("咖啡馆 · 海边 · 森林")
                        .font(.system(size: 12))
                        .foregroundColor(Design.accent.opacity(0.7))
                        .tracking(2)
                }
            }
            .padding(.bottom, 200)
        }
    }

    // MARK: - AR 地面脚印
    private var arFootprintsOverlay: some View {
        VStack {
            Spacer()
            HStack(spacing: 36) {
                Image(systemName: "shoe.fill")
                    .resizable().scaledToFit().frame(width: 24)
                    .rotationEffect(.degrees(-12))
                    .foregroundColor(Design.accent.opacity(0.25))
                Image(systemName: "shoe.fill")
                    .resizable().scaledToFit().frame(width: 24)
                    .rotationEffect(.degrees(12))
                    .foregroundColor(Design.accent.opacity(0.25))
            }
            .padding(.bottom, 220)
        }
    }

    // MARK: - 底部整体区域
    private var bottomArea: some View {
        VStack(spacing: 0) {
            // 对齐状态提示条（参考图：人物下方，仅导拍）
            if vm.captureMode == .guide && vm.isSceneReady && vm.isReady && !vm.isImmersiveMode {
                HStack(spacing: 8) {
                    Image(systemName: "checkmark.circle.fill")
                        .font(.system(size: 14))
                        .foregroundColor(Design.accent)
                    Text(alignmentStatusText)
                        .font(.system(size: 13, weight: .medium))
                        .foregroundColor(.white.opacity(0.9))
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 8)
                .background(Design.surfaceStrong, in: Capsule())
                .overlay(Capsule().stroke(Design.accent.opacity(0.4), lineWidth: 1))
                .padding(.bottom, 10)
                .transition(.move(edge: .bottom).combined(with: .opacity))
                .animation(.spring(response: 0.3, dampingFraction: 0.6), value: vm.isReady)
            }

            // 方案选择器（仅导拍模式）
            if vm.captureMode == .guide && vm.isSceneReady && !vm.isImmersiveMode {
                planPickerSection
                    .padding(.bottom, 12)
                    .transition(.move(edge: .bottom).combined(with: .opacity))
            }

            // 底栏（磨砂玻璃）
            bottomBar
        }
        .animation(.spring(response: 0.5, dampingFraction: 0.8), value: vm.isSceneReady)
        .animation(.spring(response: 0.5, dampingFraction: 0.8), value: vm.isImmersiveMode)
    }

    // MARK: - 底栏（参考图风格）
    private var bottomBar: some View {
        VStack(spacing: 0) {
            // 模式选择文字
            HStack(spacing: 28) {
                ForEach(CaptureMode.allCases, id: \.self) { mode in
                    Text(mode.rawValue)
                        .font(.system(size: 15, weight: vm.captureMode == mode ? .bold : .medium))
                        .foregroundColor(vm.captureMode == mode ? Design.accent : Color.white.opacity(0.45))
                        .onTapGesture {
                            vm.setCaptureMode(mode)
                        }
                }
            }
            .padding(.top, 14)
            .padding(.bottom, 16)

            // 快门 + 两侧按钮
            HStack(spacing: 0) {
                // 左：最近照片缩略图
                Button {
                    vm.showSessionGallery = true
                } label: {
                    ZStack {
                        RoundedRectangle(cornerRadius: 10)
                            .fill(Design.surface)
                            .frame(width: 48, height: 48)
                            .overlay(RoundedRectangle(cornerRadius: 10).stroke(Design.border, lineWidth: 1))

                        if let r = recentRecords.first {
                            HistoryLastImageThumbnail(localIdentifier: r.localIdentifier)
                        } else {
                            Image(systemName: "photo.on.rectangle")
                                .font(.system(size: 18, weight: .medium))
                                .foregroundColor(.white)
                        }
                    }
                }
                .frame(maxWidth: .infinity)
                .accessibilityLabel("拍摄历史，\(recentRecords.count)张照片")

                // 中：快门按钮
                shutterButton
                    .onTapGesture {
                        // 依据当前模式执行：导拍=AI 逻辑；照片/人像=单张直拍；全景=连拍宽幅素材
                        vm.captureWithCurrentMode()
                    }
                    .accessibilityLabel(vm.isReady ? "拍照，姿势已对齐" : "拍照")
                    .accessibilityAddTraits(.isButton)

                // 右：小贴士按钮
                Button { vm.showGuide = true } label: {
                    HStack(spacing: 6) {
                        Image(systemName: "lightbulb")
                            .font(.system(size: 14, weight: .medium))
                        Text("小贴士")
                            .font(.system(size: 13, weight: .medium))
                    }
                    .foregroundColor(.white.opacity(0.85))
                    .padding(.horizontal, 14)
                    .padding(.vertical, 8)
                    .background(Design.surfaceStrong, in: Capsule())
                    .overlay(Capsule().stroke(Design.border, lineWidth: 1))
                }
                .frame(maxWidth: .infinity)
            }
            .padding(.bottom, 36)
            .padding(.horizontal, 24)
        }
        .background(
            Group {
                if vm.isImmersiveMode {
                    LinearGradient(
                        colors: [Color.clear, Color.black.opacity(0.4)],
                        startPoint: .top, endPoint: .bottom
                    )
                } else {
                    Rectangle()
                        .fill(.ultraThinMaterial)
                        .overlay(
                            LinearGradient(
                                colors: [Color.black.opacity(0.0), Color.black.opacity(0.55)],
                                startPoint: .top, endPoint: .bottom
                            )
                        )
                        .overlay(
                            Rectangle()
                                .frame(height: 0.5)
                                .foregroundColor(Color.white.opacity(0.1)),
                            alignment: .top
                        )
                }
            }
        )
    }

    // MARK: - 方案选择器
    private var planPickerSection: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(alignment: .bottom, spacing: 10) {
                // 录制新姿势入口
                Button(action: {
                    vm.startRecordingCustomPlan()
                }) {
                    VStack(spacing: 6) {
                        Image(systemName: vm.isRecordingMode ? "record.circle" : "plus.viewfinder")
                            .font(.system(size: 18))
                            .foregroundColor(vm.isRecordingMode ? Design.danger : .white)
                        Text(vm.isRecordingMode ? "捕捉中..." : "录制专属")
                            .font(.system(size: 13, weight: .bold))
                            .foregroundColor(vm.isRecordingMode ? Design.danger : .white)
                    }
                    .padding(.horizontal, 14)
                    .padding(.vertical, 10)
                    .background(Color.white.opacity(0.08))
                    .cornerRadius(12)
                    .overlay(
                        RoundedRectangle(cornerRadius: 12)
                            .stroke(vm.isRecordingMode ? Design.danger : Color.white.opacity(0.3), lineWidth: 1)
                    )
                }

                ForEach(Array(vm.availablePlans.enumerated()), id: \.element.id) { idx, plan in
                    PlanCard(plan: plan, isSelected: idx == vm.currentPlanIndex)
                        .onTapGesture { vm.selectPlan(at: idx) }
                        .accessibilityLabel("\(plan.poseName), \(plan.composition.displayName)构图")
                        .accessibilityAddTraits(idx == vm.currentPlanIndex ? .isSelected : [])
                }
            }
            .padding(.horizontal, 20)
        }
    }

    // MARK: - 对齐状态文案
    private var alignmentStatusText: String {
        guard let plan = vm.currentPlan else { return "构图已优化" }
        switch plan.composition {
        case .center: return "对称构图已优化"
        case .goldenLeft, .goldenRight: return "黄金分割构图已优化"
        case .leftThird, .rightThird: return "三分法构图已优化"
        }
    }

    // MARK: - 快门按钮（参考图：白色圆形）
    private var shutterButton: some View {
        ZStack {
            // 外圈（对齐时呼吸动效）
            if vm.isReady {
                Circle()
                    .stroke(Design.accent.opacity(breathingOpacity), lineWidth: 3)
                    .frame(width: 76, height: 76)
                    .scaleEffect(breathingScale)
            }

            // 外环
            Circle()
                .stroke(vm.isReady ? Design.accent.opacity(0.85) : Color.white.opacity(0.6), lineWidth: 3.5)
                .frame(width: 68, height: 68)

            // 内圆主体
            Circle()
                .fill(vm.isReady ? Design.accent : Color.white)
                .frame(width: 58, height: 58)
                .shadow(color: vm.isReady ? Design.accentGlow : .clear, radius: vm.isReady ? 12 : 0)
        }
        .frame(width: 84, height: 84)
        .scaleEffect(vm.isCapturing ? 0.92 : (vm.isReady ? 1.05 : 1.0))
        .animation(.spring(response: 0.3, dampingFraction: 0.55), value: vm.isReady)
        .onChange(of: vm.isReady) { newValue in
            if newValue {
                startBreathing()
            }
        }
    }

    @State private var breathingScale: CGFloat = 1.0
    @State private var breathingOpacity: Double = 0.4

    private func startBreathing() {
        breathingScale = 1.0
        breathingOpacity = 0.4
        withAnimation(.easeOut(duration: 1.2).repeatForever(autoreverses: true)) {
            breathingScale = 1.35
            breathingOpacity = 0.05
        }
    }

    // MARK: - 内购拦截浮层
    private var paywallTeaser: some View {
        VStack(spacing: 16) {
            Image(systemName: "crown.fill")
                .font(.system(size: 44))
                .foregroundColor(Design.accent)
            Text("「\(vm.scene.displayName)」是高级场景")
                .font(.system(size: 18, weight: .bold))
                .foregroundColor(.white)
            Text("升级 Pro 即可使用专属姿势与满级体验")
                .font(.system(size: 14))
                .foregroundColor(.white.opacity(0.8))
            Button {
                vm.showPaywall = true
            } label: {
                Text("了解 Pro 特权")
                    .font(.system(size: 15, weight: .bold))
                    .foregroundColor(.black)
                    .padding(.horizontal, 24)
                    .padding(.vertical, 10)
                    .background(Design.accent, in: Capsule())
            }
        }
        .padding(.horizontal, 32)
        .padding(.vertical, 28)
        .background(Color.black.opacity(0.65).blur(radius: 20))
        .background(Color.black.opacity(0.3))
        .cornerRadius(24)
        .overlay(RoundedRectangle(cornerRadius: 24).stroke(Color.white.opacity(0.15), lineWidth: 1))
    }

    // MARK: - 构图提示浮层
    private func compositionTipOverlay(plan: ShootingPlan) -> some View {
        VStack {
            Spacer().frame(height: 130)

            HStack(spacing: 12) {
                ZStack {
                    Circle()
                        .fill(Design.accent.opacity(0.2))
                        .frame(width: 34, height: 34)
                    Image(systemName: plan.composition.icon)
                        .font(.system(size: 14, weight: .medium))
                        .foregroundColor(Design.accent)
                }
                VStack(alignment: .leading, spacing: 3) {
                    Text("\(plan.composition.displayName) 构图")
                        .font(.system(size: 13, weight: .bold))
                        .foregroundColor(.white)
                    Text(plan.composition.reason)
                        .font(.system(size: 11))
                        .foregroundColor(Design.textSecondary)
                        .lineLimit(2)
                }
                Spacer()
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 14)
            .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: Design.cornerCard))
            .overlay(
                RoundedRectangle(cornerRadius: Design.cornerCard)
                    .stroke(Design.accent.opacity(0.35), lineWidth: 1)
            )
            .padding(.horizontal, 24)
            .transition(.move(edge: .top).combined(with: .opacity))
            .shadow(color: .black.opacity(0.3), radius: 12, y: 4)

            Spacer()
        }
    }

    // MARK: - 俯拍警告
    private var pitchWarningOverlay: some View {
        VStack {
            Spacer()
            HStack(spacing: 10) {
                Image(systemName: "exclamationmark.triangle.fill")
                    .foregroundColor(Design.danger)
                Text("请平行或低角度拍摄，显腿更长")
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundColor(.white)
            }
            .padding(.horizontal, 18).padding(.vertical, 12)
            .background(.ultraThinMaterial, in: Capsule())
            .overlay(Capsule().stroke(Design.danger.opacity(0.5), lineWidth: 1))
            .padding(.bottom, 220)
            .transition(.move(edge: .bottom).combined(with: .opacity))
        }
        .animation(.spring(response: 0.4, dampingFraction: 0.75), value: vm.manager.devicePitch < -0.35)
    }

    // MARK: - 多机位角度仪表盘
    private func angleGuideOverlay(reqPitch: Double) -> some View {
        let isReaching = (reqPitch > 0 && vm.manager.devicePitch >= reqPitch) || (reqPitch < 0 && vm.manager.devicePitch <= reqPitch)

        return VStack {
            Spacer()
            HStack(spacing: 12) {
                ZStack {
                    Circle()
                        .stroke(Color.white.opacity(0.3), lineWidth: 2)
                        .frame(width: 30, height: 30)

                    Rectangle()
                        .fill(isReaching ? Design.success : Design.danger)
                        .frame(width: 20, height: 3)
                        .rotationEffect(.degrees(vm.manager.devicePitch * -90))
                }

                VStack(alignment: .leading, spacing: 2) {
                    Text(isReaching ? "机位正确，保持稳定" : (reqPitch > 0 ? "请摄影师继续下蹲仰拍" : "请摄影师抬高俯拍"))
                        .font(.system(size: 13, weight: .bold))
                        .foregroundColor(.white)
                }
            }
            .padding(.horizontal, 20).padding(.vertical, 14)
            .background(.ultraThinMaterial, in: Capsule())
            .overlay(Capsule().stroke(isReaching ? Design.success.opacity(0.8) : Design.danger.opacity(0.8), lineWidth: 2))
            .padding(.bottom, 280)
            .transition(.scale.combined(with: .opacity))
        }
        .animation(.interactiveSpring(), value: vm.manager.devicePitch)
    }

    // MARK: - Vlog 提词器
    private var vlogTextOverlay: some View {
        VStack {
            Spacer()
            Text(vm.displayVlogText)
                .font(.system(size: 26, weight: .black))
                .foregroundColor(.white)
                .padding(.horizontal, 24)
                .padding(.vertical, 16)
                .background(Color.black.opacity(0.6), in: RoundedRectangle(cornerRadius: 16))
                .shadow(color: Design.danger.opacity(vm.isVlogRecording ? 0.8 : 0.0), radius: 20, x: 0, y: 0)
                .padding(.bottom, 320)
                .transition(.opacity.combined(with: .scale(scale: 0.9)))
        }
        .zIndex(200)
    }

    // MARK: - 留白提醒
    private var spaceTipOverlay: some View {
        VStack {
            Spacer()
            HStack(spacing: 10) {
                Image(systemName: "rectangle.arrowtriangle.2.outward")
                    .foregroundColor(Design.accent)
                Text("尝试平移留出一点空白，更有氛围感")
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundColor(.white)
            }
            .padding(.horizontal, 18).padding(.vertical, 12)
            .background(.ultraThinMaterial, in: Capsule())
            .overlay(Capsule().stroke(Design.accent.opacity(0.5), lineWidth: 1))
            .padding(.bottom, 220)
            .transition(.move(edge: .bottom).combined(with: .opacity))
        }
        .animation(.spring(response: 0.4, dampingFraction: 0.75), value: vm.showSpaceTip)
    }

    // MARK: - 全景模式横向移动提示
    private var panoramaHintOverlay: some View {
        VStack {
            Spacer()
            HStack(spacing: 14) {
                Image(systemName: "arrow.left.and.right")
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundColor(Design.accent)
                VStack(alignment: .leading, spacing: 3) {
                    Text("全景模式")
                        .font(.system(size: 14, weight: .bold))
                        .foregroundColor(.white)
                    Text("按下快门后匀速横向移动手机，自动连拍 3 张宽幅素材")
                        .font(.system(size: 11))
                        .foregroundColor(Design.textSecondary)
                }
            }
            .padding(.horizontal, 18).padding(.vertical, 12)
            .background(.ultraThinMaterial, in: Capsule())
            .overlay(Capsule().stroke(Design.accent.opacity(0.5), lineWidth: 1))
            .padding(.bottom, 190)
            .transition(.move(edge: .bottom).combined(with: .opacity))
        }
    }

    // MARK: - AI 构图推荐浮层
    private func aiAdvisorBanner(text: String) -> some View {
        VStack {
            Spacer().frame(height: 120)

            HStack(alignment: .top, spacing: 14) {
                Text("✨")
                    .font(.system(size: 20))
                    .padding(.top, 2)

                VStack(alignment: .leading, spacing: 6) {
                    Text("AI 构图灵感")
                        .font(.system(size: 13, weight: .heavy))
                        .foregroundColor(Color(red: 0.75, green: 0.6, blue: 1.0))
                    Text(text)
                        .font(.system(size: 14, weight: .medium))
                        .foregroundColor(.white)
                        .lineSpacing(4)
                }
                Spacer()
            }
            .padding(.horizontal, 20)
            .padding(.vertical, 16)
            .background(
                RoundedRectangle(cornerRadius: 20)
                    .fill(.ultraThinMaterial)
                    .overlay(
                        RoundedRectangle(cornerRadius: 20)
                            .stroke(
                                LinearGradient(colors: [Color(red: 0.75, green: 0.6, blue: 1.0).opacity(0.6), .clear], startPoint: .topLeading, endPoint: .bottomTrailing),
                                lineWidth: 1.5
                            )
                    )
            )
            .shadow(color: Color(red: 0.75, green: 0.6, blue: 1.0).opacity(0.15), radius: 15, x: 0, y: 10)
            .padding(.horizontal, 24)
            .transition(.move(edge: .top).combined(with: .opacity))

            Spacer()
        }
        .zIndex(100)
    }

    // MARK: - 暗光提示 Banner
    private var lowLightBanner: some View {
        VStack {
            HStack(spacing: 8) {
                Image(systemName: "lightbulb.slash.fill")
                    .font(.system(size: 13))
                    .foregroundColor(.yellow)
                Text("光线不足，移到明亮处效果更好")
                    .font(.system(size: 13, weight: .medium))
                    .foregroundColor(.white.opacity(0.9))
            }
            .padding(.horizontal, 16).padding(.vertical, 10)
            .background(.ultraThinMaterial, in: Capsule())
            .overlay(Capsule().stroke(Color.yellow.opacity(0.4), lineWidth: 1))
            .padding(.top, 110)
            .transition(.move(edge: .top).combined(with: .opacity))
            Spacer()
        }
    }
}

// MARK: - 工具扩展
extension CGFloat {
    func clamped(to range: ClosedRange<CGFloat>) -> CGFloat {
        Swift.min(Swift.max(self, range.lowerBound), range.upperBound)
    }
}

// MARK: - 扫描框四角修饰线
struct ScanCornerLines: View {
    var body: some View {
        GeometryReader { geo in
            let w = geo.size.width
            let h = geo.size.height
            let len: CGFloat = 18
            let thick: CGFloat = 2.5
            let color = Design.accent

            Canvas { ctx, _ in
                let corners: [(CGPoint, CGPoint, CGPoint)] = [
                    (CGPoint(x: 0, y: len), CGPoint(x: 0, y: 0), CGPoint(x: len, y: 0)),
                    (CGPoint(x: w - len, y: 0), CGPoint(x: w, y: 0), CGPoint(x: w, y: len)),
                    (CGPoint(x: 0, y: h - len), CGPoint(x: 0, y: h), CGPoint(x: len, y: h)),
                    (CGPoint(x: w - len, y: h), CGPoint(x: w, y: h), CGPoint(x: w, y: h - len))
                ]
                for (a, b, c) in corners {
                    var p = Path()
                    p.move(to: a); p.addLine(to: b); p.addLine(to: c)
                    ctx.stroke(p, with: .color(color), style: StrokeStyle(lineWidth: thick, lineCap: .round))
                }
            }
        }
    }
}

// MARK: - 方案选择卡片
struct PlanCard: View {
    let plan: ShootingPlan
    let isSelected: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack(spacing: 7) {
                Text(plan.poseEmoji)
                    .font(.system(size: 17))
                Text(plan.poseName)
                    .font(.system(size: 13, weight: .bold))
                    .foregroundColor(.white)
                    .lineLimit(1)
            }

            if isSelected {
                HStack(spacing: 5) {
                    TagBadge(icon: plan.composition.icon, text: plan.composition.displayName, active: true)
                    TagBadge(icon: plan.frameRatio.icon, text: plan.frameRatio.displayName, active: true)
                }
                .transition(.opacity.combined(with: .move(edge: .top)))
            }
        }
        .padding(.horizontal, 13)
        .padding(.vertical, 10)
        .background(
            RoundedRectangle(cornerRadius: 12)
                .fill(
                    isSelected
                        ? LinearGradient(colors: [Design.accent.opacity(0.22), Design.accent.opacity(0.08)], startPoint: .topLeading, endPoint: .bottomTrailing)
                        : LinearGradient(colors: [Color.black.opacity(0.45), Color.black.opacity(0.3)], startPoint: .top, endPoint: .bottom)
                )
        )
        .overlay(
            RoundedRectangle(cornerRadius: 12)
                .stroke(
                    isSelected ? Design.accent.opacity(0.75) : Design.border,
                    lineWidth: isSelected ? 1.5 : 1
                )
        )
        .shadow(color: isSelected ? Design.accentGlow : .clear, radius: 8)
        .scaleEffect(isSelected ? 1.03 : 1.0)
        .animation(.spring(response: 0.32, dampingFraction: 0.68), value: isSelected)
    }
}

struct TagBadge: View {
    let icon: String
    let text: String
    var active: Bool = false

    var body: some View {
        HStack(spacing: 3) {
            Image(systemName: icon)
                .font(.system(size: 9, weight: .medium))
            Text(text)
                .font(.system(size: 10, weight: .semibold))
        }
        .foregroundColor(active ? Design.accent : .white.opacity(0.7))
        .padding(.horizontal, 7)
        .padding(.vertical, 3.5)
        .background(
            Capsule()
                .fill(active ? Design.accent.opacity(0.18) : Color.white.opacity(0.1))
        )
        .overlay(
            Capsule()
                .stroke(active ? Design.accent.opacity(0.4) : Color.clear, lineWidth: 1)
        )
    }
}

// MARK: - 构图辅助线（参考图：醒目白色网格）
struct CompositionGuideLines: View {
    var composition: CompositionRule?

    var body: some View {
        GeometryReader { geo in
            let w = geo.size.width
            let h = geo.size.height
            Canvas { ctx, size in
                var path = Path()

                if composition == .goldenLeft || composition == .goldenRight {
                    let isRight = (composition == .goldenRight)
                    let pivotX = isRight ? w * 0.618 : w * 0.382
                    let pivotY = h * 0.382

                    path.move(to: CGPoint(x: isRight ? 0 : w, y: h))
                    path.addQuadCurve(
                        to: CGPoint(x: pivotX, y: pivotY),
                        control: CGPoint(x: isRight ? w * 0.2 : w * 0.8, y: pivotY + h * 0.1)
                    )

                    ctx.stroke(path, with: .color(Design.accent.opacity(0.3)), lineWidth: 1.5)

                    var focus = Path()
                    focus.addEllipse(in: CGRect(x: pivotX - 4, y: pivotY - 4, width: 8, height: 8))
                    ctx.stroke(focus, with: .color(Design.accent.opacity(0.5)), lineWidth: 1)
                } else {
                    // 三分法网格线（参考图风格：更醒目的白色半透明）
                    [w/3, w*2/3].forEach { x in
                        path.move(to: CGPoint(x: x, y: 0))
                        path.addLine(to: CGPoint(x: x, y: h))
                    }
                    [h/3, h*2/3].forEach { y in
                        path.move(to: CGPoint(x: 0, y: y))
                        path.addLine(to: CGPoint(x: w, y: y))
                    }
                    ctx.stroke(path, with: .color(.white.opacity(0.22)), lineWidth: 1)
                }
            }
        }
        .ignoresSafeArea()
        .allowsHitTesting(false)
        .animation(.easeInOut(duration: 0.5), value: composition)
    }
}

// MARK: - 剪影引导叠加层
struct SilhouetteGuideOverlay: View {
    @Binding var isAligned: Bool
    let plan: ShootingPlan
    var bodyBoundingBox: CGRect?
    var forceOffset: CGFloat? = nil

    var body: some View {
        GeometryReader { geo in
            let screenW = geo.size.width
            let screenH = geo.size.height

            let (silW, silH, centerX, centerY) = resolveLayout(
                screenW: screenW, screenH: screenH, bbox: bodyBoundingBox, plan: plan
            )
            let hOffset = forceOffset ?? plan.composition.offset

            ZStack {
                PoseSilhouetteShape()
                    .fill(
                        isAligned ? Design.success.opacity(0.22) : Color.white.opacity(0.12),
                        style: FillStyle(eoFill: true)
                    )

                PoseSilhouetteShape()
                    .stroke(
                        isAligned
                            ? LinearGradient(
                                colors: [Design.success, Design.success.opacity(0.6)],
                                startPoint: .top, endPoint: .bottom)
                            : LinearGradient(
                                colors: [.white.opacity(0.7), .white.opacity(0.3)],
                                startPoint: .top, endPoint: .bottom),
                        style: StrokeStyle(
                            lineWidth: isAligned ? 3.0 : 1.8,
                            lineCap: .round,
                            dash: isAligned ? [] : [10, 7]
                        )
                    )

                HStack {
                    Text("左手边")
                        .font(.system(size: 12, weight: .black))
                        .foregroundColor(isAligned ? Design.success : .white.opacity(0.6))
                        .rotationEffect(.degrees(-90))
                        .offset(x: -30)
                    Spacer()
                    Text("右手边")
                        .font(.system(size: 12, weight: .black))
                        .foregroundColor(isAligned ? Design.success : .white.opacity(0.6))
                        .rotationEffect(.degrees(90))
                        .offset(x: 30)
                }
            }
            .frame(width: silW, height: silH)
            .shadow(color: isAligned ? Design.successGlow : .clear, radius: 14)
            .animation(.easeInOut(duration: 0.3), value: isAligned)
            .position(x: centerX + hOffset, y: centerY)
            .animation(.spring(response: 0.6, dampingFraction: 0.82), value: silH)
            .animation(.spring(response: 0.6, dampingFraction: 0.82), value: centerY)
            .animation(.spring(response: 0.55, dampingFraction: 0.8), value: hOffset)

            if !isAligned {
                HStack(spacing: 6) {
                    Image(systemName: "arrow.up.and.down")
                        .font(.system(size: 10))
                        .foregroundColor(Design.accent)
                    Text(plan.frameRatio.distanceHint)
                        .font(.system(size: 11, weight: .medium))
                        .foregroundColor(.white.opacity(0.8))
                }
                .padding(.horizontal, 12).padding(.vertical, 6)
                .background(.ultraThinMaterial, in: Capsule())
                .overlay(Capsule().stroke(Design.accent.opacity(0.3), lineWidth: 1))
                .position(
                    x: centerX + hOffset,
                    y: centerY + silH / 2 + 28
                )
                .animation(.spring(response: 0.6, dampingFraction: 0.82), value: centerY)
            }
        }
        .ignoresSafeArea()
        .allowsHitTesting(false)
    }

    private func resolveLayout(
        screenW: CGFloat, screenH: CGFloat,
        bbox: CGRect?, plan: ShootingPlan
    ) -> (CGFloat, CGFloat, CGFloat, CGFloat) {

        let aspectRatio: CGFloat = 0.52

        if let bbox = bbox, bbox.height > 0.05 {
            let paddingTop: CGFloat = 0.10
            let paddingH: CGFloat  = 0.05
            let paddingSide: CGFloat = 0.08

            let bboxH = min(bbox.height + paddingTop + paddingH, 0.95)
            var rawH = bboxH * screenH
            let minH = screenH * plan.frameRatio.heightRatio * 0.5
            let maxH = screenH * plan.frameRatio.heightRatio * 1.3
            rawH = max(minH, min(maxH, rawH))

            let silH = rawH
            let silW = silH * aspectRatio

            let detectedCenterX = (bbox.midX + paddingSide / 2) * screenW
            let centerX = detectedCenterX.clamped(to: silW/2...(screenW - silW/2))

            let detectedMidY = (bbox.minY - paddingTop / 2 + bboxH / 2) * screenH
            let centerY = detectedMidY.clamped(to: silH/2...(screenH - silH/2 - 40))

            return (silW, silH, centerX, centerY)

        } else {
            let defaultH = screenH * plan.frameRatio.heightRatio
            let defaultW = defaultH * aspectRatio
            let defaultX = screenW / 2
            let defaultY: CGFloat = plan.frameRatio == .fullBody
                ? screenH - defaultH / 2 - 140
                : screenH * 0.42
            return (defaultW, defaultH, defaultX, defaultY)
        }
    }
}

// MARK: - 剪影 Shape
struct PoseSilhouetteShape: Shape {
    func path(in rect: CGRect) -> Path {
        var path = Path()
        let w = rect.width
        let h = rect.height

        let headSize = w * 0.24
        path.addEllipse(in: CGRect(x: w * 0.38, y: h * 0.02, width: headSize, height: headSize * 1.15))

        path.move(to: CGPoint(x: w * 0.45, y: h * 0.14 + headSize * 1.15))
        path.addQuadCurve(to: CGPoint(x: w * 0.18, y: h * 0.28), control: CGPoint(x: w * 0.28, y: h * 0.22))
        path.addQuadCurve(to: CGPoint(x: w * 0.12, y: h * 0.52), control: CGPoint(x: w * 0.08, y: h * 0.38))
        path.addCurve(to: CGPoint(x: w * 0.28, y: h * 0.43), control1: CGPoint(x: w * 0.18, y: h * 0.56), control2: CGPoint(x: w * 0.22, y: h * 0.48))
        path.addLine(to: CGPoint(x: w * 0.33, y: h * 0.50))
        path.addQuadCurve(to: CGPoint(x: w * 0.24, y: h * 0.93), control: CGPoint(x: w * 0.27, y: h * 0.72))
        path.addLine(to: CGPoint(x: w * 0.40, y: h * 0.93))
        path.addQuadCurve(to: CGPoint(x: w * 0.48, y: h * 0.58), control: CGPoint(x: w * 0.44, y: h * 0.74))
        path.addQuadCurve(to: CGPoint(x: w * 0.63, y: h * 0.93), control: CGPoint(x: w * 0.54, y: h * 0.74))
        path.addLine(to: CGPoint(x: w * 0.79, y: h * 0.93))
        path.addQuadCurve(to: CGPoint(x: w * 0.70, y: h * 0.52), control: CGPoint(x: w * 0.79, y: h * 0.73))
        path.addLine(to: CGPoint(x: w * 0.64, y: h * 0.48))
        path.addQuadCurve(to: CGPoint(x: w * 0.83, y: h * 0.43), control: CGPoint(x: w * 0.74, y: h * 0.52))
        path.addQuadCurve(to: CGPoint(x: w * 0.78, y: h * 0.24), control: CGPoint(x: w * 0.94, y: h * 0.33))
        path.addQuadCurve(to: CGPoint(x: w * 0.55, y: h * 0.14 + headSize * 1.15), control: CGPoint(x: w * 0.67, y: h * 0.23))
        path.closeSubpath()

        return path
    }
}

// MARK: - 方案引导面板
struct PoseGuideSheet: View {
    let plan: ShootingPlan?
    let scene: SceneType
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationView {
            ZStack {
                Color(.systemGroupedBackground).ignoresSafeArea()

                ScrollView {
                    VStack(spacing: 20) {

                        HStack(spacing: 10) {
                            Image(systemName: scene.icon)
                                .font(.system(size: 16))
                                .foregroundColor(Design.accent)
                            Text(scene.displayName)
                                .font(.system(size: 16, weight: .semibold))
                        }
                        .padding(.horizontal, 18).padding(.vertical, 12)
                        .frame(maxWidth: .infinity)
                        .background(Color(.secondarySystemGroupedBackground))
                        .cornerRadius(14)

                        if let plan = plan {
                            VStack(spacing: 12) {
                                Text("\(plan.poseEmoji) \(plan.poseName)")
                                    .font(.system(size: 22, weight: .bold))
                                Text(plan.poseDescription)
                                    .font(.system(size: 14))
                                    .foregroundColor(.secondary)
                                    .multilineTextAlignment(.center)
                            }
                            .padding(20)
                            .frame(maxWidth: .infinity)
                            .background(Color(.secondarySystemGroupedBackground))
                            .cornerRadius(14)

                            VStack(spacing: 0) {
                                GuideInfoRow(
                                    icon: plan.composition.icon,
                                    title: "\(plan.composition.displayName)构图",
                                    detail: plan.composition.reason
                                )
                                Divider().padding(.horizontal, 16)
                                GuideInfoRow(
                                    icon: plan.frameRatio.icon,
                                    title: "\(plan.frameRatio.displayName)拍摄",
                                    detail: plan.frameRatio.distanceHint
                                )
                            }
                            .background(Color(.secondarySystemGroupedBackground))
                            .cornerRadius(14)
                        }

                        VStack(alignment: .leading, spacing: 14) {
                            Text("使用说明")
                                .font(.system(size: 13, weight: .semibold))
                                .foregroundColor(.secondary)
                                .padding(.bottom, 2)
                            GuideRow(icon: "checkmark.circle.fill", color: .green,
                                     text: "绿色边框 + 分数变绿：姿势对齐！保持不动即自动拍照")
                            GuideRow(icon: "figure.stand", color: .secondary,
                                     text: "白色虚线：未对齐，请移动身体贴合剪影")
                            GuideRow(icon: "hand.tap", color: .blue,
                                     text: "点击底部卡片可切换推荐拍摄方案")
                            GuideRow(icon: "arrow.triangle.2.circlepath.camera", color: .orange,
                                     text: "左下角图标可切换前后置摄像头")
                        }
                        .padding(18)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(Color(.secondarySystemGroupedBackground))
                        .cornerRadius(14)

                        Spacer(minLength: 20)
                    }
                    .padding(16)
                    .padding(.top, 8)
                }
            }
            .navigationTitle("拍摄指引")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("完成") { dismiss() }
                        .fontWeight(.semibold)
                }
            }
        }
    }
}

struct GuideInfoRow: View {
    let icon: String
    let title: String
    let detail: String

    var body: some View {
        HStack(alignment: .top, spacing: 14) {
            ZStack {
                Circle()
                    .fill(Color.accentColor.opacity(0.12))
                    .frame(width: 36, height: 36)
                Image(systemName: icon)
                    .font(.system(size: 16))
                    .foregroundColor(.accentColor)
            }
            VStack(alignment: .leading, spacing: 3) {
                Text(title)
                    .font(.system(size: 14, weight: .semibold))
                Text(detail)
                    .font(.system(size: 13))
                    .foregroundColor(.secondary)
                    .fixedSize(horizontal: false, vertical: true)
            }
            Spacer()
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 14)
    }
}

struct GuideRow: View {
    let icon: String
    let color: Color
    let text: String

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            Image(systemName: icon)
                .foregroundColor(color)
                .font(.system(size: 16))
                .frame(width: 20)
            Text(text)
                .font(.system(size: 13))
                .foregroundColor(.primary)
        }
    }
}

#Preview { ContentView() }

// MARK: - 动态历史封面加载器
struct HistoryLastImageThumbnail: View {
    let localIdentifier: String
    @State private var image: UIImage? = nil

    var body: some View {
        Group {
            if let img = image {
                Image(uiImage: img)
                    .resizable()
                    .scaledToFill()
            } else {
                Image(systemName: "photo.on.rectangle")
                    .font(.system(size: 19, weight: .medium))
                    .foregroundColor(.white)
            }
        }
        .frame(width: 48, height: 48)
        .clipShape(RoundedRectangle(cornerRadius: 10))
        .task {
            if let img = await PhotoAlbumUtil.shared.fetchImage(by: localIdentifier) {
                await MainActor.run { self.image = img }
            }
        }
    }
}
