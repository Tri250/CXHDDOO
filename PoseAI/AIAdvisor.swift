import Foundation
import UIKit
import CoreImage
import CoreImage.CIFilterBuiltins

/// AI 穿搭（OOTD）分析引擎
///
/// 设计目标：在任何环境下都返回**真实、可用**的分析，绝不返回随机词，也不假装上传。
/// - 已配置 Key + 网络可用：调用 OpenAI 兼容的多模态 Vision 接口，返回模型真实分析结果。
/// - 无 Key / 超时 / 网络失败：降级为**基于真实图像信号**的确定性本地分析
///   （用 `CIAreaAverage` 真实计算整图平均亮度与冷暖色调，结合当前场景输出场景专属建议）。
final class AIAdvisor {
    static let shared = AIAdvisor()

    // MARK: - 配置（可由设置页通过 configure 注入；持久化到 UserDefaults）
    struct Config: Codable {
        var apiKey: String
        var endpoint: String   // 例如 https://api.openai.com/v1/chat/completions
        var model: String      // 例如 gpt-4o-mini
    }

    private let configKey = "com.poseai.aiadvisor.config"
    private var config: Config

    private let session: URLSession
    private let ciContext = CIContext(options: [.useSoftwareRenderer: false])

    private init() {
        // 默认无 Key（用户未在设置页配置），走本地确定性降级
        if let data = UserDefaults.standard.data(forKey: configKey),
           let saved = try? JSONDecoder().decode(Config.self, from: data) {
            self.config = saved
        } else {
            self.config = Config(
                apiKey: "",
                endpoint: "https://api.openai.com/v1/chat/completions",
                model: "gpt-4o-mini"
            )
        }
        let cfg = URLSessionConfiguration.default
        cfg.timeoutIntervalForRequest = 10
        cfg.timeoutIntervalForResource = 10
        self.session = URLSession(configuration: cfg)
    }

    /// 由设置页调用：配置并持久化远程多模态服务
    func configure(apiKey: String, endpoint: String? = nil, model: String? = nil) {
        config.apiKey = apiKey
        if let endpoint, !endpoint.isEmpty { config.endpoint = endpoint }
        if let model, !model.isEmpty { config.model = model }
        if let data = try? JSONEncoder().encode(config) {
            UserDefaults.standard.set(data, forKey: configKey)
        }
    }

    /// 清除配置（回到本地确定性降级）
    func clearConfig() {
        config = Config(
            apiKey: "",
            endpoint: "https://api.openai.com/v1/chat/completions",
            model: "gpt-4o-mini"
        )
        UserDefaults.standard.removeObject(forKey: configKey)
    }

    // MARK: - 主入口（签名保持与调用方一致：analyzeOOTD(image:currentScene:) async -> String）

    /// 分析 OOTD 穿搭与构图。
    /// - 有 Key 时优先走真实远程多模态分析；远程不可用（无 Key / 超时 / 解析失败）时降级到本地确定性分析。
    func analyzeOOTD(image: UIImage, currentScene: SceneType) async -> String {
        if !config.apiKey.isEmpty {
            if let remote = await remoteAnalyze(image: image, scene: currentScene) {
                return remote
            }
            // 远程失败 → 降级到本地真实分析，而不是返回随机词
        }
        return localAnalyze(image: image, scene: currentScene)
    }

    // MARK: - 远程真实多模态分析（OpenAI 兼容 Vision）

