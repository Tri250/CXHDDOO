package com.poseai.app.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * 马赛克类型
 */
enum class MosaicType { PIXELATE, BLUR, MOSAIC, SCRIBBLE }

/**
 * 马赛克区域定义
 *
 * @param type 马赛克类型
 * @param path 区域路径点序列（点形成闭合多边形或粗笔触轨迹）
 * @param blockSize 像素化块大小（像素）
 * @param blurRadius 模糊半径（像素）
 */
data class MosaicRegion(
    val type: MosaicType,
    val path: List<DoodlePoint>,
    val blockSize: Int = 20,
    val blurRadius: Int = 15
)

/**
 * 马赛克引擎
 *
 * 在位图的指定区域内应用马赛克 / 模糊 / 像素化 / 涂抹效果。
 *
 * 区域定义方式：path 中的点序列构成一个闭合多边形；
 * 若首尾点距离较远（视为开放轨迹），则以粗笔触方式覆盖区域。
 *
 * 算法说明：
 * - PIXELATE: 降采样后升采样，产生块状像素化
 * - BLUR: Stack Blur 算法（Mario Klingemann），无需 RenderScript
 * - MOSAIC: 将区域划分为网格，每格填充平均颜色
 * - SCRIBBLE: 交叉斜线涂抹遮盖内容
 */
class MosaicEngine {

    /**
     * 在位图上应用多个马赛克区域
     *
     * @param bitmap 原始位图
     * @param regions 马赛克区域列表
     * @return 处理后的新位图（不修改原图）
     */
    fun applyMosaic(bitmap: Bitmap, regions: List<MosaicRegion>): Bitmap {
        var result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        for (region in regions) {
            if (region.path.size < 2) continue
            val next = when (region.type) {
                MosaicType.PIXELATE -> pixelateRegion(result, region.path, region.blockSize)
                MosaicType.BLUR -> blurRegion(result, region.path, region.blurRadius)
                MosaicType.MOSAIC -> mosaicRegion(result, region.path, region.blockSize)
                MosaicType.SCRIBBLE -> scribbleRegion(result, region.path)
            }
            if (next !== result) {
                result.recycle()
                result = next
            }
        }
        return result
    }

    /**
     * 像素化区域
     * 原理：先将整图缩小 blockSize 倍，再放大回原尺寸，产生块状效果；
     * 然后仅在指定区域内绘制像素化结果。
     *
     * @param bitmap 原始位图
     * @param path 区域路径点
     * @param blockSize 像素块大小
     */
    fun pixelateRegion(bitmap: Bitmap, path: List<DoodlePoint>, blockSize: Int): Bitmap {
        if (path.size < 2 || blockSize < 1) return bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val w = bitmap.width
        val h = bitmap.height

        // 降采样
        val smallW = (w / blockSize).coerceAtLeast(1)
        val smallH = (h / blockSize).coerceAtLeast(1)
        val small = Bitmap.createScaledBitmap(bitmap, smallW, smallH, true)
        // 升采样（最近邻效果，因未启用滤波）
        val pixelated = Bitmap.createScaledBitmap(small, w, h, false)
        small.recycle()

        // 仅在区域内绘制像素化结果
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        clipToRegion(canvas, path) {
            canvas.drawBitmap(pixelated, 0f, 0f, null)
        }
        pixelated.recycle()
        return result
    }

    /**
     * 高斯模糊区域（Stack Blur 算法）
     *
     * @param bitmap 原始位图
     * @param path 区域路径点
     * @param radius 模糊半径（像素）
     */
    fun blurRegion(bitmap: Bitmap, path: List<DoodlePoint>, radius: Int): Bitmap {
        if (path.size < 2 || radius < 1) return bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val blurred = stackBlur(bitmap, radius)
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        clipToRegion(canvas, path) {
            canvas.drawBitmap(blurred, 0f, 0f, null)
        }
        if (blurred !== bitmap) blurred.recycle()
        return result
    }

    /**
     * 马赛克瓷砖区域
     * 将区域划分为 tileSize 网格，每格填充该区域平均颜色
     *
     * @param bitmap 原始位图
     * @param path 区域路径点
     * @param tileSize 瓷砖大小
     */
    fun mosaicRegion(bitmap: Bitmap, path: List<DoodlePoint>, tileSize: Int): Bitmap {
        if (path.size < 2 || tileSize < 1) return bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val w = bitmap.width
        val h = bitmap.height

        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }

        // 先从原图取像素用于计算每格平均色
        val srcPixels = IntArray(w * h)
        bitmap.getPixels(srcPixels, 0, w, 0, 0, w, h)

