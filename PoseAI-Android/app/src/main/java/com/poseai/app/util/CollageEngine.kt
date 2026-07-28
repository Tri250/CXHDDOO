package com.poseai.app.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.sqrt

/**
 * 拼图引擎：将多张照片拼接为网格布局
 *
 * 支持的布局模板：
 * - GRID_2: 2 张竖排或横排
 * - GRID_3: 3 张（1 大 + 2 小）
 * - GRID_4: 4 张 2×2 网格
 * - GRID_6: 6 张 2×3 网格
 */
object CollageEngine {

    enum class Layout(val displayName: String, val count: Int) {
        GRID_2("双图拼接", 2),
        GRID_3("三图拼图", 3),
        GRID_4("四宫格", 4),
        GRID_6("六宫格", 6)
    }

    private const val SPACING_RATIO = 0.012f  // 间距占最小边的比例
    private const val CORNER_RADIUS_RATIO = 0.02f  // 圆角半径比例

    /**
     * 拼接多张图片到目标布局
     * @param bitmaps 输入图片列表（数量必须匹配 layout.count）
     * @param layout 布局模板
     * @param background 背景颜色（ARGB），默认白色
     * @return 拼接后的 Bitmap
     */
    fun createCollage(
        bitmaps: List<Bitmap>,
        layout: Layout,
        background: Int = Color.WHITE
    ): Bitmap? {
        if (bitmaps.size < layout.count) return null
        val sources = bitmaps.take(layout.count)

        return try {
            when (layout) {
                Layout.GRID_2 -> createGrid2(sources, background)
                Layout.GRID_3 -> createGrid3(sources, background)
                Layout.GRID_4 -> createGrid4(sources, background)
                Layout.GRID_6 -> createGrid6(sources, background)
            }
        } catch (e: Exception) {
            android.util.Log.e("CollageEngine", "Failed to create collage", e)
            null
        }
    }

    // ── 双图拼接：横向或纵向（根据图片比例自动选择） ──

