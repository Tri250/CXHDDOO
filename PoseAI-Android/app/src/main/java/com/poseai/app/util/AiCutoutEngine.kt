package com.poseai.app.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Shader
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * AI 抠图与背景替换引擎
 *
 * 基于 K-means 聚类的前景检测算法：
 * 1. 将图像降采样并提取 RGB 特征
 * 2. K-means (k=2) 聚类将像素分为两类
 * 3. 通过位置启发式判断前景类（中心区域占比更大的类）
 * 4. 形态学操作（腐蚀 + 膨胀）去除噪声、平滑边缘
 * 5. 将掩码作为 Alpha 通道应用于原图，实现抠图
 *
 * 天空检测：扫描顶部 40% 行，识别蓝色 / 白色天空像素
 */
class AiCutoutEngine {

    // 降采样最大边长（K-means 在小图上运算，再放大回原尺寸）
    private val maxSampleDim = 100

    /**
     * 抠图：提取前景（人物 / 主体），背景透明
     *
     * @param bitmap 原始位图
     * @return 前景像素不变、背景像素透明（alpha=0）的 ARGB_8888 位图
     */
    fun cutout(bitmap: Bitmap): Bitmap {
        val mask = detectForegroundMask(bitmap)
        val w = bitmap.width
        val h = bitmap.height

        val srcPixels = IntArray(w * h)
        bitmap.getPixels(srcPixels, 0, w, 0, 0, w, h)

        val maskPixels = IntArray(w * h)
        mask.getPixels(maskPixels, 0, w, 0, 0, w, h)

        val outPixels = IntArray(w * h)
        for (i in 0 until w * h) {
            // 掩码亮度作为 Alpha
            val alpha = (maskPixels[i] shr 16) and 0xFF
            // 保留原图 RGB，应用掩码 Alpha
            outPixels[i] = (alpha shl 24) or (srcPixels[i] and 0x00FFFFFF)
        }

        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        result.setPixels(outPixels, 0, w, 0, 0, w, h)
        mask.recycle()
        return result
    }

    /**
     * 用纯色替换背景
     *
     * @param bitmap 原始位图
     * @param color 背景色
     * @return 前景叠加在纯色背景上的位图
     */
    fun replaceBackgroundSolid(bitmap: Bitmap, color: Int): Bitmap {
        val cutout = cutout(bitmap)
        val w = bitmap.width
        val h = bitmap.height
        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        // 填充背景色
        canvas.drawColor(color)
        // 绘制抠出的前景（透明区域露出背景色）
        canvas.drawBitmap(cutout, 0f, 0f, null)
        cutout.recycle()
        return result
    }

    /**
     * 用另一张图片替换背景
     *
     * @param foreground 前景图
     * @param background 新背景图（会缩放到与前景相同尺寸）
     * @return 前景叠加在新背景上的位图
     */
    fun replaceBackgroundImage(foreground: Bitmap, background: Bitmap): Bitmap {
        val cutout = cutout(foreground)
        val w = foreground.width
        val h = foreground.height
        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        // 缩放背景图到前景尺寸
        val scaledBg = if (background.width == w && background.height == h) {
            background.copy(Bitmap.Config.ARGB_8888, true)
        } else {
            Bitmap.createScaledBitmap(background, w, h, true)
        }
        canvas.drawBitmap(scaledBg, 0f, 0f, null)
        scaledBg.recycle()
        // 绘制前景
        canvas.drawBitmap(cutout, 0f, 0f, null)
        cutout.recycle()
        return result
    }

    /**
     * 替换天空区域为渐变色
     * 检测顶部天空区域，替换为垂直渐变
     *
     * @param bitmap 原始位图
     * @param skyGradient 渐变色数组（从上到下）
     * @return 天空被替换为渐变的位图
     */
    fun replaceSky(bitmap: Bitmap, skyGradient: IntArray): Bitmap {
        if (skyGradient.isEmpty()) return bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val w = bitmap.width
        val h = bitmap.height
        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)

