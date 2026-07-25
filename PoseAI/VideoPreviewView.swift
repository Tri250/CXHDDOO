import SwiftUI
import AVKit
import Photos
import CoreImage

struct VideoPreviewView: View {
    let videoURL: URL
    
    // (savedUrl: URL) -> Void
    var onSave: () -> Void
    var onRetake: () -> Void
    
    @State private var player: AVPlayer?
    @State private var isSaving = false
    @State private var saveSuccess = false
    @State private var loopObserver: NSObjectProtocol? = nil

    // MARK: - V2 滤镜状态
    @State private var selectedFilter: PhotoFilter = .original
    @State private var filterThumbnails: [PhotoFilter: UIImage] = [:]
    @State private var showFilters = false
    @State private var isExporting = false

    // CIContext 复用（GPU 渲染）
    private static let ciContext: CIContext = {
        if let device = MTLCreateSystemDefaultDevice() {
            return CIContext(mtlDevice: device)
        }
        return CIContext(options: [.useSoftwareRenderer: false])
    }()

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()
            
            // 播放层
            if let p = player {
                VideoPlayer(player: p)
                    .ignoresSafeArea()
                    .onAppear {
                        p.play()
                        // 循环播放监听（仅注册一次）
                        if loopObserver == nil {
                            loopObserver = NotificationCenter.default.addObserver(
                                forName: .AVPlayerItemDidPlayToEndTime,
                                object: p.currentItem,
                                queue: .main
                            ) { _ in
                                p.seek(to: .zero)
                                p.play()
                            }
                        }
                    }
                    .onDisappear {
                        p.pause()
                        if let observer = loopObserver {
                            NotificationCenter.default.removeObserver(observer)
                            loopObserver = nil
                        }
                    }
            } else {
                ProgressView().tint(.white)
            }
            
            // 交互层：底部按钮
            VStack(spacing: 0) {
                Spacer()

                // MARK: - V2 滤镜选择器
                if showFilters {
                    videoFilterSelector
                        .transition(.move(edge: .bottom).combined(with: .opacity))
                        .padding(.bottom, 8)
                }

                // 滤镜开关 + 操作按钮行
                VStack(spacing: 14) {
                    // 滤镜开关按钮
                    Button {
                        withAnimation(.spring(response: 0.35, dampingFraction: 0.8)) {
                            showFilters.toggle()
                        }
                    } label: {
                        HStack(spacing: 6) {
                            Image(systemName: "camera.filters")
                                .font(.system(size: 13, weight: .medium))
                            Text("调色")
                                .font(.system(size: 12, weight: .semibold))
                        }
                        .foregroundColor(selectedFilter != .original ? Design.accent : .white.opacity(0.7))
                        .padding(.horizontal, 14)
                        .padding(.vertical, 7)
                        .background(
                            Capsule()
                                .fill(selectedFilter != .original ? Design.accent.opacity(0.15) : Color.white.opacity(0.1))
                        )
                        .overlay(
                            Capsule()
                                .stroke(selectedFilter != .original ? Design.accent.opacity(0.4) : Color.white.opacity(0.15), lineWidth: 1)
                        )
                    }

                    HStack(spacing: 30) {
                        Button(action: {
                            onRetake()
                        }) {
                            Text("重拍")
                                .font(.system(size: 16, weight: .bold))
                                .foregroundColor(.white)
                                .frame(width: 120, height: 50)
                                .background(Color.white.opacity(0.2))
                                .cornerRadius(25)
                        }
                        
                        Button(action: {
                            saveVideo()
                        }) {
                            HStack {
                                if isSaving || isExporting {
                                    ProgressView()
                                        .tint(.white)
                                    if isExporting {
                                        Text("滤镜处理中…")
                                            .font(.system(size: 14, weight: .medium))
                                    }
                                } else {
                                    Image(systemName: saveSuccess ? "checkmark" : "square.and.arrow.down")
                                    Text(saveSuccess ? "已保存" : "下发相册")
                                }
                            }
                            .font(.system(size: 16, weight: .bold))
                            .foregroundColor(.white)
                            .frame(width: 160, height: 50)
                            .background(saveSuccess ? Design.success : Design.accent)
                            .cornerRadius(25)
                        }
                        .disabled(isSaving || saveSuccess || isExporting)
                    }
                }
                .padding(.bottom, 40)
            }
            
