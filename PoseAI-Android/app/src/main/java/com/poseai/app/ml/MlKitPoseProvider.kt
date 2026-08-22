package com.poseai.app.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.RectF
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageProxy
import com.google.mlkit.common.MlKitException
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseLandmark
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import com.poseai.app.model.NormPoint

/**
 * ML Kit 姿态检测提供者——对应 iOS 的 Vision(VNDetectHumanBodyPoseRequest)。
 * 负责：姿态关键点提取(→Map<关节名,NormPoint>) + EMA 平滑 + 半身判定 + 暗光监测。
 *
 * 关键修复：
 *  - 旋转坐标归一化：90°/270° 旋转时正确交换宽高
 *  - 单人检测适配：ML Kit 仅支持单人，移除双人逻辑
 *  - 关节缺失鲁棒：低置信度关节跳过不崩溃
 *  - 异常帧保护：null/空地图统一降级
 */
class MlKitPoseProvider(private val context: Context) {

    private val detector = PoseDetection.getClient(
        PoseDetectorOptions.Builder()
            .setDetectorMode(PoseDetectorOptions.STREAM_MODE)
            .build()
    )

    /** EMA 平滑状态（单人检测） */
    private var previousPoints = mutableMapOf<String, NormPoint>()

    /** 最近一次稳定检测的点（用于平滑失败时回退） */
    private var lastStablePoints = mapOf<String, NormPoint>()

    /** 当前帧输入图像尺寸（ML Kit Pose 不暴露宽高，需自行记录用于归一化） */
    private var imageWidth = 1f
    private var imageHeight = 1f

    var isFrontCamera: Boolean = false

    /** 回调：当前帧所有人体姿态 */
    var onUpdate: (List<PoseData>) -> Unit = {}
    /** 暗光监测 */
    var onLowLight: (Boolean) -> Unit = {}
    private var lastLowLightTime = 0L
    private var isCurrentlyLowLight = false
    private val lowLightIntervalMs = 5000L

    @androidx.annotation.OptIn(ExperimentalGetImage::class)
    fun process(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image ?: run {
            imageProxy.close()
            return
        }
        updateImageDimensions(imageProxy.width, imageProxy.height, imageProxy.imageInfo.rotationDegrees)
        val rotation = imageProxy.imageInfo.rotationDegrees
        val image = InputImage.fromMediaImage(mediaImage, rotation)

        detector.process(image)
            .addOnSuccessListener { pose -> handlePose(pose) }
            .addOnCompleteListener { imageProxy.close() }
            .addOnFailureListener { e ->
                @Suppress("UNUSED_EXPRESSION")
                when (e) {
                    is MlKitException -> { /* ML Kit 内部异常，忽略该帧 */ }
                    else -> { /* 其他异常忽略 */ }
                }
            }
    }

    /** 处理一帧 Bitmap（用于场景分类读帧等场景） */
    fun process(bitmap: Bitmap) {
        imageWidth = bitmap.width.toFloat()
        imageHeight = bitmap.height.toFloat()
        val image = InputImage.fromBitmap(bitmap, 0)
        detector.process(image)
            .addOnSuccessListener { pose -> handlePose(pose) }
            .addOnFailureListener { /* ignore */ }
    }

    /** 根据旋转角度校正归一化坐标的宽高 */
    private fun updateImageDimensions(rawW: Int, rawH: Int, rotation: Int) {
        val isRotated = rotation == 90 || rotation == 270
        imageWidth = if (isRotated) rawH.toFloat() else rawW.toFloat()
        imageHeight = if (isRotated) rawW.toFloat() else rawH.toFloat()
    }

    private fun handlePose(pose: Pose) {
        val detectedPoints = parsePoints(pose)
        val isValid = detectedPoints.size >= 3

        if (isValid) {
            // EMA 平滑（防止抖动）
            val smoothed = smooth(detectedPoints)
            lastStablePoints = smoothed

            val bbox = computeBBox(smoothed)
            val isHalfBody = detectHalfBody(pose)

            previousPoints = smoothed.toMutableMap()
            onUpdate(listOf(PoseData(smoothed, isHalfBody, bbox)))

            // 暗光监测（关节点数过少 → 可能光线不足）
            val isLowLight = smoothed.size < 5
            maybeDetectLowLight(isLowLight)
        } else if (lastStablePoints.isNotEmpty()) {
            // 检测失败但有稳定历史，发送上一帧避免 UI 闪烁
            val bbox = computeBBox(lastStablePoints)
            onUpdate(listOf(PoseData(lastStablePoints, false, bbox)))
        } else {
            onUpdate(emptyList())
            maybeDetectLowLight(false)
        }
    }

