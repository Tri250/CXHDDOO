package com.poseai.app.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.util.Log
import com.poseai.app.model.SceneType
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * OOTD 穿搭分析引擎
 *
 * 集成 SceneClassifier 实现场景感知的穿搭分析，基于真实图像像素计算色彩和谐度、
 * 比例协调度、风格契合度等指标，并根据场景类型给出差异化建议。
 */
class OotdAnalyzer(context: Context) {

    companion object {
        private const val TAG = "OotdAnalyzer"
        private const val ANALYSIS_SAMPLE_SIZE = 512
    }

    private var sceneClassifier: SceneClassifier? = null

    init {
        try {
            sceneClassifier = SceneClassifier(context)
            Log.i(TAG, "SceneClassifier initialized for OOTD analysis")
        } catch (e: Exception) {
            Log.w(TAG, "SceneClassifier not available, will use color-only analysis", e)
        }
    }

    /**
     * 分析结果数据类
     */
    data class Result(
        val overallScore: Float,
        val colorHarmony: Float,
        val proportionScore: Float,
        val styleMatch: Float,
        val suggestions: List<String>,
        val styleTags: List<String>,
        val detectedScene: SceneType = SceneType.UNKNOWN,
        val error: String? = null
    )

    /**
     * 对指定路径的图片执行 OOTD 分析
     *
     * @param imagePath 图片文件路径
     * @return 分析结果，error 字段非 null 表示分析失败
     */
    fun analyze(imagePath: String): Result {
        // 1. 解码图片（降采样避免 OOM）
        val bitmap = decodeSampledBitmap(imagePath)
            ?: return Result(
                overallScore = 0f, colorHarmony = 0f, proportionScore = 0f,
                styleMatch = 0f, suggestions = emptyList(), styleTags = emptyList(),
                error = "图片解码失败，请选择其他图片"
            )

        return try {
            analyzeBitmap(bitmap)
        } finally {
            bitmap.recycle()
        }
    }

    /**
     * 对 Bitmap 执行分析（内部方法，确保 bitmap 被回收）
     */
    private fun analyzeBitmap(bitmap: Bitmap): Result {
        // 2. 场景分类（利用 SceneClassifier 的 TFLite 模型或启发式算法）
        val scene = try {
            sceneClassifier?.classify(bitmap) ?: SceneType.UNKNOWN
        } catch (e: Exception) {
            Log.w(TAG, "Scene classification failed", e)
            SceneType.UNKNOWN
        }
        Log.i(TAG, "Detected scene: $scene")

        // 3. 像素级色彩分析
        val width = bitmap.width
        val height = bitmap.height
        val sampleStep = (maxOf(width, height) / 32).coerceAtLeast(4)

        var sumR = 0L; var sumG = 0L; var sumB = 0L
        var sumLum = 0L; var sumLumSq = 0L
        var warmPixels = 0; var coolPixels = 0
        var neonPixels = 0; var darkPixels = 0; var brightPixels = 0
        var highSatPixels = 0; var lowSatPixels = 0
        var totalSampled = 0

        // 色彩分布：将图片分为上/中/下三段，分别统计
        val thirdH = height / 3
        val topStats = RegionStats()
        val midStats = RegionStats()
        val botStats = RegionStats()

        for (y in 0 until height step sampleStep) {
            for (x in 0 until width step sampleStep) {
                val pixel = bitmap.getPixel(x, y)
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)

                sumR += r; sumG += g; sumB += b
                val lum = (0.299 * r + 0.587 * g + 0.114 * b).toLong()
                sumLum += lum; sumLumSq += lum * lum
                totalSampled++

                val max = maxOf(r, g, b)
                val min = minOf(r, g, b)
                val sat = if (max > 0) (max - min).toFloat() / max else 0f

                if (r > b && r > 80 && g > 60) warmPixels++
                if (b > r && b > g && b > 100) coolPixels++
                if (sat > 0.6f && max > 180) neonPixels++
                if (lum < 50) darkPixels++
                if (lum > 200) brightPixels++
                if (sat > 0.4f) highSatPixels++
                if (sat < 0.15f) lowSatPixels++

                // 区域统计
                val region = when {
                    y < thirdH -> topStats
                    y < thirdH * 2 -> midStats
                    else -> botStats
                }
                region.addPixel(r, g, b, lum, sat)
            }
        }

