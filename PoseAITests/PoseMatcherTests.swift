import XCTest
@testable import PoseAI

/// PoseMatcher 核心算法测试
/// 验证向量夹角相似度评分的正确性、边界值处理和半身模式切换
final class PoseMatcherTests: XCTestCase {

    // MARK: - 角度计算测试

    /// 标准直角三角形的三点应计算出 90° 角
    func testAngleCalculation_rightAngle() {
        // 三点组成直角：A(0,0) B(1,0) C(1,1)
        // ∠ABC = 90°（以 B 为顶点）
        let a = CGPoint(x: 0, y: 0)
        let b = CGPoint(x: 1, y: 0)
        let c = CGPoint(x: 1, y: 1)

        let angle = PoseMatcher.calculateAngle(p1: a, center: b, p2: c)
        XCTAssertEqual(angle, 90.0, accuracy: 1.0, "标准直角应接近 90°")
    }

    /// 三点成一条直线时应计算出 180° 角
    func testAngleCalculation_straightLine() {
        let a = CGPoint(x: 0, y: 0)
        let b = CGPoint(x: 1, y: 0)
        let c = CGPoint(x: 2, y: 0)

        let angle = PoseMatcher.calculateAngle(p1: a, center: b, p2: c)
        XCTAssertEqual(angle, 180.0, accuracy: 1.0, "三点共线应接近 180°")
    }

    /// 零向量（三个重合点）应安全返回 0 而不是 crash
    func testAngleCalculation_zeroPoint() {
        let p = CGPoint(x: 0.5, y: 0.5)
        let angle = PoseMatcher.calculateAngle(p1: p, center: p, p2: p)
        XCTAssertEqual(angle, 0.0, accuracy: 0.01, "零向量应返回 0")
    }

    // MARK: - 相似度计算测试

    /// 完全一致的姿势应返回 100 分
    func testSimilarity_identicalPose_returns100() {
        let pose: [String: CGPoint] = [
            "neck":          CGPoint(x: 0.50, y: 0.28),
            "leftShoulder":  CGPoint(x: 0.38, y: 0.38),
            "rightShoulder": CGPoint(x: 0.62, y: 0.38),
            "leftElbow":     CGPoint(x: 0.30, y: 0.52),
            "rightElbow":    CGPoint(x: 0.70, y: 0.52),
            "leftWrist":     CGPoint(x: 0.28, y: 0.65),
            "rightWrist":    CGPoint(x: 0.72, y: 0.50),
            "leftHip":       CGPoint(x: 0.44, y: 0.60),
            "rightHip":      CGPoint(x: 0.56, y: 0.60)
        ]

        let score = PoseMatcher.calculateSimilarity(
            current: pose, preset: pose, isHalfBody: false
        )
        XCTAssertEqual(score, 100.0, accuracy: 1.0, "完全一致应为 100 分")
    }

    /// 空关节点集应安全返回 0 分
    func testSimilarity_emptyPoints_returns0() {
        let empty: [String: CGPoint] = [:]
        let nonEmpty: [String: CGPoint] = ["neck": CGPoint(x: 0.5, y: 0.5)]

        let score1 = PoseMatcher.calculateSimilarity(current: empty, preset: nonEmpty, isHalfBody: false)
        let score2 = PoseMatcher.calculateSimilarity(current: nonEmpty, preset: empty, isHalfBody: false)

        XCTAssertEqual(score1, 0.0, accuracy: 0.01, "空 current 应返回 0")
        XCTAssertEqual(score2, 0.0, accuracy: 0.01, "空 preset 应返回 0")
    }

