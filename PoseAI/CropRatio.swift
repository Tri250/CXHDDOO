import UIKit

// MARK: - 照片裁切比例
enum CropRatio: String, CaseIterable, Identifiable {
    case original = "original"
    case square   = "1:1"
    case fourThree = "4:3"
    case sixteenNine = "16:9"
    case cinema   = "2.35:1"

    var id: String { rawValue }

    var displayName: String {
        switch self {
        case .original: return "原比例"
        case .square:   return "正方形"
        case .fourThree: return "4:3"
        case .sixteenNine: return "16:9"
        case .cinema:   return "电影宽幅"
        }
    }

    var icon: String {
        switch self {
        case .original: return "rectangle"
        case .square:   return "square"
        case .fourThree: return "rectangle.ratio.4.to.3"
        case .sixteenNine: return "rectangle.ratio.16.to.9"
        case .cinema:   return "pano.fill"
        }
    }

    /// 应用裁切（居中裁切）
    func apply(to image: UIImage) -> UIImage {
        if self == .original { return image }

        guard let cgImage = image.cgImage else { return image }

        let originalWidth = CGFloat(cgImage.width)
        let originalHeight = CGFloat(cgImage.height)

        let targetRatio: CGFloat
        switch self {
        case .square: targetRatio = 1.0
        case .fourThree: targetRatio = 4.0 / 3.0
        case .sixteenNine: targetRatio = 16.0 / 9.0
        case .cinema: targetRatio = 2.35 / 1.0
        default: targetRatio = originalWidth / originalHeight
        }

        let currentRatio = originalWidth / originalHeight

        var cropWidth = originalWidth
        var cropHeight = originalHeight

        // 计算居中裁切区域
        if currentRatio > targetRatio {
            // 原图更宽，依据高度裁切宽度
            cropWidth = originalHeight * targetRatio
        } else if currentRatio < targetRatio {
            // 原图更高，依据宽度裁切高度
            cropHeight = originalWidth / targetRatio
        } else {
            return image
        }

        let x = (originalWidth - cropWidth) / 2.0
        let y = (originalHeight - cropHeight) / 2.0

        let cropRect = CGRect(x: x, y: y, width: cropWidth, height: cropHeight)

        guard let croppedCgImage = cgImage.cropping(to: cropRect) else { return image }

        return UIImage(cgImage: croppedCgImage, scale: image.scale, orientation: image.imageOrientation)
    }
}
