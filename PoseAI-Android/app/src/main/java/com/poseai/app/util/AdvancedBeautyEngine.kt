package com.poseai.app.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import com.poseai.app.engine.FaceLandmarkDetector.FaceData
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 高级美颜引擎
 *
 * 基于人脸关键点的精准美颜变形系统，支持：
 * - 大眼（径向放大）
 * - 瘦鼻（鼻翼内推）
 * - 下巴（缩短/收紧）
 * - 额头（放大/缩小）
 * - 颧骨（收敛）
 * - 下颌线（V脸瘦脸）
 * - 整体瘦脸
 * - 亮眼（局部提亮+饱和度）
 * - 白牙（牙齿区域增白）
 *
 * 所有变形算法使用网格扭曲（Mesh Warp）+ 径向变形函数，
 * 保证自然过渡、无撕裂。
 */
class AdvancedBeautyEngine {

    /** 高级美颜参数（每项 0-100） */
    data class BeautyParams(
        val enlargeEyes: Int = 0,       // 大眼
        val slimNose: Int = 0,          // 瘦鼻
        val shrinkChin: Int = 0,        // 缩下巴
        val enlargeForehead: Int = 0,   // 额头
        val slimCheekbone: Int = 0,     // 颧骨
        val slimJawline: Int = 0,       // 下颌线
        val slimFace: Int = 0,          // 整体瘦脸
        val brightenEyes: Int = 0,      // 亮眼
        val whitenTeeth: Int = 0        // 白牙
    )

    // ═══════════════════════════════════════════════════════════════
    // 综合应用
    // ═══════════════════════════════════════════════════════════════

    /**
     * 应用所有高级美颜效果
     */
    fun applyAll(bitmap: Bitmap, faceData: FaceData?, params: BeautyParams): Bitmap {
        if (faceData == null) return bitmap
        var result = bitmap.copy(Bitmap.Config.ARGB_8888, true)

        // 变形类效果（按顺序执行）— 每步回收旧 Bitmap 避免 OOM
        if (params.enlargeEyes > 0) {
            val temp = enlargeEyes(result, faceData, params.enlargeEyes)
            result.recycle()
            result = temp
        }
        if (params.slimNose > 0) {
            val temp = slimNose(result, faceData, params.slimNose)
            result.recycle()
            result = temp
        }
        if (params.shrinkChin > 0) {
            val temp = shrinkChin(result, faceData, params.shrinkChin)
            result.recycle()
            result = temp
        }
        if (params.enlargeForehead > 0) {
            val temp = enlargeForehead(result, faceData, params.enlargeForehead)
            result.recycle()
            result = temp
        }
        if (params.slimCheekbone > 0) {
            val temp = slimCheekbone(result, faceData, params.slimCheekbone)
            result.recycle()
            result = temp
        }
        if (params.slimJawline > 0) {
            val temp = slimJawline(result, faceData, params.slimJawline)
            result.recycle()
            result = temp
        }
        if (params.slimFace > 0) {
            val temp = slimFace(result, faceData, params.slimFace)
            result.recycle()
            result = temp
        }

        // 颜色类效果
        if (params.brightenEyes > 0) {
            val temp = brightenEyes(result, faceData, params.brightenEyes)
            result.recycle()
            result = temp
        }
        if (params.whitenTeeth > 0) {
            val temp = whitenTeeth(result, faceData, params.whitenTeeth)
            result.recycle()
            result = temp
        }

        return result
    }

    // ═══════════════════════════════════════════════════════════════
    // 变形类美颜
    // ═══════════════════════════════════════════════════════════════

    /**
     * 大眼 — 以眼睛中心为圆心的径向放大
     *
     * @param intensity 0-100，控制放大半径与强度
     */
    fun enlargeEyes(bitmap: Bitmap, face: FaceData, intensity: Int): Bitmap {
        val strength = intensity / 100f
        // 眼睛放大半径 = 瞳距的 0.4 倍
        val radius = face.interocularDistance * 0.4f
        // 最大放大系数
        val maxScale = 1f + 0.35f * strength

        val leftResult = radialWarp(bitmap, face.leftEyeCenter, radius, maxScale, true)
        val rightResult = radialWarp(leftResult, face.rightEyeCenter, radius, maxScale, true)
        leftResult.recycle()
        return rightResult
    }