        clipToRegion(canvas, path) {
            val tilesX = (w + tileSize - 1) / tileSize
            val tilesY = (h + tileSize - 1) / tileSize
            for (ty in 0 until tilesY) {
                for (tx in 0 until tilesX) {
                    val x0 = tx * tileSize
                    val y0 = ty * tileSize
                    val x1 = min(x0 + tileSize, w)
                    val y1 = min(y0 + tileSize, h)
                    // 计算该瓷砖的平均颜色
                    var rSum = 0
                    var gSum = 0
                    var bSum = 0
                    var count = 0
                    for (py in y0 until y1) {
                        val rowOffset = py * w
                        for (px in x0 until x1) {
                            val c = srcPixels[rowOffset + px]
                            rSum += (c shr 16) and 0xFF
                            gSum += (c shr 8) and 0xFF
                            bSum += c and 0xFF
                            count++
                        }
                    }
                    if (count > 0) {
                        paint.color = Color.rgb(rSum / count, gSum / count, bSum / count)
                        canvas.drawRect(
                            x0.toFloat(), y0.toFloat(),
                            x1.toFloat(), y1.toFloat(),
                            paint
                        )
                    }
                }
            }
        }
        return result
    }

    /**
     * 涂抹区域（交叉斜线遮盖）
     * 先半透明黑色覆盖，再绘制两组对角斜线形成网格涂鸦
     *
     * @param bitmap 原始位图
     * @param path 区域路径点
     */
    fun scribbleRegion(bitmap: Bitmap, path: List<DoodlePoint>): Bitmap {
        if (path.size < 2) return bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val w = bitmap.width
        val h = bitmap.height

        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)

        clipToRegion(canvas, path) {
            // 半透明黑色底
            val darkPaint = Paint().apply { color = Color.argb(100, 0, 0, 0) }
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), darkPaint)

            // 交叉斜线
            val hatchPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(180, 30, 30, 30)
                strokeWidth = 2f
            }
            val spacing = 12f
            // 方向 "/" : 从左下到右上
            var d = -h.toFloat()
            while (d < w) {
                canvas.drawLine(d, 0f, d + h, h.toFloat(), hatchPaint)
                d += spacing
            }
            // 方向 "\" : 从左上到右下
            d = 0f
            while (d < w + h) {
                canvas.drawLine(d, 0f, d - h, h.toFloat(), hatchPaint)
                d += spacing
            }
        }
        return result
    }

    // ═══════════════════════════════════════════════════════════════
    // 区域裁剪工具
    // ═══════════════════════════════════════════════════════════════

    /**
     * 将画布裁剪到 path 定义的区域，执行 block 内的绘制
     *
     * 判定逻辑：
     * - 若 path 首尾点距离 < 20px，视为闭合多边形，使用 FILL
     * - 否则视为开放轨迹，以粗笔触（宽度基于位图尺寸）绘制覆盖区域
     *
     * 由于 clipPath 不支持抗锯齿，粗笔触场景改用遮罩混合以获得平滑边缘。
     */
    private inline fun clipToRegion(
        canvas: Canvas,
        path: List<DoodlePoint>,
        block: Canvas.() -> Unit
    ) {
        val w = canvas.width
        val h = canvas.height
        val isClosed = isClosedPath(path)
        if (isClosed) {
            // 闭合多边形：直接 clipPath
            val regionPath = buildClosedPath(path)
            canvas.save()
            canvas.clipPath(regionPath)
            canvas.block()
            canvas.restore()
        } else {
            // 开放轨迹：构建遮罩位图，使用 PorterDuff 混合
            val strokeW = max(40f, min(w, h) * 0.04f)
            val mask = buildStrokeMask(w, h, path, strokeW)
            // 在临时位图上执行绘制，再用遮罩混合到画布
            drawWithMask(canvas, mask, block)
            mask.recycle()
        }
    }

    /**
     * 判断路径是否闭合（首尾点距离 < 20px）
     */
    private fun isClosedPath(path: List<DoodlePoint>): Boolean {
        if (path.size < 3) return false
        val first = path.first()
        val last = path.last()
        val dx = first.x - last.x
        val dy = first.y - last.y
        return sqrt(dx * dx + dy * dy) < 20f
    }

    /**
     * 构建闭合多边形 Path
     */
    private fun buildClosedPath(points: List<DoodlePoint>): Path {
        val p = Path()
        if (points.isEmpty()) return p
        p.moveTo(points[0].x, points[0].y)
        for (i in 1 until points.size) {
            p.lineTo(points[i].x, points[i].y)
        }
        p.close()
        return p
    }

    /**
     * 构建粗笔触遮罩（白色 = 区域，透明 = 区域外）
     */
    private fun buildStrokeMask(w: Int, h: Int, path: List<DoodlePoint>, strokeW: Float): Bitmap {
        val mask = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(mask)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = strokeW
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val p = Path()
        p.moveTo(path[0].x, path[0].y)
        for (i in 1 until path.size) {
            p.lineTo(path[i].x, path[i].y)
        }
        canvas.drawPath(p, paint)
        return mask
    }

    /**
     * 使用遮罩在画布上混合绘制
     * 先在临时位图执行 block，再用遮罩选取有效区域叠加到画布
     */
    private inline fun drawWithMask(
        canvas: Canvas,
        mask: Bitmap,
        block: Canvas.() -> Unit
    ) {
        val w = canvas.width
        val h = canvas.height
        // 临时位图：执行效果绘制
        val temp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val tempCanvas = Canvas(temp)
        tempCanvas.block()
        // 用遮罩裁剪临时位图：DST_IN 保留遮罩不透明区域的像素
        val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        maskPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        tempCanvas.drawBitmap(mask, 0f, 0f, maskPaint)
        // 将裁剪后的效果叠加到结果画布
        canvas.drawBitmap(temp, 0f, 0f, null)
        temp.recycle()
    }

    // ═══════════════════════════════════════════════════════════════
    // Stack Blur 算法（Mario Klingemann）
    // 无需 RenderScript，纯像素运算，近似高斯模糊
    // ═══════════════════════════════════════════════════════════════

    /**
     * Stack Blur
     *
     * @param src 源位图
     * @param radius 模糊半径（>=1）
     * @return 模糊后的新位图
     */
    private fun stackBlur(src: Bitmap, radius: Int): Bitmap {
        if (radius < 1) return src.copy(Bitmap.Config.ARGB_8888, true)
        val w = src.width
        val h = src.height
        if (w <= 0 || h <= 0) return src.copy(Bitmap.Config.ARGB_8888, true)

        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)
        stackBlurInPlace(pixels, w, h, radius)

        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        result.setPixels(pixels, 0, w, 0, 0, w, h)
        return result
    }

    /**
     * 原地 Stack Blur
     * 两遍扫描：水平模糊 → 垂直模糊
     */
    private fun stackBlurInPlace(pixels: IntArray, w: Int, h: Int, radius: Int) {
        val wm = w - 1
        val hm = h - 1
        val wh = w * h
        val div = radius + radius + 1

        // 分通道缓冲
        val r = IntArray(wh)
        val g = IntArray(wh)
        val b = IntArray(wh)

        // 预计算除法查找表：dv[i] = i / div
        val dvSize = 256 * div
        val dv = IntArray(dvSize) { i -> i / div }

        // 原图像素备份
        val p = pixels.copyOf()

        // 提取 RGB 通道
        for (i in 0 until wh) {
            val px = p[i]
            b[i] = px and 0xFF
            g[i] = (px shr 8) and 0xFF
            r[i] = (px shr 16) and 0xFF
        }

        // ── 水平模糊 ──
        var yi = 0
        var yw = 0
        for (y in 0 until h) {
            var pr = 0; var pg = 0; var pb = 0
            // 初始化窗口和（左边界用首像素填充）
            for (i in -radius..radius) {
                val pIdx = yw + i.coerceIn(0, wm)
                val pix = p[pIdx]
                pr += (pix shr 16) and 0xFF
                pg += (pix shr 8) and 0xFF
                pb += pix and 0xFF
            }
            for (x in 0 until w) {
                r[yi] = dv[pr]
                g[yi] = dv[pg]
                b[yi] = dv[pb]
                // 滑动窗口：移除最左像素，加入最右像素
                val x1 = if (x + radius + 1 > wm) wm else x + radius + 1
                val x2 = if (x - radius < 0) 0 else x - radius
                val pix1 = p[yw + x1]
                val pix2 = p[yw + x2]
                pr += ((pix1 shr 16) and 0xFF) - ((pix2 shr 16) and 0xFF)
                pg += ((pix1 shr 8) and 0xFF) - ((pix2 shr 8) and 0xFF)
                pb += (pix1 and 0xFF) - (pix2 and 0xFF)
                yi++
            }
            yw += w
        }

        // ── 垂直模糊 ──
        for (x in 0 until w) {
            var pr = 0; var pg = 0; var pb = 0
            for (i in -radius..radius) {
                val yp = i.coerceIn(0, hm)
                pr += r[yp * w + x]
                pg += g[yp * w + x]
                pb += b[yp * w + x]
            }
            yi = x
            for (y in 0 until h) {
                pixels[yi] = (0xFF shl 24) or (dv[pr] shl 16) or (dv[pg] shl 8) or dv[pb]
                val y1 = if (y + radius + 1 > hm) hm else y + radius + 1
                val y2 = if (y - radius < 0) 0 else y - radius
                pr += r[y1 * w + x] - r[y2 * w + x]
                pg += g[y1 * w + x] - g[y2 * w + x]
                pb += b[y1 * w + x] - b[y2 * w + x]
                yi += w
            }
        }
    }
}