        // 构建垂直渐变（覆盖上半部分）
        val positions = FloatArray(skyGradient.size) { i -> i.toFloat() / (skyGradient.size - 1).coerceAtLeast(1) }
        val gradient = LinearGradient(
            0f, 0f, 0f, h * 0.7f,
            skyGradient, positions,
            Shader.TileMode.CLAMP
        )
        val gradPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = gradient }
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), gradPaint)

        // 检测天空掩码
        val skyMask = detectSkyMask(bitmap)

        // 在临时位图上绘制原图，再用 DST_OUT 挖去天空区域
        val temp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val tempCanvas = Canvas(temp)
        tempCanvas.drawBitmap(bitmap, 0f, 0f, null)
        val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        maskPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
        tempCanvas.drawBitmap(skyMask, 0f, 0f, maskPaint)

        // 将挖空后的原图叠加到渐变背景上
        canvas.drawBitmap(temp, 0f, 0f, null)

        skyMask.recycle()
        temp.recycle()
        return result
    }

    /**
     * 替换天空区域为另一张图片
     *
     * @param bitmap 原始位图
     * @param skyBitmap 新天空图（会缩放到与原图相同尺寸）
     * @return 天空被替换为新图片的位图
     */
    fun replaceSkyImage(bitmap: Bitmap, skyBitmap: Bitmap): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)

        // 缩放天空图
        val scaledSky = if (skyBitmap.width == w && skyBitmap.height == h) {
            skyBitmap.copy(Bitmap.Config.ARGB_8888, true)
        } else {
            Bitmap.createScaledBitmap(skyBitmap, w, h, true)
        }
        canvas.drawBitmap(scaledSky, 0f, 0f, null)
        scaledSky.recycle()

        // 检测天空掩码
        val skyMask = detectSkyMask(bitmap)

        // 原图挖去天空区域后叠加
        val temp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val tempCanvas = Canvas(temp)
        tempCanvas.drawBitmap(bitmap, 0f, 0f, null)
        val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        maskPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
        tempCanvas.drawBitmap(skyMask, 0f, 0f, maskPaint)

        canvas.drawBitmap(temp, 0f, 0f, null)

        skyMask.recycle()
        temp.recycle()
        return result
    }

    /**
     * 检测前景掩码
     * 使用 K-means 聚类 (k=2) 将图像分为前景/背景两类，
     * 再通过形态学操作（腐蚀+膨胀）平滑边缘
     *
     * @param bitmap 原始位图
     * @return 掩码位图（白色 = 前景，黑色 = 背景），尺寸与原图相同
     */
    fun detectForegroundMask(bitmap: Bitmap): Bitmap {
        val w = bitmap.width
        val h = bitmap.height

        // 降采样以加速 K-means（取三者的最小值，不放大原图）
        val scale = min(min(maxSampleDim.toFloat() / w, maxSampleDim.toFloat() / h), 1f)
        val sw = (w * scale).toInt().coerceAtLeast(1)
        val sh = (h * scale).toInt().coerceAtLeast(1)
        val small = Bitmap.createScaledBitmap(bitmap, sw, sh, true)

        // 提取像素
        val pixels = IntArray(sw * sh)
        small.getPixels(pixels, 0, sw, 0, 0, sw, sh)

        // 构建特征向量 [R, G, B]
        val features = Array(sw * sh) { i ->
            val px = pixels[i]
            floatArrayOf(
                ((px shr 16) and 0xFF).toFloat(),
                ((px shr 8) and 0xFF).toFloat(),
                (px and 0xFF).toFloat()
            )
        }

        // K-means 聚类 (k=2)
        val assignment = kmeans(features, 2, 12)

        // 判断前景类：中心区域占比更大的类为前景
        val centerMinX = sw / 4
        val centerMaxX = sw * 3 / 4
        val centerMinY = sh / 4
        val centerMaxY = sh * 3 / 4
        var c0Center = 0
        var c1Center = 0
        for (y in 0 until sh) {
            for (x in 0 until sw) {
                val i = y * sw + x
                if (x in centerMinX..centerMaxX && y in centerMinY..centerMaxY) {
                    if (assignment[i] == 0) c0Center++ else c1Center++
                }
            }
        }
        val fgCluster = if (c0Center >= c1Center) 0 else 1

        // 生成小尺寸掩码
        val maskPixels = IntArray(sw * sh)
        for (i in 0 until sw * sh) {
            maskPixels[i] = if (assignment[i] == fgCluster) Color.WHITE else Color.BLACK
        }
        val maskSmall = Bitmap.createBitmap(sw, sh, Bitmap.Config.ARGB_8888)
        maskSmall.setPixels(maskPixels, 0, sw, 0, 0, sw, sh)

        small.recycle()

        // 放大到原图尺寸
        val maskFull = Bitmap.createScaledBitmap(maskSmall, w, h, true)
        maskSmall.recycle()

        // 形态学精炼：先腐蚀去噪点，再膨胀恢复主体，最后轻微模糊平滑边缘
        val eroded = erode(maskFull, 2)
        maskFull.recycle()
        val dilated = dilate(eroded, 3)
        eroded.recycle()
        val blurred = fastBlur(dilated, 2)
        dilated.recycle()
        return blurred
    }

    // ═══════════════════════════════════════════════════════════════
    // K-means 聚类
    // ═══════════════════════════════════════════════════════════════

    /**
     * K-means 聚类
     *
     * @param features 特征向量数组（每个元素为一组特征）
     * @param k 聚类数
     * @param iterations 迭代次数
     * @return 每个样本的聚类标签（0 ~ k-1）
     */
    private fun kmeans(
        features: Array<FloatArray>,
        k: Int,
        iterations: Int
    ): IntArray {
        val n = features.size
        if (n == 0) return IntArray(0)
        val dim = features[0].size
        val random = Random(42)

        // K-means++ 初始化：首个质心随机选取，后续质心按距离概率选取
        val centroids = Array(k) { FloatArray(dim) }
        centroids[0] = features[random.nextInt(n)].copyOf()
        for (c in 1 until k) {
            // 计算每个点到最近质心的距离平方
            val dists = FloatArray(n) { i ->
                var minDist = Float.MAX_VALUE
                for (cc in 0 until c) {
                    val d = squaredDistance(features[i], centroids[cc])
                    if (d < minDist) minDist = d
                }
                minDist
            }
            val total = dists.sum()
            if (total <= 0f) {
                // 所有点相同，随机选
                centroids[c] = features[random.nextInt(n)].copyOf()
                continue
            }
            // 轮盘赌选取
            var r = random.nextFloat() * total
            var selected = n - 1
            for (i in 0 until n) {
                r -= dists[i]
                if (r <= 0f) {
                    selected = i
                    break
                }
            }
            centroids[c] = features[selected].copyOf()
        }

        val assignment = IntArray(n)

        // 迭代优化
        repeat(iterations) {
            // 分配阶段：每个点分配到最近质心
            for (i in 0 until n) {
                var minDist = Float.MAX_VALUE
                var bestC = 0
                for (c in 0 until k) {
                    val d = squaredDistance(features[i], centroids[c])
                    if (d < minDist) {
                        minDist = d
                        bestC = c
                    }
                }
                assignment[i] = bestC
            }
            // 更新阶段：重新计算质心
            val sums = Array(k) { FloatArray(dim) }
            val counts = IntArray(k)
            for (i in 0 until n) {
                val c = assignment[i]
                counts[c]++
                for (d in 0 until dim) {
                    sums[c][d] += features[i][d]
                }
            }
            for (c in 0 until k) {
                if (counts[c] > 0) {
                    for (d in 0 until dim) {
                        centroids[c][d] = sums[c][d] / counts[c]
                    }
                } else {
                    // 空簇：随机重新初始化
                    centroids[c] = features[random.nextInt(n)].copyOf()
                }
            }
        }

        return assignment
    }

    /**
     * 欧氏距离平方（避免开方，加速比较）
     */
    private fun squaredDistance(a: FloatArray, b: FloatArray): Float {
        var sum = 0f
        for (i in a.indices) {
            val diff = a[i] - b[i]
            sum += diff * diff
        }
        return sum
    }

    // ═══════════════════════════════════════════════════════════════
    // 形态学操作（腐蚀 / 膨胀）
    // 使用 3x3 十字形结构元素
    // ═══════════════════════════════════════════════════════════════

    /**
     * 腐蚀：白色区域收缩，消除孤立噪点
     * 像素为白当且仅当其所有有效邻居均为白
     */
    private fun erode(mask: Bitmap, iterations: Int): Bitmap {
        var current = mask.copy(Bitmap.Config.ARGB_8888, true)
        val w = current.width
        val h = current.height
        repeat(iterations) {
            val src = IntArray(w * h)
            current.getPixels(src, 0, w, 0, 0, w, h)
            val dst = IntArray(w * h)
            for (y in 0 until h) {
                for (x in 0 until w) {
                    val i = y * w + x
                    var allWhite = true
                    for (dy in -1..1) {
                        for (dx in -1..1) {
                            val nx = x + dx
                            val ny = y + dy
                            // 越界视为黑色（边缘腐蚀）
                            if (nx < 0 || nx >= w || ny < 0 || ny >= h) {
                                allWhite = false
                                break
                            }
                            val px = src[ny * w + nx]
                            if ((px shr 16) and 0xFF < 128) {
                                allWhite = false
                                break
                            }
                        }
                        if (!allWhite) break
                    }
                    dst[i] = if (allWhite) Color.WHITE else Color.BLACK
                }
            }
            current.setPixels(dst, 0, w, 0, 0, w, h)
        }
        return current
    }

    /**
     * 膨胀：白色区域扩张，填补空洞、恢复主体
     * 像素为白当其任一有效邻居为白
     */
    private fun dilate(mask: Bitmap, iterations: Int): Bitmap {
        var current = mask.copy(Bitmap.Config.ARGB_8888, true)
        val w = current.width
        val h = current.height
        repeat(iterations) {
            val src = IntArray(w * h)
            current.getPixels(src, 0, w, 0, 0, w, h)
            val dst = IntArray(w * h)
            for (y in 0 until h) {
                for (x in 0 until w) {
                    val i = y * w + x
                    var anyWhite = false
                    for (dy in -1..1) {
                        for (dx in -1..1) {
                            val nx = x + dx
                            val ny = y + dy
                            if (nx < 0 || nx >= w || ny < 0 || ny >= h) continue
                            val px = src[ny * w + nx]
                            if ((px shr 16) and 0xFF >= 128) {
                                anyWhite = true
                                break
                            }
                        }
                        if (anyWhite) break
                    }
                    dst[i] = if (anyWhite) Color.WHITE else Color.BLACK
                }
            }
            current.setPixels(dst, 0, w, 0, 0, w, h)
        }
        return current
    }

    // ═══════════════════════════════════════════════════════════════
    // 快速模糊（盒式模糊，近似高斯）
    // ═══════════════════════════════════════════════════════════════

    /**
     * 盒式模糊（用于掩码边缘平滑）
     * 两遍可分离卷积：水平 + 垂直
     */
    private fun fastBlur(bitmap: Bitmap, radius: Int): Bitmap {
        if (radius < 1) return bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        // 分离通道
        val r = IntArray(w * h)
        val g = IntArray(w * h)
        val b = IntArray(w * h)
        val a = IntArray(w * h)
        for (i in 0 until w * h) {
            val px = pixels[i]
            a[i] = (px shr 24) and 0xFF
            r[i] = (px shr 16) and 0xFF
            g[i] = (px shr 8) and 0xFF
            b[i] = px and 0xFF
        }

        // 水平盒式模糊
        boxBlurH(a, r, g, b, w, h, radius)
        // 垂直盒式模糊
        boxBlurV(a, r, g, b, w, h, radius)

        // 写回
        for (i in 0 until w * h) {
            pixels[i] = (a[i] shl 24) or (r[i] shl 16) or (g[i] shl 8) or b[i]
        }
        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        result.setPixels(pixels, 0, w, 0, 0, w, h)
        return result
    }

    private fun boxBlurH(
        a: IntArray, r: IntArray, g: IntArray, b: IntArray,
        w: Int, h: Int, radius: Int
    ) {
        val div = 2 * radius + 1
        // 使用独立的目标数组，避免滑动窗口读取已写入的值
        val aOut = IntArray(w * h)
        val rOut = IntArray(w * h)
        val gOut = IntArray(w * h)
        val bOut = IntArray(w * h)
        for (y in 0 until h) {
            val rowOffset = y * w
            var aSum = 0; var rSum = 0; var gSum = 0; var bSum = 0
            // 初始化窗口（左边界用首像素填充）
            for (i in -radius..radius) {
                val xi = i.coerceIn(0, w - 1)
                val idx = rowOffset + xi
                aSum += a[idx]; rSum += r[idx]; gSum += g[idx]; bSum += b[idx]
            }
            for (x in 0 until w) {
                val idx = rowOffset + x
                aOut[idx] = aSum / div
                rOut[idx] = rSum / div
                gOut[idx] = gSum / div
                bOut[idx] = bSum / div
                // 滑动窗口：从源数组读取，保证读取的是原始值
                val xOut = (x - radius).coerceIn(0, w - 1)
                val xIn = (x + radius + 1).coerceIn(0, w - 1)
                val idxOut = rowOffset + xOut
                val idxIn = rowOffset + xIn
                if (xOut != xIn) {
                    aSum += a[idxIn] - a[idxOut]
                    rSum += r[idxIn] - r[idxOut]
                    gSum += g[idxIn] - g[idxOut]
                    bSum += b[idxIn] - b[idxOut]
                }
            }
        }
        // 回写到原数组
        System.arraycopy(aOut, 0, a, 0, w * h)
        System.arraycopy(rOut, 0, r, 0, w * h)
        System.arraycopy(gOut, 0, g, 0, w * h)
        System.arraycopy(bOut, 0, b, 0, w * h)
    }

    private fun boxBlurV(
        a: IntArray, r: IntArray, g: IntArray, b: IntArray,
        w: Int, h: Int, radius: Int
    ) {
        val div = 2 * radius + 1
        val aOut = IntArray(w * h)
        val rOut = IntArray(w * h)
        val gOut = IntArray(w * h)
        val bOut = IntArray(w * h)
        for (x in 0 until w) {
            var aSum = 0; var rSum = 0; var gSum = 0; var bSum = 0
            // 初始化窗口
            for (i in -radius..radius) {
                val yi = i.coerceIn(0, h - 1)
                val idx = yi * w + x
                aSum += a[idx]; rSum += r[idx]; gSum += g[idx]; bSum += b[idx]
            }
            for (y in 0 until h) {
                val idx = y * w + x
                aOut[idx] = aSum / div
                rOut[idx] = rSum / div
                gOut[idx] = gSum / div
                bOut[idx] = bSum / div
                // 滑动窗口：从源数组读取
                val yOut = (y - radius).coerceIn(0, h - 1)
                val yIn = (y + radius + 1).coerceIn(0, h - 1)
                val idxOut = yOut * w + x
                val idxIn = yIn * w + x
                if (yOut != yIn) {
                    aSum += a[idxIn] - a[idxOut]
                    rSum += r[idxIn] - r[idxOut]
                    gSum += g[idxIn] - g[idxOut]
                    bSum += b[idxIn] - b[idxOut]
                }
            }
        }
        System.arraycopy(aOut, 0, a, 0, w * h)
        System.arraycopy(rOut, 0, r, 0, w * h)
        System.arraycopy(gOut, 0, g, 0, w * h)
        System.arraycopy(bOut, 0, b, 0, w * h)
    }

    // ═══════════════════════════════════════════════════════════════
    // 天空检测
    // ═══════════════════════════════════════════════════════════════

    /**
     * 检测天空掩码
     * 扫描顶部 40% 行，识别蓝色 / 白色天空像素
     * 返回遮罩位图：天空区域不透明（白色 alpha=255），非天空区域透明（alpha=0）
     *
     * @param bitmap 原始位图
     * @return 天空遮罩（用于 DST_OUT 挖空）
     */
    private fun detectSkyMask(bitmap: Bitmap): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        // 天空判定：仅考虑顶部 40% 区域
        val topRows = (h * 0.4f).toInt().coerceAtLeast(1)
        val maskPixels = IntArray(w * h)

        for (y in 0 until h) {
            for (x in 0 until w) {
                val i = y * w + x
                val px = pixels[i]
                val r = (px shr 16) and 0xFF
                val g = (px shr 8) and 0xFF
                val b = px and 0xFF

                // 蓝色天空：B 明显大于 R 和 G，且亮度足够
                val isBlueSky = b > r + 20 && b > g + 10 && b > 80
                // 白色天空 / 阴天：高亮度、低饱和度
                val isWhiteSky = r > 200 && g > 200 && b > 200 &&
                    abs(r - g) < 20 && abs(g - b) < 20

                // 顶部区域的天空像素标记为不透明
                maskPixels[i] = if (y < topRows && (isBlueSky || isWhiteSky)) {
                    Color.WHITE
                } else {
                    Color.TRANSPARENT
                }
            }
        }

        val mask = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        mask.setPixels(maskPixels, 0, w, 0, 0, w, h)

        // 形态学闭运算（膨胀+腐蚀）填补天空内部空洞
        val dilated = dilateAlpha(mask, 4)
        mask.recycle()
        val eroded = erodeAlpha(dilated, 2)
        dilated.recycle()

        // 轻微模糊使天空边缘过渡自然
        val blurred = fastBlur(eroded, 3)
        eroded.recycle()
        return blurred
    }

    /**
     * 膨胀（Alpha 通道版）
     * 用于含透明像素的掩码：任一邻居不透明则当前像素不透明
     */
    private fun dilateAlpha(mask: Bitmap, iterations: Int): Bitmap {
        var current = mask.copy(Bitmap.Config.ARGB_8888, true)
        val w = current.width
        val h = current.height
        repeat(iterations) {
            val src = IntArray(w * h)
            current.getPixels(src, 0, w, 0, 0, w, h)
            val dst = IntArray(w * h)
            for (y in 0 until h) {
                for (x in 0 until w) {
                    val i = y * w + x
                    var anyOpaque = false
                    for (dy in -1..1) {
                        for (dx in -1..1) {
                            val nx = x + dx
                            val ny = y + dy
                            if (nx < 0 || nx >= w || ny < 0 || ny >= h) continue
                            val px = src[ny * w + nx]
                            if ((px shr 24) and 0xFF >= 128) {
                                anyOpaque = true
                                break
                            }
                        }
                        if (anyOpaque) break
                    }
                    dst[i] = if (anyOpaque) Color.WHITE else Color.TRANSPARENT
                }
            }
            current.setPixels(dst, 0, w, 0, 0, w, h)
        }
        return current
    }

    /**
     * 腐蚀（Alpha 通道版）
     * 所有邻居不透明时当前像素才不透明
     */
    private fun erodeAlpha(mask: Bitmap, iterations: Int): Bitmap {
        var current = mask.copy(Bitmap.Config.ARGB_8888, true)
        val w = current.width
        val h = current.height
        repeat(iterations) {
            val src = IntArray(w * h)
            current.getPixels(src, 0, w, 0, 0, w, h)
            val dst = IntArray(w * h)
            for (y in 0 until h) {
                for (x in 0 until w) {
                    val i = y * w + x
                    var allOpaque = true
                    for (dy in -1..1) {
                        for (dx in -1..1) {
                            val nx = x + dx
                            val ny = y + dy
                            if (nx < 0 || nx >= w || ny < 0 || ny >= h) {
                                allOpaque = false
                                break
                            }
                            val px = src[ny * w + nx]
                            if ((px shr 24) and 0xFF < 128) {
                                allOpaque = false
                                break
                            }
                        }
                        if (!allOpaque) break
                    }
                    dst[i] = if (allOpaque) Color.WHITE else Color.TRANSPARENT
                }
            }
            current.setPixels(dst, 0, w, 0, 0, w, h)
        }
        return current
    }
}
