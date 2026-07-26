import SwiftUI
import SwiftData
import AVFoundation
import AVFAudio
import StoreKit

// MARK: - 品牌设计常量
enum Design {
    // 主题色（青墨绿 + 深空黑）
    static let accent = Color(red: 0.05, green: 0.58, blue: 0.50)         // #0D9488 青墨绿
    static let accentGlow = Color(red: 0.05, green: 0.58, blue: 0.50).opacity(0.35)
    static let success = Color(red: 0.10, green: 0.75, blue: 0.55)       // #1ABF8C 翡翠绿
    static let successGlow = Color(red: 0.10, green: 0.75, blue: 0.55).opacity(0.35)
    static let danger = Color(red: 0.95, green: 0.35, blue: 0.35)         // #F25959 珊瑚红
    static let surface = Color.white.opacity(0.08)
    static let surfaceStrong = Color.white.opacity(0.15)
    static let border = Color.white.opacity(0.18)
    static let borderActive = Color.white.opacity(0.75)
    static let textPrimary = Color.white
    static let textSecondary = Color.white.opacity(0.55)
    static let overlayBg = Color.black.opacity(0.55)
    static let deepSpaceBlack = Color(red: 0.04, green: 0.06, blue: 0.05)  // 深空黑底色
    static let blur: Material = .ultraThinMaterial
    static let cornerCard: CGFloat = 18
    static let cornerBadge: CGFloat = 8
}

struct ContentView: View {

    // MARK: - SwiftData (Step 2 & Step 7)
    @Environment(\.modelContext) private var modelContext
    @Query(sort: \ShootingRecord.createdAt, order: .reverse) private var recentRecords: [ShootingRecord]
    @Query(sort: \CustomPlan.createdAt, order: .reverse) private var storedCustomPlans: [CustomPlan]

    // MARK: - ViewModel
    @StateObject private var vm = ShootingViewModel()

    // MARK: - StoreKit 2 内购管理器（从 App 层注入）
    @EnvironmentObject var storeManager: StoreManager

    // MARK: - 保存照片记录（从 body 提取以降低类型推导复杂度）
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

    // MARK: - 扫描动画（纯 UI 驱动，保留在 View 层）
    @State private var scanPulse: Bool = false
    @State private var scanRotation: Double = 0

    // MARK: - 倒计时覆盖层（提取以降低 body 类型推导复杂度）
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
            cameraLayer

            // 点击进入/退出沉浸模式的手势层
            Color.clear
                .contentShape(Rectangle())
                .onTapGesture { vm.toggleImmersiveMode() }

            if vm.isSceneReady && !vm.isImmersiveMode {
                CompositionGuideLines(composition: vm.currentPlan?.composition)
            }

