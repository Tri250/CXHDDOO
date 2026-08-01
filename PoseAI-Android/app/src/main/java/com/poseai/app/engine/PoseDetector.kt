package com.poseai.app.engine

import android.graphics.PointF
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseDetectorOptionsBase
import com.google.mlkit.vision.pose.PoseLandmark
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class PoseDetectorEngine {

    companion object {
        private const val TAG = "PoseDetector"

        // ML Kit PoseLandmark 类型 ID → 目标姿势点名映射
        // ML Kit 使用 33 个关节点，我们只需要关键的
        val LANDMARK_NAME_MAP = mapOf(
            PoseLandmark.NOSE to "nose",
            PoseLandmark.LEFT_EYE_INNER to "leftEyeInner",
            PoseLandmark.LEFT_EYE to "leftEye",
            PoseLandmark.LEFT_EYE_OUTER to "leftEyeOuter",
            PoseLandmark.RIGHT_EYE_INNER to "rightEyeInner",
            PoseLandmark.RIGHT_EYE to "rightEye",
            PoseLandmark.RIGHT_EYE_OUTER to "rightEyeOuter",
            PoseLandmark.LEFT_EAR to "leftEar",
            PoseLandmark.RIGHT_EAR to "rightEar",
            PoseLandmark.LEFT_SHOULDER to "leftShoulder",
            PoseLandmark.RIGHT_SHOULDER to "rightShoulder",
            PoseLandmark.LEFT_ELBOW to "leftElbow",
            PoseLandmark.RIGHT_ELBOW to "rightElbow",
            PoseLandmark.LEFT_WRIST to "leftWrist",
            PoseLandmark.RIGHT_WRIST to "rightWrist",
            PoseLandmark.LEFT_HIP to "leftHip",
            PoseLandmark.RIGHT_HIP to "rightHip",
            PoseLandmark.LEFT_KNEE to "leftKnee",
            PoseLandmark.RIGHT_KNEE to "rightKnee",
            PoseLandmark.LEFT_ANKLE to "leftAnkle",
            PoseLandmark.RIGHT_ANKLE to "rightAnkle",
            PoseLandmark.LEFT_PINKY to "leftPinky",
            PoseLandmark.RIGHT_PINKY to "rightPinky",
            PoseLandmark.LEFT_INDEX to "leftIndex",
            PoseLandmark.RIGHT_INDEX to "rightIndex",
            PoseLandmark.LEFT_THUMB to "leftThumb",
            PoseLandmark.RIGHT_THUMB to "rightThumb",
            PoseLandmark.LEFT_HEEL to "leftHeel",
            PoseLandmark.RIGHT_HEEL to "rightHeel",
            PoseLandmark.LEFT_FOOT_INDEX to "leftFootIndex",
            PoseLandmark.RIGHT_FOOT_INDEX to "rightFootIndex"
        )

        // 关节连线定义
        val SKELETON_CONNECTIONS = listOf(
            PoseLandmark.LEFT_SHOULDER to PoseLandmark.RIGHT_SHOULDER,
            PoseLandmark.LEFT_SHOULDER to PoseLandmark.LEFT_ELBOW,
            PoseLandmark.LEFT_ELBOW to PoseLandmark.LEFT_WRIST,
            PoseLandmark.RIGHT_SHOULDER to PoseLandmark.RIGHT_ELBOW,
            PoseLandmark.RIGHT_ELBOW to PoseLandmark.RIGHT_WRIST,
            PoseLandmark.LEFT_SHOULDER to PoseLandmark.LEFT_HIP,
            PoseLandmark.RIGHT_SHOULDER to PoseLandmark.RIGHT_HIP,
            PoseLandmark.LEFT_HIP to PoseLandmark.RIGHT_HIP,
            PoseLandmark.LEFT_HIP to PoseLandmark.LEFT_KNEE,
            PoseLandmark.LEFT_KNEE to PoseLandmark.LEFT_ANKLE,
            PoseLandmark.RIGHT_HIP to PoseLandmark.RIGHT_KNEE,
            PoseLandmark.RIGHT_KNEE to PoseLandmark.RIGHT_ANKLE,
            PoseLandmark.NOSE to PoseLandmark.LEFT_SHOULDER,
            PoseLandmark.NOSE to PoseLandmark.RIGHT_SHOULDER
        )
    }

    private val options: PoseDetectorOptionsBase = PoseDetectorOptions.Builder()
        .setDetectorMode(PoseDetectorOptions.STREAM_MODE)
        .build()

    private val detector = PoseDetection.getClient(options)

    @Volatile
    private var isClosed = false

    suspend fun detect(image: InputImage): Pose? {
        if (isClosed) return null
        return suspendCancellableCoroutine { cont ->
            detector.process(image)
                .addOnSuccessListener { pose ->
                    if (cont.isActive) cont.resume(pose)
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Detection failed", e)
                    if (cont.isActive) cont.resume(null)
                }
                .addOnCanceledListener {
                    cont.cancel()
                }
        }
    }

    fun close() {
        if (isClosed) return
        isClosed = true
        try {
            detector.close()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to close detector", e)
        }
    }
}

