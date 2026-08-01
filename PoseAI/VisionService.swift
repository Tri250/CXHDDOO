import Vision
import AVFoundation

// MARK: - AI 视觉处理服务
// 负责：高频姿态识别（~30fps）+ 低频场景分类（~0.5fps）+ 场景防抖
final class VisionService {

    // MARK: - 配置

    /// 场景识别最小间隔（秒）
    private let sceneUpdateInterval: TimeInterval = 2.0
    private var lastSceneUpdate: Date = .distantPast

    /// 场景防抖：连续 2 次相同才触发（每次间隔 2s，共 ~4s）
    private let sceneDebounceThreshold = 2
    private var sceneVoteBuffer: [SceneType] = []

    /// 场景分类器（Places365 成功则用，失败则降级到 MobileNetV2，再失败降级到 Mock）
    lazy var sceneProvider: SceneClassificationProvider = {
        let placesProvider = Places365SceneProvider()
        if placesProvider.isModelLoaded {
            #if DEBUG
            print("[VisionService] 首选启用 Places365SceneProvider")
            #endif
            return placesProvider
        }
        
        let mbProvider = MobileNetV2SceneProvider()
        guard mbProvider.isModelLoaded else {
            #if DEBUG
            print("[VisionService] MobileNetV2 和 Places365 均未加载，降级为 MockSceneProvider")
            #endif
            return MockSceneProvider()
        }
        return mbProvider
    }()

    // MARK: - 回调
    typealias PoseData = (points: [String: CGPoint], isHalfBody: Bool, bbox: CGRect?)
    
    /// points: 关节归一化坐标(0~1)
    /// isHalfBody: 是否半身
    /// bodyBoundingBox: 人体在画面中的归一化包围盒 (x,y,w,h)，可能为 nil（未检测到人）
    var onUpdate: (([PoseData]) -> Void)?
    var onSceneChange: ((SceneType) -> Void)?
    var onFaceDetected: ((CGRect?) -> Void)?
    var onSmileDetected: (() -> Void)? // Step 13: 微表情截取
    /// 暗光监测：检测到人体但关节置信度测极低时触发
    var onLowLight: ((Bool) -> Void)?
    private var lastLowLightTime: Date = .distantPast
    private let lowLightInterval: TimeInterval = 5.0
    private var isCurrentlyLowLight: Bool = false

    var isFrontCamera: Bool = false
    
    // EMA 平滑状态数组（支持双人：[Person0, Person1]）
    private var previousPointsArray: [[String: CGPoint]] = [[:], [:]]
    /// EMA 平滑系数：0.4 = 较平滑（延迟高），0.7 = 较灵敏（延迟低）
    /// 当检测到关节跳动（帧间距离>阈值）时自适应调高以减少延迟
    private let emaBaseAlpha: CGFloat = 0.45
    private let emaJitterAlpha: CGFloat = 0.7
    private let jitterThreshold: CGFloat = 0.08 // 帧间跳变阈值（归一化距离）

