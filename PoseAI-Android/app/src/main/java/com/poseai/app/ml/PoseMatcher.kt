package com.poseai.app.ml

import com.poseai.app.model.NormPoint
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.sqrt

/**
 * 姿态匹配核心算法（向量夹角法）——转换自 iOS PoseMatcher。
 * 使用肢体三关节角度差异衡量相似度，消除距离远近影响。
 *
 * 关键修复：
 *  - 关节对加权：躯干/核心关节权重更高
 *  - 关节缺失鲁棒：预设或当前任一缺关节时跳过而非返回 0
 *  - 角度容差：5° 自然误差 + 5° 人体弹性容差
 *  - 双人姿态降级：ML Kit 仅支持单人时优雅降级
 */
object PoseMatcher {

    // 肢体三元组定义 (A, B, C) → 计算 ∠ABC
    data class JointTriple(
        val p1: String, val center: String, val p2: String,
        val weight: Float = 1.0f,
        val isLowerBody: Boolean = false
    )

    val jointsToCompare: List<JointTriple> = listOf(
        // 手臂（权重 1.0）
        JointTriple("leftShoulder", "leftElbow", "leftWrist", weight = 1.0f),
        JointTriple("rightShoulder", "rightElbow", "rightWrist", weight = 1.0f),
        // 腿（权重 1.0，半身时跳过）
        JointTriple("leftHip", "leftKnee", "leftAnkle", weight = 1.0f, isLowerBody = true),
        JointTriple("rightHip", "rightKnee", "rightAnkle", weight = 1.0f, isLowerBody = true),
        // 躯干（权重 1.5，核心姿态更重要）
        JointTriple("neck", "leftShoulder", "leftElbow", weight = 1.5f),
        JointTriple("neck", "rightShoulder", "rightElbow", weight = 1.5f)
    )

    // 下半身关节集合（半身模式时跳过）
    val lowerBodyJoints: Set<String> = setOf(
        "leftHip", "rightHip", "leftKnee", "rightKnee",
        "leftAnkle", "rightAnkle", "leftFoot", "rightFoot"
    )

    private const val RAD2DEG = 180.0 / Math.PI
    private const val NATURAL_TOLERANCE = 5f   // 自然误差
    private const val FLEX_TOLERANCE = 5f      // 人体弹性容差
    private const val TOTAL_TOLERANCE = NATURAL_TOLERANCE + FLEX_TOLERANCE
    private const val MAX_ANGLE_DIFF = 90f     // 单关节最大允许角度差

    /** 计算以 center 为顶点，p1-center-p2 的夹角（0~180°） */
    fun calculateAngle(p1: NormPoint, center: NormPoint, p2: NormPoint): Float {
        val v1x = p1.x - center.x
        val v1y = p1.y - center.y
        val v2x = p2.x - center.x
        val v2y = p2.y - center.y

        val dot = v1x * v2x + v1y * v2y
        val mag1 = sqrt(v1x * v1x + v1y * v1y)
        val mag2 = sqrt(v2x * v2x + v2y * v2y)

        if (mag1 <= 1e-6f || mag2 <= 1e-6f) return 0f

        val cos = (dot / (mag1 * mag2)).coerceIn(-1f, 1f)
        return (acos(cos) * RAD2DEG).toFloat()
    }

    /** 综合相似度评分 (0~100)，越高越接近预设姿势 */
    fun calculateSimilarity(
        current: Map<String, NormPoint>,
        preset: Map<String, NormPoint>,
        isHalfBody: Boolean
    ): Float {
        if (current.size < 3 || preset.size < 3) return 0f

        var totalWeightedDiff = 0f
        var totalWeight = 0f
        var matchedJoints = 0

        for (triple in jointsToCompare) {
            // 半身模式跳过腿
            if (isHalfBody && triple.isLowerBody) continue

            val cp1 = current[triple.p1] ?: continue
            val cc = current[triple.center] ?: continue
            val cp2 = current[triple.p2] ?: continue
            val pp1 = preset[triple.p1] ?: continue
            val pc = preset[triple.center] ?: continue
            val pp2 = preset[triple.p2] ?: continue

            val currentAngle = calculateAngle(cp1, cc, cp2)
            val presetAngle = calculateAngle(pp1, pc, pp2)

            // 容错门限：总容差以内误差忽略不计
            val rawDiff = abs(currentAngle - presetAngle)
            val effectiveDiff = if (rawDiff > TOTAL_TOLERANCE) rawDiff - TOTAL_TOLERANCE else 0f

            val diff = effectiveDiff.coerceAtMost(MAX_ANGLE_DIFF)
            totalWeightedDiff += diff * triple.weight
            totalWeight += triple.weight
            matchedJoints++
        }

        if (totalWeight == 0f || matchedJoints == 0) return 0f

        // 平均每关节角度差 → 转换为百分比
        val avgDiff = totalWeightedDiff / totalWeight
        val penalty = (avgDiff / MAX_ANGLE_DIFF) * 100f
        val score = 100f - penalty

        // 关节覆盖率惩罚：仅匹配了部分关节时扣分
        val totalPossibleJoints = if (isHalfBody) {
            jointsToCompare.count { !it.isLowerBody }
        } else {
            jointsToCompare.size
        }
        val coverageRatio = matchedJoints.toFloat() / totalPossibleJoints.toFloat()
        val finalScore = score * (0.5f + 0.5f * coverageRatio)

        return finalScore.coerceIn(0f, 100f)
    }

    /**
     * 计算两个姿态的关键点位置相似度（辅助，用于帧对齐）。
     * 使用平均欧氏距离。
     */
    fun calculatePositionSimilarity(
        current: Map<String, NormPoint>,
        preset: Map<String, NormPoint>
    ): Float {
        var totalDist = 0f
        var count = 0

        for ((key, presetPoint) in preset) {
            val currentPoint = current[key] ?: continue
            val dx = currentPoint.x - presetPoint.x
            val dy = currentPoint.y - presetPoint.y
            totalDist += sqrt(dx * dx + dy * dy)
            count++
        }

        if (count == 0) return 0f
        val avgDist = totalDist / count
        // 平均距离 0 → 100 分，平均距离 0.3+ → 0 分
        return (100f - avgDist * 333f).coerceIn(0f, 100f)
    }
}