    /**
     * 瘦鼻 — 鼻翼两侧向内推
     */
    fun slimNose(bitmap: Bitmap, face: FaceData, intensity: Int): Bitmap {
        val strength = intensity / 100f
        // 鼻翼内推距离 = 鼻宽 * 0.15 * strength
        val noseWidth = distance(face.noseLeft, face.noseRight)
        val pushDist = noseWidth * 0.15f * strength
        val warpRadius = noseWidth * 0.8f

        // 左鼻翼向右推
        var result = radialWarp(bitmap, face.noseLeft, warpRadius, 1f - pushDist / warpRadius, false)
        // 右鼻翼向左推
        val temp = radialWarp(result, face.noseRight, warpRadius, 1f - pushDist / warpRadius, false)
        result.recycle()
        return temp
    }

    /**
     * 缩下巴 — 下巴底部向上推
     */
    fun shrinkChin(bitmap: Bitmap, face: FaceData, intensity: Int): Bitmap {
        val strength = intensity / 100f
        val chinPoint = PointF(face.faceCenter.x, face.faceBounds.bottom)
        val faceHeight = face.faceHeight
        val radius = faceHeight * 0.3f
        // 下巴向上压缩
        val compressFactor = 1f - 0.12f * strength

        return radialWarp(bitmap, chinPoint, radius, compressFactor, false)
    }

    /**
     * 额头 — 微调额头大小
     */
    fun enlargeForehead(bitmap: Bitmap, face: FaceData, intensity: Int): Bitmap {
        val strength = intensity / 100f
        val foreheadPoint = PointF(face.faceCenter.x, face.faceBounds.top + face.faceHeight * 0.1f)
        val radius = face.faceHeight * 0.25f
        val scale = 1f + 0.08f * strength

        return radialWarp(bitmap, foreheadPoint, radius, scale, true)
    }

    /**
     * 颧骨收敛 — 颧骨区域向内推
     */
    fun slimCheekbone(bitmap: Bitmap, face: FaceData, intensity: Int): Bitmap {
        val strength = intensity / 100f
        val cheekRadius = face.faceWidth * 0.2f
        val pushAmount = face.faceWidth * 0.08f * strength

        // 左颧骨向中心推
        var result = directionalWarp(
            bitmap,
            face.leftCheek,
            face.faceCenter,
            cheekRadius,
            pushAmount
        )
        // 右颧骨向中心推
        val temp = directionalWarp(
            result,
            face.rightCheek,
            face.faceCenter,
            cheekRadius,
            pushAmount
        )
        result.recycle()
        return temp
    }

    /**
     * 下颌线瘦脸 — 下颌角区域向内收
     */
    fun slimJawline(bitmap: Bitmap, face: FaceData, intensity: Int): Bitmap {
        val strength = intensity / 100f
        // 下颌角位置：脸部轮廓下半部分最外侧的点
        val lowerContour = face.faceContour.filter {
            it.y > face.faceCenter.y
        }
        if (lowerContour.size < 4) return bitmap

        val leftJaw = lowerContour.minByOrNull { it.x } ?: face.leftCheek
        val rightJaw = lowerContour.maxByOrNull { it.x } ?: face.rightCheek
        val jawRadius = face.faceWidth * 0.15f
        val pushAmount = face.faceWidth * 0.1f * strength

        var result = directionalWarp(bitmap, leftJaw, face.faceCenter, jawRadius, pushAmount)
        val temp = directionalWarp(result, rightJaw, face.faceCenter, jawRadius, pushAmount)
        result.recycle()
        return temp
    }

