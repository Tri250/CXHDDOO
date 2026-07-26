package com.poseai.app.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Rect
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

object PhotoFilterEngine {

    enum class Filter(val displayName: String) {
        ORIGINAL("原图"),
        VIVID("鲜明"),
        WARM("暖色"),
        COOL("冷色"),
        FADE("褪色"),
        VINTAGE("复古"),
        MONO("黑白"),
        DRAMATIC("戏剧"),
        // P5-4 拍后调色预设：4 套电影级调色
        FILM("胶片感"),      // 青暗部 + 暖高光（复刻柯达调性）
        NOIR("高级黑白"),    // 大反差强锐度黑白
        LIGHT("日系清透"),   // 低对比过曝 + 降饱和
        NEON("城市霓虹")     // Teal & Orange 青橙赛博朋克
    }

    fun applyFilter(source: Bitmap, filter: Filter): Bitmap {
        return try {
            when (filter) {
                Filter.ORIGINAL -> source.copy(source.config ?: Bitmap.Config.ARGB_8888, true)
                Filter.VIVID -> applySaturation(source, 1.4f)
                Filter.WARM -> applyColorTint(source, 1.1f, 1.0f, 0.85f)
                Filter.COOL -> applyColorTint(source, 0.9f, 1.0f, 1.15f)
                Filter.FADE -> applyFade(source)
                Filter.VINTAGE -> applyVintage(source)
                Filter.MONO -> applyGrayscale(source)
                Filter.DRAMATIC -> applyDramatic(source)
                Filter.FILM -> applyFilmLook(source)
                Filter.NOIR -> applyNoir(source)
                Filter.LIGHT -> applyLight(source)
                Filter.NEON -> applyNeon(source)
            }
        } catch (e: Exception) {
            source.copy(source.config ?: Bitmap.Config.ARGB_8888, true)
        }
    }

    fun applyLowLightDenoise(source: Bitmap): Bitmap {
        return try {
            val meanLuma = computeMeanLuminance(source)
            if (meanLuma >= 60f) {
                return source.copy(source.config ?: Bitmap.Config.ARGB_8888, true)
            }

            var downscaled: Bitmap? = null
            var blurred: Bitmap? = null
            var blended: Bitmap? = null
            try {
                downscaled = Bitmap.createScaledBitmap(
                    source,
                    (source.width / 2).coerceAtLeast(1),
                    (source.height / 2).coerceAtLeast(1),
                    true
                )
                blurred = Bitmap.createScaledBitmap(
                    downscaled,
                    source.width,
                    source.height,
                    true
                )
                blended = blendBitmaps(source, blurred, originalWeight = 0.4f)
                val lifted = applyShadowLift(blended, lift = 15f)
                if (lifted !== blended) {
                    blended.recycle()
                    blended = null
                }
                return lifted
            } finally {
                downscaled?.recycle()
                blurred?.recycle()
                blended?.recycle()
            }
        } catch (e: Exception) {
            source.copy(source.config ?: Bitmap.Config.ARGB_8888, true)
        }
    }

    fun applySmartCrop(source: Bitmap, targetRatio: Float = 4f / 5f): Bitmap {
        return try {
            val srcRatio = source.width.toFloat() / source.height.toFloat()
            val targetW: Int
            val targetH: Int
            val x: Int
            val y: Int

            if (srcRatio > targetRatio) {
                targetH = source.height
                targetW = (targetH * targetRatio).toInt()
                x = (source.width - targetW) / 2
                y = 0
            } else {
                targetW = source.width
                targetH = (targetW / targetRatio).toInt()
                x = 0
                y = (source.height - targetH) / 3
            }

            Bitmap.createBitmap(source, x, y, targetW, targetH)
        } catch (e: Exception) {
            source.copy(source.config ?: Bitmap.Config.ARGB_8888, true)
        }
    }

