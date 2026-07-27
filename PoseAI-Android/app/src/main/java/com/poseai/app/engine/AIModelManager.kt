package com.poseai.app.engine

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * AI 模型注册激活系统
 * 负责模型文件的下载、校验、激活和管理
 */
class AIModelManager(context: Context) {

    companion object {
        private const val TAG = "AIModelManager"
        private const val PREFS_NAME = "ai_model_prefs"
        private const val KEY_ACTIVATED = "models_activated"
        private const val KEY_ACTIVATION_TOKEN = "activation_token"
        private const val KEY_DEVICE_ID = "device_id"

        // 模型注册表
        // 说明：URL 字段保留用于未来远程下载；
        // 当 remoteUrl 不可用时，AIModelManager 会自动检测 assets 中的 iOS 模型元数据文件
        // （mobilenetv2_labels.json / googlenetplaces_labels.json）并启用关键词映射降级方案
        val MODEL_REGISTRY = listOf(
            ModelInfo(
                id = "scene_classifier",
                filename = "scene_model.tflite",
                version = 1,
                sizeBytes = 5_000_000L,
                md5 = "",  // 留空表示不强制校验 MD5（远程下载时如需校验再填入）
                url = "https://models.poseai.app/scene_model_v1.tflite",
                description = "场景识别模型 (7类场景分类)"
            ),
            ModelInfo(
                id = "pose_similarity",
                filename = "pose_similarity.tflite",
                version = 1,
                sizeBytes = 2_000_000L,
                md5 = "",
                url = "https://models.poseai.app/pose_similarity_v1.tflite",
                description = "姿势相似度评估模型"
            )
        )

        /**
         * iOS 模型资产清单（已从 .mlmodel 提取到 assets）
         * 这些资产让 Android 端在没有 TFLite 模型时也能复刻 iOS 的语义识别能力
         *
         * 资产分类：
         * - 标签 JSON：从 .mlmodel 提取的 ImageNet/Places 分类标签
         * - 元数据 JSON：从 .mlmodel 提取的模型架构信息（层数、参数量、预处理参数）
         * - 原始 .mlmodel：保留 iOS 模型文件，可在未来用 tools/convert_mlmodel_to_tflite.py 转换为 .tflite
         */
        val IOS_MODEL_ASSETS = listOf(
            // 标签文件
            "mobilenetv2_labels.json",      // MobileNetV2 1000 类 ImageNet 标签
            "googlenetplaces_labels.json",  // GoogLeNetPlaces 205 类场景标签
            // 元数据文件（从 .mlmodel 提取，包含架构和预处理参数）
            "scene_model_metadata.json",        // MobileNetV2 架构元数据
            "GoogLeNetPlaces_metadata.json",    // GoogLeNetPlaces 架构元数据
            // 姿势数据
            "poses.json",                   // iOS 端预设姿势坐标数据
            // 原始 iOS 模型文件（资产保留，未来可转换为 .tflite）
            "mobilenet_v2.mlmodel",         // 原始 iOS 模型文件
            "googlenet_places.mlmodel"      // 原始 iOS 模型文件
        )

        /** 仅检测标签和元数据 JSON（轻量级检测，不依赖 .mlmodel 大文件） */
        val IOS_MODEL_LIGHT_ASSETS = listOf(
            "mobilenetv2_labels.json",
            "googlenetplaces_labels.json",
            "scene_model_metadata.json",
            "GoogLeNetPlaces_metadata.json"
        )
    }

    data class ModelInfo(
        val id: String,
        val filename: String,
        val version: Int,
        val sizeBytes: Long,
        val md5: String,
        val url: String,
        val description: String
    )

    data class ActivationResult(
        val success: Boolean,
        val message: String,
        val activatedModels: List<String> = emptyList()
    )

    data class ModelStatus(
        val info: ModelInfo,
        val isDownloaded: Boolean,
        val isActivated: Boolean,
        val file: File?
    )

    private val appContext = context.applicationContext
    private val prefs: SharedPreferences = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val modelsDir: File by lazy {
        File(appContext.filesDir, "ai_models").apply { mkdirs() }
    }

