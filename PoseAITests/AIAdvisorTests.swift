import XCTest
@testable import PoseAI

/// AIAdvisor OOTD 穿搭分析测试
/// 验证多模态建议接口的输出格式、场景覆盖率和内容正确性
final class AIAdvisorTests: XCTestCase {

    // MARK: - OOTD 建议基本合约

    /// analyzeOOTD 应返回非空字符串
    func testAnalyzeOOTD_returnsNonEmpty() async {
        // 传入一张 1x1 的极小测试图片（模拟模式下图片不被实际处理）
        let testImage = createTestImage()
        let result = await AIAdvisor.shared.analyzeOOTD(image: testImage, currentScene: .coffee_shop)
        XCTAssertFalse(result.isEmpty, "OOTD 建议不应为空")
    }

    /// 建议文本应包含当前场景的 displayName
    func testAnalyzeOOTD_containsSceneName() async {
        let testImage = createTestImage()

        // 多次执行以覆盖随机分支
        for _ in 0..<10 {
            let result = await AIAdvisor.shared.analyzeOOTD(image: testImage, currentScene: .beach)
            XCTAssertTrue(result.contains(SceneType.beach.displayName),
                "OOTD 建议应包含场景名称「\(SceneType.beach.displayName)」，实际: \(result)")
        }
    }

    /// 建议文本应包含穿搭相关关键词
    func testAnalyzeOOTD_containsFashionKeyword() async {
        let testImage = createTestImage()
        let fashionKeywords = ["长裙", "针织衫", "风衣", "慵懒", "休闲", "穿搭", "穿着"]

        // 多次采样确保至少有一些命中
        var hitCount = 0
        for _ in 0..<10 {
            let result = await AIAdvisor.shared.analyzeOOTD(image: testImage, currentScene: .park)
            if fashionKeywords.contains(where: { result.contains($0) }) {
                hitCount += 1
            }
        }
        XCTAssertGreaterThan(hitCount, 0, "10 次采样中至少应有 1 次命中穿搭关键词")
    }

    // MARK: - 全场景覆盖

    /// 所有场景类型都应能正常返回建议
    func testAnalyzeOOTD_allScenes() async {
        let testImage = createTestImage()
        let allScenes: [SceneType] = [
            .coffee_shop, .beach, .forest,
            .city_street, .park, .indoor_home, .neon_night, .unknown
        ]

        for scene in allScenes {
            let result = await AIAdvisor.shared.analyzeOOTD(image: testImage, currentScene: scene)
            XCTAssertFalse(result.isEmpty,
                "\(scene.displayName) 场景应返回非空 OOTD 建议")
            XCTAssertGreaterThan(result.count, 10,
                "\(scene.displayName) 场景的建议长度应大于 10 个字符")
        }
    }

    // MARK: - 辅助方法

    /// 创建一张 1x1 的纯色测试图片
    private func createTestImage() -> UIImage {
        let renderer = UIGraphicsImageRenderer(size: CGSize(width: 1, height: 1))
        return renderer.image { ctx in
            UIColor.red.setFill()
            ctx.fill(CGRect(x: 0, y: 0, width: 1, height: 1))
        }
    }
}