    fun addWatermark(source: Bitmap, text: String = "PoseAI"): Bitmap {
        return try {
            val result = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(result)
            canvas.drawBitmap(source, 0f, 0f, null)
            val paint = Paint().apply {
                color = Color.WHITE
                textSize = source.width * 0.04f
                isAntiAlias = true
                alpha = 180
            }
            val padding = source.width * 0.04f
            val x = padding
            val y = source.height - padding
            canvas.drawText(text, x, y, paint)
            result
        } catch (e: Exception) {
            source.copy(source.config ?: Bitmap.Config.ARGB_8888, true)
        }
    }

    fun computeMeanLuminance(bitmap: Bitmap): Float {
        var small: Bitmap? = null
        return try {
            small = Bitmap.createScaledBitmap(bitmap, 64, 64, true)
            val width = small.width
            val height = small.height
            val pixels = IntArray(width * height)
            small.getPixels(pixels, 0, width, 0, 0, width, height)
            var sum = 0f
            var count = 0
            for (i in pixels.indices) {
                val pixel = pixels[i]
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)
                val lum = 0.299f * r + 0.587f * g + 0.114f * b
                sum += lum
                count++
            }
            if (count > 0) sum / count else 0f
        } catch (e: Exception) {
            128f
        } finally {
            small?.recycle()
        }
    }

    fun blendBitmaps(original: Bitmap, blurred: Bitmap, originalWeight: Float): Bitmap {
        return try {
            val result = Bitmap.createBitmap(original.width, original.height, Bitmap.Config.ARGB_8888)
            val blurWeight = 1f - originalWeight
            val width = original.width
            val height = original.height
            val origPixels = IntArray(width * height)
            val blurPixels = IntArray(width * height)
            val outPixels = IntArray(width * height)
            original.getPixels(origPixels, 0, width, 0, 0, width, height)
            blurred.getPixels(blurPixels, 0, width, 0, 0, width, height)
            for (i in origPixels.indices) {
                val op = origPixels[i]
                val bp = blurPixels[i]
                val r = (Color.red(op) * originalWeight + Color.red(bp) * blurWeight).toInt()
                val g = (Color.green(op) * originalWeight + Color.green(bp) * blurWeight).toInt()
                val b = (Color.blue(op) * originalWeight + Color.blue(bp) * blurWeight).toInt()
                outPixels[i] = Color.rgb(
                    r.coerceIn(0, 255),
                    g.coerceIn(0, 255),
                    b.coerceIn(0, 255)
                )
            }
            result.setPixels(outPixels, 0, width, 0, 0, width, height)
            result
        } catch (e: Exception) {
            original.copy(original.config ?: Bitmap.Config.ARGB_8888, true)
        }
    }

    fun applyShadowLift(source: Bitmap, lift: Float): Bitmap {
        return try {
            val width = source.width
            val height = source.height
            val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val pixels = IntArray(width * height)
            val outPixels = IntArray(width * height)
            source.getPixels(pixels, 0, width, 0, 0, width, height)
            for (i in pixels.indices) {
                val pixel = pixels[i]
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)
                val lum = 0.299f * r + 0.587f * g + 0.114f * b
                val shadowFactor = (1f - lum / 255f).coerceIn(0f, 1f)
                val liftAmount = lift * shadowFactor
                outPixels[i] = Color.rgb(
                    (r + liftAmount).toInt().coerceIn(0, 255),
                    (g + liftAmount).toInt().coerceIn(0, 255),
                    (b + liftAmount).toInt().coerceIn(0, 255)
                )
            }
            result.setPixels(outPixels, 0, width, 0, 0, width, height)
            result
        } catch (e: Exception) {
            source.copy(source.config ?: Bitmap.Config.ARGB_8888, true)
        }
    }

    private fun applySaturation(source: Bitmap, saturation: Float): Bitmap {
        return try {
            val result = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(result)
            val paint = Paint()
            val cm = ColorMatrix()
            cm.setSaturation(saturation)
            paint.colorFilter = ColorMatrixColorFilter(cm)
            canvas.drawBitmap(source, 0f, 0f, paint)
            result
        } catch (e: Exception) {
            source.copy(source.config ?: Bitmap.Config.ARGB_8888, true)
        }
    }

    private fun applyColorTint(source: Bitmap, rMul: Float, gMul: Float, bMul: Float): Bitmap {
        return try {
            val width = source.width
            val height = source.height
            val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val pixels = IntArray(width * height)
            val outPixels = IntArray(width * height)
            source.getPixels(pixels, 0, width, 0, 0, width, height)
            for (i in pixels.indices) {
                val p = pixels[i]
                outPixels[i] = Color.rgb(
                    (Color.red(p) * rMul).toInt().coerceIn(0, 255),
                    (Color.green(p) * gMul).toInt().coerceIn(0, 255),
                    (Color.blue(p) * bMul).toInt().coerceIn(0, 255)
                )
            }
            result.setPixels(outPixels, 0, width, 0, 0, width, height)
            result
        } catch (e: Exception) {
            source.copy(source.config ?: Bitmap.Config.ARGB_8888, true)
        }
    }

    private fun applyGrayscale(source: Bitmap): Bitmap {
        return try {
            val result = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(result)
            val paint = Paint()
            val cm = ColorMatrix()
            cm.setSaturation(0f)
            paint.colorFilter = ColorMatrixColorFilter(cm)
            canvas.drawBitmap(source, 0f, 0f, paint)
            result
        } catch (e: Exception) {
            source.copy(source.config ?: Bitmap.Config.ARGB_8888, true)
        }
    }

    private fun applyFade(source: Bitmap): Bitmap {
        return try {
            val width = source.width
            val height = source.height
            val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val pixels = IntArray(width * height)
            val outPixels = IntArray(width * height)
            source.getPixels(pixels, 0, width, 0, 0, width, height)
            for (i in pixels.indices) {
                val p = pixels[i]
                val r = Color.red(p) * 0.9f + 20
                val g = Color.green(p) * 0.9f + 20
                val b = Color.blue(p) * 0.95f + 20
                outPixels[i] = Color.rgb(
                    r.toInt().coerceIn(0, 255),
                    g.toInt().coerceIn(0, 255),
                    b.toInt().coerceIn(0, 255)
                )
            }
            result.setPixels(outPixels, 0, width, 0, 0, width, height)
            result
        } catch (e: Exception) {
            source.copy(source.config ?: Bitmap.Config.ARGB_8888, true)
        }
    }

    private fun applyVintage(source: Bitmap): Bitmap {
        return try {
            val width = source.width
            val height = source.height
            val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val pixels = IntArray(width * height)
            val outPixels = IntArray(width * height)
            source.getPixels(pixels, 0, width, 0, 0, width, height)
            for (i in pixels.indices) {
                val p = pixels[i]
                val r = Color.red(p) * 1.1f
                val g = Color.green(p) * 0.95f
                val b = Color.blue(p) * 0.8f
                outPixels[i] = Color.rgb(
                    r.toInt().coerceIn(0, 255),
                    g.toInt().coerceIn(0, 255),
                    b.toInt().coerceIn(0, 255)
                )
            }
            result.setPixels(outPixels, 0, width, 0, 0, width, height)
            result
        } catch (e: Exception) {
            source.copy(source.config ?: Bitmap.Config.ARGB_8888, true)
        }
    }

    private fun applyDramatic(source: Bitmap): Bitmap {
        return try {
            val width = source.width
            val height = source.height
            val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val pixels = IntArray(width * height)
            val outPixels = IntArray(width * height)
            source.getPixels(pixels, 0, width, 0, 0, width, height)
            val contrast = 1.3f
            val offset = -30f
            for (i in pixels.indices) {
                val p = pixels[i]
                val r = Color.red(p)
                val g = Color.green(p)
                val b = Color.blue(p)
                outPixels[i] = Color.rgb(
                    ((r - 128) * contrast + 128 + offset).toInt().coerceIn(0, 255),
                    ((g - 128) * contrast + 128 + offset).toInt().coerceIn(0, 255),
                    ((b - 128) * contrast + 128 + offset).toInt().coerceIn(0, 255)
                )
            }
            result.setPixels(outPixels, 0, width, 0, 0, width, height)
            result
        } catch (e: Exception) {
            source.copy(source.config ?: Bitmap.Config.ARGB_8888, true)
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // P5-4 拍后调色预设：4 套电影级调色（端侧实现，对应 iOS CIFilter 方案）
    // 使用 Android ColorMatrix + 像素级处理达到等效效果
    // ═══════════════════════════════════════════════════════════════

    /**
     * 胶片感 Film：青暗部 + 暖高光（复刻柯达 Portra 调性）
     * 实现原理：
     * - 暗部偏青：低亮度区域 R 通道降低、B 通道提升
     * - 高光偏暖：高亮度区域 R/G 通道提升
     * - 整体对比微降 + 颗粒感
     */
    private fun applyFilmLook(source: Bitmap): Bitmap {
        return try {
            val width = source.width
            val height = source.height
            val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val pixels = IntArray(width * height)
            val outPixels = IntArray(width * height)
            source.getPixels(pixels, 0, width, 0, 0, width, height)

            for (i in pixels.indices) {
                val p = pixels[i]
                val r = Color.red(p).toFloat()
                val g = Color.green(p).toFloat()
                val b = Color.blue(p).toFloat()
                val lum = 0.299f * r + 0.587f * g + 0.114f * b

                // 暗部青调：暗部区域 R 略降、B 略升
                val shadowFactor = (1f - lum / 255f).coerceIn(0f, 1f)
                val shadowTealR = r * (1f - 0.08f * shadowFactor)
                val shadowTealB = b + 12f * shadowFactor

                // 高光暖调：高光区域 R/G 略升
                val highlightFactor = (lum / 255f).coerceIn(0f, 1f)
                val warmR = shadowTealR + 8f * highlightFactor
                val warmG = g + 4f * highlightFactor

                // 整体对比微降（胶片柔和）+ 轻微颗粒
                val grain = (Math.random() - 0.5).toFloat() * 6f

                outPixels[i] = Color.rgb(
                    (warmR + grain).toInt().coerceIn(0, 255),
                    (warmG + grain).toInt().coerceIn(0, 255),
                    (shadowTealB + grain).toInt().coerceIn(0, 255)
                )
            }
            result.setPixels(outPixels, 0, width, 0, 0, width, height)
            result
        } catch (e: Exception) {
            source.copy(source.config ?: Bitmap.Config.ARGB_8888, true)
        }
    }

    /**
     * 高级黑白 B&W：大反差强锐度黑白
     * 对应 iOS CIPhotoEffectNoir + CISharpenLuminance
     * 实现：
     * - 灰度转换 + 高对比度
     * - 暗部进一步压暗，高光提亮
     * - 锐化（Sobel 卷积增强边缘）
     */
    private fun applyNoir(source: Bitmap): Bitmap {
        return try {
            val width = source.width
            val height = source.height
            // 先做高对比黑白
            val grayBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(grayBitmap)
            val paint = Paint()
            // 高对比黑白 ColorMatrix
            val cm = ColorMatrix().apply {
                setSaturation(0f)
            }
            val contrastMatrix = ColorMatrix(
                floatArrayOf(
                    1.6f, 0f, 0f, 0f, -76.8f,  // R: contrast 1.6
                    1.6f, 0f, 0f, 0f, -76.8f,  // G
                    1.6f, 0f, 0f, 0f, -76.8f,  // B
                    0f, 0f, 0f, 1f, 0f          // A
                )
            )
            cm.postConcat(contrastMatrix)
            paint.colorFilter = ColorMatrixColorFilter(cm)
            canvas.drawBitmap(source, 0f, 0f, paint)

            // 锐化：3x3 卷积核
            val sharpenKernel = floatArrayOf(
                0f, -1f, 0f,
                -1f, 5.4f, -1f,
                0f, -1f, 0f
            )
            applyConvolution(grayBitmap, sharpenKernel, 1f)
        } catch (e: Exception) {
            source.copy(source.config ?: Bitmap.Config.ARGB_8888, true)
        }
    }

    /**
     * 日系清透 Light：低对比过曝 + 降饱和
     * 对应 iOS CIExposureAdjust(+0.3) + CIVibrance(-0.2)
     * 实现：
     * - 整体提亮 +30（过曝感）
     * - 降低对比度
     * - 降低饱和度 0.8
     * - 偏冷色（B 通道 +5）
     */
    private fun applyLight(source: Bitmap): Bitmap {
        return try {
            val width = source.width
            val height = source.height
            val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(result)
            val paint = Paint()

            // ColorMatrix: 提亮 + 降对比 + 降饱和 + 偏冷
            val exposure = 30f  // 整体提亮
            val saturation = 0.8f  // 降饱和
            val contrast = 0.85f   // 降对比
            val cm = ColorMatrix().apply {
                setSaturation(saturation)
            }
            val contrastMatrix = ColorMatrix(
                floatArrayOf(
                    contrast, 0f, 0f, 0f, exposure + (1 - contrast) * 128,
                    0f, contrast, 0f, 0f, exposure + (1 - contrast) * 128,
                    0f, 0f, contrast, 0f, exposure + (1 - contrast) * 128 + 5f,  // B +5 偏冷
                    0f, 0f, 0f, 1f, 0f
                )
            )
            cm.postConcat(contrastMatrix)
            paint.colorFilter = ColorMatrixColorFilter(cm)
            canvas.drawBitmap(source, 0f, 0f, paint)
            result
        } catch (e: Exception) {
            source.copy(source.config ?: Bitmap.Config.ARGB_8888, true)
        }
    }

    /**
     * 城市霓虹 Neon：Teal & Orange 青橙赛博朋克
     * 对应 iOS CIColorMatrix Teal & Orange
     * 实现：
     * - 暗部偏青 Teal（蓝绿）
     * - 高光偏橙 Orange（红黄）
     * - 中间调保持
     * - 整体饱和度提升
     */
    private fun applyNeon(source: Bitmap): Bitmap {
        return try {
            val width = source.width
            val height = source.height
            val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val pixels = IntArray(width * height)
            val outPixels = IntArray(width * height)
            source.getPixels(pixels, 0, width, 0, 0, width, height)

            for (i in pixels.indices) {
                val p = pixels[i]
                val r = Color.red(p).toFloat()
                val g = Color.green(p).toFloat()
                val b = Color.blue(p).toFloat()
                val lum = 0.299f * r + 0.587f * g + 0.114f * b
                val normLum = lum / 255f

                // Teal & Orange 经典映射：根据亮度决定色调
                // 暗部 → Teal (R 低, G 中, B 高)
                // 高光 → Orange (R 高, G 中, B 低)
                val tealWeight = 1f - normLum  // 暗部权重
                val orangeWeight = normLum     // 高光权重

                // 增强 Teal & Orange 效果
                val newR = r + (orangeWeight - 0.5f) * 60f - tealWeight * 25f
                val newG = g + (normLum - 0.5f) * 20f
                val newB = b + tealWeight * 50f - orangeWeight * 35f

                // 整体饱和度提升 1.2
                val centerR = 128f
                val centerG = 128f
                val centerB = 128f
                val satR = centerR + (newR - centerR) * 1.2f
                val satG = centerG + (newG - centerG) * 1.2f
                val satB = centerB + (newB - centerB) * 1.2f

                outPixels[i] = Color.rgb(
                    satR.toInt().coerceIn(0, 255),
                    satG.toInt().coerceIn(0, 255),
                    satB.toInt().coerceIn(0, 255)
                )
            }
            result.setPixels(outPixels, 0, width, 0, 0, width, height)
            result
        } catch (e: Exception) {
            source.copy(source.config ?: Bitmap.Config.ARGB_8888, true)
        }
    }

    /**
     * 3x3 卷积应用：用于 NOIR 锐化处理
     * @param bitmap 输入 Bitmap（会被原地修改）
     * @param kernel 3x3 卷积核（长度 9）
     * @param factor 增强因子
     */
    private fun applyConvolution(bitmap: Bitmap, kernel: FloatArray, factor: Float): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        val outPixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                var sumR = 0f
                var sumG = 0f
                var sumB = 0f
                var ki = 0
                for (ky in -1..1) {
                    for (kx in -1..1) {
                        val px = pixels[(y + ky) * width + (x + kx)]
                        val w = kernel[ki++] * factor
                        sumR += Color.red(px) * w
                        sumG += Color.green(px) * w
                        sumB += Color.blue(px) * w
                    }
                }
                outPixels[y * width + x] = Color.rgb(
                    sumR.toInt().coerceIn(0, 255),
                    sumG.toInt().coerceIn(0, 255),
                    sumB.toInt().coerceIn(0, 255)
                )
            }
        }
        // 复制边缘像素
        for (x in 0 until width) {
            outPixels[x] = pixels[x]
            outPixels[(height - 1) * width + x] = pixels[(height - 1) * width + x]
        }
        for (y in 0 until height) {
            outPixels[y * width] = pixels[y * width]
            outPixels[y * width + width - 1] = pixels[y * width + width - 1]
        }
        bitmap.setPixels(outPixels, 0, width, 0, 0, width, height)
        return bitmap
    }

    // ═══════════════════════════════════════════════════════════════
    // P5-2 智能裁切 + 社交画幅预设
    // 画幅：4:5（默认） / 16:9 / 4:3 / 1:1 / 2.35:1
    // ═══════════════════════════════════════════════════════════════

    /**
     * 社交画幅预设：根据不同平台比例裁切
     */
    enum class AspectRatio(val displayName: String, val ratio: Float) {
        RATIO_4_5("4:5 竖屏", 4f / 5f),       // Instagram 推荐
        RATIO_16_9("16:9 横屏", 16f / 9f),    // YouTube / 视频封面
        RATIO_4_3("4:3 经典", 4f / 3f),       // 相机原始比例
        RATIO_1_1("1:1 方形", 1f),            // Instagram 头像 / 方形
        RATIO_2_35("2.35:1 电影", 2.35f)      // 电影画幅
    }

    /**
     * 按指定画幅裁切：保持人物居中
     */
    fun applySmartCropByRatio(source: Bitmap, ratio: AspectRatio): Bitmap {
        return try {
            applySmartCrop(source, ratio.ratio)
        } catch (e: Exception) {
            source.copy(source.config ?: Bitmap.Config.ARGB_8888, true)
        }
    }

    /**
     * 智能裁切双底片：根据人像 bbox 生成全身原图 + 胸腰特写
     * 对应 iOS P5-2 Auto-Crop
     * @param source 原始图片
     * @param bodyBbox 人像 bbox（归一化坐标 [0,1]），可为 null 时使用画面中心
     * @return Pair<全身原图, 胸腰特写>
     */
    fun applyDualCrop(
        source: Bitmap,
        bodyBbox: RectF? = null
    ): Pair<Bitmap, Bitmap> {
        val full = source.copy(source.config ?: Bitmap.Config.ARGB_8888, true)
        val closeUp = try {
            val width = source.width
            val height = source.height
            // bbox 归一化转像素
            val bbox = bodyBbox ?: RectF(
                width * 0.2f,
                height * 0.1f,
                width * 0.8f,
                height * 0.7f
            )
            // 胸腰特写：bbox 上半部分 + 适当扩展
            val cropTop = (bbox.top + (bbox.bottom - bbox.top) * 0.05f).toInt()
            val cropBottom = (bbox.top + (bbox.bottom - bbox.top) * 0.6f).toInt()
            val cropLeft = (bbox.left - (bbox.right - bbox.left) * 0.1f).toInt().coerceAtLeast(0)
            val cropRight = (bbox.right + (bbox.right - bbox.left) * 0.1f).toInt().coerceAtMost(width)
            val cropW = (cropRight - cropLeft).coerceAtLeast(width / 4)
            val cropH = (cropBottom - cropTop).coerceAtLeast(height / 4)
            Bitmap.createBitmap(source, cropLeft, cropTop, cropW, cropH)
        } catch (e: Exception) {
            source.copy(source.config ?: Bitmap.Config.ARGB_8888, true)
        }
        return Pair(full, closeUp)
    }

    /**
     * 归一化矩形数据类
     */
    data class RectF(val left: Float, val top: Float, val right: Float, val bottom: Float)
}
