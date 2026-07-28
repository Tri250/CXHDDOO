package com.poseai.app.engine

import android.content.Context
import android.graphics.PointF
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.File
import kotlin.math.sqrt

/**
 * 姿势相似度模型封装
 *
 * 激活 AIModelManager 中注册但未使用的 "pose_similarity" 模型。
 *
 * 设计：
 * - 模型输入：两组归一化关节坐标 (current, target)，展平为 FloatArray
 * - 模型输出：相似度标量 ∈ [0, 1]
 * - 当模型不可用时（assets 无 tflite、AIModelManager 未下载），降级到 PoseUtils.calculateSimilarity 的欧氏距离方案
 *
 * 与 iOS PoseSimilarityClassifier 对齐：
 * - 输入 17 个关键点 (COCO 标准)
 * - 预处理：坐标归一化到 [0, 1]，按 [x1,y1,c1, x2,y2,c2, ...] 顺序展开
 * - 后处理：sigmoid 输出已经落在 [0, 1]，无需额外处理
 */
class PoseSimilarityModel(context: Context) {

    companion object {
        private const val TAG = "PoseSimilarityModel"
        private const val MODEL_FILENAME = "pose_similarity.tflite"
        private const val NUM_KEYPOINTS = 17
        // 每个关键点 3 个值：x, y, confidence
        private const val INPUT_DIM = NUM_KEYPOINTS * 3

        // COCO 17 关键点顺序（与 iOS 端 PoseSimilarityClassifier 对齐）
        private val COCO_KEYPOINT_ORDER = listOf(
            "nose",
            "leftEye", "rightEye",
            "leftEar", "rightEar",
            "leftShoulder", "rightShoulder",
            "leftElbow", "rightElbow",
            "leftWrist", "rightWrist",
            "leftHip", "rightHip",
            "leftKnee", "rightKnee",
            "leftAnkle", "rightAnkle"
        )
    }

    private var interpreter: Interpreter? = null
    @Volatile
    private var isClosed = false
    private var useHeuristic = true

    init {
        // 优先级 1：从 AIModelManager 的下载目录加载
        var loaded = false
        try {
            val modelFile = File(context.filesDir, "ai_models/$MODEL_FILENAME")
            if (modelFile.exists() && modelFile.length() > 1024) {
                interpreter = Interpreter(modelFile)
                loaded = true
                Log.i(TAG, "Loaded pose_similarity.tflite from ${modelFile.absolutePath}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load from filesDir: ${e.message}")
        }

        // 优先级 2：从 assets 加载
        if (!loaded) {
            try {
                val assetList = context.assets.list("")
                if (assetList?.contains(MODEL_FILENAME) == true) {
                    val assetMapped = org.tensorflow.lite.support.common.FileUtil.loadMappedFile(
                        context, MODEL_FILENAME
                    )
                    interpreter = Interpreter(assetMapped)
                    loaded = true
                    Log.i(TAG, "Loaded pose_similarity.tflite from assets")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load from assets: ${e.message}")
            }
        }

        useHeuristic = !loaded
        if (useHeuristic) {
            Log.i(TAG, "Model not available, will use heuristic similarity (欧氏距离)")
        }
    }

    /**
     * 计算姿势相似度
     *
     * @param currentPoints 当前帧的归一化关节坐标
     * @param targetPoints 目标姿势的归一化关节坐标
     * @return 相似度 ∈ [0, 100]，100 表示完全匹配
     */
    fun computeSimilarity(
        currentPoints: Map<String, PointF>,
        targetPoints: Map<String, PointF>
    ): Float {
        if (isClosed) return 0f
        val interp = interpreter
        return if (interp != null && !useHeuristic) {
            try {
                computeWithModel(interp, currentPoints, targetPoints)
            } catch (e: Exception) {
                Log.w(TAG, "Model inference failed, falling back to heuristic", e)
                computeHeuristic(currentPoints, targetPoints)
            }
        } else {
            computeHeuristic(currentPoints, targetPoints)
        }
    }

    /**
     * 使用 TFLite 模型推理
     */
    private fun computeWithModel(
        interp: Interpreter,
        currentPoints: Map<String, PointF>,
        targetPoints: Map<String, PointF>
    ): Float {
        val input = encodePosePair(currentPoints, targetPoints)
        // 假设模型输入 shape: [1, INPUT_DIM*2]，输出 shape: [1, 1]
        val inputArray = Array(1) { input }
        val output = Array(1) { FloatArray(1) }
        interp.run(inputArray, output)
        val similarity = output[0][0]
        // 归一化到 [0, 100]
        return (similarity.coerceIn(0f, 1f) * 100f)
    }

    /**
     * 启发式相似度：基于加权欧氏距离，与 PoseUtils.calculateSimilarity 对齐
     */
    private fun computeHeuristic(
        currentPoints: Map<String, PointF>,
        targetPoints: Map<String, PointF>
    ): Float {
        if (currentPoints.isEmpty() || targetPoints.isEmpty()) return 0f

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
            val detected = currentPoints[key] ?: continue
            val weight = weights[key] ?: 1.0f
            val dx = detected.x - target.x
            val dy = detected.y - target.y
            val distance = sqrt(dx * dx + dy * dy)
            totalWeightedDistance += distance * weight
            totalWeight += weight
        }
        if (totalWeight <= 0f) return 0f
        val avgDistance = totalWeightedDistance / totalWeight
        return ((1f - (avgDistance / 0.5f).coerceIn(0f, 1f)) * 100f)
    }

    /**
     * 将两组关节坐标编码为模型输入向量
     * 顺序：[current_x1, current_y1, current_c1, ..., current_x17, current_y17, current_c17,
     *       target_x1, target_y1, target_c1, ..., target_x17, target_y17, target_c17]
     */
    private fun encodePosePair(
        current: Map<String, PointF>,
        target: Map<String, PointF>
    ): FloatArray {
        val out = FloatArray(INPUT_DIM * 2)
        var idx = 0
        for (kp in COCO_KEYPOINT_ORDER) {
            val cur = current[kp]
            out[idx++] = cur?.x ?: 0f
            out[idx++] = cur?.y ?: 0f
            out[idx++] = if (cur != null) 1f else 0f
        }
        for (kp in COCO_KEYPOINT_ORDER) {
            val tgt = target[kp]
            out[idx++] = tgt?.x ?: 0f
            out[idx++] = tgt?.y ?: 0f
            out[idx++] = if (tgt != null) 1f else 0f
        }
        return out
    }

    /** 模型是否已加载（true 表示使用模型推理，false 表示启发式降级） */
    fun isModelLoaded(): Boolean = !useHeuristic

    fun close() {
        if (isClosed) return
        isClosed = true
        try {
            interpreter?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Operation failed", e)
        }
        interpreter = null
    }
}