            if !vm.isSceneReady {
                sceneScanningOverlay
            } else if let plan = vm.currentPlan {
                if vm.requiresProUnlock {
                    paywallTeaser
                } else {
                    if plan.secondaryPosePoints != nil {
                        // 双人模式：渲染两个互相偏移的剪影
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
                        // 单人模式：渲染单个剪影
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

            if !vm.requiresProUnlock && !vm.isImmersiveMode {
                if vm.isSceneReady, vm.currentPlan?.frameRatio == .fullBody {
                    arFootprintsOverlay
                }
            }

            // 顶部信息栏
            if !vm.isImmersiveMode {
                VStack(spacing: 0) {
                    topBar
                        .padding(.top, 58)
                        .padding(.horizontal, 18)
                    Spacer()
                }
                .transition(.move(edge: .top).combined(with: .opacity))
            }

            // 构图提示浮层
            if !vm.requiresProUnlock, vm.showCompositionTip, let plan = vm.currentPlan, !vm.isImmersiveMode {
                compositionTipOverlay(plan: plan)
            }
            
            // Step 11: AI 大脑生成构图指导浮层
            if let aiTip = vm.aiSuggestion, !vm.isImmersiveMode {
                aiAdvisorBanner(text: aiTip)
                    // 上划可将其抹去
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

            // 暗光提示 Banner（有人但光线不足时显示）
            if vm.isLowLight && vm.isSceneReady && !vm.isImmersiveMode {
                lowLightBanner
            }

            // 底部控制区
            VStack(spacing: 0) {
                Spacer()
                bottomPanel
            }

            // 俯拍警告
            if vm.manager.devicePitch < -0.35 && !vm.isImmersiveMode {
                pitchWarningOverlay
            }

            // 留白提醒 (P5-3)
            if vm.showSpaceTip && vm.manager.devicePitch >= -0.35 && !vm.showCompositionTip && !vm.isImmersiveMode {
                spaceTipOverlay
            }
            
            // Step 12: 强制机位雷达仪表盘
            if let plan = vm.currentPlan, let multi = plan.multiAngles,
               vm.activeAngleIndex < multi.count, multi[vm.activeAngleIndex].requiredPitch != nil,
               !vm.isImmersiveMode {
                angleGuideOverlay(reqPitch: multi[vm.activeAngleIndex].requiredPitch!)
            }
            
            // Step 13: Vlog 录制场记字幕框
            if !vm.displayVlogText.isEmpty && !vm.isImmersiveMode {
                vlogTextOverlay
            }

            // Step 9: 屏幕柔边补光带 (暗光提示且非沉浸模式下显示)
            if vm.isLowLight && !vm.isImmersiveMode {
                RoundedRectangle(cornerRadius: 12)
                    .stroke(Color(red: 1.0, green: 0.95, blue: 0.88).opacity(0.35), lineWidth: 50)
                    .blur(radius: 30)
                    .ignoresSafeArea()
                    .allowsHitTesting(false)
                    .transition(.opacity.animation(.easeInOut(duration: 1.0)))
            }

            // 快门闪光
            if vm.showShutterFlash {
                Color(red: 1.0, green: 0.95, blue: 0.88)
                    .ignoresSafeArea()
                    .opacity(0.9)
                    .transition(.opacity)
            }

            // 倒计时大数字
            countdownOverlay
            
            // 录制专属倒计时大数字 (Step 7)
            recordCountdownOverlay
        }
        .ignoresSafeArea()
        .onAppear {
            vm.storeManager = storeManager // 注入 StoreManager
            vm.bind()
            vm.startScanTimeout()
            vm.customShootingPlans = storedCustomPlans.map { $0.asShootingPlan }
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
                // 用户点「保存」
                savePhotoRecord(image: selectedImage, filter: appliedFilter)

                UINotificationFeedbackGenerator().notificationOccurred(.success)
                vm.isReviewingPhotos = false

                vm.checkAndRequestReview()
            } onRetake: {
                // 用户点「重拍」
                vm.isReviewingPhotos = false
            }
            .environmentObject(storeManager)
        }
        // Step 13: Vlog 录播回调预览层
        .fullScreenCover(isPresented: $vm.isReviewingVlog) {
            if let outputURL = vm.exportedVlogURL {
                VideoPreviewView(videoURL: outputURL) {
                    // 已保存，进行 Vlog 后续逻辑清理
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
        // 自定义方案保存界面 (Step 7)
        .sheet(isPresented: Binding(
            get: { vm.pointsToSave != nil },
            set: { if !$0 { vm.pointsToSave = nil } }
        )) {
            if let pts = vm.pointsToSave {
                SaveCustomPlanView(points: pts) {
                    vm.pointsToSave = nil
                    // 保存完毕后可自动切到第一个
                    DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
                        vm.selectPlan(at: 0)
                    }
                }
            }
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
            // 图标
            ZStack {
                Circle()
                    .fill(Design.danger.opacity(0.15))
                    .frame(width: 90, height: 90)
                Image(systemName: "camera.slash.fill")
                    .font(.system(size: 36, weight: .light))
                    .foregroundColor(Design.danger)
            }

            // 说明文字
            VStack(spacing: 8) {
                Text("需要摄像头权限")
                    .font(.system(size: 18, weight: .semibold))
                    .foregroundColor(.white)
                Text("PoseAI 需要访问摄像头\n才能实时检测姿势和场景")
                    .font(.system(size: 14))
                    .foregroundColor(Design.textSecondary)
                    .multilineTextAlignment(.center)
            }

            // 去设置按钮
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


    // MARK: - 顶部信息栏
    private var topBar: some View {
        HStack(alignment: .center, spacing: 12) {
            // 左侧：场景 + 方案信息
            if vm.isSceneReady, let plan = vm.currentPlan {
                HStack(spacing: 10) {
                    // 场景图标
                    ZStack {
                        Circle()
                            .fill(Design.surface)
                            .frame(width: 36, height: 36)
                        Image(systemName: vm.scene.icon)
                            .font(.system(size: 15, weight: .medium))
                            .foregroundColor(Design.accent)
                    }

                    VStack(alignment: .leading, spacing: 2) {
                        Text(vm.scene.displayName)
                            .font(.system(size: 11, weight: .medium))
                            .foregroundColor(Design.textSecondary)
                        
                        // Step 10 & 12 & 13: Vlog, 动作连线与机位推进展示
                        if let vlog = plan.vlogScript, vm.activeVlogClipIndex < vlog.clips.count {
                            HStack(spacing: 6) {
                                Circle()
                                    .fill(vm.isVlogRecording ? Color.red : Color.gray)
                                    .frame(width: 8, height: 8)
                                    .opacity(vm.isVlogRecording ? 1.0 : 0.5)
                                    .animation(vm.isVlogRecording ? Animation.easeInOut(duration: 0.6).repeatForever(autoreverses: true) : .default, value: vm.isVlogRecording)
                                
                                Text("Vlog [分镜 \(vm.activeVlogClipIndex + 1)/\(vlog.clips.count)]")
                                    .font(.system(size: 15, weight: .black))
                                    .foregroundColor(Design.danger)
                            }
                            .transition(.opacity.combined(with: .move(edge: .bottom)))
                            .id("vlog_\(vm.activeVlogClipIndex)_\(vm.isVlogRecording)")
                        } else if let seq = plan.sequence, vm.activeSequenceIndex < seq.count {
                            Text("[\(vm.activeSequenceIndex + 1)/\(seq.count)] \(seq[vm.activeSequenceIndex].emoji) \(seq[vm.activeSequenceIndex].title)")
                                .font(.system(size: 15, weight: .bold))
                                .foregroundColor(Design.success)
                                .transition(.opacity.combined(with: .move(edge: .bottom)))
                                .id("seq_\(vm.activeSequenceIndex)")
                        } else if let multi = plan.multiAngles, vm.activeAngleIndex < multi.count {
                            Text("[\(vm.activeAngleIndex + 1)/\(multi.count)] 📷 \(multi[vm.activeAngleIndex].title)")
                                .font(.system(size: 15, weight: .bold))
                                .foregroundColor(Design.danger) // 红色表示需要摄影师强交互
                                .transition(.opacity.combined(with: .move(edge: .bottom)))
                                .id("ang_\(vm.activeAngleIndex)")
                        } else {
                            Text("\(plan.poseEmoji) \(plan.poseName)")
                                .font(.system(size: 15, weight: .bold))
                                .foregroundColor(Design.textPrimary)
                        }
                    }
                }
                .padding(.horizontal, 14)
                .padding(.vertical, 10)
                .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: Design.cornerCard))
                .overlay(
                    RoundedRectangle(cornerRadius: Design.cornerCard)
                        .stroke(Design.border, lineWidth: 1)
                )
                .transition(.scale(scale: 0.92).combined(with: .opacity))
            }

            Spacer()

            // 右侧：分数环 + 帮助按钮
            HStack(spacing: 10) {
                if vm.isSceneReady {
                    scoreRing
                }
                Button { vm.showGuide = true } label: {
                    ZStack {
                        Circle()
                            .fill(Design.surface)
                            .frame(width: 40, height: 40)
                            .overlay(
                                Circle().stroke(Design.border, lineWidth: 1)
                            )
                        Image(systemName: "questionmark")
                            .font(.system(size: 16, weight: .semibold))
                            .foregroundColor(.white)
                    }
                }
            }
        }
        .animation(.spring(response: 0.45, dampingFraction: 0.75), value: vm.isSceneReady)
    }

    // MARK: - 分数环
    private var scoreRing: some View {
        ZStack {
            // 外发光（对齐时）
            if vm.isReady {
                Circle()
                    .stroke(Design.successGlow, lineWidth: 10)
                    .frame(width: 54, height: 54)
                    .blur(radius: 6)
            }

            // 底层轨道
            Circle()
                .stroke(Color.white.opacity(0.12), lineWidth: 3.5)
                .frame(width: 46, height: 46)

            // 进度弧
            Circle()
                .trim(from: 0, to: vm.score / 100)
                .stroke(
                    AngularGradient(
                        colors: vm.isReady ? [Design.success, Design.success.opacity(0.6)] : vm.scoreArcColors,
                        center: .center,
                        startAngle: .degrees(-90),
                        endAngle: .degrees(270)
                    ),
                    style: StrokeStyle(lineWidth: 3.5, lineCap: .round)
                )
                .frame(width: 46, height: 46)
                .rotationEffect(.degrees(-90))
                .animation(.linear(duration: 0.12), value: vm.score)

            // 分数文字
            Text("\(Int(vm.score))")
                .font(.system(size: 12, weight: .black, design: .rounded))
                .foregroundColor(.white)
        }
        .frame(width: 54, height: 54)
        .scaleEffect(vm.isReady ? 1.08 : 1.0)
        .animation(.spring(response: 0.3, dampingFraction: 0.55), value: vm.isReady)
        .accessibilityLabel("匹配度 \(Int(vm.score))%")
    }

    // MARK: - 场景扫描引导
    private var sceneScanningOverlay: some View {
        VStack {
            Spacer()
            VStack(spacing: 28) {
                ZStack {
                    // 最外圈脉冲
                    Circle()
                        .stroke(Design.accent.opacity(scanPulse ? 0.0 : 0.35), lineWidth: 1.5)
                        .frame(width: scanPulse ? 220 : 160)
                        .animation(
                            .easeOut(duration: 1.8).repeatForever(autoreverses: false),
                            value: scanPulse
                        )

                    // 第二圈
                    Circle()
                        .stroke(Design.accent.opacity(scanPulse ? 0.0 : 0.2), lineWidth: 1)
                        .frame(width: scanPulse ? 190 : 140)
                        .animation(
                            .easeOut(duration: 1.8).delay(0.3).repeatForever(autoreverses: false),
                            value: scanPulse
                        )

                    // 主框
                    RoundedRectangle(cornerRadius: 20)
                        .stroke(Design.accent.opacity(0.8), lineWidth: 2)
                        .frame(width: 140, height: 190)

                    // 四角修饰线
                    ScanCornerLines()
                        .frame(width: 140, height: 190)

                    // 内容
                    VStack(spacing: 10) {
                        ZStack {
                            // 旋转扫描弧
                            Circle()
                                .trim(from: 0, to: 0.25)
                                .stroke(
                                    AngularGradient(colors: [Design.accent, .clear], center: .center),
                                    style: StrokeStyle(lineWidth: 2.5, lineCap: .round)
                                )
                                .frame(width: 44, height: 44)
                                .rotationEffect(.degrees(scanRotation))
                                .onAppear {
                                    withAnimation(.linear(duration: 1.6).repeatForever(autoreverses: false)) {
                                        scanRotation = 360
                                    }
                                }

                            Image(systemName: "viewfinder")
                                .font(.system(size: 22, weight: .ultraLight))
                                .foregroundColor(Design.accent.opacity(0.7))
                        }

                        Text("识别场景中…")
                            .font(.system(size: 13, weight: .semibold))
                            .foregroundColor(.white)
                    }
                }

                // 提示文字
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
        .onAppear { scanPulse = true }
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

    // MARK: - 底部整体面板（磨砂玻璃）
    private var bottomPanel: some View {
        VStack(spacing: 0) {
            // 方案选择器
            if vm.isSceneReady && !vm.isImmersiveMode {
                planPickerSection
                    .padding(.top, 10)
                    .transition(.move(edge: .bottom).combined(with: .opacity))
            }

            // 主控制行
            controlRow
                .padding(.top, 14)
                .padding(.bottom, 44)
                .padding(.horizontal, 28)
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
                                .foregroundColor(Color.white.opacity(0.12)),
                            alignment: .top
                        )
                }
            }
        )
        .animation(.spring(response: 0.5, dampingFraction: 0.8), value: vm.isSceneReady)
        .animation(.spring(response: 0.5, dampingFraction: 0.8), value: vm.isImmersiveMode)
    }

    // MARK: - 方案选择器
    private var planPickerSection: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(alignment: .bottom, spacing: 10) {
                // Step 7: 录制新姿势入口
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

                // 呈现所有方案（包含自定义和推荐）
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

    // MARK: - 主控制行
    private var controlRow: some View {
        HStack(spacing: 0) {
            // 左：历史缩略图 (P2-2 / Step 2 持久化)
            Button {
                vm.showSessionGallery = true
            } label: {
                ZStack {
                    RoundedRectangle(cornerRadius: 12)
                        .fill(Design.surface)
                        .frame(width: 50, height: 50)
                        .overlay(RoundedRectangle(cornerRadius: 12).stroke(Design.border, lineWidth: 1))

                    if let r = recentRecords.first {
                        HistoryLastImageThumbnail(localIdentifier: r.localIdentifier)
                    } else {
                        Image(systemName: "photo.on.rectangle")
                            .font(.system(size: 19, weight: .medium))
                            .foregroundColor(.white)
                    }
                }
            }
            .frame(maxWidth: .infinity)
            .accessibilityLabel("拍摄历史，\(recentRecords.count)张照片")

            // 中：快门（倒计时/直接拍）
            shutterButton
                .onTapGesture { vm.handleShutterTap() }
                .accessibilityLabel(vm.isReady ? "拍照，姿势已对齐" : "拍照")
                .accessibilityAddTraits(.isButton)

            // 右：翻转摄像头与倒计时
            HStack(spacing: 12) {
                // 切换摄像头
                Button { vm.manager.isFront.toggle() } label: {
                    ZStack {
                        Circle()
                            .fill(Design.surface)
                            .frame(width: 44, height: 44)
                            .overlay(Circle().stroke(Design.border, lineWidth: 1))
                        Image(systemName: "arrow.triangle.2.circlepath.camera")
                            .font(.system(size: 18, weight: .medium))
                            .foregroundColor(.white)
                    }
                }
                .accessibilityLabel("切换\(vm.manager.isFront ? "后置" : "前置")摄像头")

                // 倒计时
                Button { vm.cycleTimer() } label: {
                    ZStack {
                        Circle()
                            .fill(vm.timerSeconds > 0 ? Design.accent.opacity(0.18) : Design.surface)
                            .frame(width: 44, height: 44)
                            .overlay(Circle().stroke(vm.timerSeconds > 0 ? Design.accent.opacity(0.6) : Design.border, lineWidth: 1))
                        if vm.timerSeconds == 0 {
                            Image(systemName: "timer")
                                .font(.system(size: 18, weight: .medium))
                                .foregroundColor(Design.textSecondary)
                        } else {
                            Text("\(vm.timerSeconds)s")
                                .font(.system(size: 14, weight: .bold))
                                .foregroundColor(Design.accent)
                        }
                    }
                }
            }
            .frame(maxWidth: .infinity)
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

    // MARK: - 快门按钮（精致版）
    private var shutterButton: some View {
        ZStack {
            // 最外圈呼吸动效（对齐时）
            if vm.isReady {
                Circle()
                    .stroke(Design.successGlow, lineWidth: 22)
                    .frame(width: 82, height: 82)
                    .scaleEffect(vm.breathingScale)
                    .opacity(2.0 - vm.breathingScale)
                    .onAppear {
                        withAnimation(.easeOut(duration: 1.1).repeatForever(autoreverses: false)) {
                            vm.breathingScale = 1.5
                        }
                    }
            }

            // 外圈轨道
            Circle()
                .stroke(
                    vm.isReady ? Design.success.opacity(0.9) : Color.white.opacity(0.55),
                    lineWidth: 2.5
                )
                .frame(width: 82, height: 82)

            // 内圆主体
            Circle()
                .fill(
                    vm.isReady
                        ? LinearGradient(colors: [Design.success, Color(red: 0.15, green: 0.80, blue: 0.60)], startPoint: .topLeading, endPoint: .bottomTrailing)
                        : LinearGradient(colors: [Color.white.opacity(0.92), Color.white.opacity(0.78)], startPoint: .top, endPoint: .bottom)
                )
                .frame(width: 68, height: 68)
                .shadow(color: vm.isReady ? Design.successGlow : Color.black.opacity(0.3), radius: vm.isReady ? 12 : 5)

            // 图标
            if vm.isReady {
                Image(systemName: "camera.fill")
                    .font(.system(size: 22, weight: .semibold))
                    .foregroundColor(.white)
            } else {
                Circle()
                    .fill(Color.black.opacity(0.08))
                    .frame(width: 26, height: 26)
            }
        }
        .frame(width: 92, height: 92)
        .scaleEffect(vm.isCapturing ? 0.92 : (vm.isReady ? 1.05 : 1.0))
        .animation(.spring(response: 0.3, dampingFraction: 0.55), value: vm.isReady)
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
            .padding(.bottom, 170)
            .transition(.move(edge: .bottom).combined(with: .opacity))
        }
        .animation(.spring(response: 0.4, dampingFraction: 0.75), value: vm.manager.devicePitch < -0.35)
    }

    // MARK: - Step 12: 多机位角度仪表盘
    private func angleGuideOverlay(reqPitch: Double) -> some View {
        let isReaching = (reqPitch > 0 && vm.manager.devicePitch >= reqPitch) || (reqPitch < 0 && vm.manager.devicePitch <= reqPitch)
        
        return VStack {
            Spacer()
            HStack(spacing: 12) {
                // 陀螺仪视觉元件
                ZStack {
                    Circle()
                        .stroke(Color.white.opacity(0.3), lineWidth: 2)
                        .frame(width: 30, height: 30)
                    
                    Rectangle()
                        .fill(isReaching ? Design.success : Design.danger)
                        .frame(width: 20, height: 3)
                        .rotationEffect(.degrees(vm.manager.devicePitch * -90)) // 根据倾角旋转指北针
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
            .padding(.bottom, 230)
            .transition(.scale.combined(with: .opacity))
        }
        .animation(.interactiveSpring(), value: vm.manager.devicePitch)
    }
    
    // MARK: - Step 13: Vlog 提词器
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
                .padding(.bottom, 280) // 放在比较高的位置，不要挡住人物主体
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
            .padding(.bottom, 170)
            .transition(.move(edge: .bottom).combined(with: .opacity))
        }
        .animation(.spring(response: 0.4, dampingFraction: 0.75), value: vm.showSpaceTip)
    }
    
    // MARK: - Step 11 AI 构图推荐浮层
    private func aiAdvisorBanner(text: String) -> some View {
        VStack {
            Spacer().frame(height: 120) // 对齐在构图提示原本出现的位置偏下
            
            HStack(alignment: .top, spacing: 14) {
                Text("✨")
                    .font(.system(size: 20))
                    .padding(.top, 2)
                    
                VStack(alignment: .leading, spacing: 6) {
                    Text("AI 构图灵感")
                        .font(.system(size: 13, weight: .heavy))
                        .foregroundColor(Color(red: 0.75, green: 0.6, blue: 1.0)) // 高级紫光
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
        .zIndex(100) // 置顶交互层级
    }

    // 暗光提示 Banner
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
    /// 将值限制在闭合区间内
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

// MARK: - 方案选择卡片（紧凑 pill 样式）
// 设计原则：相机主界面，背景才是主角，UI 是配角
// 未选中 → 只显示 emoji + 姿势名（约 42pt 高）
// 选中   → 展开一行构图+比例标签（约 60pt 高）
// 不显示描述文字（在帮助面板 PoseGuideSheet 中可查看完整信息）
struct PlanCard: View {
    let plan: ShootingPlan
    let isSelected: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            // 主行：emoji + 姿势名（始终显示）
            HStack(spacing: 7) {
                Text(plan.poseEmoji)
                    .font(.system(size: 17))
                Text(plan.poseName)
                    .font(.system(size: 13, weight: .bold))
                    .foregroundColor(.white)
                    .lineLimit(1)
            }

            // 标签行：仅选中时显示
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

// MARK: - 构图辅助线（三分法 / 黄金螺旋）
struct CompositionGuideLines: View {
    var composition: CompositionRule?

    var body: some View {
        GeometryReader { geo in
            let w = geo.size.width
            let h = geo.size.height
            Canvas { ctx, size in
                var path = Path()

                // 判断是否绘制黄金螺旋
                if composition == .goldenLeft || composition == .goldenRight {
                    // P5-6 黄金螺旋线构图
                    let isRight = (composition == .goldenRight)
                    // 简化的黄金螺旋装饰线：结合斐波那契矩形的特征画曲线
                    let pivotX = isRight ? w * 0.618 : w * 0.382
                    let pivotY = h * 0.382 // 焦点通常在上方偏右或偏左

                    // 用极坐标方式或简单贝塞尔曲线画一条装饰用的螺旋线
                    path.move(to: CGPoint(x: isRight ? 0 : w, y: h))
                    path.addQuadCurve(
                        to: CGPoint(x: pivotX, y: pivotY),
                        control: CGPoint(x: isRight ? w * 0.2 : w * 0.8, y: pivotY + h * 0.1)
                    )

                    ctx.stroke(path, with: .color(Design.accent.opacity(0.3)), lineWidth: 1.5)

                    // 画个焦点提示
                    var focus = Path()
                    focus.addEllipse(in: CGRect(x: pivotX - 4, y: pivotY - 4, width: 8, height: 8))
                    ctx.stroke(focus, with: .color(Design.accent.opacity(0.5)), lineWidth: 1)
                } else {
                    // 三分法
                    [w/3, w*2/3].forEach { x in
                        path.move(to: CGPoint(x: x, y: 0))
                        path.addLine(to: CGPoint(x: x, y: h))
                    }
                    [h/3, h*2/3].forEach { y in
                        path.move(to: CGPoint(x: 0, y: y))
                        path.addLine(to: CGPoint(x: w, y: y))
                    }
                    ctx.stroke(path, with: .color(.white.opacity(0.06)), lineWidth: 1)
                }
            }
        }
        .ignoresSafeArea()
        .allowsHitTesting(false)
        .animation(.easeInOut(duration: 0.5), value: composition)
    }
}

// MARK: - 剪影引导叠加层
// 设计思路：
// 1. 评分引擎（PoseMatcher）使用关节夹角，与体型无关，天然免疫高矮胖瘦
// 2. 剪影作为「视觉引导」，通过 bodyBoundingBox 跟随用户实际身体大小动态缩放
//    - 有检测到人体 → 剪影高度 ≈ 人体在画面中的实际高度（归一化），水平跟随中心
//    - 无检测 / 初始化 → 退回到方案设定的默认尺寸（frameRatio.heightRatio）
// 3. 宽高比固定 0.52（人体自然比例），保持剪影不变形
// 4. 使用 withAnimation(.spring) 平滑过渡，防止抖动
struct SilhouetteGuideOverlay: View {
    @Binding var isAligned: Bool
    let plan: ShootingPlan
    var bodyBoundingBox: CGRect?   // Vision 检测到的归一化人体包围盒 (x,y,w,h)
    var forceOffset: CGFloat? = nil // 双人强制水平偏移配置

    var body: some View {
        GeometryReader { geo in
            let screenW = geo.size.width
            let screenH = geo.size.height

            // MARK: 计算剪影的目标尺寸和位置
            // 优先使用实时检测到的包围盒，否则用方案默认比例
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

                // P4-6 剪影左右标注
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
            // 位置：X 跟随构图规则偏移；Y 跟随实际人体中心（或默认）
            .position(x: centerX + hOffset, y: centerY)
            .animation(.spring(response: 0.6, dampingFraction: 0.82), value: silH)
            .animation(.spring(response: 0.6, dampingFraction: 0.82), value: centerY)
            .animation(.spring(response: 0.55, dampingFraction: 0.8), value: hOffset)

            // 距离提示（未对齐、无人体检测时显示）
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

    // MARK: - 布局计算
    // 返回 (剪影宽, 剪影高, 中心X, 中心Y)，全部单位 pt
    private func resolveLayout(
        screenW: CGFloat, screenH: CGFloat,
        bbox: CGRect?, plan: ShootingPlan
    ) -> (CGFloat, CGFloat, CGFloat, CGFloat) {

        // 剪影宽高比固定：人体自然比例约 0.52:1
        let aspectRatio: CGFloat = 0.52

        if let bbox = bbox, bbox.height > 0.05 {
            // ── 有实时人体检测 ──────────────────────────────────────────
            // Vision 的包围盒来自关节点（不含头部上方留白）
            // 向上补偿 15% 使剪影头部不被截断
            let paddingTop: CGFloat = 0.10
            let paddingH: CGFloat  = 0.05   // 底部少量留白
            let paddingSide: CGFloat = 0.08  // 左右各留白

            let bboxH = min(bbox.height + paddingTop + paddingH, 0.95)
            // 检测高度映射到屏幕像素
            var rawH = bboxH * screenH
            // 将剪影高度限制在「方案允许的范围」内，避免离太远/太近时剪影失控
            let minH = screenH * plan.frameRatio.heightRatio * 0.5
            let maxH = screenH * plan.frameRatio.heightRatio * 1.3
            rawH = max(minH, min(maxH, rawH))

            let silH = rawH
            let silW = silH * aspectRatio

            // 水平中心跟随人体（bbox.midX 是归一化坐标）
            // 但若偏差过大（构图规则要求站偏）则混合
            let detectedCenterX = (bbox.midX + paddingSide / 2) * screenW
            // 简单取检测中心（构图偏移由 hOffset 分开控制）
            let centerX = detectedCenterX.clamped(to: silW/2...(screenW - silW/2))

            // 垂直中心：人体检测到的 Y 中心，向上补偿头部空间
            let detectedMidY = (bbox.minY - paddingTop / 2 + bboxH / 2) * screenH
            let centerY = detectedMidY.clamped(to: silH/2...(screenH - silH/2 - 40))

            return (silW, silH, centerX, centerY)

        } else {
            // ── 无检测/初始化：使用方案默认布局 ────────────────────────
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

                        // 场景标识卡
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
                            // 当前方案卡
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

                            // 构图 + 比例说明
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

                        // 使用说明卡
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
        .frame(width: 50, height: 50)
        .clipShape(RoundedRectangle(cornerRadius: 12))
        .task {
            // 取出来很快
            if let img = await PhotoAlbumUtil.shared.fetchImage(by: localIdentifier) {
                await MainActor.run { self.image = img }
            }
        }
    }
}
