package com.poseai.app.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import com.poseai.app.model.SceneType
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import java.io.File
import kotlin.math.abs
import kotlin.math.sqrt

class SceneClassifier(context: Context, modelFilename: String = "scene_model.tflite") {

    companion object {
        private const val TAG = "SceneClassifier"
        // 默认预处理参数（与 iOS MobileNetV2 元数据一致：channelScale=1/127.5, bias=-1.0）
        private const val DEFAULT_CHANNEL_SCALE = 1f / 127.5f
        private const val DEFAULT_BIAS = -1f
    }

    private var interpreter: Interpreter? = null
    // 元数据驱动预处理参数（运行时从 scene_model_metadata.json 读取并校验）
    private var channelScale: Float = DEFAULT_CHANNEL_SCALE
    private var redBias: Float = DEFAULT_BIAS
    private var greenBias: Float = DEFAULT_BIAS
    private var blueBias: Float = DEFAULT_BIAS
    private var imageProcessor: ImageProcessor? = null
    private val inputSize = 224
    private var useFallback = false
    /** 是否使用关键词映射降级（比纯启发式更智能） */
    private var useKeywordMapper = false
    @Volatile
    private var isClosed = false
    /** 元数据校验结果：记录加载的模型架构信息，便于诊断 */
    private var metadataValidation: String? = null

    /** 关键词映射器：复刻 iOS MobileNetV2SceneProvider 的关键词投票逻辑 */
    private val keywordMapper: SceneKeywordMapper by lazy { SceneKeywordMapper(context) }

    private val labels = listOf(
        "COFFEE_SHOP",
        "STREET",
        "BEACH",
        "PARK",
        "HOME",
        "NIGHT_NEON",
        "UNKNOWN"
    )

