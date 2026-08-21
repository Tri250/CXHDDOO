import UIKit
import CoreImage
import CoreImage.CIFilterBuiltins

// MARK: - 滤镜预设类型
enum PhotoFilter: String, CaseIterable, Identifiable {
    case original = "original"
    case film     = "film"
    case bw       = "bw"
    case light    = "light"
    case neon     = "neon"

    var id: String { rawValue }

    var displayName: String {
        switch self {
        case .original: return "原图"
        case .film:     return "胶片"
        case .bw:       return "黑白"
        case .light:    return "日系"
        case .neon:     return "霓虹"
        }
    }

    var icon: String {
        switch self {
        case .original: return "photo"
        case .film:     return "camera.filters"
        case .bw:       return "circle.lefthalf.filled"
        case .light:    return "sun.max"
        case .neon:     return "sparkles"
        }
    }
}

// MARK: - 滤镜引擎
/// 职责：对已拍摄照片应用 CIFilter 预设
/// 4 套调色预设 + 原图，所有处理在 CPU/GPU 上同步完成
final class PhotoFilterEngine {

    /// 共享 CIContext（复用避免重复创建，性能关键）
    private static let context: CIContext = {
        // 优先使用 GPU 渲染
        if let device = MTLCreateSystemDefaultDevice() {
            return CIContext(mtlDevice: device)
        }
        return CIContext(options: [.useSoftwareRenderer: false])
    }()

    /// 滤镜缓存（同一张原图 + 同一种滤镜只处理一次）
    private var cache: [String: UIImage] = [:]

    /// 原始 CIImage（从 UIImage 转换并缓存）
    private var sourceCIImage: CIImage?
    private var sourceOrientation: UIImage.Orientation = .up
    private var sourceScale: CGFloat = 1.0

    // MARK: - 设置源图

    /// 设置要处理的原图（切换照片时调用）
    func setSource(_ image: UIImage) {
        cache.removeAll()
        sourceOrientation = image.imageOrientation
        sourceScale = image.scale

        if let ciImage = CIImage(image: image) {
            sourceCIImage = ciImage
        } else if let cgImage = image.cgImage {
            sourceCIImage = CIImage(cgImage: cgImage)
        }

        // 原图直接缓存
        cache[PhotoFilter.original.rawValue] = image
    }

    // MARK: - 应用滤镜

    /// 获取指定滤镜处理后的图片（有缓存直接返回）
    func apply(_ filter: PhotoFilter) -> UIImage? {
        // 缓存命中
        if let cached = cache[filter.rawValue] {
            return cached
        }

        guard let source = sourceCIImage else { return nil }

        let output: CIImage?

        switch filter {
        case .original:
            return cache[filter.rawValue]

        case .film:
            output = applyFilm(to: source)

        case .bw:
            output = applyBW(to: source)

        case .light:
            output = applyLight(to: source)

        case .neon:
            output = applyNeon(to: source)
        }

        guard let result = output else { return nil }

        // 渲染为 UIImage
        if let cgImage = Self.context.createCGImage(result, from: source.extent) {
            let uiImage = UIImage(cgImage: cgImage, scale: sourceScale, orientation: sourceOrientation)
            cache[filter.rawValue] = uiImage
            return uiImage
        }

        return nil
    }

    /// 对 CIImage 应用滤镜并直接返回 CIImage（供 AVVideoComposition 视频帧处理使用）
    /// 不经过 UIImage 转换，避免视频帧处理的性能开销
    func applyCIFilter(_ filter: PhotoFilter, to source: CIImage) -> CIImage? {
        switch filter {
        case .original: return source
        case .film:     return applyFilm(to: source)
        case .bw:       return applyBW(to: source)
        case .light:    return applyLight(to: source)
        case .neon:     return applyNeon(to: source)
        }
    }

    /// 生成缩略图预览（用于滤镜选择器，尺寸更小更快）
    func thumbnail(_ filter: PhotoFilter, size: CGSize = CGSize(width: 120, height: 120)) -> UIImage? {
        guard let source = sourceCIImage else { return nil }

        // 先缩小再处理，速度快 10x+
        let scale = min(size.width / source.extent.width, size.height / source.extent.height)
        let scaled = source.transformed(by: CGAffineTransform(scaleX: scale, y: scale))

        let filtered: CIImage?
        switch filter {
        case .original: filtered = scaled
        case .film:     filtered = applyFilm(to: scaled)
        case .bw:       filtered = applyBW(to: scaled)
        case .light:    filtered = applyLight(to: scaled)
        case .neon:     filtered = applyNeon(to: scaled)
        }

        guard let result = filtered,
              let cgImage = Self.context.createCGImage(result, from: result.extent) else { return nil }
        return UIImage(cgImage: cgImage)
    }

    // MARK: - 滤镜实现