        if (totalSampled == 0) {
            return Result(
                overallScore = 0f, colorHarmony = 0f, proportionScore = 0f,
                styleMatch = 0f, suggestions = emptyList(), styleTags = emptyList(),
                detectedScene = scene, error = "无法读取图片像素数据"
            )
        }

        val meanR = sumR.toFloat() / totalSampled
        val meanG = sumG.toFloat() / totalSampled
        val meanB = sumB.toFloat() / totalSampled
        val brightness = sumLum.toFloat() / totalSampled
        val variance = (sumLumSq.toFloat() / totalSampled) - brightness * brightness
        val contrast = sqrt(variance.coerceAtLeast(0f))
        val maxColor = maxOf(meanR, meanG, meanB)
        val minColor = minOf(meanR, meanG, meanB)
        val saturation = if (maxColor > 0f) (maxColor - minColor) / maxColor else 0f
        val warmRatio = warmPixels.toFloat() / totalSampled
        val coolRatio = coolPixels.toFloat() / totalSampled
        val neonRatio = neonPixels.toFloat() / totalSampled
        val darkRatio = darkPixels.toFloat() / totalSampled
        val brightRatio = brightPixels.toFloat() / totalSampled
        val highSatRatio = highSatPixels.toFloat() / totalSampled
        val lowSatRatio = lowSatPixels.toFloat() / totalSampled

        // 4. 色彩和谐度评分
        val colorHarmony = computeColorHarmony(
            saturation, warmRatio, coolRatio, highSatRatio, lowSatRatio, contrast
        )

        // 5. 比例协调度评分（考虑上下区域色彩分布和人物占比）
        val ratio = width.toFloat() / height.toFloat()
        val proportionScore = computeProportionScore(
            ratio, topStats, midStats, botStats, totalSampled
        )

        // 6. 风格契合度评分（场景感知）
        val styleMatch = computeStyleMatch(
            scene, brightness, saturation, contrast, warmRatio, coolRatio,
            neonRatio, darkRatio, highSatRatio
        )

        // 7. 综合评分
        val overallScore = (
            colorHarmony * 0.4f + proportionScore * 0.3f + styleMatch * 0.3f
        ).coerceIn(0f, 100f)

        // 8. 生成建议（场景差异化）
        val suggestions = generateSuggestions(
            scene, saturation, brightness, ratio, warmRatio, coolRatio,
            contrast, neonRatio, darkRatio, highSatRatio, lowSatRatio
        )

        // 9. 生成风格标签（场景差异化）
        val styleTags = generateStyleTags(
            scene, brightness, saturation, contrast, warmRatio, coolRatio
        )

