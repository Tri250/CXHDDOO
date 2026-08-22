package com.poseai.app.ml

import com.poseai.app.model.NormPoint
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.sqrt

/**
 * 姿态匹配核心算法（向量夹角法）——转换自 iOS PoseMatcher。
 * 使用肢体三关节角度差异衡量相似度，消除距离远近影响。
 */
object PoseMatcher {

    // 肢体三元组定义 (A, B, C) → 计算 ∠ABC
    val jointsToCompare: List<Triple<String, String, String>> = listOf(
        Triple("leftShoulder", "leftElbow", "leftWrist"),
        Triple("rightShoulder", "rightElbow", "rightWrist"),
        Triple("leftShoulder", "leftHip", "leftKnee"),
        Triple("rightShoulder", "rightHip", "rightKnee"),
        Triple("neck", "leftShoulder", "leftElbow"),
        Triple("neck", "rightShoulder", "rightElbow")
    )

    // 下半身关节集合（半身模式时跳过）
    val lowerBodyJoints: Set<String> = setOf(
        "leftHip", "rightHip", "leftKnee", "rightKnee",
        "leftAnkle", "rightAnkle", "leftFoot", "rightFoot"
    )

    private const val RAD2DEG = 180.0 / Math.PI

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
        var totalDiff = 0f
        var count = 0

        for ((p1Key, centerKey, p2Key) in jointsToCompare) {
            if (isHalfBody) {
                if (lowerBodyJoints.contains(p1Key) ||
                    lowerBodyJoints.contains(centerKey) ||
                    lowerBodyJoints.contains(p2Key)
                ) continue
            }

            val cp1 = current[p1Key] ?: continue
            val cc = current[centerKey] ?: continue
            val cp2 = current[p2Key] ?: continue
            val pp1 = preset[p1Key] ?: continue
            val pc = preset[centerKey] ?: continue
            val pp2 = preset[p2Key] ?: continue

            val currentAngle = calculateAngle(cp1, cc, cp2)
            val presetAngle = calculateAngle(pp1, pc, pp2)

            // 容错门限：5° 以内误差忽略不计
            val rawDiff = abs(currentAngle - presetAngle)
            val diff = if (rawDiff > 5f) rawDiff - 5f else 0f

            totalDiff += diff
            count += 1
        }

        if (count == 0) return 0f

        val penalty = (totalDiff / count) / 90f * 100f
        return (100f - penalty).coerceIn(0f, 100f)
    }
}