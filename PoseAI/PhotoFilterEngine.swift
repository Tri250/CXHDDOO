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
///
/// 线程安全：同一引擎实例可能被 `Task.detached` 并发调用（预览多滤镜并行生成），
/// 所有可变状态（`cache` / `sourceCIImage` 等）统一由 `lock` 保护，
/// 重计算（CIFilter 链路）在锁外执行，避免阻塞与竞态。
final class PhotoFilterEngine {

    /// 共享 CIContext（复用避免重复创建，性能关键，且 CIContext 本身线程安全）
    private static let context: CIContext = {
        if let device = MTLCreateSystemDefaultDevice() {
            return CIContext(mtlDevice: device)
        }
        return CIContext(options: [.useSoftwareRenderer: false])
    }()

    /// 受锁保护的可变状态
    private struct State {
        var cache: [String: UIImage] = [:]
        var sourceCIImage: CIImage?
        var sourceOrientation: UIImage.Orientation = .up
        var sourceScale: CGFloat = 1.0
    }

    private let lock = NSLock()
    private var state = State()

    // MARK: - 设置源图

    /// 设置要处理的原图（切换照片时调用）
    func setSource(_ image: UIImage) {
        lock.lock()
        defer { lock.unlock() }

        var newState = State()
        newState.sourceOrientation = image.imageOrientation
        newState.sourceScale = image.scale

        if let ciImage = CIImage(image: image) {
            newState.sourceCIImage = ciImage
        } else if let cgImage = image.cgImage {
            newState.sourceCIImage = CIImage(cgImage: cgImage)
        }

        // 原图直接进缓存
        newState.cache[PhotoFilter.original.rawValue] = image
        state = newState
    }

    // MARK: - 应用滤镜

    /// 获取指定滤镜处理后的图片（有缓存直接返回）
    func apply(_ filter: PhotoFilter) -> UIImage? {
        // 原图已缓存，直接命中返回
        if filter == .original {
            lock.lock()
            defer { lock.unlock() }
            return state.cache[PhotoFilter.original.rawValue]
        }

        // 读取源图快照（锁内只读，计算在锁外）
        lock.lock()
        if let cached = state.cache[filter.rawValue] {
            lock.unlock()
            return cached
        }
        guard let source = state.sourceCIImage else {
            lock.unlock()
            return nil
        }
        let orientation = state.sourceOrientation
        let scale = state.sourceScale
        lock.unlock()

        guard let output = filterOutput(for: filter, source: source) else { return nil }

        // 渲染为 UIImage（锁外，避免阻塞其他滤镜并发）
        guard let cgImage = Self.context.createCGImage(output, from: source.extent) else { return nil }
        let uiImage = UIImage(cgImage: cgImage, scale: scale, orientation: orientation)

        lock.lock()
        defer { lock.unlock() }
        state.cache[filter.rawValue] = uiImage
        return uiImage
    }

    /// 对 CIImage 应用滤镜并直接返回 CIImage（供 AVVideoComposition 视频帧处理使用）
    /// 不经过 UIImage 转换，避免视频帧处理的性能开销；此路径为无状态，无需加锁
    func applyCIFilter(_ filter: PhotoFilter, to source: CIImage) -> CIImage? {
        filterOutput(for: filter, source: source)
    }

    /// 生成缩略图预览（用于滤镜选择器，尺寸更小更快）
    func thumbnail(_ filter: PhotoFilter, size: CGSize = CGSize(width: 120, height: 120)) -> UIImage? {
        lock.lock()
        guard let source = state.sourceCIImage else {
            lock.unlock()
            return nil
        }
        let orientation = state.sourceOrientation
        let scale = state.sourceScale
        lock.unlock()

        // 先缩小再处理，速度快 10x+
        let s = min(size.width / source.extent.width, size.height / source.extent.height)
        let scaled = source.transformed(by: CGAffineTransform(scaleX: s, y: s))

        guard let result = filterOutput(for: filter, source: scaled),
              let cgImage = Self.context.createCGImage(result, from: result.extent) else { return nil }
        return UIImage(cgImage: cgImage, scale: scale, orientation: orientation)
    }

    // MARK: - 滤镜输出（纯函数，无状态）

    private func filterOutput(for filter: PhotoFilter, source: CIImage) -> CIImage? {
        switch filter {
        case .original: return source
        case .film:     return applyFilm(to: source)
        case .bw:       return applyBW(to: source)
        case .light:    return applyLight(to: source)
        case .neon:     return applyNeon(to: source)
        }
    }

