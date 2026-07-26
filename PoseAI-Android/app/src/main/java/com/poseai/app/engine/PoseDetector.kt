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

    /**
     * 计算用户姿势与目标姿势的相似度评分
     * 使用归一化欧氏距离，并对不同关节赋予不同权重
     */
    fun calculateSimilarity(
        pose: Pose,
        targetPoints: Map<String, PointF>,
        imageWidth: Float,
        imageHeight: Float,
        isFrontCamera: Boolean
    ): Float {
        val detectedMap = poseToNormalizedMap(pose, imageWidth, imageHeight, isFrontCamera)
        if (detectedMap.isEmpty()) return 0f

        // 关节权重：躯干和四肢权重更高
        val weights = mapOf(
            "leftShoulder" to 1.5f, "rightShoulder" to 1.5f,
            "leftElbow" to 1.2f, "rightElbow" to 1.2f,
            "leftWrist" to 1.0f, "rightWrist" to 1.0f,
            "leftHip" to 1.5f, "rightHip" to 1.5f,
            "leftKnee" to 1.0f, "rightKnee" to 1.0f,
            "leftAnkle" to 0.8f, "rightAnkle" to 0.8f,
            "nose" to 0.5f
        )

        var totalWeightedDistance = 0f
        var totalWeight = 0f

        for ((key, target) in targetPoints) {
            val detected = detectedMap[key] ?: continue
            val weight = weights[key] ?: 1.0f

            val dx = detected.x - target.x
            val dy = detected.y - target.y
            val distance = kotlin.math.sqrt(dx * dx + dy * dy)

            totalWeightedDistance += distance * weight
            totalWeight += weight
        }

        if (totalWeight <= 0f) return 0f
        val avgDistance = totalWeightedDistance / totalWeight

        // 距离 0 = 完全匹配 = 100 分，距离 0.5+ = 0 分
        return ((1f - (avgDistance / 0.5f).coerceIn(0f, 1f)) * 100f)
    }

    /**
     * 检查姿势是否有效（足够多的关节点在画面中）
     */
    fun isPoseValid(pose: Pose): Boolean {
        val landmarks = pose.allPoseLandmarks
        val validCount = landmarks.count { it.inFrameLikelihood > 0.5f }
        // 至少需要肩膀 + 一个肘部 = 3 个关键点
        return validCount >= 3
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
