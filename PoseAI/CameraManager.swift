import AVFoundation
import SwiftUI
import CoreMotion

// MARK: - 摄像头管理器
// 负责 AVCaptureSession 的生命周期、前后置切换、帧输出
final class CameraManager: NSObject, ObservableObject {

    // MARK: - 公开属性
    let session = AVCaptureSession()
    let visionService = VisionService()
    let videoRecorder = VideoRecorder() // Step 13: 录像流分支

    /// 拍照成功回调，传回原始 UIImage，由上层 UI 决定是否保存
    var onPhotoCapture: ((UIImage) -> Void)?
    
    // Step 14: 为大模型 OOTD 分析抽取的隐式图片快照回调
    var onOOTDSnapshot: ((UIImage) -> Void)?
    private var pendingOOTDRequest: Bool = false

    @Published var isFront: Bool = false {
        didSet {
            visionService.isFrontCamera = isFront
            configure() // 切换摄像头时重新配置 session
        }
    }

    @Published var authorizationStatus: AVAuthorizationStatus = .notDetermined
    
    // MARK: - Step 9 暗光控制模式
    @Published var isLowLightMode: Bool = false {
        didSet { applyLowLightSettings() }
    }
    
    /// 设备俯仰角（弧度）：正数仰拍，负数俯拍（手机上半部向前倾）。例如 -0.4 约等于俯角 23度
    @Published var devicePitch: Double = 0.0

    // MARK: - 私有属性
    private let videoOutput = AVCaptureVideoDataOutput()
    private let photoOutput = AVCapturePhotoOutput()
    private let motionManager = CMMotionManager()
    private let frameQueue = DispatchQueue(
        label: "com.poseai.videoQueue",
        qos: .userInteractive
    )

    // MARK: - Init
    override init() {
        super.init()
        UIDevice.current.isBatteryMonitoringEnabled = true
        checkAuthorization()
        startMotionTracking()
    }
    
    private func startMotionTracking() {
        guard motionManager.isDeviceMotionAvailable else { return }
        motionManager.deviceMotionUpdateInterval = 0.2 // 1秒5次足够防直男拍照
        motionManager.startDeviceMotionUpdates(to: .main) { [weak self] motion, _ in
            guard let motion = motion else { return }
            self?.devicePitch = motion.attitude.pitch
        }
    }

    // MARK: - 权限检查
    func checkAuthorization() {
        switch AVCaptureDevice.authorizationStatus(for: .video) {
        case .authorized:
            authorizationStatus = .authorized
            configure()
        case .notDetermined:
            AVCaptureDevice.requestAccess(for: .video) { [weak self] granted in
                DispatchQueue.main.async {
                    self?.authorizationStatus = granted ? .authorized : .denied
                    if granted { self?.configure() }
                }
            }
        case .denied, .restricted:
            authorizationStatus = .denied
        @unknown default:
            break
        }
    }

    // MARK: - Session 配置
    func configure() {
        // 在后台线程配置，避免阻塞主线程
        DispatchQueue.global(qos: .userInitiated).async { [weak self] in
            self?.setupSession()
        }
    }

    private func setupSession() {
        session.beginConfiguration()
        session.sessionPreset = .hd1280x720

        // 移除旧输入
        session.inputs.forEach { session.removeInput($0) }

        // 添加新摄像头输入
        let position: AVCaptureDevice.Position = isFront ? .front : .back
        if let device = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: position),
           let input = try? AVCaptureDeviceInput(device: device),
           session.canAddInput(input) {
            session.addInput(input)
        }

        // 添加视频输出（仅首次）
        if !session.outputs.contains(videoOutput) {
            videoOutput.setSampleBufferDelegate(self, queue: frameQueue)
            videoOutput.alwaysDiscardsLateVideoFrames = true // 保持实时性，丢弃积压帧
            if session.canAddOutput(videoOutput) {
                session.addOutput(videoOutput)
            }
        }

        // 添加拍照输出（仅首次）
        if !session.outputs.contains(photoOutput) {
            if session.canAddOutput(photoOutput) {
                session.addOutput(photoOutput)
            }
        }

        // 修正视频方向
        if let connection = videoOutput.connection(with: .video) {
            if connection.isVideoOrientationSupported {
                connection.videoOrientation = .portrait
            }
            if connection.isVideoMirroringSupported {
                connection.isVideoMirrored = isFront
            }
        }

