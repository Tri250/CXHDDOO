package com.poseai.app.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.min

/**
 * AR 贴纸引擎：在照片上叠加装饰性贴纸
 *
 * 贴纸类型：
 * - DATE：日期标记（复古相机风格）
 * - FRAME：边框装饰
 * - GLOW：边缘发光
 * - VIGNETTE：暗角
 */
object StickerEngine {

    enum class Sticker(val displayName: String) {
        NONE("无"),
        DATE("日期标记"),
        WHITE_FRAME("白边框"),
        ROUNDED_FRAME("圆角框"),
        GLOW("边缘发光"),
        VIGNETTE("暗角"),
        RETRO_DATE("复古日期"),
        FILM_FRAME("胶片边框")
    }

    /**
     * 在照片上叠加贴纸
     */
    fun applySticker(source: Bitmap, sticker: Sticker): Bitmap {
        return try {
            when (sticker) {
                Sticker.NONE -> source.copy(source.config ?: Bitmap.Config.ARGB_8888, true)
                Sticker.DATE -> applyDateStamp(source)
                Sticker.WHITE_FRAME -> applyWhiteFrame(source)
                Sticker.ROUNDED_FRAME -> applyRoundedFrame(source)
                Sticker.GLOW -> applyEdgeGlow(source)
                Sticker.VIGNETTE -> applyVignette(source)
                Sticker.RETRO_DATE -> applyRetroDate(source)
                Sticker.FILM_FRAME -> applyFilmFrame(source)
            }
        } catch (e: Exception) {
            source.copy(source.config ?: Bitmap.Config.ARGB_8888, true)
        }
    }

    // ── 日期标记（白色半透明底 + 日期文字） ──

    private fun applyDateStamp(source: Bitmap): Bitmap {
        val result = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawBitmap(source, 0f, 0f, null)

        val dateStr = SimpleDateFormat("yyyy.MM.dd", Locale.getDefault()).format(Date())
        val w = source.width.toFloat()
        val padding = w * 0.04f
        val textSize = w * 0.035f

        val textPaint = Paint().apply {
            color = Color.WHITE
            this.textSize = textSize
            isAntiAlias = true
            typeface = Typeface.DEFAULT_BOLD
        }

        val textWidth = textPaint.measureText(dateStr)
        val bgRect = RectF(
            padding,
            padding,
            padding + textWidth + padding * 2,
            padding + textSize * 1.8f
        )
        val bgPaint = Paint().apply {
            color = Color.argb(100, 0, 0, 0)
            isAntiAlias = true
        }
        canvas.drawRoundRect(bgRect, textSize * 0.3f, textSize * 0.3f, bgPaint)
        canvas.drawText(dateStr, bgRect.left + padding, bgRect.bottom - textSize * 0.4f, textPaint)

        return result
    }

    // ── 白边框 ──

    private fun applyWhiteFrame(source: Bitmap): Bitmap {
        val frameWidth = (min(source.width, source.height) * 0.04f).toInt()
        val result = Bitmap.createBitmap(
            source.width + frameWidth * 2,
            source.height + frameWidth * 2,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(result)
        canvas.drawColor(Color.WHITE)
        canvas.drawBitmap(source, frameWidth.toFloat(), frameWidth.toFloat(), null)
        return result
    }

    // ── 圆角白框 ──

    private fun applyRoundedFrame(source: Bitmap): Bitmap {
        val frameWidth = (min(source.width, source.height) * 0.04f).toInt()
        val radius = (min(source.width, source.height) * 0.03f).toFloat()
        val result = Bitmap.createBitmap(
            source.width + frameWidth * 2,
            source.height + frameWidth * 2,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(result)
        canvas.drawColor(Color.WHITE)

        // 绘制圆角图片
        val imgRect = RectF(
            frameWidth.toFloat(),
            frameWidth.toFloat(),
            (source.width + frameWidth).toFloat(),
            (source.height + frameWidth).toFloat()
        )
        val clipPaint = Paint().apply { isAntiAlias = true }
        canvas.drawRoundRect(imgRect, radius, radius, clipPaint)
        // 用 SRC_IN 模式裁切圆角
        val saveCount = canvas.saveLayer(imgRect, null)
        val srcPaint = Paint().apply { isAntiAlias = true }
        canvas.drawBitmap(source, frameWidth.toFloat(), frameWidth.toFloat(), srcPaint)
        srcPaint.xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_IN)
        canvas.drawRoundRect(imgRect, radius, radius, srcPaint)
        canvas.restoreToCount(saveCount)

        return result
    }

    // ── 边缘发光 ──

    private fun applyEdgeGlow(source: Bitmap): Bitmap {
        val result = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawBitmap(source, 0f, 0f, null)

        val w = source.width.toFloat()
        val h = source.height.toFloat()
        val glowWidth = min(w, h) * 0.03f

        val glowPaint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeWidth = glowWidth * 2
            color = Color.argb(80, 13, 148, 136) // Accent 青墨绿半透明
            maskFilter = android.graphics.BlurMaskFilter(glowWidth, android.graphics.BlurMaskFilter.Blur.NORMAL)
        }
        canvas.drawRect(RectF(glowWidth, glowWidth, w - glowWidth, h - glowWidth), glowPaint)

        return result
    }

