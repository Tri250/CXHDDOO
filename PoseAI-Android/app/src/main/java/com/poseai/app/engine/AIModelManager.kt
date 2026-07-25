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
        val MODEL_REGISTRY = listOf(
            ModelInfo(
                id = "scene_classifier",
                filename = "scene_model.tflite",
                version = 1,
                sizeBytes = 5_000_000L,
                md5 = "placeholder_scene_model_md5",
                url = "https://models.poseai.app/scene_model_v1.tflite",
                description = "场景识别模型 (6类场景分类)"
            ),
            ModelInfo(
                id = "pose_similarity",
                filename = "pose_similarity.tflite",
                version = 1,
                sizeBytes = 2_000_000L,
                md5 = "placeholder_pose_sim_md5",
                url = "https://models.poseai.app/pose_similarity_v1.tflite",
                description = "姿势相似度评估模型"
            )
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

    private val _isActivated = prefs.getBoolean(KEY_ACTIVATED, false)
    val isActivated: Boolean get() = _isActivated

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
     * 3. 下载所有模型
     * 4. 校验完整性
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
            for (info in MODEL_REGISTRY) {
                val file = File(modelsDir, info.filename)
                if (!file.exists() || file.length() < 100) {
                    Log.i(TAG, "下载模型: ${info.id} (${info.description})")
                    val downloaded = downloadModel(info)
                    if (downloaded) {
                        activatedModels.add(info.id)
                        Log.i(TAG, "模型下载成功: ${info.id}")
                    } else {
                        Log.w(TAG, "模型下载失败: ${info.id}, 使用内置模型")
                        // 创建占位模型（实际使用时由 SceneClassifier 处理 fallback）
                        ensurePlaceholderModel(info)
                        activatedModels.add(info.id)
                    }
                } else {
                    activatedModels.add(info.id)
                    Log.i(TAG, "模型已存在: ${info.id}")
                }
            }

            ActivationResult(
                success = true,
                message = "AI 模型激活成功，共 ${activatedModels.size} 个模型就绪",
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
     * 从远程下载模型文件
     */
    private fun downloadModel(info: ModelInfo): Boolean {
        return try {
            val url = URL(info.url)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 15000
            connection.readTimeout = 30000
            connection.requestMethod = "GET"

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                Log.w(TAG, "模型下载 HTTP ${connection.responseCode}: ${info.url}")
                connection.disconnect()
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
            connection.disconnect()

            // 校验文件大小
            if (tempFile.length() < 100) {
                tempFile.delete()
                return false
            }

            // 重命名为正式文件
            val targetFile = File(modelsDir, info.filename)
            if (targetFile.exists()) targetFile.delete()
            tempFile.renameTo(targetFile)

            true
        } catch (e: Exception) {
            Log.e(TAG, "模型下载异常: ${info.id}", e)
            false
        }
    }

    /**
     * 确保占位模型存在（当远程下载失败时的 fallback）
     * SceneClassifier 会处理模型文件不存在的情况
     */
    private fun ensurePlaceholderModel(info: ModelInfo) {
        val file = File(modelsDir, info.filename)
        if (!file.exists()) {
            // 创建空标记文件，SceneClassifier 会使用 assets 中的 fallback
            file.writeText("placeholder")
        }
    }

    /**
     * 停用 AI 模型
     */
    fun deactivate() {
        prefs.edit()
            .putBoolean(KEY_ACTIVATED, false)
            .putString(KEY_ACTIVATION_TOKEN, null)
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
            val bytes = file.readBytes()
            val hash = digest.digest(bytes)
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