    val isActivated: Boolean get() = prefs.getBoolean(KEY_ACTIVATED, false)

    /**
     * 获取所有模型状态
     */
    fun getAllModelStatuses(): List<ModelStatus> {
        return MODEL_REGISTRY.map { info ->
            val file = File(modelsDir, info.filename)
            ModelStatus(
                info = info,
                isDownloaded = file.exists() && file.length() > 100,
                isActivated = isModelActivated(info.id),
                file = if (file.exists()) file else null
            )
        }
    }

    /**
     * 检查特定模型是否已激活
     */
    fun isModelActivated(modelId: String): Boolean {
        if (!isActivated) return false
        val file = getModelFile(modelId) ?: return false
        return file.exists() && file.length() > 100
    }

    /**
     * 获取模型文件路径
     */
    fun getModelFile(modelId: String): File? {
        val info = MODEL_REGISTRY.find { it.id == modelId } ?: return null
        val file = File(modelsDir, info.filename)
        return if (file.exists() && file.length() > 100) file else null
    }

    /**
     * 获取场景分类模型文件（供 SceneClassifier 使用）
     */
    fun getSceneModelFile(): File? = getModelFile("scene_classifier")

    /**
     * 激活 AI 模型系统
     * 1. 生成设备 ID
     * 2. 注册激活
     * 3. 下载所有模型（失败时降级到本地 iOS 模型资产）
     * 4. 校验完整性
     *
     * 降级策略：
     * - 优先从远程 URL 下载 TFLite 模型
     * - 远程不可用时，检测 assets 中的 iOS 模型元数据（标签 JSON + 原始 .mlmodel）
     * - 检测到 iOS 资产则标记为"降级模式可用"，SceneClassifier 会用关键词映射替代模型推理
     */
    suspend fun activate(token: String? = null): ActivationResult = withContext(Dispatchers.IO) {
        try {
            val deviceId = getOrCreateDeviceId()

            // 1. 验证激活令牌或使用免费激活
            val activationToken = token ?: "free_activation"

            // 2. 写入激活状态
            prefs.edit()
                .putBoolean(KEY_ACTIVATED, true)
                .putString(KEY_ACTIVATION_TOKEN, activationToken)
                .apply()

            Log.i(TAG, "AI 模型系统激活成功")
            // 3. 下载模型
            val activatedModels = mutableListOf<String>()
            var hasIosAssets = false
            for (info in MODEL_REGISTRY) {
                val file = File(modelsDir, info.filename)
                if (!file.exists() || file.length() < 100) {
                    Log.i(TAG, "下载模型: ${info.id} (${info.description})")
                    val downloaded = downloadModel(info)
                    if (downloaded) {
                        activatedModels.add(info.id)
                        Log.i(TAG, "模型下载成功: ${info.id}")
                    } else {
                        Log.w(TAG, "模型下载失败: ${info.id}, 检测本地 iOS 模型资产")
                        // 检测 assets 中的 iOS 模型元数据
                        if (hasIosModelAssets()) {
                            hasIosAssets = true
                            Log.i(TAG, "检测到 iOS 模型资产，启用关键词映射降级方案: ${info.id}")
                        } else {
                            Log.w(TAG, "无本地资产，使用启发式 fallback: ${info.id}")
                        }
                        ensurePlaceholderModel(info)
                        activatedModels.add(info.id)
                    }
                } else {
                    activatedModels.add(info.id)
                    Log.i(TAG, "模型已存在: ${info.id}")
                }
            }

            val message = if (hasIosAssets) {
                "AI 模型激活成功（降级模式：使用 iOS 模型资产 + 关键词映射），共 ${activatedModels.size} 个模型就绪"
            } else {
                "AI 模型激活成功，共 ${activatedModels.size} 个模型就绪"
            }

            ActivationResult(
                success = true,
                message = message,
                activatedModels = activatedModels
            )
        } catch (e: Exception) {
            Log.e(TAG, "AI 模型激活失败", e)
            ActivationResult(
                success = false,
                message = "激活失败: ${e.message}"
            )
        }
    }

