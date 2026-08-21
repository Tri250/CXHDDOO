import Foundation
import CoreMedia
import AVFoundation

final class VideoRecorder: NSObject, ObservableObject {
    @Published var isRecording = false
    
    // 我们在这个目录统一存放缓存的所有片段
    lazy var tempDir: URL = {
        let dir = FileManager.default.temporaryDirectory.appendingPathComponent("VlogChunks")
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        return dir
    }()
    
    private var assetWriter: AVAssetWriter?
    private var videoInput: AVAssetWriterInput?
    private var currentChunkURL: URL?
    private var hasStartedWriting = false
    
    /// 串行队列：保护 AVAssetWriter 的所有读写操作，防止竞态
    private let writerQueue = DispatchQueue(label: "com.poseai.videoRecorder.writer")
    
    // 拍摄的所有片段列表
    var recordedChunks: [URL] = []
    
    // 配置
    private let videoWidth: Int
    private let videoHeight: Int

    init(width: Int = 720, height: Int = 1280) {
        self.videoWidth = width
        self.videoHeight = height
    }

    /// 开启新片段录制
    func startRecordingChunk() {
        writerQueue.sync {
            guard !isRecording else { return }
            
            let fileURL = tempDir.appendingPathComponent(UUID().uuidString + ".mp4")
            do {
                assetWriter = try AVAssetWriter(outputURL: fileURL, fileType: .mp4)
                
                let settings: [String: Any] = [
                    AVVideoCodecKey: AVVideoCodecType.hevc,
                    AVVideoWidthKey: videoWidth,
                    AVVideoHeightKey: videoHeight
                ]
                let input = AVAssetWriterInput(mediaType: .video, outputSettings: settings)
                input.expectsMediaDataInRealTime = true
                
                if assetWriter!.canAdd(input) {
                    assetWriter!.add(input)
                }
                
                videoInput = input
                currentChunkURL = fileURL
                hasStartedWriting = false
                
                assetWriter?.startWriting()
                
                DispatchQueue.main.async { self.isRecording = true }
            } catch {
                print("[VideoRecorder] Init AVAssetWriter Error: \(error)")
            }
        }
    }

    /// 追加帧数据（从 Delegate 回调队列调用）
    func append(sampleBuffer: CMSampleBuffer) {
        writerQueue.sync {
            guard isRecording, let writer = assetWriter, let input = videoInput else { return }
            
            if !hasStartedWriting {
                hasStartedWriting = true
                let time = CMSampleBufferGetPresentationTimeStamp(sampleBuffer)
                writer.startSession(atSourceTime: time)
            }
            
            if input.isReadyForMoreMediaData {
                input.append(sampleBuffer)
            }
        }
    }

    /// 停止录制当前片段
    func stopRecordingChunk(completion: @escaping (URL?) -> Void) {
        writerQueue.async { [weak self] in
            guard let self = self else { return }
            guard self.isRecording, let writer = self.assetWriter, let input = self.videoInput else {
                DispatchQueue.main.async { completion(nil) }
                return
            }
            
            DispatchQueue.main.async { self.isRecording = false }
            input.markAsFinished()
            
            writer.finishWriting {
                if writer.status == .completed, let url = self.currentChunkURL {
                    DispatchQueue.main.async {
                        self.recordedChunks.append(url)
                        completion(url)
                    }
                } else {
                    print("[VideoRecorder] Failed to writing chunk: \(String(describing: writer.error))")
                    DispatchQueue.main.async { completion(nil) }
                }
                self.assetWriter = nil
                self.videoInput = nil
                self.currentChunkURL = nil
            }
        }
    }
    
    func reset() {
        writerQueue.sync {
            // I-6 修复：如果正在录制，先强制停止
            if isRecording, let writer = assetWriter, let input = videoInput {
                input.markAsFinished()
                writer.cancelWriting()
            }
            assetWriter = nil
            videoInput = nil
            currentChunkURL = nil
            hasStartedWriting = false
            
            recordedChunks.forEach { try? FileManager.default.removeItem(at: $0) }
            recordedChunks.removeAll()
            DispatchQueue.main.async { self.isRecording = false }
        }
    }
}
