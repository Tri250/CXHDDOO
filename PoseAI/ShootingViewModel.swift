import SwiftUI
import AVFoundation
import AVFAudio
import StoreKit
import Combine

// MARK: - 拍摄 ViewModel
// 职责：统一管理场景/方案/拍摄/倒计时/语音等全部业务状态和逻辑
// ContentView 专注于 UI 布局和渲染
@MainActor
final class ShootingViewModel: ObservableObject {

    // MARK: - 摄像头 & 视觉
    @Published var manager = CameraManager()

    // MARK: - 场景与方案状态
    @Published var scene: SceneType = .unknown
    @Published var currentPlanIndex: Int = 0
    @Published var isSceneReady: Bool = false

    // MARK: - 姿势匹配状态 (Step 8: 支持多人体)
    @Published var detectedPoses: [VisionService.PoseData] = []
    @Published var score: Double = 0
    @Published var isDualMatchedPrimary: Bool = true // 双人模式下：true 意味左侧人配主骨架；false 意味左侧人配副骨架

    // MARK: - 拍摄状态
    @Published var stableStartTime: Date? = nil
    @Published var showShutterFlash: Bool = false
    @Published var hapticCooldown: Bool = false
    @Published var breathingScale: CGFloat = 1.0
    @Published var isCapturing: Bool = false

    // MARK: - UI 状态
    @Published var showGuide: Bool = false
    @Published var showCompositionTip: Bool = false
    @Published var showSpaceTip: Bool = false
    @Published var isImmersiveMode: Bool = false
    @Published var isLowLight: Bool = false
    
    // Step 11: AI 构图大模型灵感
    @Published var aiSuggestion: String? = nil

    @Published var burstImages: [UIImage] = []
    @Published var capturedShotsCount: Int = 0
    @Published var expectedBurstCount: Int = 1
    @Published var isReviewingPhotos: Bool = false
    @Published var showSessionGallery: Bool = false
    
    // 保护用户原始屏幕亮度 (Step 9)
    private var baseBrightness: CGFloat? = nil

    // MARK: - 内购状态 (P3-3 → T2.1.1 StoreKit 2)
    /// StoreManager 由 ContentView 在 onAppear 时注入
    weak var storeManager: StoreManager?
    @Published var showPaywall = false

    /// 便捷 Pro 判定（从 StoreManager 读取真实购买状态）
    var isPro: Bool {
        storeManager?.isPro ?? false
    }

    // MARK: - 评价打分 (P4-3)
    @AppStorage("savedPhotoTotalCount") var savedPhotoTotalCount: Int = 0
    @AppStorage("reviewRequestCount") var reviewRequestCount: Int = 0
    @AppStorage("reviewRequestMonth") var reviewRequestMonth: String = ""

    // MARK: - 倒计时自拍
    @Published var timerSeconds: Int = 0           // 0=关闭, 3/5/10
    @Published var countdown: Int = 0              // 当前倒计时数字

    // MARK: - Step 10: 连拍序列状态
    @Published var activeSequenceIndex: Int = 0

    // MARK: - Step 12: 多机位拍摄状态
    @Published var activeAngleIndex: Int = 0

    // MARK: - Step 13: Vlog 短片剧本演练状态
    @Published var activeVlogClipIndex: Int = 0
    @Published var isVlogRecording: Bool = false
    @Published var displayVlogText: String = ""
    @Published var isReviewingVlog: Bool = false
    @Published var exportedVlogURL: URL? = nil

    // MARK: - 自定义方案与录制 (Step 7)
    @Published var customShootingPlans: [ShootingPlan] = []
    @Published var isRecordingMode: Bool = false
    @Published var recordCountdown: Int = 0
    @Published var pointsToSave: [String: CGPoint]? = nil

    // MARK: - 内部管理（非 UI 驱动）
    var compositionTipTask: DispatchWorkItem? = nil
    var scanTimeoutTask: DispatchWorkItem? = nil
    var timerTask: DispatchWorkItem? = nil
    var autoRecommendLastCheck: Date = .distantPast
    var userOverrideUntil: Date = .distantPast

    // MARK: - 语音
    let synthesizer = AVSpeechSynthesizer()

    // MARK: - 常量
    let successThreshold: Double = 85
    let dualSuccessThreshold: Double = 75 // 双人稍稍放宽