class PoseSmoother(
    private val baseAlpha: Float = 0.55f,
    private val jitterAlpha: Float = 0.75f,
    private val jitterThreshold: Float = 0.08f
) {
    private val previousPoints = mutableMapOf<String, PointF>()

    fun smooth(currentPoints: Map<String, PointF>): Map<String, PointF> {
        val smoothed = mutableMapOf<String, PointF>()
        for ((key, point) in currentPoints) {
            val prev = previousPoints[key]
            if (prev != null) {
                val dx = point.x - prev.x
                val dy = point.y - prev.y
                val distance = kotlin.math.sqrt(dx * dx + dy * dy)
                val alpha = if (distance > jitterThreshold) jitterAlpha else baseAlpha
                smoothed[key] = PointF(
                    prev.x * (1 - alpha) + point.x * alpha,
                    prev.y * (1 - alpha) + point.y * alpha
                )
            } else {
                smoothed[key] = point
            }
        }
        previousPoints.clear()
        previousPoints.putAll(smoothed)
        return smoothed
    }

    fun reset() {
        previousPoints.clear()
    }
}

object PoseUtils {

    /**
     * 将 ML Kit Pose 转为以名字为 key 的归一化坐标 Map
     * 坐标已归一化到 [0,1]，支持前置摄像头镜像
     */
    fun poseToNormalizedMap(
        pose: Pose,
        imageWidth: Float,
        imageHeight: Float,
        isFrontCamera: Boolean
    ): Map<String, PointF> {
        val result = mutableMapOf<String, PointF>()
        for (landmark in pose.allPoseLandmarks) {
            val name = PoseDetectorEngine.LANDMARK_NAME_MAP[landmark.landmarkType] ?: continue
            if (landmark.inFrameLikelihood < 0.3f) continue
            val x = if (isFrontCamera) {
                1f - landmark.position.x / imageWidth
            } else {
                landmark.position.x / imageWidth
            }
            val y = landmark.position.y / imageHeight
            result[name] = PointF(x, y)
        }
        return result
    }

    fun calculateAngle(p1: PointF, center: PointF, p2: PointF): Double {
        val v1x = (p1.x - center.x).toDouble()
        val v1y = (p1.y - center.y).toDouble()
        val v2x = (p2.x - center.x).toDouble()
        val v2y = (p2.y - center.y).toDouble()
        val dot = v1x * v2x + v1y * v2y
        val mag1 = kotlin.math.sqrt(v1x * v1x + v1y * v1y)
        val mag2 = kotlin.math.sqrt(v2x * v2x + v2y * v2y)
        if (mag1 < 1e-6 || mag2 < 1e-6) return 0.0
        val cosAngle = kotlin.math.max(-1.0, kotlin.math.min(1.0, dot / (mag1 * mag2)))
        return kotlin.math.acos(cosAngle) * 180.0 / kotlin.math.PI
    }

