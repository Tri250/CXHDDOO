import XCTest
@testable import PoseAI

/// 模型数据完整性测试
/// 验证 7 个场景的方案库、构图规则、比例参数的数据完整性
final class ModelTests: XCTestCase {

    /// 所有 7 个场景（除 unknown）都应该有对应的方案
    func testAllScenes_havePlans() {
        let scenes: [SceneType] = [
            .coffee_shop, .beach, .forest,
            .city_street, .park, .indoor_home, .neon_night
        ]

        for scene in scenes {
            XCTAssertFalse(scene.plans.isEmpty,
                "\(scene.displayName) 场景应该有至少 1 套方案")
        }
    }

    /// unknown 场景应返回空方案
    func testUnknown_hasNoPlans() {
        XCTAssertTrue(SceneType.unknown.plans.isEmpty,
            "unknown 场景应返回空方案列表")
    }

    /// 每个场景都应该有 3 套方案
    func testEachScene_has3Plans() {
        let scenes: [SceneType] = [
            .coffee_shop, .beach, .forest,
            .city_street, .park, .indoor_home, .neon_night
        ]

        for scene in scenes {
            XCTAssertEqual(scene.plans.count, 3,
                "\(scene.displayName) 场景应有 3 套方案，实际有 \(scene.plans.count) 套")
        }
    }

    /// 所有 21 个方案 ID 应该唯一（无重复）
    func testPlanIds_unique() {
        let allScenes: [SceneType] = [
            .coffee_shop, .beach, .forest,
            .city_street, .park, .indoor_home, .neon_night
        ]

        var allIDs = Set<String>()
        var totalPlans = 0

        for scene in allScenes {
            for plan in scene.plans {
                allIDs.insert(plan.id)
                totalPlans += 1
            }
        }

        XCTAssertEqual(allIDs.count, totalPlans,
            "所有方案 ID 应唯一，期望 \(totalPlans) 个去重后得 \(allIDs.count)")
        XCTAssertEqual(totalPlans, 21,
            "7 场景 × 3 方案 = 21 个方案")
    }

    /// 所有构图规则都应有有效的偏移值
    func testComposition_allRulesHaveOffset() {
        for rule in CompositionRule.allCases {
            // 非 center 都应有非零偏移
            if rule != .center {
                XCTAssertNotEqual(rule.offset, 0,
                    "\(rule.displayName) 构图应有非零偏移")
            }

            // 所有规则都应有 displayName 和 icon
            XCTAssertFalse(rule.displayName.isEmpty,
                "\(rule) 应有 displayName")
            XCTAssertFalse(rule.icon.isEmpty,
                "\(rule) 应有 icon")
            XCTAssertFalse(rule.reason.isEmpty,
                "\(rule) 应有 reason 说明")
            XCTAssertFalse(rule.voiceHint.isEmpty,
                "\(rule) 应有 voiceHint")
        }
    }

    /// 所有比例模式的高度比例应在合理范围 [0.1, 1.0]
    func testFrameRatio_heightRatiosValid() {
        for ratio in FrameRatio.allCases {
            XCTAssertGreaterThan(ratio.heightRatio, 0.1,
                "\(ratio.displayName) 的高度比例应 > 0.1")
            XCTAssertLessThanOrEqual(ratio.heightRatio, 1.0,
                "\(ratio.displayName) 的高度比例应 ≤ 1.0")

            // 全身 > 半身 > 特写
            XCTAssertFalse(ratio.displayName.isEmpty,
                "\(ratio) 应有 displayName")
            XCTAssertFalse(ratio.icon.isEmpty,
                "\(ratio) 应有 icon")
            XCTAssertFalse(ratio.distanceHint.isEmpty,
                "\(ratio) 应有 distanceHint")
        }
    }

    /// 验证高度比例的相对大小关系：fullBody > halfBody > portrait
    func testFrameRatio_orderConsistency() {
        XCTAssertGreaterThan(FrameRatio.fullBody.heightRatio, FrameRatio.halfBody.heightRatio,
            "全身应比半身高度比例更大")
        XCTAssertGreaterThan(FrameRatio.halfBody.heightRatio, FrameRatio.portrait.heightRatio,
            "半身应比特写高度比例更大")
    }

    /// 每个方案都应该有至少 3 个关节点（最少需要 3 个来形成一组三元组）
    func testAllPlans_haveMinimumPosePoints() {
        let allScenes: [SceneType] = [
            .coffee_shop, .beach, .forest,
            .city_street, .park, .indoor_home, .neon_night
        ]

        for scene in allScenes {
            for plan in scene.plans {
                XCTAssertGreaterThanOrEqual(plan.posePoints.count, 3,
                    "\(scene.displayName)/\(plan.poseName) 应有至少 3 个关节点，实际 \(plan.posePoints.count)")
            }
        }
    }
}