    // MARK: - 计算属性
    var isPremiumScene: Bool {
        [.city_street, .park, .indoor_home, .neon_night].contains(scene)
    }

    var requiresProUnlock: Bool {
        isPremiumScene && !isPro
    }

    var availablePlans: [ShootingPlan] {
        // 自定义方案优先显示在前面
        customShootingPlans + scene.plans
    }

    var currentPlan: ShootingPlan? {
        let plans = availablePlans
        guard !plans.isEmpty, currentPlanIndex < plans.count else { return nil }
        return plans[currentPlanIndex]
    }

    var isReady: Bool { score > (currentPlan?.secondaryPosePoints != nil ? dualSuccessThreshold : successThreshold) }

    var scoreArcColors: [Color] {
        if score > 60 { return [Design.accent, Design.accent.opacity(0.5)] }
        return [Color.white.opacity(0.8), Color.white.opacity(0.3)]
    }

    // MARK: - 绑定回调
    func bind() {
        manager.visionService.onUpdate = { [weak self] poses in
            guard let self = self else { return }
            
            // 为了平滑过渡，如果 Vision 短暂没识别到，这里也不立刻清空
            if !poses.isEmpty {
                self.detectedPoses = poses
            }
            
            guard let plan = self.currentPlan else { return }
            let baselineScore: Double
            let isDualPlan = (plan.secondaryPosePoints != nil)
            
            // Step 10 & Step 12 介入：序列模式/多机位模式下，抽取对应的特征点重写
            let activePrimaryPosePoints: [String: CGPoint]
            if let seq = plan.sequence, self.activeSequenceIndex < seq.count {
                activePrimaryPosePoints = seq[self.activeSequenceIndex].posePoints
            } else if let multi = plan.multiAngles, self.activeAngleIndex < multi.count,
                      let customPoints = multi[self.activeAngleIndex].posePoints {
                activePrimaryPosePoints = customPoints
            } else {
                activePrimaryPosePoints = plan.posePoints
            }
            
            if let secondaryPreset = plan.secondaryPosePoints {
                // 双人匹配逻辑
                if poses.count >= 2 {
                    let scoreA_primary = PoseMatcher.calculateSimilarity(current: poses[0].points, preset: activePrimaryPosePoints, isHalfBody: poses[0].isHalfBody)
                    let scoreA_secondary = PoseMatcher.calculateSimilarity(current: poses[0].points, preset: secondaryPreset, isHalfBody: poses[0].isHalfBody)
                    
                    let scoreB_primary = PoseMatcher.calculateSimilarity(current: poses[1].points, preset: activePrimaryPosePoints, isHalfBody: poses[1].isHalfBody)
                    let scoreB_secondary = PoseMatcher.calculateSimilarity(current: poses[1].points, preset: secondaryPreset, isHalfBody: poses[1].isHalfBody)
                    
                    // 交叉配对验证最优解
                    let config1 = (scoreA_primary + scoreB_secondary) / 2
                    let config2 = (scoreB_primary + scoreA_secondary) / 2
                    
                    if config1 >= config2 {
                        self.isDualMatchedPrimary = true
                        baselineScore = config1
                    } else {
                        self.isDualMatchedPrimary = false
                        baselineScore = config2
                    }
                } else {
                    // 人数不足
                    baselineScore = 0
                }
            } else {
                // 单人匹配逻辑
                if let firstPose = poses.first {
                    baselineScore = PoseMatcher.calculateSimilarity(current: firstPose.points, preset: activePrimaryPosePoints, isHalfBody: firstPose.isHalfBody)
                } else {
                    baselineScore = 0
                }
            }

            // Step 12: 俯仰角强制约束检查
            var pitchPenalty: Double = 0
            if let multi = plan.multiAngles, self.activeAngleIndex < multi.count {
                if let reqPitch = multi[self.activeAngleIndex].requiredPitch {
                    // 若要求视角在阈值之外，则直接扣除 60 分避免触发拍照
                    if reqPitch > 0 && self.manager.devicePitch < reqPitch {
                        pitchPenalty = 60
                    } else if reqPitch < 0 && self.manager.devicePitch > reqPitch {
                        pitchPenalty = 60
                    }
                }
            }
            
            let finalBaseline = max(0, baselineScore - pitchPenalty)

            let smoothed = (self.score * 0.7) + (finalBaseline * 0.3)
            withAnimation(.linear(duration: 0.1)) { self.score = smoothed }
            
            let activeThreshold = isDualPlan ? self.dualSuccessThreshold : self.successThreshold

            if smoothed > activeThreshold {
                if self.stableStartTime == nil {
                    self.stableStartTime = Date()
                    if !self.hapticCooldown {
                        self.hapticCooldown = true
                        UIImpactFeedbackGenerator(style: .rigid).impactOccurred()
                        self.speak("对齐啦，保持不动！")
                        DispatchQueue.main.asyncAfter(deadline: .now() + 3.0) { [weak self] in
                            self?.hapticCooldown = false
                        }
                    }
                } else if let start = self.stableStartTime,
                          Date().timeIntervalSince(start) > 0.8 {
                    self.stableStartTime = nil
                    self.triggerAutoPhoto()
                }
            } else {
                self.stableStartTime = nil
            }

            // MARK: P5-3 留白智能提醒 (依托于第一个人)
            if let bbox = poses.first?.bbox {
                if plan.composition != .center && abs(bbox.midX - 0.5) < 0.05 {
                    if !self.showSpaceTip && (poses.first?.points.count ?? 0) >= 4 {
                        withAnimation(.easeInOut(duration: 0.5)) { self.showSpaceTip = true }
                    }
                } else {
                    if self.showSpaceTip {
                        withAnimation(.easeInOut(duration: 0.5)) { self.showSpaceTip = false }
                    }
                }
            } else if self.showSpaceTip {
                withAnimation(.easeInOut(duration: 0.5)) { self.showSpaceTip = false }
            }

            // MARK: P1-4 姿势亲近度自动推荐
            let now = Date()
            guard now.timeIntervalSince(self.autoRecommendLastCheck) > 0.5 else { return }
            self.autoRecommendLastCheck = now
            guard now > self.userOverrideUntil else { return }
            
            // Step 10 & 12 & 13 屏蔽：已经在连拍推进或多机位/Vlog转移推进中时，彻底无视环境晃动导致的自动切方案推荐逻辑
            guard self.activeSequenceIndex == 0 && self.activeAngleIndex == 0 && self.activeVlogClipIndex == 0 && !self.isVlogRecording else { return }

            let plans = self.availablePlans
            guard plans.count > 1, let firstPose = poses.first, firstPose.points.count >= 4 else { return }

            let scores = plans.enumerated().map { idx, p in
                if p.secondaryPosePoints != nil { return (idx, 0.0) } // 目前自动推荐不覆盖双人方案，仅手动选择
                return (idx, PoseMatcher.calculateSimilarity(current: firstPose.points, preset: p.posePoints, isHalfBody: firstPose.isHalfBody))
            }
            guard let best = scores.max(by: { $0.1 < $1.1 }) else { return }

            // 最高分必须比当前方案高 8 分，且最高分 > 15，才切换（防止无意义抄动）
            let currentScore = scores[self.currentPlanIndex].1
            if best.0 != self.currentPlanIndex, best.1 > 15, best.1 - currentScore > 8 {
                withAnimation(.spring(response: 0.4, dampingFraction: 0.75)) {
                    self.currentPlanIndex = best.0
                }
                self.score = 0
                self.stableStartTime = nil
            }
        }

        // Step 13: 微表情魔法快门
        manager.visionService.onSmileDetected = { [weak self] in
            guard let self = self else { return }
            // 仅在单张拍摄并且不在录视频/多机位推进时，响应自然微笑抓拍
            if !self.isCapturing && !self.isVlogRecording && self.activeSequenceIndex == 0 && self.activeAngleIndex == 0 {
                if self.stableStartTime == nil { // 防止正在由于匹配身形打分而即将抓拍撞车
                    self.speak("笑得真好看，咔嚓！")
                    UIImpactFeedbackGenerator(style: .rigid).impactOccurred()
                    // 直接越过骨骼打分，跳跃强制截取！
                    self.triggerAutoPhoto()
                }
            }
        }
        
        // Step 14: OOTD 画幅异步回抛处理与 AI 大脑联动
        manager.onOOTDSnapshot = { [weak self] image in
            guard let self = self else { return }
            
            Task {
                // 将截图和刚切出来的新场景一起塞进多模态分析库
                let suggestion = await AIAdvisor.shared.analyzeOOTD(image: image, currentScene: self.scene)
                
                await MainActor.run {
                    withAnimation(.spring(response: 0.5, dampingFraction: 0.8)) {
                        self.aiSuggestion = suggestion
                    }
                    if self.activeSequenceIndex == 0 {
                        self.speak(suggestion)
                    }
                    DispatchQueue.main.asyncAfter(deadline: .now() + 8.5) { [weak self] in
                        withAnimation(.easeOut(duration: 0.8)) {
                            self?.aiSuggestion = nil
                        }
                    }
                }
            }
        }
        
        manager.visionService.onSceneChange = { [weak self] newScene in
            guard let self = self else { return }
            guard newScene != .unknown else { return }
            self.scanTimeoutTask?.cancel()
            self.scanTimeoutTask = nil

            let isNew = (self.scene != newScene)
            withAnimation(.easeInOut(duration: 0.5)) {
                self.scene = newScene
                self.isSceneReady = true
                if isNew { 
                    self.currentPlanIndex = 0
                    self.activeSequenceIndex = 0
                    self.activeAngleIndex = 0
                }
            }
            if isNew {
                self.score = 0
                self.stableStartTime = nil
                if let plan = self.availablePlans.first {
                    self.speak("识别到\(newScene.displayName)，推荐\(plan.poseName)，\(plan.composition.voiceHint)")
                    self.showTipBriefly()
                }
                // Step 14: 通过向大模型提交当前人物缩略图引发 OOTD 服饰爆改语音！
                // 这将替代老旧的 Step 11 纯文本硬切提示。
                DispatchQueue.main.asyncAfter(deadline: .now() + 1.0) { [weak self] in
                    self?.manager.takeOOTDSnapshot()
                }
            }
        }

        manager.visionService.onFaceDetected = { [weak self] rect in
            self?.manager.setFaceExposure(faceRect: rect)
        }

        manager.onPhotoCapture = { [weak self] image in
            guard let self = self else { return }
            self.burstImages.append(image)

            // P5-2 智能裁切：提供一个根据 bodyBoundingBox 生成近景（胸腰特写）的底片
            if let cgImage = image.cgImage, let bbox = self.detectedPoses.first?.bbox {
                let iw = CGFloat(cgImage.width)
                let ih = CGFloat(cgImage.height)

                // bbox的归一化Y是在Vision坐标系下转换得到的（我们在VisionService里做了1.0-y计算），
                // 在图像渲染中原点同样可以看做左上角，此处的矩形对应人像在全图中的边界。
                // 向上留出一点头部空间（约全体高度的 10%）
                let cropY = max(0, bbox.minY * ih - ih * 0.10)
                // 高度：如果原图是全身照，可以裁到腹部以下。比如裁切其整体身高的一半
                let cropH = min(ih - cropY, max(bbox.height * 0.5 * ih, iw * 0.8))
                // 常见社交画幅近似 4:5 (比如 Instagram 特写图)
                let cropW = cropH * 0.8
                let cx = bbox.midX * iw
                let cropX = max(0, cx - cropW / 2)

                let cropRect = CGRect(x: cropX, y: cropY, width: cropW, height: cropH)
                    .intersection(CGRect(x: 0, y: 0, width: iw, height: ih))

                if cropRect.width > iw * 0.3, let cropped = cgImage.cropping(to: cropRect) {
                    let croppedImage = UIImage(cgImage: cropped, scale: image.scale, orientation: image.imageOrientation)
                    self.burstImages.append(croppedImage)
                }
            }

            self.capturedShotsCount += 1
            if self.capturedShotsCount >= self.expectedBurstCount {
                self.isReviewingPhotos = true
            }
        }

        manager.visionService.onLowLight = { [weak self] isLow in
            guard let self = self else { return }
            withAnimation(.easeInOut(duration: 0.4)) {
                self.isLowLight = isLow
                // 同步到底层触发曝光偏置和降噪
                self.manager.isLowLightMode = isLow
            }
            
            // Step 9: 物理屏幕补光
            if isLow {
                if self.baseBrightness == nil {
                    self.baseBrightness = UIScreen.main.brightness
                }
                UIScreen.main.brightness = min((self.baseBrightness ?? 0.5) + 0.4, 1.0)
            } else {
                if let base = self.baseBrightness {
                    UIScreen.main.brightness = base
                    self.baseBrightness = nil
                }
            }
        }

        evaluateCameraState()
    }

