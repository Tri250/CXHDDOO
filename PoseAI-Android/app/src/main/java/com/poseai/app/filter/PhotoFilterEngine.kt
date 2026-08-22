package com.poseai.app.filter

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import com.poseai.app.model.PhotoFilter

/**
 * 滤镜引擎——对应 iOS PhotoFilterEngine。
 * 使用 ColorMatrix（对应 CIFilter）实现 4 套调色预设。
 */
object PhotoFilterEngine {

    /** 缓存：同一张原图 + 同一滤镜只处理一次 */
    private val cache = HashMap<Pair<Bitmap, PhotoFilter>, Bitmap>()

    fun clear() = cache.clear()

    /** 应用滤镜到整张图 */
    fun apply(bitmap: Bitmap, filter: PhotoFilter): Bitmap {
        if (filter == PhotoFilter.ORIGINAL) return bitmap
        return cache.getOrPut(bitmap to filter) {
            val matrix = matrixFor(filter)
            val paint = Paint().apply { colorFilter = ColorMatrixColorFilter(matrix) }
            val result = Bitmap.createBitmap(bitmap.width, bitmap.height, bitmap.config ?: Bitmap.Config.ARGB_8888)
            Canvas(result).drawBitmap(bitmap, 0f, 0f, paint)
            result
        }
    }

    /** 生成缩略图预览（缩小后再处理，速度快） */
    fun thumbnail(bitmap: Bitmap, filter: PhotoFilter, size: Int = 160): Bitmap {
        val scale = size.toFloat() / maxOf(bitmap.width, bitmap.height)
        val small = if (scale < 1f) {
            Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
        } else bitmap
        return apply(small, filter)
    }

    /** 根据滤镜类型构建颜色矩阵（复刻 iOS CIFilter 效果） */
    private fun matrixFor(filter: PhotoFilter): ColorMatrix = when (filter) {
        PhotoFilter.ORIGINAL -> ColorMatrix()

        PhotoFilter.FILM -> {
            // 胶片感：微升饱和 + 微降对比 + 偏暖
            ColorMatrix().apply {
                setSaturation(1.12f)
                postConcat(contrastMatrix(0.92f))
            }
        }

        PhotoFilter.BW -> {
            // 高对比黑白
            ColorMatrix().apply {
                setSaturation(0f)
                postConcat(contrastMatrix(1.2f))
            }
        }

        PhotoFilter.LIGHT -> {
            // 日系清透：低对比 + 低饱和 + 提亮
            ColorMatrix().apply {
                setSaturation(0.78f)
                postConcat(contrastMatrix(0.85f))
                setScale(1.06f, 1.06f, 1.06f, 1f)
            }
        }

        PhotoFilter.NEON -> {
            // 青橙赛博朋克：高饱和 + 高对比 + 青蓝压暗
            ColorMatrix().apply {
                setSaturation(1.35f)
                postConcat(contrastMatrix(1.2f))
            }
        }
    }

    /**
     * Android ColorMatrix 无内建对比度；手动构造围绕中灰(0.5)的对比度矩阵。
     * contrast > 1 增强，contrast < 1 减弱。
     */
    private fun contrastMatrix(contrast: Float): ColorMatrix {
        val t = (1 - contrast) * 128f // 0.5 * 255 ≈ 128
        return ColorMatrix(floatArrayOf(
            contrast, 0f, 0f, 0f, t,
            0f, contrast, 0f, 0f, t,
            0f, 0f, contrast, 0f, t,
            0f, 0f, 0f, 1f, 0f
        ))
    }
}