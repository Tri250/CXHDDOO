import Foundation
import CoreML
import Vision
import CoreVideo

// MARK: - Places365 场景分类实现
/// 专用于室内外场景分类的提供者，直接对接 Places205/Places365 模型
class Places365SceneProvider: SceneClassificationProvider {
    private let visionQueue = DispatchQueue(label: "com.poseai.places365Queue", qos: .utility)
    private var mlModel: VNCoreMLModel?

    /// 供 VisionService 检查模型是否成功加载
    var isModelLoaded: Bool { mlModel != nil }

    init() {
        // 模型名称可能因用户导入的具体文件名而异。常见名字有 GoogLeNetPlaces 或 Places365
        // 建议用户将下载下来的模型重命名为 GoogLeNetPlaces.mlmodel
        let modelNames = ["GoogLeNetPlaces", "Places205GoogLeNet", "Places365GoogLeNet"]
        
        for name in modelNames {
            let compiledURL = Bundle.main.url(forResource: name, withExtension: "mlmodelc")
                ?? (Bundle.main.url(forResource: name, withExtension: "mlmodel")
                    .flatMap { try? MLModel.compileModel(at: $0) })
            
            if let url = compiledURL,
               let compiled = try? MLModel(contentsOf: url),
               let visionModel = try? VNCoreMLModel(for: compiled) {
                self.mlModel = visionModel
                #if DEBUG
                print("[Places365] 成功加载模型: \(name)")
                #endif
                break
            }
        }
    }

    func classify(pixelBuffer: CVPixelBuffer, completion: @escaping (SceneType) -> Void) {
        guard let mlModel = mlModel else {
            DispatchQueue.main.async { completion(.unknown) }
            return
        }

        let request = VNCoreMLRequest(model: mlModel) { req, _ in
            guard let results = req.results as? [VNClassificationObservation],
                  !results.isEmpty else {
                DispatchQueue.main.async { completion(.unknown) }
                return
            }

            // top-3 结果用于判断，防止单次抖动
            let topResults = Array(results.prefix(3))
            
            var scene: SceneType = .unknown
            var highestConfidence: Float = 0
            
            for obs in topResults {
                let id = obs.identifier.lowercased()  // Places 模型的固有长字符串，例如 "b/beach" 或 "coffee_shop"
                let w = obs.confidence
                
                if let mappedScene = self.mapLabelToSceneType(id), w > highestConfidence {
                    scene = mappedScene
                    highestConfidence = w
                }
            }

            #if DEBUG
            print("[Places365] → \(scene.rawValue) | \(topResults.map { "\($0.identifier)(\(String(format:"%.2f",$0.confidence)))" }.joined(separator:", "))")
            #endif

            DispatchQueue.main.async { completion(scene) }
        }
        
        // 场景模型推荐使用 CenterCrop 送入网络
        request.imageCropAndScaleOption = .centerCrop

        let handler = VNImageRequestHandler(cvPixelBuffer: pixelBuffer, options: [:])
        visionQueue.async { try? handler.perform([request]) }
    }
    
    // MARK: - Places 标签映射到 SceneType
    private func mapLabelToSceneType(_ label: String) -> SceneType? {
        // Places 系列模型通常会有类似 "coffee_shop", "beach", "forest_path" 等直接标定
        if label.contains("coffee") || label.contains("cafe") || label.contains("restaurant") || label.contains("diner") || label.contains("bakery") {
            return .coffee_shop
        }
        if label.contains("beach") || label.contains("ocean") || label.contains("coast") || label.contains("sea") || label.contains("lake") {
            return .beach
        }
        if label.contains("forest") || label.contains("woods") || label.contains("jungle") || label.contains("orchard") || label.contains("tree") {
            return .forest
        }
        if label.contains("street") || label.contains("downtown") || label.contains("alley") || label.contains("road") || label.contains("city") {
            return .city_street
        }
        if label.contains("park") || label.contains("plaza") || label.contains("courtyard") || label.contains("garden") || label.contains("pasture") || label.contains("field") {
            return .park
        }
        if label.contains("bedroom") || label.contains("living_room") || label.contains("kitchen") || label.contains("home") || label.contains("bathroom") || label.contains("dining_room") {
            return .indoor_home
        }
        if label.contains("neon") || label.contains("nightclub") || label.contains("pub") || label.contains("bar") || label.contains("discotheque") {
            return .neon_night
        }
        return nil
    }
}