        return Result(
            overallScore = overallScore,
            colorHarmony = colorHarmony,
            proportionScore = proportionScore,
            styleMatch = styleMatch,
            suggestions = suggestions,
            styleTags = styleTags,
            detectedScene = scene
        )
    }

    /**
     * 色彩和谐度：基于饱和度均衡、冷暖平衡、对比度适中
     */
    private fun computeColorHarmony(
        saturation: Float, warmRatio: Float, coolRatio: Float,
        highSatRatio: Float, lowSatRatio: Float, contrast: Float
    ): Float {
        var score = 0f
        // 饱和度在 0.25-0.45 之间最和谐
        val satScore = 1f - abs(saturation - 0.35f) * 3f
        score += satScore.coerceIn(0f, 1f) * 30f

        // 冷暖平衡：不会过度偏暖或偏冷
        val balance = 1f - abs(warmRatio - coolRatio) * 2f
        score += balance.coerceIn(0f, 1f) * 20f

        // 高饱和度占比适中（0.2-0.5 为佳）
        val highSatScore = 1f - abs(highSatRatio - 0.35f) * 2.5f
        score += highSatScore.coerceIn(0f, 1f) * 20f

        // 对比度适中（40-80 为佳）
        val contrastScore = 1f - abs(contrast - 60f) / 80f
        score += contrastScore.coerceIn(0f, 1f) * 15f

        // 低饱和度不过多（< 0.3 为佳）
        val lowSatScore = if (lowSatRatio < 0.3f) 1f else (1f - (lowSatRatio - 0.3f) * 2f)
        score += lowSatScore.coerceIn(0f, 1f) * 15f

        return score.coerceIn(0f, 100f)
    }

    /**
     * 比例协调度：基于图片宽高比和上下区域色彩分布
     */
    private fun computeProportionScore(
        ratio: Float, topStats: RegionStats, midStats: RegionStats,
        botStats: RegionStats, totalSampled: Int
    ): Float {
        var score = 0f

        // 竖版全身照最佳比例 0.5-0.75（3:4 或 9:16）
        val ratioScore = when {
            ratio in 0.5f..0.75f -> 1f
            ratio in 0.4f..0.85f -> 0.7f
            else -> 0.4f
        }
        score += ratioScore * 40f

        // 上中下区域色彩变化度（有变化但不过度跳跃 = 有层次感）
        val topBri = topStats.avgLuminance()
        val midBri = midStats.avgLuminance()
        val botBri = botStats.avgLuminance()
        val diff1 = abs(topBri - midBri)
        val diff2 = abs(midBri - botBri)
        val layerScore = when {
            diff1 in 10f..60f && diff2 in 10f..60f -> 1f   // 有层次
            diff1 < 5f && diff2 < 5f -> 0.6f                // 均匀（也不错）
            diff1 > 80f || diff2 > 80f -> 0.3f               // 过度跳跃
            else -> 0.7f
        }
        score += layerScore * 30f

        // 中间区域（人物主体区）饱和度较高 = 焦点明确
        val midSat = midStats.avgSaturation()
        val focusScore = if (midSat > 0.2f) 1f else midSat / 0.2f
        score += focusScore * 30f

        return score.coerceIn(0f, 100f)
    }

    /**
     * 风格契合度：场景感知的风格评分
     */
    private fun computeStyleMatch(
        scene: SceneType, brightness: Float, saturation: Float, contrast: Float,
        warmRatio: Float, coolRatio: Float, neonRatio: Float,
        darkRatio: Float, highSatRatio: Float
    ): Float {
        return when (scene) {
            SceneType.COFFEE_SHOP -> {
                // 咖啡馆：暖色调、低-中亮度、低对比度、柔和
                var s = 0f
                if (warmRatio > 0.3f) s += 25f
                if (brightness in 60f..130f) s += 20f
                if (contrast < 60) s += 15f
                if (saturation in 0.15f..0.4f) s += 20f
                if (coolRatio < 0.15f) s += 20f
                s.coerceIn(0f, 100f)
            }
            SceneType.STREET -> {
                // 街拍：中高亮度、中等对比度、中性色调
                var s = 0f
                if (brightness in 80f..170f) s += 25f
                if (contrast in 40f..80f) s += 20f
                if (saturation in 0.2f..0.45f) s += 20f
                if (abs(warmRatio - coolRatio) < 0.2f) s += 15f
                if (highSatRatio in 0.1f..0.4f) s += 20f
                s.coerceIn(0f, 100f)
            }
            SceneType.BEACH -> {
                // 海边：高亮度、高饱和度、蓝天占比
                var s = 0f
                if (brightness > 120f) s += 25f
                if (saturation > 0.3f) s += 20f
                if (coolRatio > 0.1f) s += 20f
                if (highSatRatio > 0.15f) s += 15f
                if (warmRatio < 0.3f) s += 20f
                s.coerceIn(0f, 100f)
            }
            SceneType.PARK -> {
                // 公园：中亮度、绿色为主、自然饱和度
                var s = 0f
                if (brightness in 70f..160f) s += 25f
                if (saturation in 0.2f..0.45f) s += 20f
                if (contrast in 30f..70f) s += 20f
                if (warmRatio < 0.3f) s += 15f
                if (highSatRatio in 0.1f..0.35f) s += 20f
                s.coerceIn(0f, 100f)
            }
            SceneType.HOME -> {
                // 居家：暖色调、低亮度、低对比度
                var s = 0f
                if (warmRatio > 0.3f) s += 25f
                if (brightness in 50f..130f) s += 25f
                if (contrast < 55) s += 20f
                if (saturation < 0.35f) s += 15f
                if (coolRatio < 0.1f) s += 15f
                s.coerceIn(0f, 100f)
            }
            SceneType.NIGHT_NEON -> {
                // 霓虹夜：暗背景、高饱和霓虹光源、高对比度
                var s = 0f
                if (darkRatio > 0.25f) s += 25f
                if (neonRatio > 0.05f) s += 20f
                if (highSatRatio > 0.15f) s += 20f
                if (contrast > 60) s += 20f
                if (brightness < 100f) s += 15f
                s.coerceIn(0f, 100f)
            }
            SceneType.UNKNOWN -> {
                // 未识别场景：通用评分
                var s = 0f
                if (brightness in 60f..180f) s += 25f
                if (saturation in 0.15f..0.5f) s += 25f
                if (contrast in 30f..80f) s += 25f
                s += 25f // 基础分
                s.coerceIn(0f, 100f)
            }
        }
    }

    /**
     * 生成搭配建议（场景差异化）
     */
    private fun generateSuggestions(
        scene: SceneType, saturation: Float, brightness: Float, ratio: Float,
        warmRatio: Float, coolRatio: Float, contrast: Float,
        neonRatio: Float, darkRatio: Float, highSatRatio: Float, lowSatRatio: Float
    ): List<String> {
        val suggestions = mutableListOf<String>()

        // 通用建议
        if (saturation < 0.2f) {
            suggestions.add("整体色彩偏淡，建议添加亮色配饰（围巾/包包/首饰）提升视觉亮点")
        }
        if (brightness > 180f) {
            suggestions.add("整体偏亮色调，适合春夏清爽风格，可尝试加入浅色系单品")
        }
        if (brightness < 80f) {
            suggestions.add("整体偏暗色调，建议搭配亮色系单品或金属色配饰增加层次感")
        }
        if (ratio > 0.75f) {
            suggestions.add("照片比例偏宽，建议竖拍能更好地展示全身搭配比例")
        }
        if (lowSatRatio > 0.5f) {
            suggestions.add("大面积低饱和度区域，整体偏灰调，加入一件彩色单品可提亮整体")
        }

        // 场景差异化建议
        when (scene) {
            SceneType.COFFEE_SHOP -> {
                if (warmRatio < 0.2f) {
                    suggestions.add("咖啡馆场景适合暖色调穿搭，尝试驼色/卡其/焦糖色系")
                }
                if (contrast > 70) {
                    suggestions.add("对比度偏高，柔和光线下的咖啡馆更适合低饱和度柔和穿搭")
                }
            }
            SceneType.STREET -> {
                if (highSatRatio < 0.1f) {
                    suggestions.add("街拍建议加入一件高饱和度单品作为视觉焦点")
                }
                if (abs(warmRatio - coolRatio) > 0.3f) {
                    suggestions.add("冷暖色调不均衡，可考虑加入中性色（黑/白/灰）过渡")
                }
            }
            SceneType.BEACH -> {
                if (saturation < 0.3f) {
                    suggestions.add("海边穿搭适合高饱和度色彩，亮色/荧光色在阳光下更出彩")
                }
                if (warmRatio > 0.4f) {
                    suggestions.add("海边偏暖色调过多，加入蓝色系单品可呼应海天色彩")
                }
            }
            SceneType.PARK -> {
                if (saturation > 0.45f) {
                    suggestions.add("公园绿意较浓，穿搭避免过于鲜艳，低饱和度或白色系更和谐")
                }
            }
            SceneType.HOME -> {
                if (contrast > 50) {
                    suggestions.add("居家光线柔和，穿搭也宜偏柔和，避免强对比搭配")
                }
            }
            SceneType.NIGHT_NEON -> {
                if (darkRatio > 0.5f) {
                    suggestions.add("暗区占比过大，建议穿反光/亮色单品在霓虹光下形成视觉焦点")
                }
                if (neonRatio < 0.03f) {
                    suggestions.add("霓虹光源不足，尝试找更丰富的灯牌背景增强氛围感")
                }
            }
            SceneType.UNKNOWN -> { /* 无场景特定建议 */ }
        }

        if (suggestions.isEmpty()) {
            suggestions.add("整体搭配和谐，继续保持！")
        }

        return suggestions
    }

    /**
     * 生成风格标签（场景差异化）
     */
    private fun generateStyleTags(
        scene: SceneType, brightness: Float, saturation: Float,
        contrast: Float, warmRatio: Float, coolRatio: Float
    ): List<String> {
        val tags = mutableListOf<String>()

        // 基于色彩特征
        if (brightness > 150f && saturation > 0.3f) { tags.add("清新"); tags.add("活力") }
        if (brightness < 100f && saturation < 0.3f) { tags.add("沉稳"); tags.add("高级感") }
        if (saturation < 0.25f) { tags.add("极简"); tags.add("简约") }
        if (warmRatio > 0.3f && saturation > 0.2f) { tags.add("温暖"); tags.add("亲和") }
        if (coolRatio > 0.15f) { tags.add("冷调"); tags.add("知性") }
        if (contrast > 70 && saturation > 0.3f) { tags.add("个性"); tags.add("前卫") }

        // 场景差异化标签
        when (scene) {
            SceneType.COFFEE_SHOP -> { tags.add("文艺"); tags.add("慢生活") }
            SceneType.STREET -> { tags.add("街头"); tags.add("都市") }
            SceneType.BEACH -> { tags.add("度假"); tags.add("假日") }
            SceneType.PARK -> { tags.add("自然"); tags.add("户外") }
            SceneType.HOME -> { tags.add("慵懒"); tags.add("舒适") }
            SceneType.NIGHT_NEON -> { tags.add("赛博"); tags.add("潮酷") }
            SceneType.UNKNOWN -> { /* 不添加场景标签 */ }
        }

        // 去重 + 限制数量
        return tags.distinct().take(6).ifEmpty { listOf("日常", "休闲") }
    }

    /**
     * 降采样解码图片，避免 OOM
     */
    private fun decodeSampledBitmap(imagePath: String): Bitmap? {
        return try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(imagePath, options)
            val inSampleSize = calculateInSampleSize(options, ANALYSIS_SAMPLE_SIZE, ANALYSIS_SAMPLE_SIZE)
            val decodeOptions = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val rawBitmap = BitmapFactory.decodeFile(imagePath, decodeOptions) ?: return null
            // HARDWARE 位图无法读取像素，转为软件位图
            if (rawBitmap.config == Bitmap.Config.HARDWARE) {
                val converted = rawBitmap.copy(Bitmap.Config.ARGB_8888, false)
                rawBitmap.recycle()
                converted
            } else {
                rawBitmap
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decode bitmap: $imagePath", e)
            null
        }
    }

    private fun calculateInSampleSize(
        options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int
    ): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1
        while (height / inSampleSize >= reqHeight || width / inSampleSize >= reqWidth) {
            inSampleSize *= 2
        }
        return inSampleSize
    }

    fun close() {
        try {
            sceneClassifier?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to close SceneClassifier", e)
        }
        sceneClassifier = null
    }

    /**
     * 图片区域统计辅助类（上/中/下三段）
     */
    private class RegionStats {
        var sumR = 0L; var sumG = 0L; var sumB = 0L
        var sumLum = 0L; var sumSat = 0f; var count = 0

        fun addPixel(r: Int, g: Int, b: Int, lum: Long, sat: Float) {
            sumR += r; sumG += g; sumB += b
            sumLum += lum; sumSat += sat; count++
        }

        fun avgLuminance(): Float = if (count > 0) sumLum.toFloat() / count else 0f
        fun avgSaturation(): Float = if (count > 0) sumSat / count else 0f
    }
}
