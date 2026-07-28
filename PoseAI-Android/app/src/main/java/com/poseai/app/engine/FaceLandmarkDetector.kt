package com.poseai.app.engine

import android.graphics.Bitmap
import android.graphics.PointF
import android.graphics.RectF
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceContour
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import kotlin.math.sqrt

/**
 * 人脸关键点检测系统
 *
 * 基于 ML Kit Face Detection，提取完整人脸轮廓与所有关键点，
 * 为高级美颜、美妆、皮肤修复、AR特效提供精准的人脸几何数据。
 *
 * 检测能力：
 * - 全部轮廓（脸/眼/眉/鼻/唇/颊）
 * - 全部关键点（眼/鼻/嘴/颊/耳）
 * - 表情分类（微笑概率/睁眼概率）
 * - 头部姿态（欧拉角 X/Y/Z）
 * - 人脸追踪 ID
 */
class FaceLandmarkDetector {

    /** 单张人脸的完整关键点数据 */
    data class FaceData(
        // ── 脸部轮廓 ──
        val faceContour: List<PointF>,          // 完整脸部轮廓（36点）
        val faceOval: List<PointF>,             // 脸部椭圆轮廓
        val faceBounds: RectF,                  // 脸部边界框

        // ── 眼部 ──
        val leftEyeContour: List<PointF>,       // 左眼轮廓（16点）
        val rightEyeContour: List<PointF>,      // 右眼轮廓（16点）
        val leftEyeCenter: PointF,              // 左眼中心
        val rightEyeCenter: PointF,             // 右眼中心
        val leftEyeOpen: Float,                 // 左眼睁开概率 0-1
        val rightEyeOpen: Float,                // 右眼睁开概率 0-1

        // ── 眉毛 ──
        val leftEyebrowContour: List<PointF>,   // 左眉轮廓
        val rightEyebrowContour: List<PointF>,  // 右眉轮廓
        val leftEyebrowTop: PointF,             // 左眉最高点
        val rightEyebrowTop: PointF,            // 右眉最高点

        // ── 鼻部 ──
        val noseBridge: List<PointF>,           // 鼻梁轮廓
        val noseBottom: List<PointF>,           // 鼻底轮廓
        val noseBase: PointF,                   // 鼻尖/鼻底中心
        val noseLeft: PointF,                   // 鼻翼左侧
        val noseRight: PointF,                  // 鼻翼右侧

        // ── 唇部 ──
        val upperLipTop: List<PointF>,          // 上唇上沿
        val upperLipBottom: List<PointF>,       // 上唇下沿
        val lowerLipTop: List<PointF>,          // 下唇上沿
        val lowerLipBottom: List<PointF>,       // 下唇下沿
        val mouthLeft: PointF,                  // 嘴左角
        val mouthRight: PointF,                 // 嘴右角
        val mouthCenter: PointF,                // 嘴中心
        val mouthBottom: PointF,               // 下唇底

        // ── 脸颊 ──
        val leftCheek: PointF,                  // 左脸颊
        val rightCheek: PointF,                 // 右脸颊

        // ── 耳部 ──
        val leftEar: PointF?,                   // 左耳（可见时）
        val rightEar: PointF?,                  // 右耳（可见时）

        // ── 表情与姿态 ──
        val smilingProbability: Float,          // 微笑概率 0-1
        val headEulerAngleX: Float,             // 俯仰角（度）
        val headEulerAngleY: Float,             // 偏航角（度）
        val headEulerAngleZ: Float,             // 翻滚角（度）
        val trackingId: Int?,                   // 追踪 ID

        // ── 派生数据 ──
        val faceWidth: Float,                   // 脸宽（像素）
        val faceHeight: Float,                  // 脸高（像素）
        val faceCenter: PointF,                 // 脸中心
        val rollAngle: Float,                   // 翻滚角（= Z轴欧拉角）
        val interocularDistance: Float          // 瞳距（像素）
    )

    private val detector: FaceDetector

    init {
        val optionsBuilder = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .setMinFaceSize(0.15f)
        @Suppress("DEPRECATION")
        optionsBuilder.enableTracking()
        val options = optionsBuilder.build()
        detector = FaceDetection.getClient(options)
    }

    /**
     * 从 Bitmap 检测所有人脸
     * 注意：此方法同步阻塞等待 ML Kit 结果，必须在 IO 线程调用，禁止在主线程使用。
     */
    fun detect(bitmap: Bitmap): List<FaceData> {
        val image = InputImage.fromBitmap(bitmap, 0)
        // ML Kit detect 返回 Task<List<Face>>，此处使用同步等待
        val latch = java.util.concurrent.CountDownLatch(1)
        val facesRef = java.util.concurrent.atomic.AtomicReference<List<Face>>(emptyList())
        val errorRef = java.util.concurrent.atomic.AtomicReference<Exception?>(null)

        detector.process(image)
            .addOnSuccessListener { detectedFaces ->
                facesRef.set(detectedFaces)
                latch.countDown()
            }
            .addOnFailureListener { e ->
                errorRef.set(e)
                latch.countDown()
            }

        val completed = latch.await(5, java.util.concurrent.TimeUnit.SECONDS)
        if (!completed) {
            android.util.Log.w("FaceLandmarkDetector", "detect timed out after 5s")
            return emptyList()
        }

        errorRef.get()?.let {
            android.util.Log.e("FaceLandmarkDetector", "Face detection failed", it)
            return emptyList()
        }

        val result = mutableListOf<FaceData>()
        for (face in facesRef.get()) {
            result.add(convertFace(face, bitmap.width, bitmap.height))
        }
        return result
    }