        session.commitConfiguration()
    }

    // MARK: - Session 控制
    func start() {
        guard !session.isRunning else { return }
        DispatchQueue.global(qos: .userInitiated).async { [weak self] in
            self?.session.startRunning()
        }
    }

    func stop() {
        guard session.isRunning else { return }
        DispatchQueue.global(qos: .background).async { [weak self] in
            self?.session.stopRunning()
        }
    }

    // MARK: - P5-5 人脸曝光补偿 & Step 9 暗光提亮
    func setFaceExposure(faceRect: CGRect?) {
        guard let device = session.inputs.compactMap({ ($0 as? AVCaptureDeviceInput)?.device }).first else { return }
        do {
            try device.lockForConfiguration()
            
            // Step 9: 原基础根据环境是否处于弱光决定 base bias
            let baseBias: Float = isLowLightMode ? 1.5 : 0.0
            
            if let rect = faceRect {
                if device.isExposurePointOfInterestSupported {
                    let sensorX: CGFloat
                    let sensorY: CGFloat
                    if isFront {
                        sensorX = 1.0 - rect.midY
                        sensorY = 1.0 - rect.midX
                    } else {
                        sensorX = rect.midY
                        sensorY = rect.midX
                    }
                    device.exposurePointOfInterest = CGPoint(x: sensorX, y: sensorY)
                    device.exposureMode = .continuousAutoExposure
                }
                // 在 baseBias 基础上叠加 0.5 补偿人脸高光
                let finalBias = min(baseBias + 0.5, device.maxExposureTargetBias)
                device.setExposureTargetBias(finalBias, completionHandler: nil)
            } else {
                if device.isExposurePointOfInterestSupported {
                    device.exposurePointOfInterest = CGPoint(x: 0.5, y: 0.5)
                    device.exposureMode = .continuousAutoExposure
                }
                device.setExposureTargetBias(baseBias, completionHandler: nil)
            }
            device.unlockForConfiguration()
        } catch {
            print("[CameraManager] Failed to lock device for exposure bias.")
        }
    }
    
    // MARK: - Step 9 暗光帧率降维以延时曝光
    private func applyLowLightSettings() {
        guard let device = session.inputs.compactMap({ ($0 as? AVCaptureDeviceInput)?.device }).first else { return }
        do {
            try device.lockForConfiguration()
            
            if isLowLightMode {
                if device.isLowLightBoostSupported {
                    device.automaticallyEnablesLowLightBoostWhenAvailable = true
                }
                // 从 30fps 的 1/30s 变成放宽到 1/15s 增加一倍入光量（视觉上有轻微拖拉，但成相亮度提升巨大）
                device.activeVideoMinFrameDuration = CMTime(value: 1, timescale: 15)
            } else {
                if device.isLowLightBoostSupported {
                    device.automaticallyEnablesLowLightBoostWhenAvailable = false
                }
                // 恢复标准 30 fps
                device.activeVideoMinFrameDuration = CMTime(value: 1, timescale: 30)
            }
            device.unlockForConfiguration()
            
            // 顺便触发一遍曝光逻辑将基础 bias 写进去
            setFaceExposure(faceRect: nil)
            
        } catch {
            print("[CameraManager] Failed to apply low light video settings.")
        }
    }

    // MARK: - 拍照接口
    func takePhoto() {
        // 这里的配置极其重要：防止照出来的原图是横向的或是没有前置镜像的！ (经典底层Bug修复)
        if let connection = photoOutput.connection(with: .video) {
            if connection.isVideoOrientationSupported {
                connection.videoOrientation = .portrait
            }
            if connection.isVideoMirroringSupported {
                connection.isVideoMirrored = isFront
            }
        }
        
        let settings = AVCapturePhotoSettings()
        // 建议开启防抖，特别适用于这种需要定格 0.5 秒的抓拍场景
        settings.photoQualityPrioritization = .balanced
        photoOutput.capturePhoto(with: settings, delegate: self)
    }
    
    // MARK: - Step 14: 抽像 OOTD (低分辨率模型看图)
    func takeOOTDSnapshot() {
        pendingOOTDRequest = true
    }
}