    /**
     * 整体瘦脸 — 全脸轮廓向中心收缩
     */
    fun slimFace(bitmap: Bitmap, face: FaceData, intensity: Int): Bitmap {
        val strength = intensity / 100f
        val radius = face.faceWidth * 0.6f
        // 瘦脸系数：从边缘向内推
        val scale = 1f - 0.15f * strength

        return radialWarp(bitmap, face.faceCenter, radius, scale, false)
    }

    // ═══════════════════════════════════════════════════════════════
    // 颜色类美颜
    // ═══════════════════════════════════════════════════════════════

    /**
     * 亮眼 — 眼睛区域提亮 + 增加饱和度
     */
    fun brightenEyes(bitmap: Bitmap, face: FaceData, intensity: Int): Bitmap {
        val strength = intensity / 100f
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)

        val eyeRadius = face.interocularDistance * 0.25f

        // 左眼提亮
        applyLocalBrightness(canvas, face.leftEyeCenter, eyeRadius, 0.3f * strength, 0.15f * strength)
        // 右眼提亮
        applyLocalBrightness(canvas, face.rightEyeCenter, eyeRadius, 0.3f * strength, 0.15f * strength)

        // 眼白增白：扫描眼睛区域像素，将白色部分增亮
        brightenSclera(result, face.leftEyeContour, strength)
        brightenSclera(result, face.rightEyeContour, strength)