    /// 胶片感 Film — 青色暗部 + 暖色高光，复刻柯达胶卷调性
    private func applyFilm(to input: CIImage) -> CIImage? {
        // 1. 色温偏暖
        let warmth = CIFilter(name: "CITemperatureAndTint")!
        warmth.setValue(input, forKey: kCIInputImageKey)
        warmth.setValue(CIVector(x: 6800, y: 0), forKey: "inputNeutral")      // 偏暖色温
        warmth.setValue(CIVector(x: 6500, y: 0), forKey: "inputTargetNeutral")

        guard let warmed = warmth.outputImage else { return nil }

        // 2. 降低对比度 + 提升饱和度 → 胶片质感
        let adjust = CIFilter(name: "CIColorControls")!
        adjust.setValue(warmed, forKey: kCIInputImageKey)
        adjust.setValue(0.95, forKey: kCIInputContrastKey)      // 微降对比
        adjust.setValue(1.12, forKey: kCIInputSaturationKey)    // 微升饱和
        adjust.setValue(0.02, forKey: kCIInputBrightnessKey)    // 微提亮

        guard let adjusted = adjust.outputImage else { return nil }

        // 3. 暗部偏青（胶片特征）— 用 Gamma 曲线模拟
        let gamma = CIFilter(name: "CIGammaAdjust")!
        gamma.setValue(adjusted, forKey: kCIInputImageKey)
        gamma.setValue(0.92, forKey: "inputPower")  // 略微提亮暗部

        return gamma.outputImage
    }

    /// 高级黑白 B&W — 大反差 + 强锐化
    private func applyBW(to input: CIImage) -> CIImage? {
        // 1. 电影级黑白
        let noir = CIFilter(name: "CIPhotoEffectNoir")!
        noir.setValue(input, forKey: kCIInputImageKey)

        guard let bw = noir.outputImage else { return nil }

        // 2. 提升对比度 → 大反差效果
        let contrast = CIFilter(name: "CIColorControls")!
        contrast.setValue(bw, forKey: kCIInputImageKey)
        contrast.setValue(1.15, forKey: kCIInputContrastKey)    // 增强对比
        contrast.setValue(0.0, forKey: kCIInputSaturationKey)   // 确保纯黑白

        guard let contrasted = contrast.outputImage else { return nil }

        // 3. 锐化强化细节
        let sharpen = CIFilter(name: "CISharpenLuminance")!
        sharpen.setValue(contrasted, forKey: kCIInputImageKey)
        sharpen.setValue(0.6, forKey: kCIInputSharpnessKey)     // 中等锐化

        return sharpen.outputImage
    }

    /// 日系清透 Light — 低对比 + 微过曝 + 低饱和
    private func applyLight(to input: CIImage) -> CIImage? {
        // 1. 提亮曝光
        let exposure = CIFilter(name: "CIExposureAdjust")!
        exposure.setValue(input, forKey: kCIInputImageKey)
        exposure.setValue(0.35, forKey: kCIInputEVKey)  // +0.35 EV 微过曝

        guard let bright = exposure.outputImage else { return nil }

        // 2. 降低饱和度 + 降低对比度 → 清透空气感
        let adjust = CIFilter(name: "CIColorControls")!
        adjust.setValue(bright, forKey: kCIInputImageKey)
        adjust.setValue(0.85, forKey: kCIInputContrastKey)      // 低对比
        adjust.setValue(0.75, forKey: kCIInputSaturationKey)    // 低饱和

        guard let adjusted = adjust.outputImage else { return nil }

        // 3. Vibrance 保护肤色（比 Saturation 更智能）
        let vibrance = CIFilter(name: "CIVibrance")!
        vibrance.setValue(adjusted, forKey: kCIInputImageKey)
        vibrance.setValue(-0.15, forKey: "inputAmount")  // 进一步柔化色彩

        return vibrance.outputImage
    }

    /// 城市霓虹 Neon — Teal & Orange 青橙赛博朋克
    private func applyNeon(to input: CIImage) -> CIImage? {
        // 1. 提升对比度和饱和度 → 霓虹色彩张力
        let adjust = CIFilter(name: "CIColorControls")!
        adjust.setValue(input, forKey: kCIInputImageKey)
        adjust.setValue(1.20, forKey: kCIInputContrastKey)      // 高对比
        adjust.setValue(1.35, forKey: kCIInputSaturationKey)    // 高饱和

        guard let adjusted = adjust.outputImage else { return nil }

        // 2. 色温偏冷 → 青色基调
        let temp = CIFilter(name: "CITemperatureAndTint")!
        temp.setValue(adjusted, forKey: kCIInputImageKey)
        temp.setValue(CIVector(x: 5500, y: 0), forKey: "inputNeutral")
        temp.setValue(CIVector(x: 7200, y: 0), forKey: "inputTargetNeutral")  // 偏冷

        guard let cooled = temp.outputImage else { return nil }

        // 3. 暗角（聚焦中心）→ 赛博氛围
        let vignette = CIFilter(name: "CIVignette")!
        vignette.setValue(cooled, forKey: kCIInputImageKey)
        vignette.setValue(1.5, forKey: kCIInputIntensityKey)    // 暗角强度
        vignette.setValue(1.2, forKey: kCIInputRadiusKey)       // 暗角半径

        return vignette.outputImage
    }
}
