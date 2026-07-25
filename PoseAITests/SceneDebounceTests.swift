import XCTest
@testable import PoseAI

/// VisionService 场景防抖逻辑测试
/// 验证场景分类的防抖机制（连续 2 帧一致才触发切换）
final class SceneDebounceTests: XCTestCase {

    /// 测试辅助：模拟场景缓冲区和防抖逻辑
    /// VisionService 中的防抖逻辑是内嵌在 classifyScene 中的，
    /// 这里提取出核心逻辑进行独立验证
    private func simulateDebounce(
        frames: [SceneType],
        currentScene: SceneType = .unknown
    ) -> SceneType {
        // 复现 VisionService 的防抖策略：
        // 维护一个 lastRawScene 缓冲，只有连续 2 帧相同才触发 onSceneChange
        var lastRawScene: SceneType = .unknown
        var confirmedScene = currentScene

        for frame in frames {
            if frame == .unknown { continue } // unknown 不进 buffer
            if frame == lastRawScene {
                // 连续 2 帧一致 → 确认场景切换
                confirmedScene = frame
            }
            lastRawScene = frame
        }

        return confirmedScene
    }

    /// 单帧场景不应触发切换
    func testSingleFrame_noChange() {
        let result = simulateDebounce(
            frames: [.beach],
            currentScene: .coffee_shop
        )
        // 只有 1 帧 beach，不满足连续 2 帧要求
        XCTAssertEqual(result, .coffee_shop,
            "单帧不应触发场景切换")
    }

    /// 连续 2 帧相同场景应触发切换
    func testConsecutiveSame_triggers() {
        let result = simulateDebounce(
            frames: [.beach, .beach],
            currentScene: .coffee_shop
        )
        XCTAssertEqual(result, .beach,
            "连续 2 帧一致应触发切换到 beach")
    }

    /// unknown 帧应被忽略，不进入防抖 buffer
    func testUnknown_ignored() {
        let result = simulateDebounce(
            frames: [.beach, .unknown, .beach],
            currentScene: .coffee_shop
        )
        // unknown 被跳过，所以 beach → skip → beach，
        // 此时 lastRawScene 在 unknown 之后保持为 beach
        // 第三帧 beach == lastRawScene(beach) → 触发
        XCTAssertEqual(result, .beach,
            "unknown 应被跳过，beach-unknown-beach 仍应触发")
    }

    /// 交替不同场景不应触发切换
    func testMixedFrames_noTrigger() {
        let result = simulateDebounce(
            frames: [.beach, .forest, .beach, .forest],
            currentScene: .coffee_shop
        )
        XCTAssertEqual(result, .coffee_shop,
            "交替场景不满足连续 2 帧条件，不应切换")
    }

    /// 连续多帧后切换应取最后稳定的场景
    func testMultipleTransitions() {
        let result = simulateDebounce(
            frames: [.beach, .beach, .forest, .forest, .park, .park],
            currentScene: .coffee_shop
        )
        XCTAssertEqual(result, .park,
            "多次连续帧切换应最终稳定到 park")
    }

    /// 起始场景为 unknown 时，第一次连续 2 帧应触发
    func testFromUnknown_firstDetection() {
        let result = simulateDebounce(
            frames: [.coffee_shop, .coffee_shop],
            currentScene: .unknown
        )
        XCTAssertEqual(result, .coffee_shop,
            "从 unknown 开始，首次连续 2 帧应确认场景")
    }
}