    fun calculateAngleSimilarity(
        currentPoints: Map<String, PointF>,
        targetPoints: Map<String, PointF>,
        isHalfBody: Boolean = false
    ): Float {
        val lowerBodyJoints = setOf("leftHip", "rightHip", "leftKnee", "rightKnee", "leftAnkle", "rightAnkle")

        val jointTriples = listOf(
            Triple("leftShoulder", "leftElbow", "leftWrist"),
            Triple("rightShoulder", "rightElbow", "rightWrist"),
            Triple("leftHip", "leftKnee", "leftAnkle"),
            Triple("rightHip", "rightKnee", "rightAnkle"),
            Triple("neck", "leftShoulder", "leftElbow"),
            Triple("neck", "rightShoulder", "rightElbow"),
            Triple("leftShoulder", "leftHip", "leftKnee"),
            Triple("rightShoulder", "rightHip", "rightKnee"),
            Triple("leftShoulder", "neck", "rightShoulder"),
            Triple("leftHip", "neck", "rightHip")
        )

        var totalDiff = 0.0
        var count = 0

        for ((p1Key, centerKey, p2Key) in jointTriples) {
            if (isHalfBody) {
                if (lowerBodyJoints.contains(p1Key) || lowerBodyJoints.contains(centerKey) || lowerBodyJoints.contains(p2Key)) continue
            }
            val cp1 = currentPoints[p1Key] ?: continue
            val cc = currentPoints[centerKey] ?: continue
            val cp2 = currentPoints[p2Key] ?: continue
            val tp1 = targetPoints[p1Key] ?: continue
            val tc = targetPoints[centerKey] ?: continue
            val tp2 = targetPoints[p2Key] ?: continue

            val currentAngle = calculateAngle(cp1, cc, cp2)
            val targetAngle = calculateAngle(tp1, tc, tp2)

            val tolerance = when (targetAngle) {
                in 0.0..30.0 -> 3.0
                in 30.0..60.0 -> 5.0
                in 60.0..120.0 -> 6.0
                in 120.0..150.0 -> 7.0
                else -> 8.0
            }
            val rawDiff = kotlin.math.abs(currentAngle - targetAngle)
            val diff = kotlin.math.max(0.0, rawDiff - tolerance)
            totalDiff += diff
            count++
        }

        if (count == 0) return 0f
        val penalty = (totalDiff / count) / 90.0 * 100.0
        return (100.0 - penalty).coerceIn(0.0, 100.0).toFloat()
    }

    /**
     * 计算用户姿势与目标姿势的相似度评分
     * 使用归一化欧氏距离，并对不同关节赋予不同权重
     */
    @Deprecated("Use calculateAngleSimilarity instead", ReplaceWith("calculateAngleSimilarity(poseToNormalizedMap(pose, imageWidth, imageHeight, isFrontCamera), targetPoints)"))
    fun calculateSimilarity(
        pose: Pose,
        targetPoints: Map<String, PointF>,
        imageWidth: Float,
        imageHeight: Float,
        isFrontCamera: Boolean
    ): Float {
        val detectedMap = poseToNormalizedMap(pose, imageWidth, imageHeight, isFrontCamera)
        if (detectedMap.isEmpty()) return 0f
        return calculateAngleSimilarity(detectedMap, targetPoints)
    }

    /**
     * 检查姿势是否有效（足够多的关节点在画面中）
     */
    fun isPoseValid(pose: Pose): Boolean {
        val landmarks = pose.allPoseLandmarks
        val validCount = landmarks.count { it.inFrameLikelihood > 0.3f }
        // 至少需要 2 个关键点即可判定为有效，提高灵敏度
        return validCount >= 2
    }

    /**
     * 获取骨架连线用于 UI 渲染
     */
    fun getSkeletonLines(
        pose: Pose,
        imageWidth: Float,
        imageHeight: Float,
        isFrontCamera: Boolean
    ): List<Pair<PointF, PointF>> {
        val lines = mutableListOf<Pair<PointF, PointF>>()
        for ((typeA, typeB) in PoseDetectorEngine.SKELETON_CONNECTIONS) {
            val lmA = pose.getPoseLandmark(typeA)
            val lmB = pose.getPoseLandmark(typeB)
            if (lmA != null && lmB != null &&
                lmA.inFrameLikelihood > 0.3f && lmB.inFrameLikelihood > 0.3f
            ) {
                val ax = if (isFrontCamera) 1f - lmA.position.x / imageWidth else lmA.position.x / imageWidth
                val ay = lmA.position.y / imageHeight
                val bx = if (isFrontCamera) 1f - lmB.position.x / imageWidth else lmB.position.x / imageWidth
                val by = lmB.position.y / imageHeight
                lines.add(PointF(ax, ay) to PointF(bx, by))
            }
        }
        return lines
    }
}
