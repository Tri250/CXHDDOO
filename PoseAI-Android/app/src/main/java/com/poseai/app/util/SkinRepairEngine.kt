package com.poseai.app.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Region
import com.poseai.app.engine.FaceLandmarkDetector.FaceData
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * 皮肤修复引擎
 *
 * 基于人脸关键点的智能皮肤修复系统，支持：
 * - 祛痘（红色/暗色斑点检测 + 局部修复）
 * - 祛斑（色斑检测 + 均匀化）
 * - 祛黑眼圈（眼下方暗区提亮）
 * - 均匀肤色（全脸肤色均匀化）
 *
 * 所有算法使用像素级颜色分析与图像修复技术。
 */
class SkinRepairEngine {

    /** 皮肤修复参数（每项 0-100） */
    data class SkinRepairParams(
        val removeAcne: Int = 0,        // 祛痘
        val removeSpots: Int = 0,       // 祛斑
        val removeDarkCircles: Int = 0, // 祛黑眼圈
        val brightenSkinTone: Int = 0   // 均匀肤色
    )

    /**
     * 应用所有皮肤修复
     */
    fun applyAll(bitmap: Bitmap, faceData: FaceData?, params: SkinRepairParams): Bitmap {
        if (faceData == null) return bitmap
        var result = bitmap.copy(Bitmap.Config.ARGB_8888, true)

        if (params.removeAcne > 0) {
            val temp = removeAcne(result, faceData, params.removeAcne)
            result.recycle()
            result = temp
        }
        if (params.removeSpots > 0) {
            val temp = removeSpots(result, faceData, params.removeSpots)
            result.recycle()
            result = temp
        }
        if (params.removeDarkCircles > 0) {
            val temp = removeDarkCircles(result, faceData, params.removeDarkCircles)
            result.recycle()
            result = temp
        }
        if (params.brightenSkinTone > 0) {
            val temp = brightenSkinTone(result, faceData, params.brightenSkinTone)
            result.recycle()
            result = temp
        }

        return result
    }

    // ═══════════════════════════════════════════════════════════════
    // 祛痘
    // ═══════════════════════════════════════════════════════════════

    /**
     * 祛痘 — 检测皮肤区域的红色/暗色斑点并修复
     *
     * 算法：在脸部区域内扫描，检测比周围皮肤更红或更暗的小区域，
     * 使用周围像素的加权平均进行修复（类似内容感知填充）。
     */
    fun removeAcne(bitmap: Bitmap, face: FaceData, intensity: Int): Bitmap {
        val strength = intensity / 100f
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val width = result.width
        val height = result.height

        // 构建脸部区域 Path
        val facePath = Path().apply {
            if (face.faceContour.isNotEmpty()) {
                moveTo(face.faceContour[0].x, face.faceContour[0].y)
                for (i in 1 until face.faceContour.size) {
                    lineTo(face.faceContour[i].x, face.faceContour[i].y)
                }
                close()
            } else {
                addRect(face.faceBounds, Path.Direction.CW)
            }
        }

        val bounds = RectF()
        facePath.computeBounds(bounds, true)
        val region = Region()
        region.setPath(facePath, Region(
            bounds.left.toInt().coerceIn(0, width - 1),
            bounds.top.toInt().coerceIn(0, height - 1),
            (bounds.right.toInt() + 1).coerceIn(1, width),
            (bounds.bottom.toInt() + 1).coerceIn(1, height)
        ))

        val pixels = IntArray(width * height)
        result.getPixels(pixels, 0, width, 0, 0, width, height)

        // 扫描脸部区域，检测痘痘
        val repairRadius = 4 // 修复半径
        val detections = mutableListOf<PointF>()

        val left = bounds.left.toInt().coerceIn(0, width - 1)
        val top = bounds.top.toInt().coerceIn(0, height - 1)
        val right = bounds.right.toInt().coerceIn(0, width - 1)
        val bottom = bounds.bottom.toInt().coerceIn(0, height - 1)

        for (y in top..bottom) {
            for (x in left..right) {
                if (!region.contains(x, y)) continue

                val pixel = pixels[y * width + x]
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)

                // 痘痘特征：红色偏高，且比周围暗
                val isRedSpot = r > g + 25 && r > b + 25 && r > 80
                // 暗色斑点
                val brightness = (r + g + b) / 3f
                val isDarkSpot = brightness < 60

                if (isRedSpot || isDarkSpot) {
                    // 二次验证：与周围平均颜色的差异
                    val neighborAvg = getNeighborAverage(pixels, width, height, x, y, 6)
                    val diffR = abs(r - neighborAvg[0])
                    val diffG = abs(g - neighborAvg[1])
                    val diffB = abs(b - neighborAvg[2])
                    val totalDiff = diffR + diffG + diffB

                    if (totalDiff > 40) {
                        detections.add(PointF(x.toFloat(), y.toFloat()))
                    }
                }
            }
        }

