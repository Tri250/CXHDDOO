package com.poseai.app.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
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
 * 美妆引擎
 *
 * 基于人脸关键点的虚拟美妆系统，支持：
 * - 口红（唇部上色 + 渐变过渡）
 * - 腮红（脸颊径向渐变）
 * - 眉毛（眉部填充）
 * - 眼影（眼上方渐变）
 * - 眼线（沿上睫毛线绘制）
 * - 睫毛（假睫毛效果）
 *
 * 所有美妆使用 Canvas + Path + Shader + PorterDuff 混合模式实现自然过渡。
 */
class MakeupEngine {

    /** 美妆参数 */
    data class MakeupParams(
        val lipstickColor: Int = 0xFFE8367F.toInt(),  // 口红色号
        val lipstickIntensity: Int = 0,                // 口红强度 0-100
        val blushColor: Int = 0xFFFF9999.toInt(),       // 腮红色号
        val blushIntensity: Int = 0,                    // 腮红强度 0-100
        val eyebrowColor: Int = 0xFF5C3A21.toInt(),     // 眉毛色号
        val eyebrowIntensity: Int = 0,                  // 眉毛强度 0-100
        val eyeshadowColor: Int = 0xFFB47B7B.toInt(),   // 眼影色号
        val eyeshadowIntensity: Int = 0,                // 眼影强度 0-100
        val eyelinerColor: Int = 0xFF1A1A1A.toInt(),    // 眼线色号
        val eyelinerIntensity: Int = 0,                 // 眼线强度 0-100
        val eyelashColor: Int = 0xFF1A1A1A.toInt(),     // 睫毛色号
        val eyelashIntensity: Int = 0                   // 睫毛强度 0-100
    )

    /**
     * 应用所有美妆
     */
    fun applyAll(bitmap: Bitmap, faceData: FaceData?, params: MakeupParams): Bitmap {
        if (faceData == null) return bitmap
        var result = bitmap.copy(Bitmap.Config.ARGB_8888, true)

        if (params.eyebrowIntensity > 0) {
            val temp = applyEyebrow(result, faceData, params.eyebrowColor, params.eyebrowIntensity)
            result.recycle()
            result = temp
        }
        if (params.eyeshadowIntensity > 0) {
            val temp = applyEyeshadow(result, faceData, params.eyeshadowColor, params.eyeshadowIntensity)
            result.recycle()
            result = temp
        }
        if (params.eyelinerIntensity > 0) {
            val temp = applyEyeliner(result, faceData, params.eyelinerColor, params.eyelinerIntensity)
            result.recycle()
            result = temp
        }
        if (params.eyelashIntensity > 0) {
            val temp = applyEyelashes(result, faceData, params.eyelashColor, params.eyelashIntensity)
            result.recycle()
            result = temp
        }
        if (params.blushIntensity > 0) {
            val temp = applyBlush(result, faceData, params.blushColor, params.blushIntensity)
            result.recycle()
            result = temp
        }
        if (params.lipstickIntensity > 0) {
            val temp = applyLipstick(result, faceData, params.lipstickColor, params.lipstickIntensity)
            result.recycle()
            result = temp
        }

        return result
    }

    // ═══════════════════════════════════════════════════════════════
    // 口红
    // ═══════════════════════════════════════════════════════════════

    /**
     * 口红 — 唇部上色，使用 Path 定义唇形 + 渐变混合
     */
    fun applyLipstick(bitmap: Bitmap, face: FaceData, color: Int, intensity: Int): Bitmap {
        val strength = intensity / 100f
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)

        // 构建完整唇形 Path（上唇 + 下唇）
        val lipPath = Path().apply {
            // 上唇上沿 → 嘴右角 → 下唇下沿 → 嘴左角 → 闭合
            if (face.upperLipTop.isNotEmpty()) {
                moveTo(face.upperLipTop[0].x, face.upperLipTop[0].y)
                for (i in 1 until face.upperLipTop.size) {
                    lineTo(face.upperLipTop[i].x, face.upperLipTop[i].y)
                }
            }
            // 连接到右嘴角
            lineTo(face.mouthRight.x, face.mouthRight.y)
            // 下唇下沿（反向）
            if (face.lowerLipBottom.isNotEmpty()) {
                for (i in face.lowerLipBottom.indices.reversed()) {
                    lineTo(face.lowerLipBottom[i].x, face.lowerLipBottom[i].y)
                }
            }
            // 连接到左嘴角
            lineTo(face.mouthLeft.x, face.mouthLeft.y)
            close()
        }

        // 嘴唇区域边界
        val lipBounds = RectF()
        lipPath.computeBounds(lipBounds, true)

        // 创建渐变 Shader：中心更浓，边缘渐淡
        val centerX = lipBounds.centerX()
        val centerY = lipBounds.centerY()
        val radius = sqrt(lipBounds.width() * lipBounds.width() + lipBounds.height() * lipBounds.height()) / 2f

