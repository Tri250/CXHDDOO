import Photos
import UIKit
import SwiftData

/// 相册工具：保存图片并获取 localIdentifier，读取已保存图片
final class PhotoAlbumUtil: NSObject {
    static let shared = PhotoAlbumUtil()

    /// 保存图片到系统相册，返回 asset 的 localIdentifier
    func saveToAlbumAndGetIdentifier(image: UIImage) async throws -> String? {
        let status = await PHPhotoLibrary.requestAuthorization(for: .readWrite)
        guard status == .authorized || status == .limited else {
            return nil
        }

        var localId: String?

        try await PHPhotoLibrary.shared().performChanges {
            let request = PHAssetChangeRequest.creationRequestForAsset(from: image)
            localId = request.placeholderForCreatedAsset?.localIdentifier
        }

        return localId
    }

    /// 根据 localIdentifier 读取图片
    func fetchImage(by localIdentifier: String) async -> UIImage? {
        let status = await PHPhotoLibrary.requestAuthorization(for: .readWrite)
        guard status == .authorized || status == .limited else { return nil }

        let assets = PHAsset.fetchAssets(withLocalIdentifiers: [localIdentifier], options: nil)
        guard let asset = assets.firstObject else { return nil }

        let manager = PHImageManager.default()
        let options = PHImageRequestOptions()
        options.isSynchronous = false
        options.deliveryMode = .highQualityFormat
        options.isNetworkAccessAllowed = true

        return await withCheckedContinuation { continuation in
            manager.requestImage(for: asset, targetSize: PHImageManagerMaximumSize, contentMode: .aspectFit, options: options) { image, _ in
                continuation.resume(returning: image)
            }
        }
    }
}