        // 对每个检测到的痘痘进行修复
        for (spot in detections) {
            val sx = spot.x.toInt()
            val sy = spot.y.toInt()
            inpaintRegion(pixels, width, height, sx, sy, repairRadius, strength)
        }

        result.setPixels(pixels, 0, width, 0, 0, width, height)
        return result
    }

    // ═══════════════════════════════════════════════════════════════
    // 祛斑
    // ═══════════════════════════════════════════════════════════════

    /**
     * 祛斑 — 检测色斑（局部颜色方差大的区域）并平滑
     *
     * 算法：在脸部区域分块计算颜色方差，方差大的区域为色斑，
     * 使用局部均值替换 + 边缘保留滤波。
     */
    fun removeSpots(bitmap: Bitmap, face: FaceData, intensity: Int): Bitmap {
        val strength = intensity / 100f
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val width = result.width
        val height = result.height

        val pixels = IntArray(width * height)
        result.getPixels(pixels, 0, width, 0, 0, width, height)

        // 在脸部区域内分块处理
        val blockSize = 8
        val bounds = face.faceBounds

        val startX = bounds.left.toInt().coerceIn(0, width - 1)
        val startY = bounds.top.toInt().coerceIn(0, height - 1)
        val endX = bounds.right.toInt().coerceIn(0, width - 1)
        val endY = bounds.bottom.toInt().coerceIn(0, height - 1)

        for (by in startY..endY step blockSize) {
            for (bx in startX..endX step blockSize) {
                // 计算块内颜色统计
                var count = 0
                var sumR = 0L
                var sumG = 0L
                var sumB = 0L
                val blockColors = mutableListOf<IntArray>()

                for (y in by until (by + blockSize).coerceAtMost(endY + 1)) {
                    for (x in bx until (bx + blockSize).coerceAtMost(endX + 1)) {
                        val pixel = pixels[y * width + x]
                        val r = Color.red(pixel)
                        val g = Color.green(pixel)
                        val b = Color.blue(pixel)
                        sumR += r
                        sumG += g
                        sumB += b
                        blockColors.add(intArrayOf(r, g, b, x, y))
                        count++
                    }
                }

                if (count == 0) continue

                val avgR = sumR / count
                val avgG = sumG / count
                val avgB = sumB / count

                // 计算方差
                var varSum = 0L
                for (c in blockColors) {
                    val dr = c[0] - avgR
                    val dg = c[1] - avgG
                    val db = c[2] - avgB
                    varSum += (dr * dr + dg * dg + db * db).toLong()
                }
                val variance = varSum / count

                // 方差大 = 有色斑
                if (variance > 300) {
                    // 平滑该块：向均值靠拢
                    for (c in blockColors) {
                        val x = c[3]
                        val y = c[4]
                        val idx = y * width + x
                        val pixel = pixels[idx]
                        val r = Color.red(pixel)
                        val g = Color.green(pixel)
                        val b = Color.blue(pixel)

                        // 混合因子：强度 * 与均值的差异比例
                        val diffRatio = (abs(r - avgR) + abs(g - avgG) + abs(b - avgB)) / 765f
                        val blend = strength * diffRatio.coerceAtMost(1f)

                        val newR = (r * (1 - blend) + avgR * blend).toInt().coerceIn(0, 255)
                        val newG = (g * (1 - blend) + avgG * blend).toInt().coerceIn(0, 255)
                        val newB = (b * (1 - blend) + avgB * blend).toInt().coerceIn(0, 255)
                        pixels[idx] = Color.argb(Color.alpha(pixel), newR, newG, newB)
                    }
                }
            }
        }

        result.setPixels(pixels, 0, width, 0, 0, width, height)
        return result
    }

    // ═══════════════════════════════════════════════════════════════
    // 祛黑眼圈
    // ═══════════════════════════════════════════════════════════════

    /**
     * 祛黑眼圈 — 检测眼下方暗区并提亮
     *
     * 算法：在眼睛下方矩形区域内，检测比周围皮肤更暗的像素，
     * 使用 HSL 空间提亮 + 颜色修正（减少青蓝色调）。
     */
    fun removeDarkCircles(bitmap: Bitmap, face: FaceData, intensity: Int): Bitmap {
        val strength = intensity / 100f
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val width = result.width
        val height = result.height

        val pixels = IntArray(width * height)
        result.getPixels(pixels, 0, width, 0, 0, width, height)

        // 左眼下方区域
        processUnderEyeRegion(pixels, width, height, face.leftEyeCenter, face.leftEyeContour, face, strength)
        // 右眼下方区域
        processUnderEyeRegion(pixels, width, height, face.rightEyeCenter, face.rightEyeContour, face, strength)

        result.setPixels(pixels, 0, width, 0, 0, width, height)
        return result
    }

    private fun processUnderEyeRegion(
        pixels: IntArray,
        width: Int,
        height: Int,
        eyeCenter: PointF,
        eyeContour: List<PointF>,
        face: FaceData,
        strength: Float
    ) {
        if (eyeContour.size < 4) return

        // 眼睛下方区域：从眼睛底部到眼睛下方 0.4 倍眼距
        val eyeHeight = eyeContour.maxOfOrNull { it.y } ?: eyeCenter.y
        val regionTop = eyeHeight.toInt()
        val regionBottom = (eyeHeight + face.interocularDistance * 0.4f).toInt()
        val regionLeft = (eyeCenter.x - face.interocularDistance * 0.25f).toInt()
        val regionRight = (eyeCenter.x + face.interocularDistance * 0.25f).toInt()

        // 计算该区域的平均肤色（排除最暗的 30%）
        val samples = mutableListOf<IntArray>()
        for (y in regionTop..regionBottom.coerceAtMost(height - 1)) {
            for (x in regionLeft.coerceAtLeast(0)..regionRight.coerceAtMost(width - 1)) {
                val pixel = pixels[y * width + x]
                samples.add(intArrayOf(Color.red(pixel), Color.green(pixel), Color.blue(pixel)))
            }
        }
        if (samples.isEmpty()) return

        // 排序按亮度，取上 70% 作为肤色基准
        samples.sortBy { (it[0] + it[1] + it[2]) }
        val skinSamples = samples.subList((samples.size * 0.3).toInt(), samples.size)
        val avgR = skinSamples.map { it[0] }.average().toInt()
        val avgG = skinSamples.map { it[1] }.average().toInt()
        val avgB = skinSamples.map { it[2] }.average().toInt()

        // 处理每个像素：暗于平均肤色的提亮
        for (y in regionTop..regionBottom.coerceAtMost(height - 1)) {
            for (x in regionLeft.coerceAtLeast(0)..regionRight.coerceAtMost(width - 1)) {
                val idx = y * width + x
                val pixel = pixels[idx]
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)
                val brightness = (r + g + b) / 3f
                val avgBrightness = (avgR + avgG + avgB) / 3f

                // 如果该像素比平均肤色暗
                if (brightness < avgBrightness * 0.85f) {
                    // 提亮因子
                    val darknessRatio = 1f - brightness / avgBrightness
                    val lift = strength * darknessRatio

                    // 向平均肤色靠拢
                    val newR = (r + (avgR - r) * lift).toInt().coerceIn(0, 255)
                    val newG = (g + (avgG - g) * lift).toInt().coerceIn(0, 255)
                    val newB = (b + (avgB - b) * lift).toInt().coerceIn(0, 255)
                    pixels[idx] = Color.argb(Color.alpha(pixel), newR, newG, newB)
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 均匀肤色
    // ═══════════════════════════════════════════════════════════════

    /**
     * 均匀肤色 — 全脸肤色均匀化，减少局部色差
     *
     * 算法：计算全脸平均肤色，对偏离均值的像素进行部分修正。
     * 使用保边平滑（类似表面模糊），只平滑色差小但存在的不均匀。
     */
    fun brightenSkinTone(bitmap: Bitmap, face: FaceData, intensity: Int): Bitmap {
        val strength = intensity / 100f
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val width = result.width
        val height = result.height

        val pixels = IntArray(width * height)
        result.getPixels(pixels, 0, width, 0, 0, width, height)

        // 构建脸部区域
        val facePath = Path().apply {
            if (face.faceContour.isNotEmpty()) {
                moveTo(face.faceContour[0].x, face.faceContour[0].y)
                for (i in 1 until face.faceContour.size) {
                    lineTo(face.faceContour[i].x, face.faceContour[i].y)
                }
                close()
            } else {
                addRect(face.faceBounds, Path.Direction.CW)
            }
        }

        val bounds = RectF()
        facePath.computeBounds(bounds, true)
        val region = Region()
        region.setPath(facePath, Region(
            bounds.left.toInt().coerceIn(0, width - 1),
            bounds.top.toInt().coerceIn(0, height - 1),
            (bounds.right.toInt() + 1).coerceIn(1, width),
            (bounds.bottom.toInt() + 1).coerceIn(1, height)
        ))

        // 1. 计算脸部平均肤色
        var sumR = 0L
        var sumG = 0L
        var sumB = 0L
        var count = 0L

        for (y in bounds.top.toInt()..bounds.bottom.toInt().coerceAtMost(height - 1)) {
            for (x in bounds.left.toInt()..bounds.right.toInt().coerceAtMost(width - 1)) {
                if (region.contains(x, y)) {
                    val pixel = pixels[y * width + x]
                    sumR += Color.red(pixel)
                    sumG += Color.green(pixel)
                    sumB += Color.blue(pixel)
                    count++
                }
            }
        }

        if (count == 0L) return result

        val avgR = (sumR / count).toInt()
        val avgG = (sumG / count).toInt()
        val avgB = (sumB / count).toInt()

        // 2. 表面模糊：对脸部每个像素，与周围像素做加权平均（只混合色差小于阈值的）
        val radius = 3
        val threshold = 40 // 色差阈值

        val copy = pixels.copyOf()

        for (y in bounds.top.toInt()..bounds.bottom.toInt().coerceAtMost(height - 1)) {
            for (x in bounds.left.toInt()..bounds.right.toInt().coerceAtMost(width - 1)) {
                if (!region.contains(x, y)) continue

                val centerPixel = copy[y * width + x]
                val cr = Color.red(centerPixel)
                val cg = Color.green(centerPixel)
                val cb = Color.blue(centerPixel)

                var sumR2 = 0L
                var sumG2 = 0L
                var sumB2 = 0L
                var weightSum = 0L

                for (dy in -radius..radius) {
                    for (dx in -radius..radius) {
                        val nx = x + dx
                        val ny = y + dy
                        if (nx < 0 || nx >= width || ny < 0 || ny >= height) continue
                        if (!region.contains(nx, ny)) continue

                        val np = copy[ny * width + nx]
                        val nr = Color.red(np)
                        val ng = Color.green(np)
                        val nb = Color.blue(np)

                        val colorDist = abs(nr - cr) + abs(ng - cg) + abs(nb - cb)
                        if (colorDist < threshold) {
                            val weight = (threshold - colorDist).toLong()
                            sumR2 += nr * weight
                            sumG2 += ng * weight
                            sumB2 += nb * weight
                            weightSum += weight
                        }
                    }
                }

                if (weightSum > 0) {
                    val blend = strength * 0.6f // 不过度平滑
                    val newR = (cr * (1 - blend) + (sumR2 / weightSum) * blend).toInt().coerceIn(0, 255)
                    val newG = (cg * (1 - blend) + (sumG2 / weightSum) * blend).toInt().coerceIn(0, 255)
                    val newB = (cb * (1 - blend) + (sumB2 / weightSum) * blend).toInt().coerceIn(0, 255)
                    pixels[y * width + x] = Color.argb(Color.alpha(centerPixel), newR, newG, newB)
                }
            }
        }

        result.setPixels(pixels, 0, width, 0, 0, width, height)
        return result
    }

    // ═══════════════════════════════════════════════════════════════
    // 辅助函数
    // ═══════════════════════════════════════════════════════════════

    /** 获取周围像素的平均颜色 */
    private fun getNeighborAverage(
        pixels: IntArray,
        width: Int,
        height: Int,
        x: Int,
        y: Int,
        radius: Int
    ): IntArray {
        var sumR = 0L
        var sumG = 0L
        var sumB = 0L
        var count = 0

        for (dy in -radius..radius) {
            for (dx in -radius..radius) {
                if (dx == 0 && dy == 0) continue
                val nx = x + dx
                val ny = y + dy
                if (nx < 0 || nx >= width || ny < 0 || ny >= height) continue

                val pixel = pixels[ny * width + nx]
                sumR += Color.red(pixel)
                sumG += Color.green(pixel)
                sumB += Color.blue(pixel)
                count++
            }
        }

        if (count == 0) return intArrayOf(128, 128, 128)
        return intArrayOf((sumR / count).toInt(), (sumG / count).toInt(), (sumB / count).toInt())
    }

    /**
     * 图像修复：用周围像素的加权平均替换中心区域
     */
    private fun inpaintRegion(
        pixels: IntArray,
        width: Int,
        height: Int,
        cx: Int,
        cy: Int,
        radius: Int,
        strength: Float
    ) {
        // 收集修复区域外围的像素作为参考
        var sumR = 0L
        var sumG = 0L
        var sumB = 0L
        var count = 0

        // 采样环：radius 到 radius+2 之间的像素
        for (dy in -(radius + 2)..(radius + 2)) {
            for (dx in -(radius + 2)..(radius + 2)) {
                val dist = sqrt((dx * dx + dy * dy).toFloat())
                if (dist > radius && dist <= radius + 2) {
                    val nx = cx + dx
                    val ny = cy + dy
                    if (nx < 0 || nx >= width || ny < 0 || ny >= height) continue
                    val pixel = pixels[ny * width + nx]
                    sumR += Color.red(pixel)
                    sumG += Color.green(pixel)
                    sumB += Color.blue(pixel)
                    count++
                }
            }
        }

        if (count == 0) return

        val avgR = (sumR / count).toInt()
        val avgG = (sumG / count).toInt()
        val avgB = (sumB / count).toInt()

        // 用平均色替换修复区域内的像素（带强度混合）
        for (dy in -radius..radius) {
            for (dx in -radius..radius) {
                val dist = sqrt((dx * dx + dy * dy).toFloat())
                if (dist <= radius) {
                    val nx = cx + dx
                    val ny = cy + dy
                    if (nx < 0 || nx >= width || ny < 0 || ny >= height) continue

                    val idx = ny * width + nx
                    val pixel = pixels[idx]
                    val r = Color.red(pixel)
                    val g = Color.green(pixel)
                    val b = Color.blue(pixel)

                    // 距离中心越近，替换程度越大
                    val t = (1f - dist / radius) * strength
                    val newR = (r * (1 - t) + avgR * t).toInt().coerceIn(0, 255)
                    val newG = (g * (1 - t) + avgG * t).toInt().coerceIn(0, 255)
                    val newB = (b * (1 - t) + avgB * t).toInt().coerceIn(0, 255)
                    pixels[idx] = Color.argb(Color.alpha(pixel), newR, newG, newB)
                }
            }
        }
    }
}