        val alpha = (strength * 200).toInt().coerceIn(0, 255)
        val centerColor = Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
        val edgeColor = Color.argb((alpha * 0.6f).toInt(), Color.red(color), Color.green(color), Color.blue(color))

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                centerX, centerY, radius.coerceAtLeast(1f),
                intArrayOf(centerColor, edgeColor),
                floatArrayOf(0f, 1f),
                Shader.TileMode.CLAMP
            )
            xfermode = PorterDuffXfermode(PorterDuff.Mode.MULTIPLY)
        }

        // 裁剪到唇形区域
        canvas.save()
        canvas.clipPath(lipPath)
        canvas.drawRect(lipBounds, paint)
        canvas.restore()

        // 叠加一层柔光，让颜色更自然
        val softPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = Color.argb((strength * 60).toInt(), Color.red(color), Color.green(color), Color.blue(color))
            xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
        }
        canvas.save()
        canvas.clipPath(lipPath)
        canvas.drawRect(lipBounds, softPaint)
        canvas.restore()

        return result
    }

    // ═══════════════════════════════════════════════════════════════
    // 腮红
    // ═══════════════════════════════════════════════════════════════

    /**
     * 腮红 — 脸颊区域径向渐变，自然晕染
     */
    fun applyBlush(bitmap: Bitmap, face: FaceData, color: Int, intensity: Int): Bitmap {
        val strength = intensity / 100f
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)

        // 腮红半径 = 脸宽的 0.12 倍
        val blushRadius = face.faceWidth * 0.12f
        // 腮红位置：眼睛下方略偏外侧
        val leftBlushCenter = PointF(
            face.leftEyeCenter.x - face.interocularDistance * 0.15f,
            face.leftEyeCenter.y + face.interocularDistance * 0.35f
        )
        val rightBlushCenter = PointF(
            face.rightEyeCenter.x + face.interocularDistance * 0.15f,
            face.rightEyeCenter.y + face.interocularDistance * 0.35f
        )

        val alpha = (strength * 120).toInt().coerceIn(0, 255)
        val centerColor = Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
        val edgeColor = Color.argb(0, Color.red(color), Color.green(color), Color.blue(color))

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                0f, 0f, blushRadius,
                intArrayOf(centerColor, edgeColor),
                floatArrayOf(0f, 1f),
                Shader.TileMode.CLAMP
            )
            xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
        }

        // 左腮红
        canvas.save()
        canvas.translate(leftBlushCenter.x, leftBlushCenter.y)
        canvas.drawCircle(0f, 0f, blushRadius, paint)
        canvas.restore()

        // 右腮红
        canvas.save()
        canvas.translate(rightBlushCenter.x, rightBlushCenter.y)
        canvas.drawCircle(0f, 0f, blushRadius, paint)
        canvas.restore()

        return result
    }

    // ═══════════════════════════════════════════════════════════════
    // 眉毛
    // ═══════════════════════════════════════════════════════════════

    /**
     * 眉毛填充 — 沿眉部轮廓填充颜色
     */
    fun applyEyebrow(bitmap: Bitmap, face: FaceData, color: Int, intensity: Int): Bitmap {
        val strength = intensity / 100f
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)

        val alpha = (strength * 150).toInt().coerceIn(0, 255)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
            style = Paint.Style.FILL
            xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_OVER)
        }

        // 左眉
        drawEyebrowPath(canvas, face.leftEyebrowContour, paint)
        // 右眉
        drawEyebrowPath(canvas, face.rightEyebrowContour, paint)

        return result
    }

    private fun drawEyebrowPath(canvas: Canvas, contour: List<android.graphics.PointF>, paint: Paint) {
        if (contour.size < 2) return
        val path = Path().apply {
            moveTo(contour[0].x, contour[0].y)
            for (i in 1 until contour.size) {
                lineTo(contour[i].x, contour[i].y)
            }
            // 闭合：沿底部返回
            for (i in contour.indices.reversed()) {
                val p = contour[i]
                lineTo(p.x, p.y + 4f) // 向下偏移 4px 形成眉体厚度
            }
            close()
        }
        canvas.drawPath(path, paint)
    }

    // ═══════════════════════════════════════════════════════════════
    // 眼影
    // ═══════════════════════════════════════════════════════════════

    /**
     * 眼影 — 眼睛上方渐变区域
     */
    fun applyEyeshadow(bitmap: Bitmap, face: FaceData, color: Int, intensity: Int): Bitmap {
        val strength = intensity / 100f
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)

        // 眼影区域：眉毛到眼睛之间
        applyEyeshadowSingle(canvas, face.leftEyeCenter, face.leftEyebrowTop, color, strength, face)
        applyEyeshadowSingle(canvas, face.rightEyeCenter, face.rightEyebrowTop, color, strength, face)

        return result
    }

    private fun applyEyeshadowSingle(
        canvas: Canvas,
        eyeCenter: android.graphics.PointF,
        eyebrowTop: android.graphics.PointF,
        color: Int,
        strength: Float,
        face: FaceData
    ) {
        val eyeWidth = face.interocularDistance * 0.4f
        val eyeHeight = (eyebrowTop.y - eyeCenter.y).coerceAtLeast(eyeWidth * 0.3f)
        val shadowRect = RectF(
            eyeCenter.x - eyeWidth * 0.6f,
            eyeCenter.y - eyeHeight * 0.8f,
            eyeCenter.x + eyeWidth * 0.6f,
            eyeCenter.y
        )

        val alpha = (strength * 100).toInt().coerceIn(0, 255)
        val topColor = Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
        val bottomColor = Color.argb(0, Color.red(color), Color.green(color), Color.blue(color))

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                shadowRect.left, shadowRect.top,
                shadowRect.left, shadowRect.bottom,
                intArrayOf(topColor, bottomColor),
                floatArrayOf(0f, 1f),
                Shader.TileMode.CLAMP
            )
            xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
        }

        // 椭圆形眼影
        canvas.save()
        canvas.clipPath(Path().apply {
            addOval(shadowRect, Path.Direction.CW)
        })
        canvas.drawRect(shadowRect, paint)
        canvas.restore()
    }

    // ═══════════════════════════════════════════════════════════════
    // 眼线
    // ═══════════════════════════════════════════════════════════════

    /**
     * 眼线 — 沿上睫毛线绘制
     */
    fun applyEyeliner(bitmap: Bitmap, face: FaceData, color: Int, intensity: Int): Bitmap {
        val strength = intensity / 100f
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)

        val alpha = (strength * 220).toInt().coerceIn(0, 255)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        // 左眼眼线
        drawEyelinerForEye(canvas, face.leftEyeContour, paint, face, isLeft = true)
        // 右眼眼线
        drawEyelinerForEye(canvas, face.rightEyeContour, paint, face, isLeft = false)

        return result
    }

    private fun drawEyelinerForEye(
        canvas: Canvas,
        eyeContour: List<android.graphics.PointF>,
        paint: Paint,
        face: FaceData,
        isLeft: Boolean
    ) {
        if (eyeContour.size < 6) return

        // 上眼睑 = 轮廓中 y 最小的一半点
        val upperLid = eyeContour.sortedBy { it.y }.take(eyeContour.size / 2)
        // 按 x 排序形成从左到右的曲线
        val sortedUpper = upperLid.sortedBy { it.x }

        // 线宽随强度变化
        paint.strokeWidth = 1.5f + 2.5f * (paint.alpha / 220f)

        // 绘制上眼线（沿上睫毛线）
        val path = Path().apply {
            if (sortedUpper.isNotEmpty()) {
                moveTo(sortedUpper[0].x, sortedUpper[0].y)
                for (i in 1 until sortedUpper.size) {
                    // 稍微上移 1px 模拟眼线贴着睫毛上方
                    val p = sortedUpper[i]
                    lineTo(p.x, p.y - 1f)
                }
                // 眼尾上扬
                val last = sortedUpper.last()
                val tailLen = face.interocularDistance * 0.08f
                lineTo(last.x + tailLen, last.y - tailLen * 0.5f - 1f)
            }
        }
        canvas.drawPath(path, paint)
    }

    // ═══════════════════════════════════════════════════════════════
    // 睫毛
    // ═══════════════════════════════════════════════════════════════

    /**
     * 假睫毛 — 在上眼睑绘制放射状线条模拟睫毛
     */
    fun applyEyelashes(bitmap: Bitmap, face: FaceData, color: Int, intensity: Int): Bitmap {
        val strength = intensity / 100f
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)

        val alpha = (strength * 200).toInt().coerceIn(0, 255)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeWidth = 1.2f
        }

        drawEyelashesForEye(canvas, face.leftEyeContour, paint, face)
        drawEyelashesForEye(canvas, face.rightEyeContour, paint, face)

        return result
    }

    private fun drawEyelashesForEye(
        canvas: Canvas,
        eyeContour: List<android.graphics.PointF>,
        paint: Paint,
        face: FaceData
    ) {
        if (eyeContour.size < 6) return

        // 上眼睑
        val upperLid = eyeContour.sortedBy { it.y }.take(eyeContour.size / 2).sortedBy { it.x }
        if (upperLid.size < 3) return

        // 睫毛长度
        val lashLength = face.interocularDistance * 0.06f * (paint.alpha / 200f)
        // 睫毛数量
        val lashCount = 12

        for (i in 0 until lashCount) {
            val t = i.toFloat() / (lashCount - 1)
            val idx = (t * (upperLid.size - 1)).toInt()
            val p = upperLid[idx]

            // 睫毛方向：向上外侧
            val angle = -90f + (t - 0.5f) * 60f // 从 -120° 到 -60°
            val rad = Math.toRadians(angle.toDouble())
            val endX = (p.x + cos(rad) * lashLength).toFloat()
            val endY = (p.y + sin(rad) * lashLength).toFloat()

            // 稍微弯曲的睫毛
            val ctrlX = (p.x + endX) / 2f + cos(rad).toFloat() * lashLength * 0.2f
            val ctrlY = (p.y + endY) / 2f + sin(rad).toFloat() * lashLength * 0.2f

            val path = Path().apply {
                moveTo(p.x, p.y - 1f)
                quadTo(ctrlX, ctrlY, endX, endY)
            }
            canvas.drawPath(path, paint)
        }
    }
}
