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

            // top-5 加权投票：累积匹配标签的置信度，取总权重最高的场景
            let topResults = Array(results.prefix(5))

            var votes: [SceneType: Float] = [
                .coffee_shop: 0, .beach: 0, .forest: 0,
                .city_street: 0, .park: 0, .indoor_home: 0, .neon_night: 0
            ]

            for obs in topResults {
                let id = obs.identifier.lowercased()
                let w = obs.confidence
                if let mappedScene = self.mapLabelToSceneType(id) {
                    votes[mappedScene]! += w
                }
            }

            let scene = votes.max(by: { $0.value < $1.value })?.key ?? .unknown

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
    
    // MARK: - Places 标签映射到 SceneType（加权投票版）
    /// 对 top-N 结果进行加权投票：每个标签映射到场景后乘以置信度权重
    /// 返回 (场景类型, 权重) 的数组，供外部聚合
    private func mapLabelToSceneType(_ label: String) -> SceneType? {
        // Places365 标签全集映射（覆盖 365 类中所有与 7 大场景相关的标签）
        // 咖啡馆/餐饮场景
        if label.contains("coffee") || label.contains("cafe") || label.contains("cafeteria")
            || label.contains("restaurant") || label.contains("diner") || label.contains("bakery")
            || label.contains("bistro") || label.contains("tea_house") || label.contains("pub")
            || label.contains("bar") || label.contains("lounge") || label.contains("tavern")
            || label.contains("food_court") || label.contains("snack_bar") || label.contains("ice_cream_parlor") {
            return .coffee_shop
        }
        // 海边/水景场景
        if label.contains("beach") || label.contains("ocean") || label.contains("coast")
            || label.contains("sea") || label.contains("lake") || label.contains("seashore")
            || label.contains("sand") || label.contains("pier") || label.contains("dock")
            || label.contains("boardwalk") || label.contains("lagoon") || label.contains("bayou")
            || label.contains("wharf") || label.contains("boathouse") || label.contains("waves") {
            return .beach
        }
        // 森林/自然场景
        if label.contains("forest") || label.contains("woods") || label.contains("jungle")
            || label.contains("orchard") || label.contains("tree_farm") || label.contains("grove")
            || label.contains("rainforest") || label.contains("bamboo_forest")
            || label.contains("botanical_garden") || label.contains("canopy")
            || label.contains("moss") || label.contains("swamp") || label.contains("wetland") {
            return .forest
        }
        // 城市街道场景
        if label.contains("street") || label.contains("downtown") || label.contains("alley")
            || label.contains("road") || label.contains("city") || label.contains("avenue")
            || label.contains("boulevard") || label.contains("highway") || label.contains("freeway")
            || label.contains("intersection") || label.contains("crosswalk") || label.contains("overpass")
            || label.contains("skyscraper") || label.contains("office_building") || label.contains("tower")
            || label.contains("bridge") || label.contains("viaduct") || label.contains("embassy") {
            return .city_street
        }
        // 公园/户外场景
        if label.contains("park") || label.contains("plaza") || label.contains("courtyard")
            || label.contains("garden") || label.contains("pasture") || label.contains("field")
            || label.contains("playground") || label.contains("recreation") || label.contains("campus")
            || label.contains("athletic_field") || label.contains("stadium") || label.contains("fairway")
            || label.contains("golf_course") || label.contains("outdoor") || label.contains("picnic_area") {
            return .park
        }
        // 室内家居场景
        if label.contains("bedroom") || label.contains("living_room") || label.contains("kitchen")
            || label.contains("home") || label.contains("bathroom") || label.contains("dining_room")
            || label.contains("apartment") || label.contains("house") || label.contains("attic")
            || label.contains("basement") || label.contains("nursery") || label.contains("closet")
            || label.contains("hallway") || label.contains("laundry") || label.contains("pantry") {
            return .indoor_home
        }
        // 夜晚霓虹场景
        if label.contains("neon") || label.contains("nightclub") || label.contains("discotheque")
            || label.contains("night") || label.contains("lamp") || label.contains("lantern")
            || label.contains("casino") || label.contains("concert_hall") || label.contains("movie_theater")
            || label.contains("stage") || label.contains("marquee") || label.contains("light") {
            return .neon_night
        }
        return nil
    }
}
