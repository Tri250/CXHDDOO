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
}