    // MARK: - 帧处理入口
    func process(_ buffer: CMSampleBuffer, isDegraded: Bool = false) {
        autoreleasepool {
            guard let pixelBuffer = CMSampleBufferGetImageBuffer(buffer) else { return }

            var requests: [VNRequest] = []

            // 1. 高频姿态检测（每帧执行）
            let poseRequest = VNDetectHumanBodyPoseRequest { [weak self] req, error in
                if let error = error {
                    #if DEBUG
                    print("[VisionService] Pose error: \(error.localizedDescription)")
                    #endif
                    return
                }
                self?.handlePose(req)
            }
            requests.append(poseRequest)

            // 2. 低频场景分类与人脸检测（组合 Request，优化流水线）
            let now = Date()
            let currentInterval = isDegraded ? sceneUpdateInterval * 2 : sceneUpdateInterval
            let shouldRunLowFreq = now.timeIntervalSince(lastSceneUpdate) > currentInterval
            
            if shouldRunLowFreq {
                lastSceneUpdate = now
                
                let faceRequest = VNDetectFaceLandmarksRequest { [weak self] req, _ in
                    guard let faces = req.results as? [VNFaceObservation], let firstFace = faces.first else {
                        DispatchQueue.main.async { self?.onFaceDetected?(nil) }
                        return
                    }
                    
                    DispatchQueue.main.async { self?.onFaceDetected?(firstFace.boundingBox) }
                    
                    // Step 13: 微笑判定大嘴猴模式 (Smile/Laugh Trigger)
                    // 通过外嘴唇特征点的宽度与高度计算宽高比，捕捉肆无忌惮地大小笑
                    if let lips = firstFace.landmarks?.outerLips {
                        let points = lips.normalizedPoints
                        guard points.count >= 2 else { return }
                        
                        let xs = points.map { $0.x }
                        let ys = points.map { $0.y }
                        
                        // 由于人脸不一定在中间，计算嘴唇的几何宽与高
                        let width = (xs.max() ?? 0) - (xs.min() ?? 0)
                        let height = (ys.max() ?? 0) - (ys.min() ?? 0)
                        
                        guard height > 0 else { return }
                        let mouthAspectRatio = width / height
                        
                        // 当一个人正常闭嘴时比例在 2.0 左右。咧嘴大笑会拉扯极宽且张开，但主要是宽度剧增。
                        // 或者宽度占整体脸部宽度的比例：width / boundingBox.width。
                        // 我们直接用面部比更精确防歪头：
                        let ratioToFace = width / firstFace.boundingBox.width
                        
                        if ratioToFace > 0.40 || mouthAspectRatio > 3.0 {
                            guard let self = self else { return }
                            // 防抖，避免一帧误判
                            DispatchQueue.main.async {
                                self.onSmileDetected?()
                            }
                        }
                    }
                }
                requests.append(faceRequest)
            }

            // 3. 同步执行：阻塞 Delegate 线程以触发 alwaysDiscardsLateVideoFrames 避免积压
            let handler = VNImageRequestHandler(cvPixelBuffer: pixelBuffer, options: [:])
            try? handler.perform(requests)

            if shouldRunLowFreq {
                // 场景分类依赖于 CoreML
                sceneProvider.classify(pixelBuffer: pixelBuffer) { [weak self] scene in
                    self?.handleSceneResult(scene)
                }
            }
        }
    }

    // MARK: - 场景防抖处理
    private func handleSceneResult(_ scene: SceneType) {
        // unknown 不进 buffer，但也不重置（避免偶发 unknown 打断连续性）
        guard scene != .unknown else { return }

        sceneVoteBuffer.append(scene)
        if sceneVoteBuffer.count > sceneDebounceThreshold {
            sceneVoteBuffer.removeFirst()
        }

        // 连续 N 帧一致才触发
        guard sceneVoteBuffer.count == sceneDebounceThreshold,
              sceneVoteBuffer.allSatisfy({ $0 == scene }) else { return }

        sceneVoteBuffer.removeAll()
        onSceneChange?(scene)
    }