    /**
     * 从 ImageProxy 检测（实时相机预览用）
     * 注意：此方法同步阻塞等待 ML Kit 结果，必须在 IO 线程调用，禁止在主线程使用。
     */
    fun detectFromImageProxy(imageProxy: ImageProxy): List<FaceData> {
        val image = InputImage.fromMediaImage(
            imageProxy.image ?: return emptyList(),
            imageProxy.imageInfo.rotationDegrees
        )
        val latch = java.util.concurrent.CountDownLatch(1)
        val facesRef = java.util.concurrent.atomic.AtomicReference<List<Face>>(emptyList())
        val errorRef = java.util.concurrent.atomic.AtomicReference<Exception?>(null)

        detector.process(image)
            .addOnSuccessListener { detectedFaces ->
                facesRef.set(detectedFaces)
                latch.countDown()
            }
            .addOnFailureListener { e ->
                errorRef.set(e)
                latch.countDown()
            }

        val completed = latch.await(3, java.util.concurrent.TimeUnit.SECONDS)
        if (!completed) {
            android.util.Log.w("FaceLandmarkDetector", "detectFromImageProxy timed out after 3s")
            return emptyList()
        }

        errorRef.get()?.let {
            android.util.Log.e("FaceLandmarkDetector", "Face detection failed", it)
            return emptyList()
        }

        val result = mutableListOf<FaceData>()
        for (face in facesRef.get()) {
            result.add(convertFace(face, imageProxy.width, imageProxy.height))
        }
        return result
    }

    /** 释放检测器资源 */
    fun close() {
        detector.close()
    }

    // ═══════════════════════════════════════════════════════════════
    // 内部转换
    // ═══════════════════════════════════════════════════════════════