    init {
        // 优先级 0：从 iOS 模型元数据加载预处理参数（运行时一致性校验）
        // 元数据文件由 convert_mlmodel_to_tflite.py 从 .mlmodel 提取生成
        loadPreprocessingParamsFromMetadata(context)

        // 用元数据驱动的预处理参数构建 ImageProcessor
        // NormalizeOp(mean, std): output = (input - mean) / std
        // 转换 iOS 公式 output = input * channelScale + bias 到 (mean, std) 形式：
        //   mean = -bias / channelScale, std = 1 / channelScale
        // 三通道 bias 不同时使用各通道独立计算（NormalizeOp 支持 3 通道均值/方差）
        val mean = floatArrayOf(-redBias / channelScale, -greenBias / channelScale, -blueBias / channelScale)
        val std = floatArrayOf(1f / channelScale, 1f / channelScale, 1f / channelScale)
        imageProcessor = ImageProcessor.Builder()
            .add(ResizeOp(inputSize, inputSize, ResizeOp.ResizeMethod.BILINEAR))
            .add(NormalizeOp(mean, std))
            .build()

        var loaded = false
        // 优先级 1：从内部存储加载 AIModelManager 下载的 TFLite 模型
        try {
            val modelFile = File(context.filesDir, "ai_models/$modelFilename")
            if (modelFile.exists() && modelFile.length() > 1024) {
                interpreter?.close()
                interpreter = Interpreter(modelFile)
                loaded = true
                Log.i(TAG, "Loaded TFLite model from ${modelFile.absolutePath}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load from filesDir: ${e.message}")
        }

        // 优先级 2：从 assets 加载内置 TFLite 模型
        if (!loaded) {
            try {
                val assetList = context.assets.list("")
                if (assetList?.contains(modelFilename) == true) {
                    val assetMapped = FileUtil.loadMappedFile(context, modelFilename)
                    interpreter?.close()
                    interpreter = Interpreter(assetMapped)
                    loaded = true
                    Log.i(TAG, "Loaded TFLite model from assets")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load from assets: ${e.message}")
            }
        }

        // 优先级 3：检查 assets 中的 iOS 模型元数据文件（.mlmodel 已转换为标签 JSON + 架构元数据）
        // 即使没有 TFLite 模型，也能加载 iOS 模型的标签数据，配合关键词映射做语义识别
        val hasIosModelAssets = try {
            val assetList = context.assets.list("")
            assetList?.any { it == "mobilenetv2_labels.json" || it == "googlenetplaces_labels.json" } == true
        } catch (_: Exception) {
            false
        }

        // 优先级 4：加载 iOS 模型架构元数据（用于运行时校验和调试）
        if (hasIosModelAssets) {
            metadataValidation = try {
                val metaFiles = listOf("scene_model_metadata.json", "GoogLeNetPlaces_metadata.json")
                val summaries = mutableListOf<String>()
                for (metaFile in metaFiles) {
                    val json = context.assets.open(metaFile).bufferedReader().use { it.readText() }
                    // 简单解析关键字段（不引入完整 JSON 库依赖）
                    val layerCount = Regex("\"layer_count\"\\s*:\\s*(\\d+)").find(json)?.groupValues?.get(1)
                    val totalParams = Regex("\"total_params\"\\s*:\\s*(\\d+)").find(json)?.groupValues?.get(1)
                    if (layerCount != null && totalParams != null) {
                        val modelName = metaFile.substringBefore("_metadata.json").substringBefore(".")
                        summaries.add("$modelName: $layerCount 层 / $totalParams 参数")
                    }
                }
                if (summaries.isNotEmpty()) summaries.joinToString("; ") else null
            } catch (_: Exception) {
                null
            }
        }

        useFallback = !loaded
        useKeywordMapper = !loaded && hasIosModelAssets
        if (useKeywordMapper) {
            val metaInfo = metadataValidation ?: "无元数据"
            Log.i(TAG, "Using keyword-mapping fallback (iOS MobileNetV2 keywords + label assets) | 元数据: $metaInfo")
        } else if (useFallback) {
            Log.i(TAG, "Using heuristic fallback classifier")
        } else if (loaded) {
            // 模型加载成功时打印预处理参数一致性校验结果
            Log.i(TAG, "Model loaded. Preprocessing params | channelScale=$channelScale, biases=[$redBias, $greenBias, $blueBias] | ${metadataValidation ?: "无元数据"}")
        }
    }

    /**
     * 从 scene_model_metadata.json 加载预处理参数（channelScale + bias）
     *
     * 元数据由 iOS 端 .mlmodel 提取生成，包含与 iOS CoreML 完全一致的预处理公式：
     *   output = input * channelScale + bias
     *
     * 这里读取后用于构建 TFLite 的 NormalizeOp，确保 Android 端预处理与 iOS 一致。
     * 若元数据缺失或解析失败，使用 MobileNetV2 默认值 (1/127.5, -1.0)。
     */
    private fun loadPreprocessingParamsFromMetadata(context: Context) {
        try {
            val json = context.assets.open("scene_model_metadata.json")
                .bufferedReader().use { it.readText() }
            // 解析 preprocessing 段：{"image": {"channelScale": ..., "redBias": ..., "greenBias": ..., "blueBias": ...}}
            val csMatch = Regex("\"channelScale\"\\s*:\\s*([\\-0-9.eE+]+)").find(json)
            val rMatch = Regex("\"redBias\"\\s*:\\s*([\\-0-9.eE+]+)").find(json)
            val gMatch = Regex("\"greenBias\"\\s*:\\s*([\\-0-9.eE+]+)").find(json)
            val bMatch = Regex("\"blueBias\"\\s*:\\s*([\\-0-9.eE+]+)").find(json)
            if (csMatch != null) {
                channelScale = csMatch.groupValues[1].toFloat()
                redBias = rMatch?.groupValues?.get(1)?.toFloat() ?: DEFAULT_BIAS
                greenBias = gMatch?.groupValues?.get(1)?.toFloat() ?: DEFAULT_BIAS
                blueBias = bMatch?.groupValues?.get(1)?.toFloat() ?: DEFAULT_BIAS
                Log.i(TAG, "Loaded preprocessing from metadata: scale=$channelScale, biases=[$redBias, $greenBias, $blueBias]")
            }
        } catch (e: Exception) {
            // 元数据文件缺失或解析失败时使用默认值（与 MobileNetV2 元数据等价）
            Log.d(TAG, "Metadata not available, using default MobileNetV2 preprocessing params")
        }
    }

    fun classify(bitmap: Bitmap): SceneType {
        if (isClosed) return SceneType.UNKNOWN
        return when {
            !useFallback -> classifyWithModel(bitmap)
            useKeywordMapper -> {
                // 优先用关键词映射（更接近 iOS 语义），失败再退回纯启发式
                try {
                    keywordMapper.classifyByKeywordVote(bitmap)
                } catch (e: Exception) {
                    Log.w(TAG, "Keyword mapper failed, falling back to heuristic", e)
                    classifyFallback(bitmap)
                }
            }
            else -> classifyFallback(bitmap)
        }
    }

    private fun classifyWithModel(bitmap: Bitmap): SceneType {
        val interp = interpreter ?: return classifyFallback(bitmap)
        val processor = imageProcessor ?: return classifyFallback(bitmap)
        if (isClosed) return SceneType.UNKNOWN

        return try {
            var tensorImage = TensorImage(DataType.FLOAT32)
            tensorImage.load(bitmap)
            tensorImage = processor.process(tensorImage)

            val output = Array(1) { FloatArray(labels.size) }
            interp.run(tensorImage.buffer, output)

            val probabilities = output[0]
            val maxIndex = probabilities.indices.maxByOrNull { probabilities[it] } ?: -1

            if (maxIndex >= 0 && maxIndex < labels.size && probabilities[maxIndex] > 0.3f) {
                SceneType.valueOf(labels[maxIndex])
            } else {
                SceneType.UNKNOWN
            }
        } catch (e: Exception) {
            Log.e(TAG, "Classification with model failed, using fallback", e)
            classifyFallback(bitmap)
        }
    }

    private fun classifyFallback(bitmap: Bitmap): SceneType {
        var small: Bitmap? = null
        return try {
            small = Bitmap.createScaledBitmap(bitmap, 64, 64, true)
            val stats = computeColorStats(small)

            val scores = mutableMapOf<SceneType, Float>()

            scores[SceneType.BEACH] = scoreBeach(stats)
            scores[SceneType.PARK] = scorePark(stats)
            scores[SceneType.HOME] = scoreHome(stats)
            scores[SceneType.COFFEE_SHOP] = scoreCoffeeShop(stats)
            scores[SceneType.STREET] = scoreStreet(stats)
            scores[SceneType.NIGHT_NEON] = scoreNightNeon(stats)

            val best = scores.maxByOrNull { it.value }
            if (best != null && best.value > 0.25f) {
                best.key
            } else {
                SceneType.UNKNOWN
            }
        } catch (e: Exception) {
            Log.e(TAG, "Fallback classification failed", e)
            SceneType.UNKNOWN
        } finally {
            small?.recycle()
        }
    }

    data class ColorStats(
        val avgR: Float,
        val avgG: Float,
        val avgB: Float,
        val brightness: Float,
        val contrast: Float,
        val saturation: Float,
        val greenRatio: Float,
        val blueRatio: Float,
        val warmRatio: Float,
        val skyBlueRatio: Float,
        val neonRatio: Float,
        val darkRatio: Float
    )

    private fun computeColorStats(bitmap: Bitmap): ColorStats {
        val width = bitmap.width
        val height = bitmap.height
        val total = width * height
        val pixels = IntArray(total)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        var sumR = 0L
        var sumG = 0L
        var sumB = 0L
        var sumLum = 0L
        var sumLumSq = 0L
        var greenPixels = 0
        var bluePixels = 0
        var warmPixels = 0
        var skyBluePixels = 0
        var neonPixels = 0
        var darkPixels = 0
        var satSum = 0f

        for (i in pixels.indices) {
            val pixel = pixels[i]
            val r = Color.red(pixel)
            val g = Color.green(pixel)
            val b = Color.blue(pixel)

            sumR += r
            sumG += g
            sumB += b

            val lum = (0.299 * r + 0.587 * g + 0.114 * b).toLong()
            sumLum += lum
            sumLumSq += lum * lum

            val max = maxOf(r, g, b)
            val min = minOf(r, g, b)
            val sat = if (max > 0) (max - min).toFloat() / max else 0f
            satSum += sat

            if (g > r && g > b && g > 80) greenPixels++
            if (b > r && b > g && b > 100) bluePixels++
            if (r > b && r > 80 && g > 60) warmPixels++
            if (b > 150 && b > g && b > r && (r + g) < 300) skyBluePixels++
            // 霓虹灯特征：高饱和度 + 某通道极高值（如纯红/纯蓝/纯品红）
            if (sat > 0.6f && max > 180) neonPixels++
            // 暗区像素：亮度 < 50
            if (lum < 50) darkPixels++
        }

        val avgR = sumR.toFloat() / total
        val avgG = sumG.toFloat() / total
        val avgB = sumB.toFloat() / total
        val brightness = sumLum.toFloat() / total
        val avgLumSq = sumLumSq.toFloat() / total
        val variance = avgLumSq - brightness * brightness
        val contrast = sqrt(variance.coerceAtLeast(0f))
        val saturation = satSum / total

        return ColorStats(
            avgR = avgR,
            avgG = avgG,
            avgB = avgB,
            brightness = brightness,
            contrast = contrast,
            saturation = saturation,
            greenRatio = greenPixels.toFloat() / total,
            blueRatio = bluePixels.toFloat() / total,
            warmRatio = warmPixels.toFloat() / total,
            skyBlueRatio = skyBluePixels.toFloat() / total,
            neonRatio = neonPixels.toFloat() / total,
            darkRatio = darkPixels.toFloat() / total
        )
    }

    private fun scoreBeach(stats: ColorStats): Float {
        var score = 0f
        if (stats.skyBlueRatio > 0.15f) score += 0.3f
        if (stats.blueRatio > 0.25f) score += 0.2f
        if (stats.brightness > 140) score += 0.2f
        if (stats.saturation > 0.3f) score += 0.15f
        if (stats.avgB > stats.avgR && stats.avgB > 110) score += 0.15f
        return score.coerceIn(0f, 1f)
    }

    private fun scorePark(stats: ColorStats): Float {
        var score = 0f
        if (stats.greenRatio > 0.25f) score += 0.35f
        if (stats.avgG > stats.avgR && stats.avgG > stats.avgB) score += 0.2f
        if (stats.brightness in 80f..160f) score += 0.15f
        if (stats.saturation > 0.25f) score += 0.15f
        if (stats.avgG > 100) score += 0.15f
        return score.coerceIn(0f, 1f)
    }

    private fun scoreHome(stats: ColorStats): Float {
        var score = 0f
        if (stats.warmRatio > 0.4f) score += 0.25f
        if (stats.brightness in 70f..140f) score += 0.2f
        if (stats.saturation < 0.35f) score += 0.2f
        if (stats.contrast < 60) score += 0.15f
        if (abs(stats.avgR - stats.avgG) < 20) score += 0.1f
        if (stats.avgR > stats.avgB) score += 0.1f
        return score.coerceIn(0f, 1f)
    }

    private fun scoreCoffeeShop(stats: ColorStats): Float {
        var score = 0f
        if (stats.warmRatio > 0.5f) score += 0.3f
        if (stats.brightness < 100) score += 0.25f
        if (stats.avgR > stats.avgB + 20) score += 0.15f
        if (stats.saturation in 0.15f..0.4f) score += 0.15f
        if (stats.contrast < 55) score += 0.15f
        return score.coerceIn(0f, 1f)
    }

    private fun scoreStreet(stats: ColorStats): Float {
        var score = 0f
        if (stats.contrast > 50) score += 0.25f
        if (stats.brightness in 90f..170f) score += 0.2f
        val neutralBalance = abs(stats.avgR - stats.avgG) + abs(stats.avgG - stats.avgB)
        if (neutralBalance < 40) score += 0.25f
        if (stats.saturation in 0.2f..0.45f) score += 0.15f
        if (stats.greenRatio < 0.2f) score += 0.15f
        return score.coerceIn(0f, 1f)
    }

    private fun scoreNightNeon(stats: ColorStats): Float {
        var score = 0f
        // 霓虹场景核心特征：暗背景 + 高饱和霓虹光源
        if (stats.darkRatio > 0.3f) score += 0.3f
        if (stats.neonRatio > 0.08f) score += 0.3f
        if (stats.brightness < 90f) score += 0.15f
        if (stats.contrast > 70) score += 0.15f
        if (stats.saturation > 0.35f) score += 0.1f
        return score.coerceIn(0f, 1f)
    }

    fun close() {
        if (isClosed) return
        isClosed = true
        try {
            interpreter?.close()
        } catch (_: Exception) {}
        interpreter = null
    }

    /** 暴露运行时状态供诊断 UI 使用 */
    fun isModelLoaded(): Boolean = !useFallback
    fun isUsingKeywordMapper(): Boolean = useKeywordMapper
    fun getPreprocessingParams(): String =
        "scale=$channelScale, biases=[$redBias, $greenBias, $blueBias]"
    fun getMetadataValidation(): String? = metadataValidation
}