    // MARK: - 姿态结果解析 (Step 8 重构：支持双人)
    private func handlePose(_ request: VNRequest) {
        guard let observations = request.results as? [VNHumanBodyPoseObservation], !observations.isEmpty else {
            DispatchQueue.main.async { [weak self] in self?.onUpdate?([]) }
            return
        }

        // 取前两个检测结果
        let topOps = Array(observations.prefix(2))

        var allPoses: [PoseData] = []
        var newPreviousPointsArray: [[String: CGPoint]] = [[:], [:]]
        var totalPointsCount = 0

        for (idx, obs) in topOps.enumerated() {
            var points: [String: CGPoint] = [:]
            var lowerBodyConfSum: Float = 0
            var lowerBodyCount = 0
            
            var minX: CGFloat = 1.0, maxX: CGFloat = 0.0
            var minY: CGFloat = 1.0, maxY: CGFloat = 0.0
            
            // 逐个查询目标关节点
            let targetJoints: [(VNHumanBodyPoseObservation.JointName, String)] = [
                (.leftShoulder, "leftShoulder"), (.rightShoulder, "rightShoulder"),
                (.leftElbow, "leftElbow"), (.rightElbow, "rightElbow"),
                (.leftWrist, "leftWrist"), (.rightWrist, "rightWrist"),
                (.leftHip, "leftHip"), (.rightHip, "rightHip"),
                (.leftKnee, "leftKnee"), (.rightKnee, "rightKnee"),
                (.leftAnkle, "leftAnkle"), (.rightAnkle, "rightAnkle"),
                (.neck, "neck")
            ]
            
            let oldPoints = (idx < previousPointsArray.count) ? previousPointsArray[idx] : [:]
            
            for (jointName, key) in targetJoints {
                guard let point = try? obs.recognizedPoint(jointName),
                      point.confidence > 0.2 else { continue }  // 降低阈值0.3→0.2，捕捉更多关节点
                var x = point.location.x
                if isFrontCamera { x = 1.0 - x }
                let y = 1.0 - point.location.y

                let pointRaw = CGPoint(x: x, y: y)
                let smoothedPoint: CGPoint
                if let oldPoint = oldPoints[key] {
                    // 自适应 EMA：检测到跳变时增大 alpha 以减少延迟
                    let dx = pointRaw.x - oldPoint.x
                    let dy = pointRaw.y - oldPoint.y
                    let distance = sqrt(dx * dx + dy * dy)
                    let alpha = distance > jitterThreshold ? emaJitterAlpha : emaBaseAlpha
                    smoothedPoint = CGPoint(
                        x: oldPoint.x * (1 - alpha) + pointRaw.x * alpha,
                        y: oldPoint.y * (1 - alpha) + pointRaw.y * alpha
                    )
                } else {
                    smoothedPoint = pointRaw
                }
                
                points[key] = smoothedPoint
                minX = min(minX, smoothedPoint.x); maxX = max(maxX, smoothedPoint.x)
                minY = min(minY, smoothedPoint.y); maxY = max(maxY, smoothedPoint.y)
                
                if PoseMatcher.lowerBodyJoints.contains(key) {
                    lowerBodyConfSum += point.confidence
                    lowerBodyCount += 1
                }
            }
            
            newPreviousPointsArray[idx] = points
            totalPointsCount += points.count
            
            let avgLowerConf = lowerBodyCount > 0 ? lowerBodyConfSum / Float(lowerBodyCount) : 0
            let isHalfBody = avgLowerConf < 0.25
            let bbox: CGRect? = points.count >= 3
                ? CGRect(x: minX, y: minY, width: maxX - minX, height: maxY - minY)
                : nil
                
            allPoses.append((points: points, isHalfBody: isHalfBody, bbox: bbox))
        }

        previousPointsArray = newPreviousPointsArray

        // 暗光检测：综合考虑关节点稀少和平均置信度
        let avgPointsPerPerson = topOps.count > 0 ? Double(totalPointsCount) / Double(topOps.count) : 0
        let lowLight = topOps.count > 0 && (totalPointsCount < (topOps.count * 5) || avgPointsPerPerson < 4.0)
        let now = Date()
        if lowLight != isCurrentlyLowLight {
            if now.timeIntervalSince(lastLowLightTime) > lowLightInterval || !lowLight {
                isCurrentlyLowLight = lowLight
                lastLowLightTime = now
                DispatchQueue.main.async { [weak self] in self?.onLowLight?(lowLight) }
            }
        }

        DispatchQueue.main.async { [weak self] in self?.onUpdate?(allPoses) }
    }

    // MARK: - 关节字段映射
    private func mapJointName(_ joint: VNHumanBodyPoseObservation.JointName) -> String? {
        switch joint {
        case .leftShoulder:  return "leftShoulder"
        case .rightShoulder: return "rightShoulder"
        case .leftElbow:     return "leftElbow"
        case .rightElbow:    return "rightElbow"
        case .leftWrist:     return "leftWrist"
        case .rightWrist:    return "rightWrist"
        case .leftHip:       return "leftHip"
        case .rightHip:      return "rightHip"
        case .leftKnee:      return "leftKnee"
        case .rightKnee:     return "rightKnee"
        case .leftAnkle:     return "leftAnkle"
        case .rightAnkle:    return "rightAnkle"
        case .neck:          return "neck"
        default:             return nil
        }
    }
}