    /// 半身模式下应跳过下半身关节计算
    func testSimilarity_halfBody_skipsLower() {
        let fullPose: [String: CGPoint] = [
            "neck":          CGPoint(x: 0.50, y: 0.28),
            "leftShoulder":  CGPoint(x: 0.38, y: 0.38),
            "rightShoulder": CGPoint(x: 0.62, y: 0.38),
            "leftElbow":     CGPoint(x: 0.30, y: 0.52),
            "rightElbow":    CGPoint(x: 0.70, y: 0.52),
            "leftWrist":     CGPoint(x: 0.28, y: 0.65),
            "rightWrist":    CGPoint(x: 0.72, y: 0.50),
            "leftHip":       CGPoint(x: 0.44, y: 0.60),
            "rightHip":      CGPoint(x: 0.56, y: 0.60),
            "leftKnee":      CGPoint(x: 0.42, y: 0.78),
            "rightKnee":     CGPoint(x: 0.58, y: 0.78)
        ]

        // 半身模式下把下半身关节挪到错误位置，不应影响评分
        var mismatchedLower = fullPose
        mismatchedLower["leftKnee"] = CGPoint(x: 0.1, y: 0.1)
        mismatchedLower["rightKnee"] = CGPoint(x: 0.9, y: 0.9)

        let scoreHalf = PoseMatcher.calculateSimilarity(
            current: mismatchedLower, preset: fullPose, isHalfBody: true
        )
        let scoreFull = PoseMatcher.calculateSimilarity(
            current: mismatchedLower, preset: fullPose, isHalfBody: false
        )

        // 半身模式评分应高于全身模式（因为半身模式忽略了错位的下半身）
        XCTAssertGreaterThan(scoreHalf, scoreFull,
            "半身模式应忽略下半身错位，评分更高")
    }

    /// 5° 容错门限验证：非常接近的角度差异应被宽容
    func testSimilarity_toleranceThreshold() {
        let pose1: [String: CGPoint] = [
            "neck":          CGPoint(x: 0.50, y: 0.28),
            "leftShoulder":  CGPoint(x: 0.38, y: 0.38),
            "rightShoulder": CGPoint(x: 0.62, y: 0.38),
            "leftElbow":     CGPoint(x: 0.30, y: 0.52),
            "rightElbow":    CGPoint(x: 0.70, y: 0.52),
            "leftWrist":     CGPoint(x: 0.28, y: 0.65),
            "rightWrist":    CGPoint(x: 0.72, y: 0.50),
            "leftHip":       CGPoint(x: 0.44, y: 0.60),
            "rightHip":      CGPoint(x: 0.56, y: 0.60)
        ]

        // 微小偏移（模拟 Vision 推理噪声）
        var pose2 = pose1
        pose2["leftElbow"] = CGPoint(x: 0.31, y: 0.53) // 轻微偏移

        let score = PoseMatcher.calculateSimilarity(
            current: pose2, preset: pose1, isHalfBody: false
        )
        XCTAssertGreaterThan(score, 80.0,
            "微小偏移（约 2-3°）应在 5° 容错范围内，评分应仍然很高")
    }

    /// 手臂完全反向应得低分
    func testSimilarity_oppositeArms() {
        let normalPose: [String: CGPoint] = [
            "neck":          CGPoint(x: 0.50, y: 0.28),
            "leftShoulder":  CGPoint(x: 0.38, y: 0.38),
            "rightShoulder": CGPoint(x: 0.62, y: 0.38),
            "leftElbow":     CGPoint(x: 0.30, y: 0.52),
            "rightElbow":    CGPoint(x: 0.70, y: 0.52),
            "leftWrist":     CGPoint(x: 0.28, y: 0.65),
            "rightWrist":    CGPoint(x: 0.72, y: 0.50),
            "leftHip":       CGPoint(x: 0.44, y: 0.60),
            "rightHip":      CGPoint(x: 0.56, y: 0.60)
        ]

        // 手臂方向反转
        var oppositeArms = normalPose
        oppositeArms["leftElbow"]  = CGPoint(x: 0.46, y: 0.30) // 手肘在肩膀上方
        oppositeArms["rightElbow"] = CGPoint(x: 0.54, y: 0.30)
        oppositeArms["leftWrist"]  = CGPoint(x: 0.48, y: 0.20) // 手腕更高
        oppositeArms["rightWrist"] = CGPoint(x: 0.52, y: 0.20)

        let score = PoseMatcher.calculateSimilarity(
            current: oppositeArms, preset: normalPose, isHalfBody: false
        )
        XCTAssertLessThan(score, 60.0,
            "手臂方向完全反向应得低分")
    }
}