    // ── 暗角 ──

    private fun applyVignette(source: Bitmap): Bitmap {
        val result = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawBitmap(source, 0f, 0f, null)

        val w = source.width.toFloat()
        val h = source.height.toFloat()
        val cx = w / 2f
        val cy = h / 2f
        val radius = Math.sqrt((cx * cx + cy * cy).toDouble()).toFloat() * 0.7f

        val vignettePaint = Paint().apply {
            isAntiAlias = true
            shader = android.graphics.RadialGradient(
                cx, cy, radius * 1.5f,
                intArrayOf(Color.TRANSPARENT, Color.argb(120, 0, 0, 0)),
                floatArrayOf(0.5f, 1f),
                android.graphics.Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(RectF(0f, 0f, w, h), vignettePaint)

        return result
    }

    // ── 复古日期（橙色颗粒感日期 + 底部横线） ──

    private fun applyRetroDate(source: Bitmap): Bitmap {
        val result = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawBitmap(source, 0f, 0f, null)

        val dateStr = SimpleDateFormat("yyyy / MM / dd", Locale.getDefault()).format(Date())
        val w = source.width.toFloat()
        val textSize = w * 0.04f
        val padding = w * 0.04f
        val lineY = source.height - padding * 2

        // 橙色日期文字
        val textPaint = Paint().apply {
            color = Color.argb(220, 255, 140, 0)
            this.textSize = textSize
            isAntiAlias = true
            typeface = Typeface.MONOSPACE
        }
        val textWidth = textPaint.measureText(dateStr)
        canvas.drawText(dateStr, padding, lineY, textPaint)

        // 底部装饰线
        val linePaint = Paint().apply {
            color = Color.argb(180, 255, 140, 0)
            strokeWidth = textSize * 0.08f
            isAntiAlias = true
        }
        canvas.drawLine(padding, lineY + textSize * 0.3f, padding + textWidth, lineY + textSize * 0.3f, linePaint)

        return result
    }

    // ── 胶片边框（上下黑边 + 齿孔） ──

    private fun applyFilmFrame(source: Bitmap): Bitmap {
        val barHeight = (source.height * 0.06f).toInt()
        val totalHeight = source.height + barHeight * 2
        val result = Bitmap.createBitmap(source.width, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)

        // 黑色背景
        canvas.drawColor(Color.BLACK)

        // 照片居中
        canvas.drawBitmap(source, 0f, barHeight.toFloat(), null)

        // 齿孔（白色小圆点）
        val dotPaint = Paint().apply {
            color = Color.argb(150, 255, 255, 255)
            isAntiAlias = true
        }
        val dotRadius = barHeight * 0.2f
        val step = source.width / 20f
        var x = step / 2f
        while (x < source.width) {
            canvas.drawCircle(x, barHeight / 2f, dotRadius, dotPaint)
            canvas.drawCircle(x, totalHeight - barHeight / 2f, dotRadius, dotPaint)
            x += step
        }

        return result
    }
}