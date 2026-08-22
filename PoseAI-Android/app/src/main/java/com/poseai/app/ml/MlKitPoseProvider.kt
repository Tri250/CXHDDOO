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
 */
class MlKitPoseProvider(private val context: Context) {

    private val detector = PoseDetection.getClient(
        PoseDetectorOptions.Builder()
            .setDetectorMode(PoseDetectorOptions.STREAM_MODE)
            .build()
    )

    /** EMA 平滑状态（支持双人） */
    private val previousPointsArray = arrayOf<MutableMap<String, NormPoint>>(mutableMapOf(), mutableMapOf())

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
        imageWidth = imageProxy.width.toFloat()
        imageHeight = imageProxy.height.toFloat()
        val rotation = imageProxy.imageInfo.rotationDegrees
        val image = InputImage.fromMediaImage(mediaImage, rotation)

        detector.process(image)
            .addOnSuccessListener { pose -> handlePose(pose) }
            .addOnCompleteListener { imageProxy.close() }
            .addOnFailureListener { e ->
                @Suppress("UNUSED_EXPRESSION")
                when (e) {
                    is MlKitException -> { /* 处理失败，忽略该帧 */ }
                    else -> { /* ignore */ }
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

    private fun handlePose(pose: Pose) {
        val detectedPoints = parsePoints(pose)
        val isValid = detectedPoints.isNotEmpty()

        if (isValid) {
            // EMA 平滑
            val smoothed = smooth(0, detectedPoints)
            val bbox = computeBBox(smoothed)
            val isHalfBody = detectHalfBody(pose)

            previousPointsArray[0] = smoothed.toMutableMap()
            onUpdate(listOf(PoseData(smoothed, isHalfBody, bbox)))
            maybeDetectLowLight(isValidFramePoinCount = smoothed.size < 4)
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

        // ML Kit 无 neck，用「鼻-肩中点」近似（与 iOS Vision neck 位置近似）
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

    /** EMA 平滑：smoothed = old * 0.6 + new * 0.4 */
    private fun smooth(index: Int, current: Map<String, NormPoint>): Map<String, NormPoint> {
        val old = previousPointsArray[index]
        return current.mapValues { (key, p) ->
            val oldPoint = old[key]
            if (oldPoint != null) {
                NormPoint(oldPoint.x * 0.6f + p.x * 0.4f, oldPoint.y * 0.6f + p.y * 0.4f)
            } else p
        }
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
    private fun maybeDetectLowLight(isValidFramePoinCount: Boolean) {
        val lowLight = isValidFramePoinCount
        val now = System.currentTimeMillis()
        if (lowLight != isCurrentlyLowLight) {
            if ((now - lastLowLightTime > lowLightIntervalMs) || !lowLight) {
                isCurrentlyLowLight = lowLight
                lastLowLightTime = now
                onLowLight(lowLight)
            }
        }
    }

    fun close() {
        detector.close()
    }
}