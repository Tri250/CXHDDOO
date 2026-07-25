import Foundation
import UIKit
import ImageIO
import UniformTypeIdentifiers

struct GIFExporter {
    /// 将一组 UIImage 导出为本机缓存目录下的 GIF 动图
    /// - Parameters:
    ///   - images: 需要压制的图像数组
    ///   - delays: 每一帧的停留时间（若是 nil 默认 0.3）
    ///   - filename: 生成的文件名称标定
    ///   - completion: 异步处理完毕后回调产生的 URL
    static func export(images: [UIImage], delays: [Double]? = nil, filename: String = "sequence.gif", completion: @escaping (URL?) -> Void) {
        DispatchQueue.global(qos: .userInitiated).async {
            let fileManager = FileManager.default
            let tmpPath = fileManager.temporaryDirectory.appendingPathComponent(filename)
            
            guard let destination = CGImageDestinationCreateWithURL(
                tmpPath as CFURL,
                UTType.gif.identifier as CFString,
                images.count,
                nil
            ) else {
                DispatchQueue.main.async { completion(nil) }
                return
            }
            
            // LoopCount: 0 表示无限循环播放
            let fileProperties = [kCGImagePropertyGIFDictionary: [kCGImagePropertyGIFLoopCount: 0] as [CFString: Any]] as CFDictionary
            CGImageDestinationSetProperties(destination, fileProperties)
            
            for (index, image) in images.enumerated() {
                guard let cgImage = image.cgImage else { continue }
                
                let delay = delays?[index] ?? 0.3
                let frameProperties = [kCGImagePropertyGIFDictionary: [kCGImagePropertyGIFDelayTime: delay]] as CFDictionary
                
                CGImageDestinationAddImage(destination, cgImage, frameProperties)
            }
            
            let success = CGImageDestinationFinalize(destination)
            DispatchQueue.main.async {
                if success {
                    completion(tmpPath)
                } else {
                    completion(nil)
                }
            }
        }
    }
}