    private fun convertFace(face: Face, imgWidth: Int, imgHeight: Int): FaceData {
        // 脸部轮廓
        val faceContour = face.getContour(FaceContour.FACE)?.points?.map { PointF(it.x, it.y) } ?: emptyList()
        val faceOval = faceContour // ML Kit 的 FACE contour 就是椭圆轮廓

        // 边界框
        val bounds = face.boundingBox
        val faceBounds = RectF(bounds.left.toFloat(), bounds.top.toFloat(), bounds.right.toFloat(), bounds.bottom.toFloat())

        // 眼部轮廓
        val leftEyeContour = face.getContour(FaceContour.LEFT_EYE)?.points?.map { PointF(it.x, it.y) } ?: emptyList()
        val rightEyeContour = face.getContour(FaceContour.RIGHT_EYE)?.points?.map { PointF(it.x, it.y) } ?: emptyList()
        val leftEyeCenter = centroid(leftEyeContour)
        val rightEyeCenter = centroid(rightEyeContour)

        // 眉毛轮廓
        val leftEyebrowContour = face.getContour(FaceContour.LEFT_EYEBROW_TOP)?.points?.map { PointF(it.x, it.y) } ?: emptyList()
        val rightEyebrowContour = face.getContour(FaceContour.RIGHT_EYEBROW_TOP)?.points?.map { PointF(it.x, it.y) } ?: emptyList()
        val leftEyebrowTop = leftEyebrowContour.minByOrNull { it.y } ?: leftEyeCenter
        val rightEyebrowTop = rightEyebrowContour.minByOrNull { it.y } ?: rightEyeCenter

        // 鼻部
        val noseBridge = face.getContour(FaceContour.NOSE_BRIDGE)?.points?.map { PointF(it.x, it.y) } ?: emptyList()
        val noseBottom = face.getContour(FaceContour.NOSE_BOTTOM)?.points?.map { PointF(it.x, it.y) } ?: emptyList()
        val noseBase = if (noseBottom.isNotEmpty()) centroid(noseBottom) else centerOf(bounds)
        val noseLeft = noseBottom.minByOrNull { it.x } ?: noseBase
        val noseRight = noseBottom.maxByOrNull { it.x } ?: noseBase

        // 唇部
        val upperLipTop = face.getContour(FaceContour.UPPER_LIP_TOP)?.points?.map { PointF(it.x, it.y) } ?: emptyList()
        val upperLipBottom = face.getContour(FaceContour.UPPER_LIP_BOTTOM)?.points?.map { PointF(it.x, it.y) } ?: emptyList()
        val lowerLipTop = face.getContour(FaceContour.LOWER_LIP_TOP)?.points?.map { PointF(it.x, it.y) } ?: emptyList()
        val lowerLipBottom = face.getContour(FaceContour.LOWER_LIP_BOTTOM)?.points?.map { PointF(it.x, it.y) } ?: emptyList()

        val allLipPoints = upperLipTop + upperLipBottom + lowerLipTop + lowerLipBottom
        val mouthLeft = allLipPoints.minByOrNull { it.x } ?: noseBase
        val mouthRight = allLipPoints.maxByOrNull { it.x } ?: noseBase
        val mouthCenter = if (allLipPoints.isNotEmpty()) centroid(allLipPoints) else noseBase
        val mouthBottom = lowerLipBottom.maxByOrNull { it.y } ?: mouthCenter

        // 脸颊
        val leftCheekPoint = face.getLandmark(FaceLandmark.LEFT_CHEEK)?.position
        val rightCheekPoint = face.getLandmark(FaceLandmark.RIGHT_CHEEK)?.position
        val leftCheek = if (leftCheekPoint != null) PointF(leftCheekPoint.x, leftCheekPoint.y) else estimateCheek(faceContour, isLeft = true)
        val rightCheek = if (rightCheekPoint != null) PointF(rightCheekPoint.x, rightCheekPoint.y) else estimateCheek(faceContour, isLeft = false)

        // 耳部
        val leftEar = face.getLandmark(FaceLandmark.LEFT_EAR)?.position?.let { PointF(it.x, it.y) }
        val rightEar = face.getLandmark(FaceLandmark.RIGHT_EAR)?.position?.let { PointF(it.x, it.y) }

        // 派生数据
        val faceWidth = bounds.width().toFloat()
        val faceHeight = bounds.height().toFloat()
        val faceCenter = centerOf(bounds)
        val interocular = distance(leftEyeCenter, rightEyeCenter)

        return FaceData(
            faceContour = faceContour,
            faceOval = faceOval,
            faceBounds = faceBounds,
            leftEyeContour = leftEyeContour,
            rightEyeContour = rightEyeContour,
            leftEyeCenter = leftEyeCenter,
            rightEyeCenter = rightEyeCenter,
            leftEyeOpen = face.leftEyeOpenProbability ?: 1f,
            rightEyeOpen = face.rightEyeOpenProbability ?: 1f,
            leftEyebrowContour = leftEyebrowContour,
            rightEyebrowContour = rightEyebrowContour,
            leftEyebrowTop = leftEyebrowTop,
            rightEyebrowTop = rightEyebrowTop,
            noseBridge = noseBridge,
            noseBottom = noseBottom,
            noseBase = noseBase,
            noseLeft = noseLeft,
            noseRight = noseRight,
            upperLipTop = upperLipTop,
            upperLipBottom = upperLipBottom,
            lowerLipTop = lowerLipTop,
            lowerLipBottom = lowerLipBottom,
            mouthLeft = mouthLeft,
            mouthRight = mouthRight,
            mouthCenter = mouthCenter,
            mouthBottom = mouthBottom,
            leftCheek = leftCheek,
            rightCheek = rightCheek,
            leftEar = leftEar,
            rightEar = rightEar,
            smilingProbability = face.smilingProbability ?: 0f,
            headEulerAngleX = face.headEulerAngleX,
            headEulerAngleY = face.headEulerAngleY,
            headEulerAngleZ = face.headEulerAngleZ,
            trackingId = face.trackingId,
            faceWidth = faceWidth,
            faceHeight = faceHeight,
            faceCenter = faceCenter,
            rollAngle = face.headEulerAngleZ,
            interocularDistance = interocular
        )
    }

    /** 计算点集重心 */
    private fun centroid(points: List<PointF>): PointF {
        if (points.isEmpty()) return PointF(0f, 0f)
        var sx = 0f
        var sy = 0f
        for (p in points) {
            sx += p.x
            sy += p.y
        }
        return PointF(sx / points.size, sy / points.size)
    }

    /** 计算 RectF 中心 */
    private fun centerOf(rect: android.graphics.Rect): PointF {
        return PointF(rect.exactCenterX(), rect.exactCenterY())
    }

    /** 两点距离 */
    private fun distance(a: PointF, b: PointF): Float {
        val dx = b.x - a.x
        val dy = b.y - a.y
        return sqrt(dx * dx + dy * dy)
    }

    /** 当 ML Kit 未返回脸颊点时，从脸部轮廓估算 */
    private fun estimateCheek(faceContour: List<PointF>, isLeft: Boolean): PointF {
        if (faceContour.isEmpty()) return PointF(0f, 0f)
        // 左脸颊 = 轮廓中 x 最小的一半区域的中心
        val sorted = if (isLeft) {
            faceContour.sortedBy { it.x }.take(faceContour.size / 3)
        } else {
            faceContour.sortedByDescending { it.x }.take(faceContour.size / 3)
        }
        return centroid(sorted)
    }
}