    private func remoteAnalyze(image: UIImage, scene: SceneType) async -> String? {
        guard
            let url = URL(string: config.endpoint),
            let jpegData = image.jpegData(compressionQuality: 0.6),
            let base64 = String(data: jpegData.base64Encoded(), encoding: .utf8)
        else { return nil }

        let sceneHint = currentScene.displayName
        let systemPrompt = """
        你是一个专业的时尚穿搭与摄影构图顾问。用户上传了一张在「\(sceneHint)」场景下的自拍/穿搭照片。
        请基于图片给出 2-4 条简洁、可执行的穿搭与拍摄建议（中文，每条不超过 25 字）。
        只输出建议本身，不要寒暄，也不要使用编号以外的多余格式。
        """

        let payload: [String: Any] = [
            "model": config.model,
            "messages": [
                ["role": "system", "content": systemPrompt],
                ["role": "user", "content": [
                    ["type": "text", "text": "请分析这张照片的穿搭与构图。"],
                    ["type": "image_url", "image_url": ["url": "data:image/jpeg;base64,\(base64)"]]
                ]]
            ],
            "max_tokens": 200,
            "temperature": 0.6
        ]

        guard let httpBody = try? JSONSerialization.data(withJSONObject: payload) else { return nil }

        var req = URLRequest(url: url)
        req.httpMethod = "POST"
        req.setValue("application/json", forHTTPHeaderField: "Content-Type")
        req.setValue("Bearer \(config.apiKey)", forHTTPHeaderField: "Authorization")
        req.httpBody = httpBody

        do {
            let (data, resp) = try await session.data(for: req)
            if let http = resp as? HTTPURLResponse, !(200...299).contains(http.statusCode) {
                return nil
            }
            guard
                let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
                let choices = json["choices"] as? [[String: Any]],
                let message = choices.first?["message"] as? [String: Any],
                let content = message["content"] as? String
            else { return nil }

            let cleaned = content.trimmingCharacters(in: .whitespacesAndNewlines)
            return cleaned.isEmpty ? nil : cleaned
        } catch {
            // 超时或网络错误 → 调用方会自动降级到本地分析
            return nil
        }
    }

    // MARK: - 本地确定性降级（真实计算，非随机）

    /// 基于真实图像统计（平均亮度 + 冷暖色调）生成场景专属建议。
    private func localAnalyze(image: UIImage, scene: SceneType) -> String {
        let (brightness, warmth) = averageColor(image)

        var tips: [String] = []

        // 1. 亮度建议（确定性，由真实像素均值决定）
        if brightness < 0.3 {
            tips.append("画面偏暗，建议靠近光源或开启补光")
        } else if brightness > 0.8 {
            tips.append("光线偏曝，建议寻找遮挡或改用侧光")
        } else {
            tips.append("当前亮度适中，稳住构图即可出片")
        }

        // 2. 冷暖色调建议（红-蓝差，确定性）
        if warmth > 0.08 {
            tips.append("暖调突出，适合米色/卡其等大地色穿搭")
        } else if warmth < -0.08 {
            tips.append("冷调明显，蓝白冷色系更清爽利落")
        } else {
            tips.append("色彩中性，黑白或撞色都能驾驭")
        }

        // 3. 场景专属建议
        tips.append(sceneTip(for: scene))

        return tips.joined(separator: "\n")
    }

    /// 真实计算整图平均亮度(0~1)与冷暖倾向(红-蓝, 约 -1~1)
    private func averageColor(_ image: UIImage) -> (brightness: Float, warmth: Float) {
        guard let ci = CIImage(image: image) else { return (0.5, 0.0) }

        // 先降采样到 64px 以内，极大降低计算量
        let scale = min(1.0, 64.0 / max(ci.extent.width, ci.extent.height))
        let small = ci.transformed(by: CGAffineTransform(scaleX: scale, y: scale))

        let filter = CIFilter(name: "CIAreaAverage")!
        filter.setValue(small, forKey: kCIInputImageKey)
        filter.setValue(
            CIVector(x: 0, y: 0, z: small.extent.width, w: small.extent.height),
            forKey: kCIInputExtentKey
        )
        guard let output = filter.outputImage else { return (0.5, 0.0) }

        var bitmap = [UInt8](repeating: 0, count: 4)
        ciContext.render(
            output,
            toBitmap: &bitmap,
            rowBytes: 4,
            bounds: CGRect(x: 0, y: 0, width: 1, height: 1),
            format: .RGBA8,
            colorSpace: nil
        )

        let r = Float(bitmap[0]) / 255.0
        let g = Float(bitmap[1]) / 255.0
        let b = Float(bitmap[2]) / 255.0
        return ((r + g + b) / 3.0, r - b)
    }

    private func sceneTip(for scene: SceneType) -> String {
        switch scene {
        case .coffeeShop:  return "咖啡馆：靠窗自然光下单品更易出片"
        case .beach:       return "海边：纯色长裙与天空形成对比更吸睛"
        case .forest:      return "森林：绿调背景配浅色衣物层次更分明"
        case .cityStreet:  return "街拍：利用建筑线条做对角线构图"
        case .park:        return "公园：用前景花草制造景深更灵动"
        case .indoorHome:  return "室内：面向窗户用柔光打亮面部"
        case .neonNight:   return "夜景：暗底配亮色或反光材质更出彩"
        case .unknown:     return "当前场景：主体居中、背景简洁即可"
        }
    }
}