    // MARK: - 生命周期与相机状态管理
    func evaluateCameraState() {
        if isReviewingPhotos || isReviewingVlog || showPaywall || showSessionGallery || showGuide {
            manager.stop()
        } else {
            manager.start()
        }
    }

    func startScanTimeout() {
        let task = DispatchWorkItem { [weak self] in
            guard let self = self, !self.isSceneReady else { return }
            withAnimation(.easeInOut(duration: 0.5)) {
                self.scene = .coffee_shop
                self.isSceneReady = true
                self.currentPlanIndex = 0
            }
            self.speak("未能识别背景，展示通用方案，您可以手动切换")
            self.showTipBriefly()
        }
        self.scanTimeoutTask = task
        DispatchQueue.main.asyncAfter(deadline: .now() + 8.0, execute: task)
    }

    func showTipBriefly() {
        compositionTipTask?.cancel()
        withAnimation(.spring(response: 0.4, dampingFraction: 0.8)) { showCompositionTip = true }
        let task = DispatchWorkItem { [weak self] in
            withAnimation(.easeInOut(duration: 0.4)) { self?.showCompositionTip = false }
        }
        compositionTipTask = task
        DispatchQueue.main.asyncAfter(deadline: .now() + 2.8, execute: task)
    }

    func triggerAutoPhoto() {
        if let plan = currentPlan {
            if let vlog = plan.vlogScript {
                // Step 13: Vlog 录制特例组 (最高优先级)
                executeVlogCapture(vlog: vlog)
                return
            } else if let seq = plan.sequence {
                // Step 10: 连拍序列模式
                executeSequenceCapture(seqCount: seq.count)
                return
            } else if let multi = plan.multiAngles {
                // Step 12 多机位模式
                executeMultiAngleCapture(angleCount: multi.count)
                return
            }
        }
        
        // 默认正常拍摄
        let finalCount = isPro ? 3 : 1
        speak(isPro ? "拍好了！连拍三张" : "拍好了")
        takeBurst(count: finalCount)
        score = 0
        stableStartTime = nil
    }
    