// MARK: - AVCapturePhotoCaptureDelegate
extension CameraManager: AVCapturePhotoCaptureDelegate {
    func photoOutput(_ output: AVCapturePhotoOutput, didFinishProcessingPhoto photo: AVCapturePhoto, error: Error?) {
        guard error == nil else {
            print("📸 照片捕获失败: \(String(describing: error))")
            return
        }
        guard let imageData = photo.fileDataRepresentation(),
              let originalImage = UIImage(data: imageData) else { return }

        // 触觉反馈
        UIImpactFeedbackGenerator(style: .medium).impactOccurred()

        // Step 9: 如果在暗光模式下出片，施加机器降噪滤镜消除噪点
        var finalImage = originalImage
        if isLowLightMode {
            if let ciImage = CIImage(image: originalImage),
               let filter = CIFilter(name: "CINoiseReduction") {
                filter.setValue(ciImage, forKey: kCIInputImageKey)
                // 噪点减弱程度 (0.0~1.0) 和锐度维持 (0.0~1.0)
                filter.setValue(0.5, forKey: "inputNoiseLevel")
                filter.setValue(0.6, forKey: "inputSharpness")
                
                let context = CIContext(options: nil)
                if let output = filter.outputImage,
                   let cgImage = context.createCGImage(output, from: output.extent) {
                    finalImage = UIImage(cgImage: cgImage, scale: originalImage.scale, orientation: originalImage.imageOrientation)
                }
            }
        }

        // 回调给上层（ContentView 负责展示预览 + 用户确认后保存）
        DispatchQueue.main.async { [weak self] in
            self?.onPhotoCapture?(finalImage)
        }
    }
}

// MARK: - AVCaptureVideoDataOutputSampleBufferDelegate
extension CameraManager: AVCaptureVideoDataOutputSampleBufferDelegate {
    private struct State { static var frameCounter: Int = 0 }

    func captureOutput(
        _ output: AVCaptureOutput,
        didOutput sampleBuffer: CMSampleBuffer,
        from connection: AVCaptureConnection
    ) {
        let thermal = ProcessInfo.processInfo.thermalState
        let isDegraded = thermal == .serious || thermal == .critical ||
                         (UIDevice.current.batteryState != .charging && UIDevice.current.batteryLevel > 0 && UIDevice.current.batteryLevel < 0.1)
                         
        if isDegraded {
            State.frameCounter += 1
            if State.frameCounter % 2 != 0 { return }
        } else {
            State.frameCounter = 0
        }
        
        visionService.process(sampleBuffer, isDegraded: isDegraded)
        
        // Step 13: 录像分发 (注意要给 VideoRecorder 喂食实时帧)
        if videoRecorder.isRecording {
            videoRecorder.append(sampleBuffer: sampleBuffer)
        }
        
        // Step 14: OOTD 需求触发时提取低清画面
        if pendingOOTDRequest {
            pendingOOTDRequest = false
            if let pixelBuffer = CMSampleBufferGetImageBuffer(sampleBuffer) {
                let ciImage = CIImage(cvPixelBuffer: pixelBuffer)
                let context = CIContext()
                
                // 将大像素强行暴力缩减为低分辨率，极大节约大模型计算所需内存并且加快传输
                let scale: CGFloat = 512.0 / CGFloat(max(CVPixelBufferGetWidth(pixelBuffer), CVPixelBufferGetHeight(pixelBuffer)))
                let scaledImage = ciImage.transformed(by: CGAffineTransform(scaleX: scale, y: scale))
                
                if let cgImage = context.createCGImage(scaledImage, from: scaledImage.extent) {
                    // CVPixelBuffer 默认是横着的，需要根据是不是前置处理方向
                    let orientation: UIImage.Orientation = isFront ? .leftMirrored : .right
                    let uiImage = UIImage(cgImage: cgImage, scale: 1.0, orientation: orientation)
                    DispatchQueue.main.async { [weak self] in
                        self?.onOOTDSnapshot?(uiImage)
                    }
                }
            }
        }
    }
}

// MARK: - SwiftUI 摄像头预览层
struct CameraPreview: UIViewRepresentable {
    @ObservedObject var manager: CameraManager

    func makeUIView(context: Context) -> PreviewUIView {
        let view = PreviewUIView()
        view.session = manager.session
        return view
    }

    func updateUIView(_ uiView: PreviewUIView, context: Context) {
        // 前后置切换时更新预览层（镜像由 AVCaptureConnection 处理）
    }

    // MARK: 自定义 UIView 确保预览层自动适配尺寸
    class PreviewUIView: UIView {
        override class var layerClass: AnyClass {
            AVCaptureVideoPreviewLayer.self
        }

        var previewLayer: AVCaptureVideoPreviewLayer {
            layer as! AVCaptureVideoPreviewLayer
        }

        var session: AVCaptureSession? {
            didSet { previewLayer.session = session }
        }

        override func layoutSubviews() {
            super.layoutSubviews()
            previewLayer.videoGravity = .resizeAspectFill
            previewLayer.frame = bounds
        }
    }
}