    private fun createGrid2(bitmaps: List<Bitmap>, bg: Int): Bitmap {
        val b1 = bitmaps[0]
        val b2 = bitmaps[1]
        // 根据图片比例选择横排或竖排
        val useHorizontal = b1.width.toFloat() / b1.height > 1.5f

        val spacing = (min(b1.width, b1.height) * SPACING_RATIO).toInt()
        val radius = (min(b1.width, b1.height) * CORNER_RADIUS_RATIO)

        if (useHorizontal) {
            // 横排：统一高度，左右并排
            val h = min(b1.height, b2.height)
            val w1 = (b1.width * h / b1.height)
            val w2 = (b2.width * h / b2.height)
            val totalW = w1 + spacing + w2
            val result = Bitmap.createBitmap(totalW, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(result)
            canvas.drawColor(bg)
            drawRoundedBitmap(canvas, b1, 0f, 0f, w1.toFloat(), h.toFloat(), radius)
            drawRoundedBitmap(canvas, b2, (w1 + spacing).toFloat(), 0f, w2.toFloat(), h.toFloat(), radius)
            return result
        } else {
            // 竖排：统一宽度，上下
            val w = min(b1.width, b2.width)
            val h1 = (b1.height * w / b1.width)
            val h2 = (b2.height * w / b2.width)
            val totalH = h1 + spacing + h2
            val result = Bitmap.createBitmap(w, totalH, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(result)
            canvas.drawColor(bg)
            drawRoundedBitmap(canvas, b1, 0f, 0f, w.toFloat(), h1.toFloat(), radius)
            drawRoundedBitmap(canvas, b2, 0f, (h1 + spacing).toFloat(), w.toFloat(), h2.toFloat(), radius)
            return result
        }
    }

    // ── 三图拼图：1 大图（左）+ 2 小图（右上下） ──

    private fun createGrid3(bitmaps: List<Bitmap>, bg: Int): Bitmap {
        val spacing = (min(bitmaps[0].width, bitmaps[0].height) * SPACING_RATIO).toInt()
        val radius = (min(bitmaps[0].width, bitmaps[0].height) * CORNER_RADIUS_RATIO)

        val w = minOf(bitmaps[0].width, bitmaps[1].width, bitmaps[2].width)
        val h = (w * 1.3f).toInt()  // 统一高度
        val bigW = w * 2 / 3
        val smallW = w - bigW - spacing
        val halfH = (h - spacing) / 2

        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawColor(bg)

        drawRoundedBitmap(canvas, bitmaps[0], 0f, 0f, bigW.toFloat(), h.toFloat(), radius)
        drawRoundedBitmap(canvas, bitmaps[1], (bigW + spacing).toFloat(), 0f, smallW.toFloat(), halfH.toFloat(), radius)
        drawRoundedBitmap(canvas, bitmaps[2], (bigW + spacing).toFloat(), (halfH + spacing).toFloat(), smallW.toFloat(), halfH.toFloat(), radius)

        return result
    }

    // ── 四宫格 2×2 ──

    private fun createGrid4(bitmaps: List<Bitmap>, bg: Int): Bitmap {
        val spacing = (min(bitmaps[0].width, bitmaps[0].height) * SPACING_RATIO).toInt()
        val radius = (min(bitmaps[0].width, bitmaps[0].height) * CORNER_RADIUS_RATIO)

        val w = minOf(bitmaps[0].width, bitmaps[1].width, bitmaps[2].width, bitmaps[3].width)
        // 方形容器
        val cellSize = (w - spacing) / 2
        val canvasSize = cellSize * 2 + spacing

        val result = Bitmap.createBitmap(canvasSize, canvasSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawColor(bg)

        for (i in 0 until 4) {
            val col = i % 2
            val row = i / 2
            val x = col * (cellSize + spacing)
            val y = row * (cellSize + spacing)
            drawRoundedBitmap(canvas, bitmaps[i], x.toFloat(), y.toFloat(), cellSize.toFloat(), cellSize.toFloat(), radius)
        }

        return result
    }

    // ── 六宫格 2×3 ──

    private fun createGrid6(bitmaps: List<Bitmap>, bg: Int): Bitmap {
        val spacing = (min(bitmaps[0].width, bitmaps[0].height) * SPACING_RATIO).toInt()
        val radius = (min(bitmaps[0].width, bitmaps[0].height) * CORNER_RADIUS_RATIO)

        val w = bitmaps.minOf { it.width }
        val cellSize = (w - spacing * 2) / 3
        val canvasW = cellSize * 3 + spacing * 2
        val canvasH = cellSize * 2 + spacing

        val result = Bitmap.createBitmap(canvasW, canvasH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawColor(bg)

        for (i in 0 until 6) {
            val col = i % 3
            val row = i / 3
            val x = col * (cellSize + spacing)
            val y = row * (cellSize + spacing)
            drawRoundedBitmap(canvas, bitmaps[i], x.toFloat(), y.toFloat(), cellSize.toFloat(), cellSize.toFloat(), radius)
        }

        return result
    }

    /**
     * 绘制圆角位图到指定区域
     */
    private fun drawRoundedBitmap(
        canvas: Canvas,
        bitmap: Bitmap,
        x: Float, y: Float, w: Float, h: Float,
        radius: Float
    ) {
        val dst = RectF(x, y, x + w, y + h)
        val src = Rect(0, 0, bitmap.width, bitmap.height)

        // 缩放以填充目标区域（居中裁剪）
        val scale = maxOf(w / bitmap.width, h / bitmap.height)
        val scaledW = (bitmap.width * scale)
        val scaledH = (bitmap.height * scale)
        val offsetX = (w - scaledW) / 2f
        val offsetY = (h - scaledH) / 2f
        val scaledRect = RectF(x + offsetX, y + offsetY, x + offsetX + scaledW, y + offsetY + scaledH)

        val saveCount = canvas.saveLayer(dst, null)
        // 裁切圆角路径
        val path = android.graphics.Path().apply {
            addRoundRect(dst, radius, radius, android.graphics.Path.Direction.CW)
        }
        canvas.clipPath(path)
        canvas.drawBitmap(bitmap, src, scaledRect, null)
        canvas.restoreToCount(saveCount)

        // 描边
        val borderPaint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeWidth = 1f
            color = Color.argb(30, 0, 0, 0)
        }
        canvas.drawRoundRect(dst, radius, radius, borderPaint)
    }
}