    /** 解析 ML Kit 姿态为归一化关键点 */
    private fun parsePoints(pose: Pose): Map<String, NormPoint> {
        val imgW = imageWidth
        val imgH = imageHeight
        val points = LinkedHashMap<String, NormPoint>()

        val me = { lm: PoseLandmark? ->
            lm?.let {
                var x = it.position.x / imgW
                if (isFrontCamera) x = 1f - x
                val y = 1f - (it.position.y / imgH)
                NormPoint(x, y)
            }
        }

        fun add(key: String, lm: PoseLandmark?) {
            val p = me(lm) ?: return
            if (lm!!.inFrameLikelihood < 0.5f) return
            points[key] = p
        }

        with(pose.getAllPoseLandmarks()) {
            add("leftShoulder", getOrNullOrSelf(this, PoseLandmark.LEFT_SHOULDER))
            add("rightShoulder", getOrNullOrSelf(this, PoseLandmark.RIGHT_SHOULDER))
            add("leftElbow", getOrNullOrSelf(this, PoseLandmark.LEFT_ELBOW))
            add("rightElbow", getOrNullOrSelf(this, PoseLandmark.RIGHT_ELBOW))
            add("leftWrist", getOrNullOrSelf(this, PoseLandmark.LEFT_WRIST))
            add("rightWrist", getOrNullOrSelf(this, PoseLandmark.RIGHT_WRIST))
            add("leftHip", getOrNullOrSelf(this, PoseLandmark.LEFT_HIP))
            add("rightHip", getOrNullOrSelf(this, PoseLandmark.RIGHT_HIP))
            add("leftKnee", getOrNullOrSelf(this, PoseLandmark.LEFT_KNEE))
            add("rightKnee", getOrNullOrSelf(this, PoseLandmark.RIGHT_KNEE))
            add("leftAnkle", getOrNullOrSelf(this, PoseLandmark.LEFT_ANKLE))
            add("rightAnkle", getOrNullOrSelf(this, PoseLandmark.RIGHT_ANKLE))
        }

        // ML Kit 无 neck，用「鼻-肩中点」近似
        val nose = pose.getPoseLandmark(PoseLandmark.NOSE)
        val ls = points["leftShoulder"] ?: (pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER)?.let { me(it) })
        val rs = points["rightShoulder"] ?: (pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER)?.let { me(it) })
        val noseP = me(nose)
        if (ls != null && rs != null) {
            val mid = NormPoint((ls.x + rs.x) / 2f, (ls.y + rs.y) / 2f)
            points["neck"] = if (noseP != null) {
                NormPoint((mid.x + noseP.x) / 2f, (mid.y + noseP.y) / 2f)
            } else mid
        }

        return points
    }

    private fun getOrNullOrSelf(list: List<PoseLandmark>, index: Int): PoseLandmark? =
        if (index in list.indices) list[index] else null

    /** EMA 平滑：new = old * 0.65 + current * 0.35（略偏重历史以稳定） */
    private fun smooth(current: Map<String, NormPoint>): Map<String, NormPoint> {
        if (previousPoints.isEmpty()) return current
        val result = LinkedHashMap<String, NormPoint>()
        for ((key, point) in current) {
            val oldPoint = previousPoints[key]
            result[key] = if (oldPoint != null) {
                NormPoint(
                    x = oldPoint.x * 0.65f + point.x * 0.35f,
                    y = oldPoint.y * 0.65f + point.y * 0.35f
                )
            } else {
                point
            }
        }
        return result
    }

    /** 计算归一化包围盒 */
    private fun computeBBox(points: Map<String, NormPoint>): RectF? {
        if (points.size < 3) return null
        var minX = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE
        var minY = Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
        for (p in points.values) {
            minX = minOf(minX, p.x); maxX = maxOf(maxX, p.x)
            minY = minOf(minY, p.y); maxY = maxOf(maxY, p.y)
        }
        return RectF(minX, minY, maxX, maxY)
    }

    /** 根据下半身关节点的存在情况判定半身 */
    private fun detectHalfBody(pose: Pose): Boolean {
        var lowerCount = 0
        val lower = listOf(
            PoseLandmark.LEFT_HIP, PoseLandmark.RIGHT_HIP,
            PoseLandmark.LEFT_KNEE, PoseLandmark.RIGHT_KNEE,
            PoseLandmark.LEFT_ANKLE, PoseLandmark.RIGHT_ANKLE
        )
        for (idx in lower) {
            if (idx in 0 until pose.getAllPoseLandmarks().size) {
                val lm = pose.getAllPoseLandmarks()[idx]
                if (lm != null && lm.inFrameLikelihood > 0.5f) lowerCount++
            }
        }
        return lowerCount < 3
    }

    /** 暗光监测（节流 5s） */
    private fun maybeDetectLowLight(isLowLightNow: Boolean) {
        if (isLowLightNow == isCurrentlyLowLight) return
        val now = System.currentTimeMillis()
        if ((now - lastLowLightTime > lowLightIntervalMs) || !isLowLightNow) {
            isCurrentlyLowLight = isLowLightNow
            lastLowLightTime = now
            onLowLight(isLowLightNow)
        }
    }

    fun close() {
        detector.close()
    }
}