        return result
    }

    /**
     * 白牙 — 牙齿区域增白
     */
    fun whitenTeeth(bitmap: Bitmap, face: FaceData, intensity: Int): Bitmap {
        val strength = intensity / 100f
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)

        // 牙齿区域 = 嘴唇内部
        val mouthRegion = RectF(
            face.mouthLeft.x,
            face.mouthCenter.y - (face.mouthBottom.y - face.mouthCenter.y) * 0.5f,
            face.mouthRight.x,
            face.mouthBottom.y
        )

        // 扫描嘴部区域，检测白色/偏黄像素（牙齿）并增白
        val pixels = IntArray(result.width * result.height)
        result.getPixels(pixels, 0, result.width, 0, 0, result.width, result.height)

        val left = mouthRegion.left.toInt().coerceIn(0, result.width - 1)
        val top = mouthRegion.top.toInt().coerceIn(0, result.height - 1)
        val right = mouthRegion.right.toInt().coerceIn(0, result.width - 1)
        val bottom = mouthRegion.bottom.toInt().coerceIn(0, result.height - 1)

        for (y in top..bottom) {
            for (x in left..right) {
                val idx = y * result.width + x
                val pixel = pixels[idx]
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)

                // 检测牙齿：亮度较高，R>G>B（偏黄白）
                val brightness = (r + g + b) / 3f
                val isToothLike = brightness > 100 && r > g && g > b * 0.8f

                if (isToothLike) {
                    // 增白：提高亮度，降低黄色
                    val newR = (r + (255 - r) * 0.5f * strength).toInt().coerceIn(0, 255)
                    val newG = (g + (255 - g) * 0.4f * strength).toInt().coerceIn(0, 255)
                    val newB = (b + (255 - b) * 0.6f * strength).toInt().coerceIn(0, 255)
                    pixels[idx] = Color.argb(Color.alpha(pixel), newR, newG, newB)
                }
            }
        }

        result.setPixels(pixels, 0, result.width, 0, 0, result.width, result.height)
        return result
    }

    // ═══════════════════════════════════════════════════════════════
    // 核心变形算法
    // ═══════════════════════════════════════════════════════════════

    /**
     * 径向变形：以中心点为圆心，在半径范围内进行放大或缩小
     *
     * @param center 变形中心
     * @param radius 变形影响半径
     * @param scale 变形系数：>1 放大，<1 缩小
     * @param expand true=向外推（放大），false=向内拉（缩小）
     */
    private fun radialWarp(
        bitmap: Bitmap,
        center: PointF,
        radius: Float,
        scale: Float,
        expand: Boolean
    ): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val cx = center.x
        val cy = center.y
        val r2 = radius * radius

        val srcPixels = IntArray(width * height)
        bitmap.getPixels(srcPixels, 0, width, 0, 0, width, height)
        val dstPixels = IntArray(width * height)

        for (y in 0 until height) {
            for (x in 0 until width) {
                val dx = x - cx
                val dy = y - cy
                val distSq = dx * dx + dy * dy

                if (distSq > r2) {
                    // 变形范围外，原样复制
                    dstPixels[y * width + x] = srcPixels[y * width + x]
                } else {
                    val dist = sqrt(distSq)
                    // 影响因子：中心为 1，边缘为 0
                    val t = 1f - dist / radius
                    val factor = t * t * (3f - 2f * t) // smoothstep

                    // 计算源像素位置
                    val warpScale = if (expand) {
                        // 放大：源像素从更靠近中心的位置取
                        1f - (scale - 1f) * factor
                    } else {
                        // 缩小：源像素从更远的位置取
                        1f + (1f - scale) * factor
                    }

                    val safeWarpScale = if (warpScale == 0f) 0.0001f else warpScale
                    val srcX = cx + dx * safeWarpScale
                    val srcY = cy + dy * safeWarpScale

                    // 双线性插值
                    dstPixels[y * width + x] = bilinearSample(srcPixels, width, height, srcX, srcY)
                }
            }
        }

        result.setPixels(dstPixels, 0, width, 0, 0, width, height)
        return result
    }

    /**
     * 方向变形：将某点向目标点方向推/拉
     *
     * @param source 变形起点
     * @param target 变形方向目标
     * @param radius 影响半径
     * @param amount 推/拉距离
     */
    private fun directionalWarp(
        bitmap: Bitmap,
        source: PointF,
        target: PointF,
        radius: Float,
        amount: Float
    ): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        // 变形方向（单位向量）
        val dirX = target.x - source.x
        val dirY = target.y - source.y
        val dirLen = sqrt(dirX * dirX + dirY * dirY)
        if (dirLen < 0.001f) return bitmap.copy(Bitmap.Config.ARGB_8888, true)

        val nx = dirX / dirLen
        val ny = dirY / dirLen
        // 推移向量
        val pushX = nx * amount
        val pushY = ny * amount

        val r2 = radius * radius
        val srcPixels = IntArray(width * height)
        bitmap.getPixels(srcPixels, 0, width, 0, 0, width, height)
        val dstPixels = IntArray(width * height)

        for (y in 0 until height) {
            for (x in 0 until width) {
                val dx = x - source.x
                val dy = y - source.y
                val distSq = dx * dx + dy * dy

                if (distSq > r2) {
                    dstPixels[y * width + x] = srcPixels[y * width + x]
                } else {
                    val dist = sqrt(distSq)
                    val t = 1f - dist / radius
                    val factor = t * t * (3f - 2f * t) // smoothstep

                    // 源像素位置 = 当前位置 + 推移量 * factor
                    val srcX = x - pushX * factor
                    val srcY = y - pushY * factor

                    dstPixels[y * width + x] = bilinearSample(srcPixels, width, height, srcX, srcY)
                }
            }
        }

        result.setPixels(dstPixels, 0, width, 0, 0, width, height)
        return result
    }

    /**
     * 局部提亮（使用径向渐变叠加）
     */
    private fun applyLocalBrightness(
        canvas: Canvas,
        center: PointF,
        radius: Float,
        brightness: Float,
        saturationBoost: Float
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                center.x, center.y, radius,
                intArrayOf(
                    Color.argb((brightness * 255).toInt(), 255, 255, 240),
                    Color.argb(0, 255, 255, 240)
                ),
                floatArrayOf(0f, 1f),
                Shader.TileMode.CLAMP
            )
            xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
        }
        canvas.drawCircle(center.x, center.y, radius, paint)
    }

    /**
     * 眼白增白：扫描眼轮廓内像素，将白色部分增亮
     */
    private fun brightenSclera(bitmap: Bitmap, eyeContour: List<PointF>, strength: Float) {
        if (eyeContour.size < 4) return

        // 计算眼睛边界
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var maxY = Float.MIN_VALUE
        for (p in eyeContour) {
            if (p.x < minX) minX = p.x
            if (p.x > maxX) maxX = p.x
            if (p.y < minY) minY = p.y
            if (p.y > maxY) maxY = p.y
        }

        val left = minX.toInt().coerceIn(0, bitmap.width - 1)
        val top = minY.toInt().coerceIn(0, bitmap.height - 1)
        val right = maxX.toInt().coerceIn(0, bitmap.width - 1)
        val bottom = maxY.toInt().coerceIn(0, bitmap.height - 1)

        // 构建 Path 用于判断点是否在眼内
        val eyePath = Path().apply {
            moveTo(eyeContour[0].x, eyeContour[0].y)
            for (i in 1 until eyeContour.size) {
                lineTo(eyeContour[i].x, eyeContour[i].y)
            }
            close()
        }
        val region = android.graphics.Region()
        val clip = android.graphics.Region(left, top, right + 1, bottom + 1)
        region.setPath(eyePath, clip)

        for (y in top..bottom) {
            for (x in left..right) {
                if (region.contains(x, y)) {
                    val pixel = bitmap.getPixel(x, y)
                    val r = Color.red(pixel)
                    val g = Color.green(pixel)
                    val b = Color.blue(pixel)
                    val brightness = (r + g + b) / 3f

                    // 眼白区域：亮度较高，偏白
                    if (brightness > 120) {
                        val newR = (r + (255 - r) * 0.3f * strength).toInt().coerceIn(0, 255)
                        val newG = (g + (255 - g) * 0.3f * strength).toInt().coerceIn(0, 255)
                        val newB = (b + (255 - b) * 0.4f * strength).toInt().coerceIn(0, 255)
                        bitmap.setPixel(x, y, Color.argb(Color.alpha(pixel), newR, newG, newB))
                    }
                }
            }
        }
    }

    /**
     * 双线性插值采样
     */
    private fun bilinearSample(
        pixels: IntArray,
        width: Int,
        height: Int,
        x: Float,
        y: Float
    ): Int {
        val x0 = x.toInt()
        val y0 = y.toInt()
        val x1 = (x0 + 1).coerceIn(0, width - 1)
        val y1 = (y0 + 1).coerceIn(0, height - 1)
        val tx = x - x0
        val ty = y - y0

        val safeX0 = x0.coerceIn(0, width - 1)
        val safeY0 = y0.coerceIn(0, height - 1)

        val p00 = pixels[safeY0 * width + safeX0]
        val p10 = pixels[safeY0 * width + x1]
        val p01 = pixels[y1 * width + safeX0]
        val p11 = pixels[y1 * width + x1]

        val r = bilinearChannel(p00, p10, p01, p11, tx, ty) { Color.red(it) }
        val g = bilinearChannel(p00, p10, p01, p11, tx, ty) { Color.green(it) }
        val b = bilinearChannel(p00, p10, p01, p11, tx, ty) { Color.blue(it) }
        val a = bilinearChannel(p00, p10, p01, p11, tx, ty) { Color.alpha(it) }

        return Color.argb(a, r, g, b)
    }

    private inline fun bilinearChannel(
        p00: Int, p10: Int, p01: Int, p11: Int,
        tx: Float, ty: Float,
        channel: (Int) -> Int
    ): Int {
        val c00 = channel(p00).toFloat()
        val c10 = channel(p10).toFloat()
        val c01 = channel(p01).toFloat()
        val c11 = channel(p11).toFloat()
        val top = c00 * (1 - tx) + c10 * tx
        val bottom = c01 * (1 - tx) + c11 * tx
        return (top * (1 - ty) + bottom * ty).toInt().coerceIn(0, 255)
    }

    private fun distance(a: PointF, b: PointF): Float {
        val dx = b.x - a.x
        val dy = b.y - a.y
        return sqrt(dx * dx + dy * dy)
    }
}
