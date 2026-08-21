import Foundation
import AVFoundation

final class VideoMerger {
    
    /// 将多个 MP4 切片无缝首尾相接，可选叠加一条 BGM 音轨
    /// - Parameters:
    ///   - videoURLs: 录制的各个镜头切片临时 URL
    ///   - bgmURL: 可选的背景音乐 URL
    ///   - completion: 完成后抛回成片的最终合并 URL
    static func merge(videoURLs: [URL], bgmURL: URL? = nil, completion: @escaping (URL?) -> Void) {
        guard !videoURLs.isEmpty else {
            completion(nil)
            return
        }
        
        let composition = AVMutableComposition()
        
        guard let videoTrack = composition.addMutableTrack(withMediaType: .video, preferredTrackID: kCMPersistentTrackID_Invalid) else {
            completion(nil)
            return
        }
        
        var currentTime: CMTime = .zero
        var renderSize: CGSize = .zero
        
        // 1. 拼接所有视频轨
        for url in videoURLs {
            let asset = AVAsset(url: url)
            guard let assetTrack = asset.tracks(withMediaType: .video).first else { continue }
            
            // 取第一轨的尺寸作为最后画布尺寸
            if renderSize == .zero {
                let size = assetTrack.naturalSize
                let transform = assetTrack.preferredTransform
                // 根据 Transform 判定真实宽高
                if transform.a == 0 && transform.b == 1.0 && transform.c == -1.0 && transform.d == 0 {
                    // Portrait
                    renderSize = CGSize(width: size.height, height: size.width)
                } else {
                    renderSize = size
                }
            }
            
            do {
                let timeRange = CMTimeRange(start: .zero, duration: asset.duration)
                try videoTrack.insertTimeRange(timeRange, of: assetTrack, at: currentTime)
                videoTrack.preferredTransform = assetTrack.preferredTransform
                currentTime = CMTimeAdd(currentTime, asset.duration)
            } catch {
                print("[VideoMerger] Failed to insert video chunk: \(error)")
            }
        }
        
        let totalDuration = currentTime
        
        // 2. 注入背景音乐（循环/裁剪以适应视频长)
        if let bgmURL = bgmURL, let audioTrack = composition.addMutableTrack(withMediaType: .audio, preferredTrackID: kCMPersistentTrackID_Invalid) {
            let audioAsset = AVAsset(url: bgmURL)
            if let assetAudioTrack = audioAsset.tracks(withMediaType: .audio).first {
                do {
                    var audioTime: CMTime = .zero
                    while audioTime < totalDuration {
                        let remaining = CMTimeSubtract(totalDuration, audioTime)
                        let insertDuration = CMTimeMinimum(remaining, audioAsset.duration)
                        let timeRange = CMTimeRange(start: .zero, duration: insertDuration)
                        try audioTrack.insertTimeRange(timeRange, of: assetAudioTrack, at: audioTime)
                        audioTime = CMTimeAdd(audioTime, insertDuration)
                    }
                } catch {
                    print("[VideoMerger] Failed to insert audio track: \(error)")
                }
            }
        }
        
        // 3. 压制与导出
        let outputURL = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString + "_final.mp4")
        guard let exporter = AVAssetExportSession(asset: composition, presetName: AVAssetExportPresetHighestQuality) else {
            completion(nil)
            return
        }
        
        exporter.outputURL = outputURL
        exporter.outputFileType = .mp4
        exporter.shouldOptimizeForNetworkUse = true
        
        exporter.exportAsynchronously {
            DispatchQueue.main.async {
                if exporter.status == .completed {
                    completion(outputURL)
                } else {
                    print("[VideoMerger] Export Failed: \(String(describing: exporter.error))")
                    completion(nil)
                }
            }
        }
    }
}