    // MARK: - 滤镜实现

    /// 胶片感 Film — 青色暗部 + 暖色高光，复刻柯达胶卷调性
    private func applyFilm(to input: CIImage) -> CIImage? {
        let warmth = CIFilter(name: "CITemperatureAndTint")!
        warmth.setValue(input, forKey: kCIInputImageKey)
        warmth.setValue(CIVector(x: 6800, y: 0), forKey: "inputNeutral")
        warmth.setValue(CIVector(x: 6500, y: 0), forKey: "inputTargetNeutral")

        guard let warmed = warmth.outputImage else { return nil }

        let adjust = CIFilter(name: "CIColorControls")!
        adjust.setValue(warmed, forKey: kCIInputImageKey)
        adjust.setValue(0.95, forKey: kCIInputContrastKey)
        adjust.setValue(1.12, forKey: kCIInputSaturationKey)
        adjust.setValue(0.02, forKey: kCIInputBrightnessKey)

        guard let adjusted = adjust.outputImage else { return nil }

        let gamma = CIFilter(name: "CIGammaAdjust")!
        gamma.setValue(adjusted, forKey: kCIInputImageKey)
        gamma.setValue(0.92, forKey: "inputPower")

        return gamma.outputImage
    }

    /// 高级黑白 B&W — 大反差 + 强锐化
    private func applyBW(to input: CIImage) -> CIImage? {
        let noir = CIFilter(name: "CIPhotoEffectNoir")!
        noir.setValue(input, forKey: kCIInputImageKey)

        guard let bw = noir.outputImage else { return nil }

        let contrast = CIFilter(name: "CIColorControls")!
        contrast.setValue(bw, forKey: kCIInputImageKey)
        contrast.setValue(1.15, forKey: kCIInputContrastKey)
        contrast.setValue(0.0, forKey: kCIInputSaturationKey)

        guard let contrasted = contrast.outputImage else { return nil }

        let sharpen = CIFilter(name: "CISharpenLuminance")!
        sharpen.setValue(contrasted, forKey: kCIInputImageKey)
        sharpen.setValue(0.6, forKey: kCIInputSharpnessKey)

        return sharpen.outputImage
    }

    /// 日系清透 Light — 低对比 + 微过曝 + 低饱和
    private func applyLight(to input: CIImage) -> CIImage? {
        let exposure = CIFilter(name: "CIExposureAdjust")!
        exposure.setValue(input, forKey: kCIInputImageKey)
        exposure.setValue(0.35, forKey: kCIInputEVKey)

        guard let bright = exposure.outputImage else { return nil }

        let adjust = CIFilter(name: "CIColorControls")!
        adjust.setValue(bright, forKey: kCIInputImageKey)
        adjust.setValue(0.85, forKey: kCIInputContrastKey)
        adjust.setValue(0.75, forKey: kCIInputSaturationKey)

        guard let adjusted = adjust.outputImage else { return nil }

        let vibrance = CIFilter(name: "CIVibrance")!
        vibrance.setValue(adjusted, forKey: kCIInputImageKey)
        vibrance.setValue(-0.15, forKey: "inputAmount")

        return vibrance.outputImage
    }

    /// 城市霓虹 Neon — Teal & Orange 青橙赛博朋克
    private func applyNeon(to input: CIImage) -> CIImage? {
        let adjust = CIFilter(name: "CIColorControls")!
        adjust.setValue(input, forKey: kCIInputImageKey)
        adjust.setValue(1.20, forKey: kCIInputContrastKey)
        adjust.setValue(1.35, forKey: kCIInputSaturationKey)

        guard let adjusted = adjust.outputImage else { return nil }

        let temp = CIFilter(name: "CITemperatureAndTint")!
        temp.setValue(adjusted, forKey: kCIInputImageKey)
        temp.setValue(CIVector(x: 5500, y: 0), forKey: "inputNeutral")
        temp.setValue(CIVector(x: 7200, y: 0), forKey: "inputTargetNeutral")

        guard let cooled = temp.outputImage else { return nil }

        let vignette = CIFilter(name: "CIVignette")!
        vignette.setValue(cooled, forKey: kCIInputImageKey)
        vignette.setValue(1.5, forKey: kCIInputIntensityKey)
        vignette.setValue(1.2, forKey: kCIInputRadiusKey)

        return vignette.outputImage
    }
}