    // MARK: - Step 12 多机位专用执行体
    private func executeMultiAngleCapture(angleCount: Int) {
        guard !isCapturing else { return }
        isCapturing = true
        
        if activeAngleIndex == 0 {
            burstImages.removeAll()
            capturedShotsCount = 0
            expectedBurstCount = angleCount
        }
        
        manager.takePhoto()
        triggerFlash()
        
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) { [weak self] in
            guard let self = self else { return }
            self.isCapturing = false
            self.score = 0
            self.stableStartTime = nil
            
            if self.activeAngleIndex + 1 < angleCount {
                self.activeAngleIndex += 1
                if let nextHint = self.currentPlan?.multiAngles?[self.activeAngleIndex].voiceHint {
                    self.speak(nextHint)
                }
            } else {
                self.speak("真棒！这组机位全都囊括了。")
                self.activeAngleIndex = 0 
            }
        }
    }
    
    // MARK: - Step 13 Vlog 短片导演排演系统
    private func executeVlogCapture(vlog: VlogTemplate) {
        guard !isVlogRecording else { return }
        
        let clips = vlog.clips
        guard activeVlogClipIndex < clips.count else { return }
        
        let clip = clips[activeVlogClipIndex]
        
        // 1. 如果是首位，重置清空录制片段
        if activeVlogClipIndex == 0 {
            manager.videoRecorder.reset()
            burstImages.removeAll() // Vlog 也是走画廊 review，这里只借用生命周期
        }
        
        // 2. 播报本分镜指令
        speak(clip.voiceCommand)
        withAnimation(.easeInOut) { self.displayVlogText = clip.overlayText }
        
        // 3. 给摄影师和模特 1.5 秒反应时间去听指令
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) { [weak self] in
            guard let self = self else { return }
            self.isVlogRecording = true
            
            // 开启视频流落盘
            self.manager.videoRecorder.startRecordingChunk()
            
            // UI 反馈
            UIImpactFeedbackGenerator(style: .heavy).impactOccurred()
            
            // 4. 开始计时期长排演
            DispatchQueue.main.asyncAfter(deadline: .now() + clip.duration) { [weak self] in
                guard let self = self else { return }
                // 本段结束，进行落盘
                self.manager.videoRecorder.stopRecordingChunk { [weak self] url in
                    guard let self = self else { return }
                    self.isVlogRecording = false
                    self.score = 0
                    self.stableStartTime = nil
                    
                    if self.activeVlogClipIndex + 1 < clips.count {
                        self.activeVlogClipIndex += 1
                        print("[Vlog] 切跳至第 \(self.activeVlogClipIndex) 幕")
                        // 紧凑排播，直接触发下一幕的播报
                        self.executeVlogCapture(vlog: vlog)
                    } else {
                        // 杀青！
                        self.speak("卡！非常完美，杀青！正在缝合成交……")
                        self.activeVlogClipIndex = 0
                        withAnimation { self.displayVlogText = "🎞️ 正在合成 Vlog 大片..." }
                        
                        // 提取背景音乐 URL (如果库内存在的话)
                        var bgmURL: URL? = nil
                        if let bgmName = vlog.bgmFilename, let path = Bundle.main.path(forResource: bgmName, ofType: nil) {
                            bgmURL = URL(fileURLWithPath: path)
                        }
                        
                        // 调用工厂车间发起缝合
                        VideoMerger.merge(videoURLs: self.manager.videoRecorder.recordedChunks, bgmURL: bgmURL) { [weak self] finalURL in
                            guard let self = self else { return }
                            DispatchQueue.main.async { [weak self] in
                                guard let self = self else { return }
                                withAnimation { self.displayVlogText = "" }
                                if let finalURL = finalURL {
                                    self.exportedVlogURL = finalURL
                                    self.isReviewingVlog = true
                                } else {
                                    self.speak("抱歉，合成过程中出现故障。")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    
    // MARK: - Step 10 连拍专用执行体
    private func executeSequenceCapture(seqCount: Int) {
        guard !isCapturing else { return }
        isCapturing = true
        
        if activeSequenceIndex == 0 {
            // 序列的首拍，洗空画布
            burstImages.removeAll()
            capturedShotsCount = 0
            expectedBurstCount = seqCount
        }
        
        // 硬件抓图
        manager.takePhoto()
        triggerFlash()
        
        // 分离控制循环，延时将控制权交还给下一帧
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) { [weak self] in
            guard let self = self else { return }
            self.isCapturing = false
            self.score = 0
            self.stableStartTime = nil
            
            if self.activeSequenceIndex + 1 < seqCount {
                // 继续切下一张
                self.activeSequenceIndex += 1
                if let nextHint = self.currentPlan?.sequence?[self.activeSequenceIndex].voiceHint {
                    self.speak(nextHint)
                }
            } else {
                // 全套完成
                self.speak("真棒！收工结算。")
                // 将序列计数归零备待下次
                self.activeSequenceIndex = 0 
            }
        }
    }

    func triggerManualPhoto() {
        if let plan = currentPlan, let seq = plan.sequence, activeSequenceIndex != 0 {
            // 如果连拍进程被中途由于按了手拍打崩了，直接把它强制结束提走
            // TODO: 这里目前选择手动快门直接当作最后一张处理
            executeSequenceCapture(seqCount: activeSequenceIndex + 1)
            activeSequenceIndex = 0
        } else {
            takeBurst(count: 1)
        }
    }

    func takeBurst(count: Int) {
        guard !isCapturing else { return }
        isCapturing = true
        expectedBurstCount = count
        burstImages.removeAll()
        capturedShotsCount = 0
        var taken = 0

        func snap() {
            guard taken < count else {
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.15) { [weak self] in self?.isCapturing = false }
                return
            }
            manager.takePhoto()
            triggerFlash()
            taken += 1
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.2) {
                snap()
            }
        }
        snap()
    }

    // MARK: - 倒计时循环切换
    func cycleTimer() {
        let options = [0, 3, 5, 10]
        let cur = options.firstIndex(of: timerSeconds) ?? 0
        timerSeconds = options[(cur + 1) % options.count]
        cancelTimer()
    }

    // MARK: - 快门点击：有倒计时则启动倒计时，否则直接拍
    func handleShutterTap() {
        if timerSeconds == 0 {
            triggerManualPhoto()
        } else {
            if countdown > 0 {
                cancelTimer() // 再次点击取消倒计时
            } else {
                startCountdown()
            }
        }
    }

    func startCountdown() {
        countdown = timerSeconds
        let task = DispatchWorkItem {}
        timerTask = task
        func tick() {
            guard countdown > 0 else {
                triggerManualPhoto()
                return
            }
            UIImpactFeedbackGenerator(style: .light).impactOccurred()
            DispatchQueue.main.asyncAfter(deadline: .now() + 1) {
                guard self.countdown > 0 else { return }
                withAnimation { self.countdown -= 1 }
                tick()
            }
        }
        tick()
    }

    func cancelTimer() {
        countdown = 0
        timerTask?.cancel()
        timerTask = nil
    }

    func triggerFlash() {
        let oldBrightness = UIScreen.main.brightness
        UIScreen.main.brightness = 1.0

        // 播放系统相机原生快门音效 (系统预置 ID 1108)
        AudioServicesPlaySystemSound(1108)

        withAnimation(.easeIn(duration: 0.15)) { showShutterFlash = true }
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) { [weak self] in
            withAnimation(.easeOut(duration: 0.2)) { self?.showShutterFlash = false }
            // 恢复亮度
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.25) {
                UIScreen.main.brightness = oldBrightness
            }
        }
    }

    // MARK: - M-5: Vlog 临时文件主动清理
    func cleanupVlogTempFiles() {
        // 清理录制切片
        manager.videoRecorder.reset()
        // 清理合成成片
        if let url = exportedVlogURL {
            try? FileManager.default.removeItem(at: url)
        }
        exportedVlogURL = nil
    }

    func speak(_ text: String) {
        // M-6: 中断式语音——新指令立即替换当前播报，避免重要提示被吞
        if synthesizer.isSpeaking {
            synthesizer.stopSpeaking(at: .word)
        }
        let utterance = AVSpeechUtterance(string: text)
        utterance.voice = AVSpeechSynthesisVoice(language: "zh-CN")
        utterance.rate = 0.52
        synthesizer.speak(utterance)
    }

    func stopSpeaking() {
        synthesizer.stopSpeaking(at: .immediate)
    }

    func checkAndRequestReview() {
        savedPhotoTotalCount += 1

        guard savedPhotoTotalCount >= 3, score > successThreshold else { return }

        let currentMonth = Calendar.current.component(.month, from: Date())
        let monthKey = "\(Calendar.current.component(.year, from: Date()))-\(currentMonth)"

        if reviewRequestMonth != monthKey {
            reviewRequestMonth = monthKey
            reviewRequestCount = 0
        }

        guard reviewRequestCount < 2 else { return }

        if let scene = UIApplication.shared.connectedScenes.first(where: { $0.activationState == .foregroundActive }) as? UIWindowScene {
            SKStoreReviewController.requestReview(in: scene)
            reviewRequestCount += 1
        }
    }

    // MARK: - 方案选择
    func selectPlan(at index: Int) {
        withAnimation(.spring(response: 0.38, dampingFraction: 0.72)) {
            currentPlanIndex = index
        }
        // 用户主动选择 → 8s 内屏蔽自动推荐
        userOverrideUntil = Date().addingTimeInterval(8)
        score = 0
        stableStartTime = nil
    }

    // MARK: - 沉浸模式切换
    func toggleImmersiveMode() {
        withAnimation(.spring(response: 0.4, dampingFraction: 0.8)) {
            isImmersiveMode.toggle()
            UIImpactFeedbackGenerator(style: .light).impactOccurred()
        }
    }

    // MARK: - 自定义录制流程 (Step 7)
    
    func startRecordingCustomPlan() {
        guard !isRecordingMode else { return }
        isRecordingMode = true
        recordCountdown = 3
        
        // 倒计时捕捉
        recordTick()
    }
    
    private func recordTick() {
        if recordCountdown > 0 {
            UIImpactFeedbackGenerator(style: .medium).impactOccurred()
            DispatchQueue.main.asyncAfter(deadline: .now() + 1.0) { [weak self] in
                guard let self = self, self.isRecordingMode else { return }
                self.recordCountdown -= 1
                self.recordTick()
            }
        } else {
            // 倒计时结束，执行快照抓取
            UIImpactFeedbackGenerator(style: .heavy).impactOccurred()
            
            if let firstPose = self.detectedPoses.first, firstPose.points.count >= 5 {
                // 有效捕捉
                self.pointsToSave = firstPose.points
            } else {
                // 无效捕捉
                self.pointsToSave = nil
                self.speak("未能检测到完整的人体骨架，请站远一点。")
            }
            self.isRecordingMode = false
        }
    }
    
    func cancelRecording() {
        isRecordingMode = false
        recordCountdown = 0
        pointsToSave = nil
    }
}
