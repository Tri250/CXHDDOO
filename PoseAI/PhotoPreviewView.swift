import SwiftUI

// MARK: - 照片预览页（拍照后展示，用户确认保存）
struct PhotoPreviewView: View {
    let images: [UIImage]
    let onSave: (UIImage, PhotoFilter) -> Void
    let onRetake: () -> Void

    @EnvironmentObject var storeManager: StoreManager
    @State private var appeared = false
    @State private var selectedIndex = 0

    // MARK: - 滤镜状态
    @State private var selectedFilter: PhotoFilter = .original
    @State private var filterEngine = PhotoFilterEngine()
    @State private var filteredImage: UIImage? = nil
    @State private var filterThumbnails: [PhotoFilter: UIImage] = [:]
    @State private var showFilters = false

    // MARK: - 裁切状态
    @State private var selectedCropRatio: CropRatio = .original
    @State private var showCropRatios = false

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()

            // 全屏照片（应用滤镜后的）
            if !images.isEmpty {
                let displayImage = filteredImage ?? (selectedIndex < images.count ? images[selectedIndex] : nil)
                if let displayImage = displayImage {
                    // 使用 GeometryReader 来实现裁切比例的实时预览
                    GeometryReader { geo in
                        let croppedImage = selectedCropRatio.apply(to: displayImage)
                        Image(uiImage: croppedImage)
                            .resizable()
                            .scaledToFit()
                            .frame(width: geo.size.width, height: geo.size.height)
                            .scaleEffect(appeared ? 1.0 : 1.05)
                            .opacity(appeared ? 1.0 : 0)
                            .animation(.easeOut(duration: 0.3), value: appeared)
                    }
                    .ignoresSafeArea()
                }
            }

            // 顶部渐变遮罩
            VStack {
                LinearGradient(
                    colors: [.black.opacity(0.6), .clear],
                    startPoint: .top, endPoint: .bottom
                )
                .frame(height: 120)
                .ignoresSafeArea()
                Spacer()
            }

