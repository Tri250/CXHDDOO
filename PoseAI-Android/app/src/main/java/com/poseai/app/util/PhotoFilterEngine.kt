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
        DRAMATIC("戏剧")
    }

    fun applyFilter(source: Bitmap, filter: Filter): Bitmap {
        return when (filter) {
            Filter.ORIGINAL -> source.copy(source.config ?: Bitmap.Config.ARGB_8888, true)
            Filter.VIVID -> applySaturation(source, 1.4f)
            Filter.WARM -> applyColorTint(source, 1.1f, 1.0f, 0.85f)
            Filter.COOL -> applyColorTint(source, 0.9f, 1.0f, 1.15f)
            Filter.FADE -> applyFade(source)
            Filter.VINTAGE -> applyVintage(source)
            Filter.MONO -> applyGrayscale(source)
            Filter.DRAMATIC -> applyDramatic(source)
        }
    }

    fun applyLowLightDenoise(source: Bitmap): Bitmap {
        val meanLuma = computeMeanLuminance(source)
        if (meanLuma >= 60f) return source

        val downscaled = Bitmap.createScaledBitmap(
            source,
            (source.width / 2).coerceAtLeast(1),
            (source.height / 2).coerceAtLeast(1),
            true
        )
        val blurred = Bitmap.createScaledBitmap(
            downscaled,
            source.width,
            source.height,
            true
        )
        downscaled.recycle()
        val blended = blendBitmaps(source, blurred, originalWeight = 0.4f)
        blurred.recycle()
        val lifted = applyShadowLift(blended, lift = 15f)
        if (lifted !== blended) blended.recycle()
        return lifted
    }

    fun applySmartCrop(source: Bitmap, targetRatio: Float = 4f / 5f): Bitmap {
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

        return Bitmap.createBitmap(source, x, y, targetW, targetH)
    }

    fun addWatermark(source: Bitmap, text: String = "PoseAI"): Bitmap {
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
        return result
    }

    fun computeMeanLuminance(bitmap: Bitmap): Float {
        val small = Bitmap.createScaledBitmap(bitmap, 64, 64, true)
        var sum = 0f
        var count = 0
        for (y in 0 until small.height) {
            for (x in 0 until small.width) {
                val pixel = small.getPixel(x, y)
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)
                val lum = 0.299f * r + 0.587f * g + 0.114f * b
                sum += lum
                count++
            }
        }
        small.recycle()
        return if (count > 0) sum / count else 0f
    }

    fun blendBitmaps(original: Bitmap, blurred: Bitmap, originalWeight: Float): Bitmap {
        val result = Bitmap.createBitmap(original.width, original.height, Bitmap.Config.ARGB_8888)
        val blurWeight = 1f - originalWeight
        for (y in 0 until original.height) {
            for (x in 0 until original.width) {
                val op = original.getPixel(x, y)
                val bp = blurred.getPixel(x, y)
                val r = (Color.red(op) * originalWeight + Color.red(bp) * blurWeight).toInt()
                val g = (Color.green(op) * originalWeight + Color.green(bp) * blurWeight).toInt()
                val b = (Color.blue(op) * originalWeight + Color.blue(bp) * blurWeight).toInt()
                result.setPixel(x, y, Color.rgb(
                    r.coerceIn(0, 255),
                    g.coerceIn(0, 255),
                    b.coerceIn(0, 255)
                ))
            }
        }
        return result
    }

    fun applyShadowLift(source: Bitmap, lift: Float): Bitmap {
        val result = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        for (y in 0 until source.height) {
            for (x in 0 until source.width) {
                val pixel = source.getPixel(x, y)
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)
                val lum = 0.299f * r + 0.587f * g + 0.114f * b
                val shadowFactor = (1f - lum / 255f).coerceIn(0f, 1f)
                val liftAmount = lift * shadowFactor
                result.setPixel(x, y, Color.rgb(
                    (r + liftAmount).toInt().coerceIn(0, 255),
                    (g + liftAmount).toInt().coerceIn(0, 255),
                    (b + liftAmount).toInt().coerceIn(0, 255)
                ))
            }
        }
        return result
    }

    private fun applySaturation(source: Bitmap, saturation: Float): Bitmap {
        val result = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint()
        val cm = ColorMatrix()
        cm.setSaturation(saturation)
        paint.colorFilter = ColorMatrixColorFilter(cm)
        canvas.drawBitmap(source, 0f, 0f, paint)
        return result
    }

    private fun applyColorTint(source: Bitmap, rMul: Float, gMul: Float, bMul: Float): Bitmap {
        val result = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        for (y in 0 until source.height) {
            for (x in 0 until source.width) {
                val p = source.getPixel(x, y)
                result.setPixel(x, y, Color.rgb(
                    (Color.red(p) * rMul).toInt().coerceIn(0, 255),
                    (Color.green(p) * gMul).toInt().coerceIn(0, 255),
                    (Color.blue(p) * bMul).toInt().coerceIn(0, 255)
                ))
            }
        }
        return result
    }

    private fun applyGrayscale(source: Bitmap): Bitmap {
        val result = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint()
        val cm = ColorMatrix()
        cm.setSaturation(0f)
        paint.colorFilter = ColorMatrixColorFilter(cm)
        canvas.drawBitmap(source, 0f, 0f, paint)
        return result
    }

    private fun applyFade(source: Bitmap): Bitmap {
        val result = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        for (y in 0 until source.height) {
            for (x in 0 until source.width) {
                val p = source.getPixel(x, y)
                val r = Color.red(p) * 0.9f + 20
                val g = Color.green(p) * 0.9f + 20
                val b = Color.blue(p) * 0.95f + 20
                result.setPixel(x, y, Color.rgb(
                    r.toInt().coerceIn(0, 255),
                    g.toInt().coerceIn(0, 255),
                    b.toInt().coerceIn(0, 255)
                ))
            }
        }
        return result
    }

    private fun applyVintage(source: Bitmap): Bitmap {
        val result = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        for (y in 0 until source.height) {
            for (x in 0 until source.width) {
                val p = source.getPixel(x, y)
                val r = Color.red(p) * 1.1f
                val g = Color.green(p) * 0.95f
                val b = Color.blue(p) * 0.8f
                result.setPixel(x, y, Color.rgb(
                    r.toInt().coerceIn(0, 255),
                    g.toInt().coerceIn(0, 255),
                    b.toInt().coerceIn(0, 255)
                ))
            }
        }
        return result
    }

    private fun applyDramatic(source: Bitmap): Bitmap {
        val result = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        for (y in 0 until source.height) {
            for (x in 0 until source.width) {
                val p = source.getPixel(x, y)
                val r = Color.red(p)
                val g = Color.green(p)
                val b = Color.blue(p)
                val contrast = 1.3f
                val offset = -30f
                result.setPixel(x, y, Color.rgb(
                    ((r - 128) * contrast + 128 + offset).toInt().coerceIn(0, 255),
                    ((g - 128) * contrast + 128 + offset).toInt().coerceIn(0, 255),
                    ((b - 128) * contrast + 128 + offset).toInt().coerceIn(0, 255)
                ))
            }
        }
        return result
    }
}