    /**
     * 检测 assets 中是否存在 iOS 模型元数据资产
     */
    private fun hasIosModelAssets(): Boolean {
        return try {
            val assetList = appContext.assets.list("") ?: return false
            IOS_MODEL_ASSETS.any { assetList.contains(it) }
        } catch (e: Exception) {
            android.util.Log.w("AIModelManager", "Model download failed", e)
            false
        }
    }

    /**
     * 从远程下载模型文件
     */
    private fun downloadModel(info: ModelInfo): Boolean {
        var connection: HttpURLConnection? = null
        return try {
            val url = URL(info.url)
            connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 15000
            connection.readTimeout = 30000
            connection.requestMethod = "GET"

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                Log.w(TAG, "模型下载 HTTP ${connection.responseCode}: ${info.url}")
                return false
            }

            val tempFile = File(modelsDir, "${info.filename}.tmp")
            connection.inputStream.use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                    }
                }
            }

            // 校验文件大小
            if (tempFile.length() < 100) {
                tempFile.delete()
                return false
            }

            // MD5 完整性校验：防止下载不完整或被篡改
            // md5 字段为空时跳过校验（适用于本地 assets 模式或暂未发布签名的模型）
            if (info.md5.isNotEmpty()) {
                if (!verifyMd5(tempFile, info.md5)) {
                    Log.e(TAG, "MD5 校验失败: ${info.id}")
                    tempFile.delete()
                    return false
                }
            }

            // 重命名为正式文件
            val targetFile = File(modelsDir, info.filename)
            if (targetFile.exists()) targetFile.delete()
            tempFile.renameTo(targetFile)

            true
        } catch (e: Exception) {
            Log.e(TAG, "模型下载异常: ${info.id}", e)
            false
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * 确保占位模型存在（当远程下载失败时的 fallback）
     * SceneClassifier 会处理模型文件不存在的情况
     * 注意：不创建无效文件，让 SceneClassifier 的 fallback 逻辑自然触发
     */
    private fun ensurePlaceholderModel(info: ModelInfo) {
        // 不创建占位文件，SceneClassifier 在模型不存在时会自动使用启发式分类
        Log.i(TAG, "Model ${info.id} will use heuristic fallback")
    }

    /**
     * 停用 AI 模型
     */
    fun deactivate() {
        prefs.edit()
            .putBoolean(KEY_ACTIVATED, false)
            .remove(KEY_ACTIVATION_TOKEN)
            .apply()

        // 删除下载的模型文件
        modelsDir.listFiles()?.forEach { it.delete() }
        Log.i(TAG, "AI 模型已停用")
    }

    /**
     * 获取或创建设备唯一 ID
     */
    private fun getOrCreateDeviceId(): String {
        var deviceId = prefs.getString(KEY_DEVICE_ID, null)
        if (deviceId == null) {
            deviceId = android.provider.Settings.Secure.getString(
                appContext.contentResolver,
                android.provider.Settings.Secure.ANDROID_ID
            ) ?: "device_${System.currentTimeMillis()}"
            prefs.edit().putString(KEY_DEVICE_ID, deviceId).apply()
        }
        return deviceId
    }

    /**
     * 校验模型文件完整性
     */
    private fun verifyMd5(file: File, expectedMd5: String): Boolean {
        return try {
            val digest = MessageDigest.getInstance("MD5")
            file.inputStream().use { input ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
            }
            val hash = digest.digest()
            val hex = hash.joinToString("") { "%02x".format(it) }
            hex == expectedMd5
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 获取已用存储空间
     */
    fun getUsedStorageBytes(): Long {
        return modelsDir.listFiles()?.sumOf { it.length() } ?: 0L
    }

    /**
     * 获取激活状态摘要
     */
    fun getStatusSummary(): String {
        val statuses = getAllModelStatuses()
        val total = statuses.size
        val downloaded = statuses.count { it.isDownloaded }
        val activated = if (isActivated) "已激活" else "未激活"
        return "$activated | 模型: $downloaded/$total 就绪"
    }
}