            // 顶部关闭区域，防止用户没点任何按钮就直接下滑取消的话也要中断录像
            VStack {
                HStack {
                    Spacer()
                    Button {
                        onRetake()
                    } label: {
                        Image(systemName: "xmark.circle.fill")
                            .font(.system(size: 30))
                            .foregroundColor(.white.opacity(0.8))
                    }
                    .padding()
                }
                Spacer()
            }
        }
        .onAppear {
            player = AVPlayer(url: videoURL)
            generateFilterThumbnails()
        }
    }

    // MARK: - V2 滤镜选择器 UI

    private var videoFilterSelector: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 14) {
                ForEach(PhotoFilter.allCases) { filter in
                    videoFilterItem(filter)
                }
            }
            .padding(.horizontal, 20)
            .padding(.vertical, 8)
        }
    }

    private func videoFilterItem(_ filter: PhotoFilter) -> some View {
        let isActive = selectedFilter == filter

        return Button {
            withAnimation(.easeInOut(duration: 0.2)) {
                selectedFilter = filter
            }
            // V2: 滤镜切换微震动反馈
            UIImpactFeedbackGenerator(style: .light).impactOccurred()
        } label: {
            VStack(spacing: 6) {
                // 缩略图预览
                ZStack {
                    RoundedRectangle(cornerRadius: 10)
                        .fill(Color.white.opacity(0.08))
                        .frame(width: 60, height: 60)

                    if let thumb = filterThumbnails[filter] {
                        Image(uiImage: thumb)
                            .resizable()
                            .scaledToFill()
                            .frame(width: 60, height: 60)
                            .clipShape(RoundedRectangle(cornerRadius: 10))
                    } else {
                        Image(systemName: filter.icon)
                            .font(.system(size: 20))
                            .foregroundColor(.white.opacity(0.5))
                    }
                }
                .overlay(
                    RoundedRectangle(cornerRadius: 10)
                        .stroke(isActive ? Design.accent : Color.clear, lineWidth: 2)
                )
                .shadow(color: isActive ? Design.accentGlow : .clear, radius: 6)

                // 名称
                Text(filter.displayName)
                    .font(.system(size: 11, weight: isActive ? .bold : .medium))
                    .foregroundColor(isActive ? Design.accent : .white.opacity(0.7))
            }
        }
        .scaleEffect(isActive ? 1.05 : 1.0)
        .animation(.spring(response: 0.3, dampingFraction: 0.7), value: isActive)
    }

    // MARK: - 从视频首帧生成滤镜缩略图

    private func generateFilterThumbnails() {
        Task.detached(priority: .userInitiated) {
            let asset = AVAsset(url: videoURL)
            let generator = AVAssetImageGenerator(asset: asset)
            generator.appliesPreferredTrackTransform = true
            generator.maximumSize = CGSize(width: 240, height: 240)

            guard let cgImage = try? generator.copyCGImage(at: .zero, actualTime: nil) else { return }

            let ciImage = CIImage(cgImage: cgImage)
            let filterEngine = PhotoFilterEngine()
            let sourceImage = UIImage(cgImage: cgImage)
            filterEngine.setSource(sourceImage)

            var thumbs: [PhotoFilter: UIImage] = [:]
            for filter in PhotoFilter.allCases {
                if let thumb = filterEngine.thumbnail(filter) {
                    thumbs[filter] = thumb
                }
            }

            await MainActor.run {
                filterThumbnails = thumbs
            }
        }
    }
    
    // MARK: - 存储到相册

    private func saveVideo() {
        guard !isSaving, !isExporting else { return }

        // 如果选了滤镜（非原图），先导出带滤镜的视频再保存
        if selectedFilter != .original {
            exportFilteredVideo { filteredURL in
                if let filteredURL = filteredURL {
                    self.saveToPhotoLibrary(url: filteredURL)
                } else {
                    // 导出失败，降级保存原视频
                    self.saveToPhotoLibrary(url: videoURL)
                }
            }
        } else {
            saveToPhotoLibrary(url: videoURL)
        }
    }

    // MARK: - V2 视频滤镜导出

    private func exportFilteredVideo(completion: @escaping (URL?) -> Void) {
        isExporting = true
        let filter = selectedFilter

        Task.detached(priority: .userInitiated) {
            let asset = AVAsset(url: videoURL)
            let filterEngine = PhotoFilterEngine()

            // 构建带 CIFilter 的 VideoComposition
            let videoComposition = AVMutableVideoComposition(asset: asset) { request in
                let source = request.sourceImage.clampedToExtent()
                if let filtered = filterEngine.applyCIFilter(filter, to: source) {
                    // 裁切回原始范围（clampedToExtent 会扩展到无限）
                    let cropped = filtered.cropped(to: request.sourceImage.extent)
                    request.finish(with: cropped, context: Self.ciContext)
                } else {
                    request.finish(with: request.sourceImage, context: Self.ciContext)
                }
            }

            // 导出
            let outputURL = FileManager.default.temporaryDirectory
                .appendingPathComponent("PoseAI_filtered_\(UUID().uuidString).mov")

            guard let exportSession = AVAssetExportSession(asset: asset, presetName: AVAssetExportPresetHighestQuality) else {
                await MainActor.run {
                    isExporting = false
                    completion(nil)
                }
                return
            }

            exportSession.videoComposition = videoComposition
            exportSession.outputURL = outputURL
            exportSession.outputFileType = .mov

            await exportSession.export()

            await MainActor.run {
                isExporting = false
                if exportSession.status == .completed {
                    completion(outputURL)
                } else {
                    print("[VideoPreview] 滤镜导出失败: \(String(describing: exportSession.error))")
                    completion(nil)
                }
            }
        }
    }

    // MARK: - 保存到系统相册

    private func saveToPhotoLibrary(url: URL) {
        isSaving = true

        PHPhotoLibrary.requestAuthorization(for: .addOnly) { status in
            guard status == .authorized || status == .limited else {
                DispatchQueue.main.async {
                    self.isSaving = false
                }
                return
            }
            
            PHPhotoLibrary.shared().performChanges({
                PHAssetChangeRequest.creationRequestForAssetFromVideo(atFileURL: url)
            }) { saved, error in
                DispatchQueue.main.async {
                    self.isSaving = false
                    if saved {
                        self.saveSuccess = true
                        UIImpactFeedbackGenerator(style: .medium).impactOccurred()
                        DispatchQueue.main.asyncAfter(deadline: .now() + 1.0) {
                            self.onSave()
                        }
                    } else {
                        print("[VideoPreview] 出错: \(String(describing: error))")
                    }
                }
            }
        }
    }
}
