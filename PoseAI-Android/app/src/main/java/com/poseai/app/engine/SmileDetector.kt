package com.poseai.app.engine

import android.util.Log

private const val TAG = "SmileDetector"
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class SmileDetector(
    triggerThreshold: Float = 0.7f,
    private val stableDurationMs: Long = 800L,
    private val cooldownMs: Long = 3000L
) {
    private val options = FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
        .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
        .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
        .build()

    private val detector = FaceDetection.getClient(options)

    /**
     * 微笑触发阈值，支持运行时更新
     * 激活 StoreManager.smileThreshold 持久化字段：用户可在设置页调节灵敏度
     * - 0.5f: 高灵敏度（容易触发，适合微笑不明显用户）
     * - 0.7f: 默认（标准灵敏度）
     * - 0.9f: 低灵敏度（需要明显笑容才触发）
     */
    @Volatile
    var triggerThreshold: Float = triggerThreshold
        set(value) {
            field = value.coerceIn(0.3f, 0.95f)
        }

    private var smoothedProbability: Float = 0f
    private var smileStableStartTime: Long = 0L
    private var lastTriggerTime: Long = 0L
    private var frameCount: Int = 0

    private val EMA_ALPHA = 0.3f

    val currentSmileProbability: Float
        get() = smoothedProbability

    suspend fun detect(image: InputImage): List<Face>? = suspendCancellableCoroutine { cont ->
        detector.process(image)
            .addOnSuccessListener { faces -> cont.resume(faces) }
            .addOnFailureListener { e ->
                Log.e(TAG, "Face detection failed", e)
                cont.resume(null)
            }
            .addOnCanceledListener { cont.cancel() }
    }

    suspend fun process(image: InputImage): Boolean {
        frameCount++
        if (frameCount % 3 != 0) {
            return false
        }

        if (System.currentTimeMillis() - lastTriggerTime < cooldownMs) {
            return false
        }

        val faces = detect(image) ?: return false
        if (faces.isEmpty()) {
            smoothedProbability = 0f
            smileStableStartTime = 0L
            return false
        }

        val mainFace = faces.maxByOrNull { it.smilingProbability ?: 0f } ?: return false
        val rawProb = mainFace.smilingProbability ?: 0f

        smoothedProbability = if (smoothedProbability <= 0f) {
            rawProb
        } else {
            smoothedProbability * (1 - EMA_ALPHA) + rawProb * EMA_ALPHA
        }

        if (smoothedProbability >= triggerThreshold) {
            if (smileStableStartTime == 0L) {
                smileStableStartTime = System.currentTimeMillis()
            } else if (System.currentTimeMillis() - smileStableStartTime >= stableDurationMs) {
                lastTriggerTime = System.currentTimeMillis()
                smileStableStartTime = 0L
                return true
            }
        } else {
            smileStableStartTime = 0L
        }

        return false
    }

    fun reset() {
        smoothedProbability = 0f
        smileStableStartTime = 0L
        lastTriggerTime = 0L
        frameCount = 0
    }

    fun close() {
        try {
            detector.close()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to close detector", e)
        }
    }
}