            // 底部渐变遮罩 + 选择器 + 按钮
            VStack(spacing: 0) {
                Spacer()

                // 图片选择器 (仅当有连拍时显示)
                if images.count > 1 {
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 12) {
                            ForEach(Array(images.enumerated()), id: \.offset) { idx, img in
                                Image(uiImage: img)
                                    .resizable()
                                    .scaledToFill()
                                    .frame(width: 50, height: 50)
                                    .clipShape(RoundedRectangle(cornerRadius: 8))
                                    .overlay(
                                        RoundedRectangle(cornerRadius: 8)
                                            .stroke(selectedIndex == idx ? Design.accent : Color.clear, lineWidth: 2)
                                    )
                                    .onTapGesture {
                                        withAnimation { selectedIndex = idx }
                                        onImageChanged(idx)
                                    }
                            }
                        }
                        .padding(.horizontal, 24)
                    }
                    .padding(.bottom, 12)
                }

                // MARK: - 滤镜选择器
                if showFilters {
                    filterSelector
                        .transition(.move(edge: .bottom).combined(with: .opacity))
                        .padding(.bottom, 8)
                }

                // MARK: - 画幅选择器
                if showCropRatios {
                    cropRatioSelector
                        .transition(.move(edge: .bottom).combined(with: .opacity))
                        .padding(.bottom, 8)
                }

                // 底部操作栏
                LinearGradient(
                    colors: [.clear, .black.opacity(0.75)],
                    startPoint: .top, endPoint: .bottom
                )
                .frame(height: 130)
                .overlay(
                    VStack(spacing: 12) {
                        HStack(spacing: 16) {
                            // 滤镜开关按钮
                            Button {
                                withAnimation(.spring(response: 0.35, dampingFraction: 0.8)) {
                                    showFilters.toggle()
                                    if showFilters { showCropRatios = false }
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

                            // 裁切开关按钮
                            Button {
                                withAnimation(.spring(response: 0.35, dampingFraction: 0.8)) {
                                    showCropRatios.toggle()
                                    if showCropRatios { showFilters = false }
                                }
                            } label: {
                                HStack(spacing: 6) {
                                    Image(systemName: selectedCropRatio.icon)
                                        .font(.system(size: 13, weight: .medium))
                                    Text(selectedCropRatio == .original ? "画幅" : selectedCropRatio.displayName)
                                        .font(.system(size: 12, weight: .semibold))
                                }
                                .foregroundColor(selectedCropRatio != .original ? Design.accent : .white.opacity(0.7))
                                .padding(.horizontal, 14)
                                .padding(.vertical, 7)
                                .background(
                                    Capsule()
                                        .fill(selectedCropRatio != .original ? Design.accent.opacity(0.15) : Color.white.opacity(0.1))
                                )
                                .overlay(
                                    Capsule()
                                        .stroke(selectedCropRatio != .original ? Design.accent.opacity(0.4) : Color.white.opacity(0.15), lineWidth: 1)
                                )
                            }
                        }

                        // 主操作按钮行
                        HStack(spacing: 16) {
                            // 重拍
                            Button(action: onRetake) {
                                VStack(spacing: 6) {
                                    Image(systemName: "arrow.counterclockwise")
                                        .font(.system(size: 22, weight: .medium))
                                    Text("重拍")
                                        .font(.system(size: 12, weight: .medium))
                                }
                                .foregroundColor(.white.opacity(0.85))
                                .frame(maxWidth: .infinity)
                            }

                            // 保存
                            Button {
                                saveCurrentImage()
                            } label: {
                                VStack(spacing: 6) {
                                    ZStack {
                                        Circle()
                                            .fill(Design.success)
                                            .frame(width: 56, height: 56)
                                        Image(systemName: "arrow.down.to.line")
                                            .font(.system(size: 22, weight: .semibold))
                                            .foregroundColor(.black)
                                    }
                                    Text("保存")
                                        .font(.system(size: 12, weight: .semibold))
                                        .foregroundColor(Design.success)
                                }
                            }

                            // 分享（P2-4 加入水印）
                            Button {
                                shareCurrentImage()
                            } label: {
                                VStack(spacing: 6) {
                                    Image(systemName: "square.and.arrow.up")
                                        .font(.system(size: 22, weight: .medium))
                                    Text("分享")
                                        .font(.system(size: 12, weight: .medium))
                                }
                                .foregroundColor(.white.opacity(0.85))
                                .frame(maxWidth: .infinity)
                            }
                        }
                        .padding(.horizontal, 24)
                    }
                    .padding(.bottom, 44),
                    alignment: .bottom
                )
            }
            .ignoresSafeArea()
        }
        .onAppear {
            withAnimation { appeared = true }
            onImageChanged(selectedIndex)
        }
    }

    // MARK: - 滤镜选择器 UI

    private var filterSelector: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 14) {
                ForEach(PhotoFilter.allCases) { filter in
                    filterItem(filter)
                }
            }
            .padding(.horizontal, 20)
            .padding(.vertical, 8)
        }
    }

    private func filterItem(_ filter: PhotoFilter) -> some View {
        let isActive = selectedFilter == filter

        return Button {
            withAnimation(.easeInOut(duration: 0.2)) {
                selectedFilter = filter
            }
            applyFilter(filter)
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

    // MARK: - 画幅选择器 UI

    private var cropRatioSelector: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 14) {
                ForEach(CropRatio.allCases) { ratio in
                    let isActive = selectedCropRatio == ratio

                    Button {
                        withAnimation(.easeInOut(duration: 0.2)) {
                            selectedCropRatio = ratio
                        }
                    } label: {
                        VStack(spacing: 6) {
                            ZStack {
                                RoundedRectangle(cornerRadius: 10)
                                    .fill(Color.white.opacity(0.08))
                                    .frame(width: 60, height: 60)

                                Image(systemName: ratio.icon)
                                    .font(.system(size: 24))
                                    .foregroundColor(isActive ? Design.accent : .white.opacity(0.5))
                            }
                            .overlay(
                                RoundedRectangle(cornerRadius: 10)
                                    .stroke(isActive ? Design.accent : Color.clear, lineWidth: 2)
                            )
                            .shadow(color: isActive ? Design.accentGlow : .clear, radius: 6)

                            Text(ratio.displayName)
                                .font(.system(size: 11, weight: isActive ? .bold : .medium))
                                .foregroundColor(isActive ? Design.accent : .white.opacity(0.7))
                        }
                    }
                    .scaleEffect(isActive ? 1.05 : 1.0)
                    .animation(.spring(response: 0.3, dampingFraction: 0.7), value: isActive)
                }
            }
            .padding(.horizontal, 20)
            .padding(.vertical, 8)
        }
    }

    // MARK: - 滤镜操作

    /// 切换照片时重新加载滤镜
    private func onImageChanged(_ index: Int) {
        guard index < images.count else { return }
        let image = images[index]
        filterEngine.setSource(image)
        selectedFilter = .original
        filteredImage = image

        // 异步生成所有滤镜缩略图
        Task.detached(priority: .userInitiated) {
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

    /// 应用选中的滤镜
    private func applyFilter(_ filter: PhotoFilter) {
        // V2: 滤镜切换微震动反馈
        UIImpactFeedbackGenerator(style: .light).impactOccurred()
        Task.detached(priority: .userInitiated) {
            let result = filterEngine.apply(filter)
            await MainActor.run {
                if let result = result {
                    withAnimation(.easeInOut(duration: 0.15)) {
                        filteredImage = result
                    }
                }
            }
        }
    }

    /// 获取当前显示的图片（应用了滤镜的 和 裁切的）
    private func currentDisplayImage() -> UIImage? {
        let baseImage = filteredImage ?? (selectedIndex < images.count ? images[selectedIndex] : nil)
        guard let imageToCrop = baseImage else { return nil }

        // 计算裁切（如果非 original）
        return selectedCropRatio.apply(to: imageToCrop)
    }

    // MARK: - 保存与分享

    private func saveCurrentImage() {
        guard let image = currentDisplayImage() else { return }
        onSave(image, selectedFilter)
    }

    private func shareCurrentImage() {
        guard let image = currentDisplayImage() else { return }
        let watermarkedImg = storeManager.isPro ? image : image.withPoseAIWatermark()
        let av = UIActivityViewController(activityItems: [watermarkedImg], applicationActivities: nil)
        if let scene = UIApplication.shared.connectedScenes.first(where: { $0.activationState == .foregroundActive }) as? UIWindowScene,
           let root = scene.windows.first(where: { $0.isKeyWindow })?.rootViewController {
            var topVC = root
            while let presented = topVC.presentedViewController {
                topVC = presented
            }
            // iPad 支持
            if UIDevice.current.userInterfaceIdiom == .pad {
                av.popoverPresentationController?.sourceView = topVC.view
                av.popoverPresentationController?.sourceRect = CGRect(x: topVC.view.bounds.midX, y: topVC.view.bounds.maxY, width: 0, height: 0)
            }
            topVC.present(av, animated: true)
        }
    }
}

// MARK: - 可识别图片包装（用于 fullScreenCover item binding）
struct IdentifiableImage: Identifiable {
    let id = UUID()
    let image: UIImage
}

// MARK: - 水印扩展 (P2-4)
extension UIImage {
    func withPoseAIWatermark() -> UIImage {
        let renderer = UIGraphicsImageRenderer(size: self.size)
        return renderer.image { ctx in
            self.draw(in: CGRect(origin: .zero, size: self.size))

            let text = " 📸 Shot on PoseAI " as NSString
            let fontSize = max(self.size.width, self.size.height) * 0.015
            let attributes: [NSAttributedString.Key: Any] = [
                .font: UIFont.boldSystemFont(ofSize: fontSize),
                .foregroundColor: UIColor.white.withAlphaComponent(0.9),
                .backgroundColor: UIColor.black.withAlphaComponent(0.3)
            ]

            let textSize = text.size(withAttributes: attributes)
            let padding = self.size.width * 0.02
            let rect = CGRect(
                x: self.size.width - textSize.width - padding,
                y: self.size.height - textSize.height - padding,
                width: textSize.width,
                height: textSize.height
            )
            let bgPath = UIBezierPath(roundedRect: rect.insetBy(dx: -8, dy: -4), cornerRadius: 8)
            UIColor.black.withAlphaComponent(0.4).setFill()
            bgPath.fill()

            text.draw(in: rect, withAttributes: attributes)
        }
    }
}

// MARK: - 拍摄历史相册
struct SessionGallerySheet: View {
    let images: [UIImage]
    @Environment(\.dismiss) var dismiss
    @State private var selectedImage: UIImage? = nil

    let columns = [GridItem(.adaptive(minimum: 100), spacing: 2)]

    var body: some View {
        NavigationView {
            ScrollView {
                LazyVGrid(columns: columns, spacing: 2) {
                    ForEach(Array(images.enumerated()), id: \.offset) { idx, img in
                        Image(uiImage: img)
                            .resizable()
                            .scaledToFill()
                            .frame(minWidth: 0, maxWidth: .infinity, minHeight: 0, maxHeight: .infinity)
                            .aspectRatio(1, contentMode: .fill)
                            .clipped()
                            .onTapGesture { selectedImage = img }
                    }
                }
            }
            .navigationTitle("本次拍摄 (\(images.count))")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("完成") { dismiss() }
                }
            }
            .sheet(item: Binding(
                get: { selectedImage.map { IdentifiableImage(image: $0) } },
                set: { if $0 == nil { selectedImage = nil } }
            )) { wrapper in
                ZStack {
                    Color.black.ignoresSafeArea()
                    Image(uiImage: wrapper.image)
                        .resizable()
                        .scaledToFit()
                        .ignoresSafeArea()
                    VStack {
                        HStack {
                            Button { selectedImage = nil } label: {
                                Image(systemName: "xmark.circle.fill")
                                    .font(.system(size: 28))
                                    .foregroundColor(.white.opacity(0.8))
                                    .padding()
                            }
                            Spacer()
                        }
                        Spacer()
                    }
                }
            }
        }
    }
}
