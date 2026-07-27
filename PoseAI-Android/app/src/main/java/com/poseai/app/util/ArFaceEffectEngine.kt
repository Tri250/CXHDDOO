package com.poseai.app.util

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.graphics.PointF
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * AR 人脸特效引擎
 *
 * 基于人脸关键点数据，使用 Canvas 绘制各类 AR 特效（动物脸、头饰、面部装饰、动态特效、滤镜）。
 *
 * 设计要点：
 * - 所有特效均使用真实 Canvas 操作（drawPath / drawCircle / drawRect / drawLine / drawBitmap / Shader）。
 * - 动态特效使用 [System.currentTimeMillis] 驱动动画。
 * - 动态/滤镜类特效在无人脸时仍可全屏生效；动物/头饰/装饰类特效在无人脸时跳过。
 * - 混合模式使用 [PorterDuffXfermode]（兼容 minSdk 26）。
 */
class ArFaceEffectEngine {

    /**
     * 人脸关键点检测器占位类。
     *
     * 项目中完整的 [FaceLandmarkDetector] 实现可能尚未存在，此处提供精简版 [FaceData]，
     * 以保证 [ArFaceEffectEngine] 可独立编译。所有坐标均为位图像素坐标。
     */
    class FaceLandmarkDetector {
        data class FaceData(
            val leftEye: PointF,        // 左眼中心
            val rightEye: PointF,       // 右眼中心
            val noseBase: PointF,       // 鼻底
            val mouthLeft: PointF,      // 嘴左角
            val mouthRight: PointF,     // 嘴右角
            val mouthBottom: PointF,    // 下唇底
            val faceCenter: PointF,     // 脸部中心
            val faceWidth: Float,       // 脸宽（像素）
            val faceHeight: Float,      // 脸高（像素）
            val rollAngle: Float = 0f   // 脸部倾斜角（度），用于让特效跟随脸部旋转
        )
    }

    // ═══════════════════════════════════════════════════════════════
    // 公共 API
    // ═══════════════════════════════════════════════════════════════

    /**
     * 在位图上叠加单个 AR 特效。
     *
     * @param bitmap 原图
     * @param effect 特效类型
     * @param faceData 人脸关键点（动物/头饰/装饰类特效需要，动态/滤镜类可为 null）
     * @return 叠加特效后的新位图
     */
    fun applyEffect(
        bitmap: Bitmap,
        effect: ArEffect,
        faceData: FaceLandmarkDetector.FaceData?
    ): Bitmap {
        val result = Bitmap.createBitmap(
            bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(result)
        canvas.drawBitmap(bitmap, 0f, 0f, null)
        drawEffect(canvas, effect, faceData, bitmap.width, bitmap.height)
        return result
    }

    /**
     * 在位图上叠加多个 AR 特效（按列表顺序依次绘制）。
     */
    fun applyEffects(
        bitmap: Bitmap,
        effects: List<ArEffect>,
        faceData: FaceLandmarkDetector.FaceData?
    ): Bitmap {
        val result = Bitmap.createBitmap(
            bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(result)
        canvas.drawBitmap(bitmap, 0f, 0f, null)
        for (effect in effects) {
            drawEffect(canvas, effect, faceData, bitmap.width, bitmap.height)
        }
        return result
    }

    /**
     * 按分类获取可用特效列表。
     */
    fun getEffectsByCategory(category: String): List<ArEffect> {
        return ArEffect.values().filter { it.category == category }
    }

    // ═══════════════════════════════════════════════════════════════
    // 特效分发
    // ═══════════════════════════════════════════════════════════════

    private fun drawEffect(
        canvas: Canvas,
        effect: ArEffect,
        faceData: FaceLandmarkDetector.FaceData?,
        w: Int,
        h: Int
    ) {
        when (effect) {
            // 动物脸
            ArEffect.CAT_EARS -> faceData?.let { drawCatEars(canvas, it) }
            ArEffect.CAT_NOSE -> faceData?.let { drawCatNose(canvas, it) }
            ArEffect.DOG_NOSE -> faceData?.let { drawDogNose(canvas, it) }
            ArEffect.DOG_EARS -> faceData?.let { drawDogEars(canvas, it) }
            ArEffect.BUNNY_EARS -> faceData?.let { drawBunnyEars(canvas, it) }
            ArEffect.PANDA_EYES -> faceData?.let { drawPandaEyes(canvas, it) }
            ArEffect.FOX_EARS -> faceData?.let { drawFoxEars(canvas, it) }
            // 头饰
            ArEffect.CROWN -> faceData?.let { drawCrown(canvas, it) }
            ArEffect.FLOWER_CROWN -> faceData?.let { drawFlowerCrown(canvas, it) }
            ArEffect.HAT -> faceData?.let { drawHat(canvas, it) }
            ArEffect.CAP -> faceData?.let { drawCap(canvas, it) }
            ArEffect.HALO -> faceData?.let { drawHalo(canvas, it) }
            // 面部装饰
            ArEffect.GLASSES -> faceData?.let { drawGlasses(canvas, it) }
            ArEffect.SUNGLASSES -> faceData?.let { drawSunglasses(canvas, it) }
            ArEffect.MASK -> faceData?.let { drawMask(canvas, it) }
            ArEffect.BUTTERFLY -> faceData?.let { drawButterfly(canvas, it) }
            ArEffect.STAR -> faceData?.let { drawStarOnCheek(canvas, it) }
            // 动态特效（无需人脸）
            ArEffect.SPARKLE -> drawSparkle(canvas, w, h)
            ArEffect.HEART_RAIN -> drawHeartRain(canvas, w, h)
            ArEffect.PETAL_RAIN -> drawPetalRain(canvas, w, h)
            ArEffect.SNOW -> drawSnow(canvas, w, h)
            ArEffect.BUBBLES -> drawBubbles(canvas, w, h)
            ArEffect.FIREWORKS -> drawFireworks(canvas, w, h)
            ArEffect.LIGHTNING -> drawLightning(canvas, w, h)
            // 滤镜特效（无需人脸，全屏）
            ArEffect.RAINBOW -> drawRainbow(canvas, w, h)
            ArEffect.GALAXY -> drawGalaxy(canvas, w, h)
            ArEffect.AURORA -> drawAurora(canvas, w, h)
            ArEffect.NEON_GLOW -> drawNeonGlow(canvas, w, h, faceData)
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 人脸度量辅助
    // ═══════════════════════════════════════════════════════════════

    /** 从 [FaceLandmarkDetector.FaceData] 派生的常用度量，统一缩放基准。 */
    private class FaceMetrics(fd: FaceLandmarkDetector.FaceData) {
        val cx = fd.faceCenter.x
        val cy = fd.faceCenter.y
        val faceW = fd.faceWidth.coerceAtLeast(1f)
        val faceH = fd.faceHeight.coerceAtLeast(1f)
        val unit = (faceW / 4f).coerceAtLeast(1f)           // 基准单位（约一只眼睛宽度）
        val eyeDist = sqrt(
            (fd.rightEye.x - fd.leftEye.x).let { it * it } +
                (fd.rightEye.y - fd.leftEye.y).let { it * it }
        ).coerceAtLeast(1f)
        val leftEye = fd.leftEye
        val rightEye = fd.rightEye
        val nose = fd.noseBase
        val mouthLeft = fd.mouthLeft
        val mouthRight = fd.mouthRight
        val mouthBottom = fd.mouthBottom
        val foreheadY = cy - faceH * 0.45f                  // 额头上沿估算
        val crownY = cy - faceH * 0.55f                     // 头顶（皇冠/耳朵根部）
        val chinY = cy + faceH * 0.5f
        // 脸部朝向角度（弧度），由双眼连线估算
        val angleRad: Double = Math.atan2(
            (rightEye.y - leftEye.y).toDouble(),
            (rightEye.x - leftEye.x).toDouble()
        )
        val angleDeg: Float = Math.toDegrees(angleRad).toFloat()
    }

    // ═══════════════════════════════════════════════════════════════
    // 动物脸特效
    // ═══════════════════════════════════════════════════════════════

    /** 猫耳朵：头顶两个三角耳，含粉色内耳。 */
    private fun drawCatEars(canvas: Canvas, fd: FaceLandmarkDetector.FaceData) {
        val m = FaceMetrics(fd)
        val earSize = m.faceW * 0.32f
        val offsetX = m.faceW * 0.28f
        val baseY = m.crownY

        val outerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(60, 60, 60) }
        val innerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(255, 170, 200) }

        for (side in intArrayOf(-1, 1)) {
            canvas.save()
            canvas.rotate(m.angleDeg, m.cx, baseY)
            val bx = m.cx + side * offsetX
            // 外耳三角
            val outer = Path().apply {
                moveTo(bx - earSize * 0.5f, baseY)
                lineTo(bx, baseY - earSize)
                lineTo(bx + earSize * 0.5f, baseY)
                close()
            }
            canvas.drawPath(outer, outerPaint)
            // 内耳三角（粉色，居中缩小）
            val inner = Path().apply {
                moveTo(bx - earSize * 0.25f, baseY - earSize * 0.1f)
                lineTo(bx, baseY - earSize * 0.7f)
                lineTo(bx + earSize * 0.25f, baseY - earSize * 0.1f)
                close()
            }
            canvas.drawPath(inner, innerPaint)
            canvas.restore()
        }
    }

    /** 猫鼻胡须：鼻底三角鼻 + 两侧胡须线。 */
    private fun drawCatNose(canvas: Canvas, fd: FaceLandmarkDetector.FaceData) {
        val m = FaceMetrics(fd)
        val noseSize = m.faceW * 0.07f
        val nosePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(255, 120, 150) }
        val whiskerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(220, 245, 245, 245)
            strokeWidth = m.faceW * 0.008f
            style = Paint.Style.STROKE
        }

        canvas.save()
        canvas.rotate(m.angleDeg, m.nose.x, m.nose.y)
        // 倒三角鼻
        val nose = Path().apply {
            moveTo(m.nose.x - noseSize, m.nose.y - noseSize * 0.5f)
            lineTo(m.nose.x + noseSize, m.nose.y - noseSize * 0.5f)
            lineTo(m.nose.x, m.nose.y + noseSize)
            close()
        }
        canvas.drawPath(nose, nosePaint)
        // 嘴中线
        canvas.drawLine(
            m.nose.x, m.nose.y + noseSize,
            m.nose.x, m.nose.y + noseSize * 2.2f, whiskerPaint
        )
        // 三根胡须（两侧各三根）
        val whiskerLen = m.faceW * 0.45f
        for (side in intArrayOf(-1, 1)) {
            val sx = m.nose.x + side * noseSize * 1.2f
            for (i in 0 until 3) {
                val dy = (i - 1) * noseSize * 0.9f
                canvas.drawLine(
                    sx, m.nose.y + dy,
                    sx + side * whiskerLen, m.nose.y + dy + side * noseSize * 0.4f,
                    whiskerPaint
                )
            }
        }
        canvas.restore()
    }

    /** 狗鼻子：椭圆鼻 + 高光。 */
    private fun drawDogNose(canvas: Canvas, fd: FaceLandmarkDetector.FaceData) {
        val m = FaceMetrics(fd)
        val rw = m.faceW * 0.09f
        val rh = m.faceW * 0.07f

        val nosePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(35, 30, 30) }
        val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(180, 255, 255, 255) }

        canvas.save()
        canvas.rotate(m.angleDeg, m.nose.x, m.nose.y)
        canvas.drawOval(
            RectF(m.nose.x - rw, m.nose.y - rh, m.nose.x + rw, m.nose.y + rh),
            nosePaint
        )
        // 高光小圆
        canvas.drawCircle(m.nose.x - rw * 0.35f, m.nose.y - rh * 0.4f, rw * 0.22f, highlightPaint)
        canvas.restore()
    }

    /** 狗耳朵：头部两侧下垂的长椭圆耳。 */
    private fun drawDogEars(canvas: Canvas, fd: FaceLandmarkDetector.FaceData) {
        val m = FaceMetrics(fd)
        val earH = m.faceH * 0.55f
        val earW = m.faceW * 0.22f
        val baseY = m.crownY + m.faceH * 0.05f
        val offsetX = m.faceW * 0.42f

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(120, 75, 45) }
        val innerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(180, 130, 95) }

        for (side in intArrayOf(-1, 1)) {
            canvas.save()
            canvas.rotate(m.angleDeg, m.cx, baseY)
            val cx = m.cx + side * offsetX
            // 外耳（下垂椭圆，顶端在头顶，底端向下垂）
            val outer = Path().apply {
                moveTo(cx, baseY)
                cubicTo(
                    cx - side * earW, baseY + earH * 0.3f,
                    cx - side * earW * 0.7f, baseY + earH,
                    cx, baseY + earH * 0.9f
                )
                cubicTo(
                    cx + side * earW * 0.4f, baseY + earH * 0.6f,
                    cx + side * earW * 0.3f, baseY + earH * 0.2f,
                    cx, baseY
                )
                close()
            }
            canvas.drawPath(outer, paint)
            // 内耳（更浅色）
            val inner = Path().apply {
                moveTo(cx, baseY + earH * 0.1f)
                cubicTo(
                    cx - side * earW * 0.6f, baseY + earH * 0.35f,
                    cx - side * earW * 0.4f, baseY + earH * 0.85f,
                    cx, baseY + earH * 0.8f
                )
                cubicTo(
                    cx + side * earW * 0.2f, baseY + earH * 0.55f,
                    cx + side * earW * 0.15f, baseY + earH * 0.25f,
                    cx, baseY + earH * 0.1f
                )
                close()
            }
            canvas.drawPath(inner, innerPaint)
            canvas.restore()
        }
    }

    /** 兔耳朵：头顶两根长椭圆耳，含粉色内耳。 */
    private fun drawBunnyEars(canvas: Canvas, fd: FaceLandmarkDetector.FaceData) {
        val m = FaceMetrics(fd)
        val earH = m.faceH * 0.85f
        val earW = m.faceW * 0.16f
        val baseY = m.crownY
        val offsetX = m.faceW * 0.16f

        val outerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(240, 240, 240) }
        val innerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(255, 190, 205) }

        for (side in intArrayOf(-1, 1)) {
            canvas.save()
            canvas.rotate(m.angleDeg, m.cx, baseY)
            // 外耳向两侧略微倾斜
            canvas.rotate(side * 12f, m.cx + side * offsetX, baseY)
            val cx = m.cx + side * offsetX
            canvas.drawOval(
                RectF(cx - earW, baseY - earH, cx + earW, baseY + earW * 0.4f),
                outerPaint
            )
            // 内耳
            canvas.drawOval(
                RectF(cx - earW * 0.5f, baseY - earH * 0.85f, cx + earW * 0.5f, baseY),
                innerPaint
            )
            canvas.restore()
        }
    }

    /** 熊猫眼：两眼周围黑色椭圆斑块。 */
    private fun drawPandaEyes(canvas: Canvas, fd: FaceLandmarkDetector.FaceData) {
        val m = FaceMetrics(fd)
        val patchW = m.eyeDist * 0.4f
        val patchH = m.eyeDist * 0.32f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(235, 20, 20, 20) }

        canvas.save()
        canvas.rotate(m.angleDeg, m.cx, m.cy)
        // 左眼斑块（椭圆形，略向左下倾斜）
        canvas.save()
        canvas.rotate(-15f, m.leftEye.x, m.leftEye.y)
        canvas.drawOval(
            RectF(m.leftEye.x - patchW, m.leftEye.y - patchH, m.leftEye.x + patchW, m.leftEye.y + patchH),
            paint
        )
        canvas.restore()
        // 右眼斑块（略向右下倾斜）
        canvas.save()
        canvas.rotate(15f, m.rightEye.x, m.rightEye.y)
        canvas.drawOval(
            RectF(m.rightEye.x - patchW, m.rightEye.y - patchH, m.rightEye.x + patchW, m.rightEye.y + patchH),
            paint
        )
        canvas.restore()
        canvas.restore()
    }

    /** 狐狸耳朵：头顶尖三角耳，橙色填充 + 深色内耳。 */
    private fun drawFoxEars(canvas: Canvas, fd: FaceLandmarkDetector.FaceData) {
        val m = FaceMetrics(fd)
        val earSize = m.faceW * 0.38f
        val offsetX = m.faceW * 0.3f
        val baseY = m.crownY

        val outerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(230, 110, 40) }
        val innerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(40, 30, 30) }

        for (side in intArrayOf(-1, 1)) {
            canvas.save()
            canvas.rotate(m.angleDeg, m.cx, baseY)
            val bx = m.cx + side * offsetX
            // 外耳（更瘦高的尖三角）
            val outer = Path().apply {
                moveTo(bx - earSize * 0.45f, baseY)
                lineTo(bx + side * earSize * 0.1f, baseY - earSize * 1.1f)
                lineTo(bx + earSize * 0.45f, baseY)
                close()
            }
            canvas.drawPath(outer, outerPaint)
            // 内耳
            val inner = Path().apply {
                moveTo(bx - earSize * 0.22f, baseY - earSize * 0.05f)
                lineTo(bx + side * earSize * 0.05f, baseY - earSize * 0.85f)
                lineTo(bx + earSize * 0.22f, baseY - earSize * 0.05f)
                close()
            }
            canvas.drawPath(inner, innerPaint)
            canvas.restore()
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 头饰特效
    // ═══════════════════════════════════════════════════════════════

    /** 皇冠：金色锯齿冠 + 宝石。 */
    private fun drawCrown(canvas: Canvas, fd: FaceLandmarkDetector.FaceData) {
        val m = FaceMetrics(fd)
        val w = m.faceW * 0.7f
        val h = m.faceW * 0.28f
        val baseY = m.crownY + m.faceH * 0.05f
        val left = m.cx - w / 2f

        val goldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, baseY - h, 0f, baseY,
                intArrayOf(Color.rgb(255, 230, 120), Color.rgb(210, 160, 40), Color.rgb(255, 240, 170)),
                null, Shader.TileMode.CLAMP
            )
        }
        val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(150, 110, 20)
            strokeWidth = m.faceW * 0.006f
            style = Paint.Style.STROKE
        }
        val jewelColors = intArrayOf(
            Color.rgb(230, 60, 60), Color.rgb(60, 180, 90),
            Color.rgb(60, 110, 230), Color.rgb(220, 180, 40), Color.rgb(180, 80, 220)
        )
        val jewelPaint = Paint(Paint.ANTI_ALIAS_FLAG)

        canvas.save()
        canvas.rotate(m.angleDeg, m.cx, baseY)
        // 皇冠路径：底边 + 5 个锯齿尖
        val path = Path().apply {
            moveTo(left, baseY)
            lineTo(left, baseY - h * 0.5f)
            val spikes = 5
            val spikeW = w / spikes
            for (i in 0 until spikes) {
                val x0 = left + i * spikeW
                lineTo(x0 + spikeW * 0.5f, baseY - h)
                lineTo(x0 + spikeW, baseY - h * 0.5f)
            }
            lineTo(left + w, baseY)
            close()
        }
        canvas.drawPath(path, goldPaint)
        canvas.drawPath(path, outlinePaint)
        // 宝石：在每个尖顶放一颗
        val spikes = 5
        val spikeW = w / spikes
        jewelPaint.color = Color.argb(120, 255, 255, 255)
        for (i in 0 until spikes) {
            val x = left + i * spikeW + spikeW * 0.5f
            val y = baseY - h
            jewelPaint.color = jewelColors[i % jewelColors.size]
            canvas.drawCircle(x, y, h * 0.12f, jewelPaint)
        }
        canvas.restore()
    }

    /** 花环：头顶沿弧线排列的一圈花。 */
    private fun drawFlowerCrown(canvas: Canvas, fd: FaceLandmarkDetector.FaceData) {
        val m = FaceMetrics(fd)
        val radius = m.faceW * 0.42f
        val centerX = m.cx
        val centerY = m.crownY + m.faceH * 0.08f
        val flowerSize = m.faceW * 0.1f
        val petalColors = intArrayOf(
            Color.rgb(255, 180, 200), Color.rgb(255, 230, 150),
            Color.rgb(200, 170, 255), Color.rgb(255, 210, 130), Color.rgb(180, 220, 255)
        )

        canvas.save()
        canvas.rotate(m.angleDeg, centerX, centerY)
        val count = 7
        for (i in 0 until count) {
            // 在头顶半圆弧上分布
            val t = i.toFloat() / (count - 1)
            val angle = PI + t * PI // 从左到右扫过头顶
            val fx = (centerX + radius * cos(angle)).toFloat()
            val fy = (centerY + radius * sin(angle)).toFloat()
            drawFlower(canvas, fx, fy, flowerSize, petalColors[i % petalColors.size])
        }
        canvas.restore()
    }

    /** 礼帽：圆柱冠 + 宽帽檐。 */
    private fun drawHat(canvas: Canvas, fd: FaceLandmarkDetector.FaceData) {
        val m = FaceMetrics(fd)
        val crownW = m.faceW * 0.55f
        val crownH = m.faceW * 0.6f
        val brimW = m.faceW * 0.95f
        val brimH = m.faceW * 0.08f
        val baseY = m.crownY + m.faceH * 0.05f

        val blackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(30, 30, 35) }
        val bandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(120, 30, 40) }

        canvas.save()
        canvas.rotate(m.angleDeg, m.cx, baseY)
        // 帽檐
        canvas.drawOval(
            RectF(m.cx - brimW / 2f, baseY - brimH, m.cx + brimW / 2f, baseY + brimH),
            blackPaint
        )
        // 圆柱冠
        canvas.drawRect(
            RectF(m.cx - crownW / 2f, baseY - crownH, m.cx + crownW / 2f, baseY),
            blackPaint
        )
        // 帽冠顶部椭圆（透视）
        canvas.drawOval(
            RectF(m.cx - crownW / 2f, baseY - crownH - brimH, m.cx + crownW / 2f, baseY - crownH + brimH),
            blackPaint
        )
        // 红色帽带
        canvas.drawRect(
            RectF(m.cx - crownW / 2f, baseY - crownH * 0.22f, m.cx + crownW / 2f, baseY - crownH * 0.05f),
            bandPaint
        )
        canvas.restore()
    }

    /** 棒球帽：圆顶 + 弧形帽檐。 */
    private fun drawCap(canvas: Canvas, fd: FaceLandmarkDetector.FaceData) {
        val m = FaceMetrics(fd)
        val capW = m.faceW * 0.62f
        val capH = m.faceW * 0.32f
        val baseY = m.crownY + m.faceH * 0.06f

        val capPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(40, 90, 180) }
        val brimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(30, 70, 150) }
        val buttonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(255, 255, 255) }

        canvas.save()
        canvas.rotate(m.angleDeg, m.cx, baseY)
        // 圆顶（半椭圆）
        canvas.drawOval(
            RectF(m.cx - capW / 2f, baseY - capH, m.cx + capW / 2f, baseY + capH * 0.2f),
            capPaint
        )
        // 帽檐（向下弯曲的弧形）
        val brim = Path().apply {
            moveTo(m.cx - capW / 2f, baseY)
            quadTo(m.cx, baseY + capH * 0.6f, m.cx + capW / 2f, baseY)
            lineTo(m.cx + capW / 2f, baseY - capH * 0.05f)
            quadTo(m.cx, baseY + capH * 0.55f, m.cx - capW / 2f, baseY - capH * 0.05f)
            close()
        }
        canvas.drawPath(brim, brimPaint)
        // 帽顶纽扣
        canvas.drawCircle(m.cx, baseY - capH * 0.95f, capW * 0.03f, buttonPaint)
        canvas.restore()
    }

    /** 光环：头顶金色发光圆环。 */
    private fun drawHalo(canvas: Canvas, fd: FaceLandmarkDetector.FaceData) {
        val m = FaceMetrics(fd)
        val radius = m.faceW * 0.4f
        val cx = m.cx
        val cy = m.crownY - m.faceH * 0.05f

        // 发光底色（模糊描边）
        val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(90, 255, 230, 120)
            style = Paint.Style.STROKE
            strokeWidth = radius * 0.25f
            maskFilter = BlurMaskFilter(radius * 0.18f, BlurMaskFilter.Blur.NORMAL)
        }
        // 主体金环（渐变描边）
        val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = radius * 0.12f
            shader = SweepGradient(cx, cy, intArrayOf(
                Color.rgb(255, 245, 180), Color.rgb(255, 215, 80),
                Color.rgb(255, 245, 180), Color.rgb(255, 215, 80),
                Color.rgb(255, 245, 180)
            ), null)
        }

        canvas.save()
        canvas.rotate(m.angleDeg, cx, cy)
        // 椭圆环（透视感：略压扁）
        canvas.drawOval(
            RectF(cx - radius, cy - radius * 0.3f, cx + radius, cy + radius * 0.3f),
            glowPaint
        )
        canvas.drawOval(
            RectF(cx - radius, cy - radius * 0.3f, cx + radius, cy + radius * 0.3f),
            ringPaint
        )
        canvas.restore()
    }

    // ═══════════════════════════════════════════════════════════════
    // 面部装饰特效
    // ═══════════════════════════════════════════════════════════════

    /** 眼镜：两个圆框 + 鼻梁桥。 */
    private fun drawGlasses(canvas: Canvas, fd: FaceLandmarkDetector.FaceData) {
        val m = FaceMetrics(fd)
        val lensR = m.eyeDist * 0.32f
        val bridgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(50, 50, 60)
            strokeWidth = lensR * 0.12f
            style = Paint.Style.STROKE
        }
        val lensPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(30, 80, 140, 200)
            style = Paint.Style.STROKE
            strokeWidth = lensR * 0.12f
        }

        canvas.save()
        canvas.rotate(m.angleDeg, m.cx, m.cy)
        // 左右镜框
        canvas.drawCircle(m.leftEye.x, m.leftEye.y, lensR, lensPaint)
        canvas.drawCircle(m.rightEye.x, m.rightEye.y, lensR, lensPaint)
        // 鼻梁桥
        canvas.drawLine(
            m.leftEye.x + lensR, m.leftEye.y,
            m.rightEye.x - lensR, m.rightEye.y,
            bridgePaint
        )
        // 镜腿
        canvas.drawLine(
            m.leftEye.x - lensR, m.leftEye.y,
            m.leftEye.x - lensR * 1.6f, m.leftEye.y - lensR * 0.2f,
            bridgePaint
        )
        canvas.drawLine(
            m.rightEye.x + lensR, m.rightEye.y,
            m.rightEye.x + lensR * 1.6f, m.rightEye.y - lensR * 0.2f,
            bridgePaint
        )
        canvas.restore()
    }

    /** 墨镜：两个圆角矩形 + 深色镜片。 */
    private fun drawSunglasses(canvas: Canvas, fd: FaceLandmarkDetector.FaceData) {
        val m = FaceMetrics(fd)
        val lensW = m.eyeDist * 0.42f
        val lensH = m.eyeDist * 0.32f
        val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(20, 20, 25) }
        val lensPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(200, 20, 25, 40) }
        val bridgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(20, 20, 25)
            strokeWidth = lensH * 0.25f
            style = Paint.Style.STROKE
        }
        val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(70, 255, 255, 255) }

        canvas.save()
        canvas.rotate(m.angleDeg, m.cx, m.cy)
        for (side in intArrayOf(-1, 1)) {
            val eye = if (side < 0) m.leftEye else m.rightEye
            val rect = RectF(eye.x - lensW, eye.y - lensH, eye.x + lensW, eye.y + lensH)
            // 镜片深色
            canvas.drawRoundRect(rect, lensH * 0.4f, lensH * 0.4f, lensPaint)
            // 框
            canvas.drawRoundRect(rect, lensH * 0.4f, lensH * 0.4f, framePaint.apply {
                style = Paint.Style.STROKE; strokeWidth = lensH * 0.12f
            })
            // 反光
            canvas.drawRoundRect(
                RectF(eye.x - lensW * 0.7f, eye.y - lensH * 0.7f, eye.x - lensW * 0.1f, eye.y - lensH * 0.2f),
                lensH * 0.2f, lensH * 0.2f, highlightPaint
            )
        }
        // 鼻梁桥
        canvas.drawLine(
            m.leftEye.x + lensW * 0.9f, m.leftEye.y,
            m.rightEye.x - lensW * 0.9f, m.rightEye.y,
            bridgePaint
        )
        canvas.restore()
    }

    /** 面具：覆盖双眼区域的化装舞会面具。 */
    private fun drawMask(canvas: Canvas, fd: FaceLandmarkDetector.FaceData) {
        val m = FaceMetrics(fd)
        val halfW = m.eyeDist * 0.95f
        val h = m.eyeDist * 0.7f
        val cy = (m.leftEye.y + m.rightEye.y) / 2f

        val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(220, 120, 40, 90)
            shader = LinearGradient(
                m.cx - halfW, cy, m.cx + halfW, cy,
                intArrayOf(Color.rgb(140, 40, 110), Color.rgb(200, 70, 60), Color.rgb(140, 40, 110)),
                null, Shader.TileMode.CLAMP
            )
        }
        val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(255, 215, 90)
            style = Paint.Style.STROKE
            strokeWidth = m.eyeDist * 0.04f
        }
        val jewelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(255, 230, 120) }

        canvas.save()
        canvas.rotate(m.angleDeg, m.cx, cy)
        // 面具轮廓：左右两个凸起的眼孔区 + 中间内凹
        val path = Path().apply {
            moveTo(m.cx - halfW, cy)
            cubicTo(
                m.cx - halfW, cy - h * 0.9f,
                m.cx - m.eyeDist * 0.2f, cy - h * 0.8f,
                m.cx, cy - h * 0.35f
            )
            cubicTo(
                m.cx + m.eyeDist * 0.2f, cy - h * 0.8f,
                m.cx + halfW, cy - h * 0.9f,
                m.cx + halfW, cy
            )
            cubicTo(
                m.cx + halfW, cy + h * 0.6f,
                m.cx + m.eyeDist * 0.2f, cy + h * 0.5f,
                m.cx, cy + h * 0.2f
            )
            cubicTo(
                m.cx - m.eyeDist * 0.2f, cy + h * 0.5f,
                m.cx - halfW, cy + h * 0.6f,
                m.cx - halfW, cy
            )
            close()
        }
        canvas.drawPath(path, maskPaint)
        canvas.drawPath(path, edgePaint)
        // 眼孔（镂空感：用深色椭圆）
        val eyeHolePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(180, 20, 10, 20) }
        canvas.drawOval(
            RectF(m.leftEye.x - m.eyeDist * 0.22f, m.leftEye.y - h * 0.28f,
                m.leftEye.x + m.eyeDist * 0.22f, m.leftEye.y + h * 0.2f),
            eyeHolePaint
        )
        canvas.drawOval(
            RectF(m.rightEye.x - m.eyeDist * 0.22f, m.rightEye.y - h * 0.28f,
                m.rightEye.x + m.eyeDist * 0.22f, m.rightEye.y + h * 0.2f),
            eyeHolePaint
        )
        // 中央装饰宝石
        canvas.drawCircle(m.cx, cy - h * 0.3f, m.eyeDist * 0.05f, jewelPaint)
        canvas.restore()
    }

    /** 蝴蝶贴：贴在脸颊（右脸太阳穴附近）的蝴蝶。 */
    private fun drawButterfly(canvas: Canvas, fd: FaceLandmarkDetector.FaceData) {
        val m = FaceMetrics(fd)
        // 贴在右脸颊偏上（朝向画面左侧的人像的右脸）
        val cx = m.rightEye.x + m.eyeDist * 0.5f
        val cy = m.rightEye.y - m.eyeDist * 0.15f
        val size = m.faceW * 0.12f

        val wingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(cx - size, cy, cx + size, cy,
                intArrayOf(Color.rgb(160, 90, 230), Color.rgb(255, 140, 200), Color.rgb(120, 200, 255)),
                null, Shader.TileMode.CLAMP)
        }
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(50, 40, 60) }
        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(200, 255, 240, 120) }

        canvas.save()
        canvas.rotate(m.angleDeg, cx, cy)
        // 上翅膀（左右两片）
        val upperL = Path().apply {
            moveTo(cx, cy)
            cubicTo(cx - size, cy - size, cx - size * 1.1f, cy - size * 0.2f, cx, cy - size * 0.2f)
            close()
        }
        val upperR = Path().apply {
            moveTo(cx, cy)
            cubicTo(cx + size, cy - size, cx + size * 1.1f, cy - size * 0.2f, cx, cy - size * 0.2f)
            close()
        }
        // 下翅膀（略小）
        val lowerL = Path().apply {
            moveTo(cx, cy)
            cubicTo(cx - size * 0.7f, cy + size * 0.5f, cx - size * 0.8f, cy + size * 0.9f, cx, cy + size * 0.3f)
            close()
        }
        val lowerR = Path().apply {
            moveTo(cx, cy)
            cubicTo(cx + size * 0.7f, cy + size * 0.5f, cx + size * 0.8f, cy + size * 0.9f, cx, cy + size * 0.3f)
            close()
        }
        canvas.drawPath(upperL, wingPaint)
        canvas.drawPath(upperR, wingPaint)
        canvas.drawPath(lowerL, wingPaint)
        canvas.drawPath(lowerR, wingPaint)
        // 身体（椭圆）
        canvas.drawOval(
            RectF(cx - size * 0.06f, cy - size * 0.3f, cx + size * 0.06f, cy + size * 0.5f),
            bodyPaint
        )
        // 翅膀斑点
        canvas.drawCircle(cx - size * 0.5f, cy - size * 0.5f, size * 0.1f, dotPaint)
        canvas.drawCircle(cx + size * 0.5f, cy - size * 0.5f, size * 0.1f, dotPaint)
        // 触须
        val feeler = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(50, 40, 60); style = Paint.Style.STROKE; strokeWidth = size * 0.03f
        }
        canvas.drawLine(cx, cy - size * 0.3f, cx - size * 0.25f, cy - size * 0.55f, feeler)
        canvas.drawLine(cx, cy - size * 0.3f, cx + size * 0.25f, cy - size * 0.55f, feeler)
        canvas.restore()
    }

    /** 星星贴：贴在脸颊上的五角星。 */
    private fun drawStarOnCheek(canvas: Canvas, fd: FaceLandmarkDetector.FaceData) {
        val m = FaceMetrics(fd)
        val cx = m.leftEye.x - m.eyeDist * 0.45f
        val cy = m.leftEye.y + m.eyeDist * 0.35f
        val outerR = m.faceW * 0.06f

        val starPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(cx, cy, outerR,
                intArrayOf(Color.rgb(255, 245, 160), Color.rgb(255, 180, 30)),
                null, Shader.TileMode.CLAMP)
        }
        val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(200, 130, 10); style = Paint.Style.STROKE; strokeWidth = outerR * 0.08f
        }

        canvas.save()
        canvas.rotate(m.angleDeg, cx, cy)
        val path = starPath(cx, cy, outerR, outerR * 0.45f)
        canvas.drawPath(path, starPaint)
        canvas.drawPath(path, outlinePaint)
        canvas.restore()
    }

    // ═══════════════════════════════════════════════════════════════
    // 动态特效（基于时间动画，无需人脸）
    // ═══════════════════════════════════════════════════════════════

    /** 闪烁星光：随机分布的星点随时间明灭。 */
    private fun drawSparkle(canvas: Canvas, w: Int, h: Int) {
        val t = System.currentTimeMillis() / 1000f
        val count = 40
        val baseSize = (w.coerceAtMost(h)) * 0.015f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        for (i in 0 until count) {
            val px = rand(i * 3) * w
            val py = rand(i * 3 + 1) * h
            // 每颗星有独立相位与频率
            val phase = rand(i * 3 + 2) * (2 * PI).toFloat()
            val freq = 1.5f + rand(i * 5) * 2.5f
            val alpha = ((0.5f + 0.5f * sin(t * freq + phase)) * 230).toInt().coerceIn(0, 255)
            val size = baseSize * (0.6f + 0.6f * sin(t * freq + phase))
            paint.color = Color.argb(alpha, 255, 250, 210)
            drawSparkleShape(canvas, px, py, size, paint)
        }
    }

    /** 爱心雨：从顶部下落的心形。 */
    private fun drawHeartRain(canvas: Canvas, w: Int, h: Int) {
        val t = System.currentTimeMillis() / 1000f
        val count = 30
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        for (i in 0 until count) {
            val px = rand(i * 7) * w
            val speed = 60f + rand(i * 7 + 1) * 140f          // 像素/秒
            val startY = rand(i * 7 + 2) * h                   // 错开初始位置
            val py = ((startY + t * speed) % (h + h * 0.2f)) - h * 0.1f
            val size = (w.coerceAtMost(h)) * (0.018f + rand(i * 7 + 3) * 0.02f)
            val drift = sin(t * 1.5f + i) * w * 0.02f
            val hue = rand(i * 7 + 4)
            paint.color = if (hue < 0.5f) Color.argb(220, 255, 90, 130)
            else Color.argb(220, 255, 130, 160)
            drawHeartShape(canvas, px + drift, py, size, paint)
        }
    }

    /** 花瓣雨：飘落的樱花花瓣（带旋转与横向飘动）。 */
    private fun drawPetalRain(canvas: Canvas, w: Int, h: Int) {
        val t = System.currentTimeMillis() / 1000f
        val count = 35
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        for (i in 0 until count) {
            val px = rand(i * 11) * w
            val speed = 50f + rand(i * 11 + 1) * 110f
            val startY = rand(i * 11 + 2) * h
            val py = ((startY + t * speed) % (h + h * 0.2f)) - h * 0.1f
            val size = (w.coerceAtMost(h)) * (0.012f + rand(i * 11 + 3) * 0.012f)
            val drift = sin(t * 1.2f + i * 0.7f) * w * 0.05f
            val rot = (t * (60f + rand(i * 11 + 4) * 120f) + i * 30f) % 360f
            val tint = rand(i * 11 + 5)
            paint.color = when {
                tint < 0.33f -> Color.argb(230, 255, 190, 210)
                tint < 0.66f -> Color.argb(230, 255, 215, 225)
                else -> Color.argb(230, 255, 170, 195)
            }
            canvas.save()
            canvas.rotate(rot, px + drift, py)
            // 花瓣：椭圆，一端略尖
            canvas.drawOval(
                RectF(px + drift - size * 0.5f, py - size, px + drift + size * 0.5f, py + size),
                paint
            )
            canvas.restore()
        }
    }

    /** 雪花飘落：白色圆点下落，带大小与速度差异。 */
    private fun drawSnow(canvas: Canvas, w: Int, h: Int) {
        val t = System.currentTimeMillis() / 1000f
        val count = 60
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        for (i in 0 until count) {
            val px = rand(i * 13) * w
            val speed = 40f + rand(i * 13 + 1) * 120f
            val startY = rand(i * 13 + 2) * h
            val py = ((startY + t * speed) % (h + h * 0.1f)) - h * 0.05f
            val size = (w.coerceAtMost(h)) * (0.004f + rand(i * 13 + 3) * 0.01f)
            val drift = sin(t * 0.8f + i) * w * 0.03f
            val alpha = (140 + rand(i * 13 + 4) * 115).toInt()
            paint.color = Color.argb(alpha, 255, 255, 255)
            canvas.drawCircle(px + drift, py, size, paint)
        }
    }

    /** 气泡：从底部上浮的半透明气泡。 */
    private fun drawBubbles(canvas: Canvas, w: Int, h: Int) {
        val t = System.currentTimeMillis() / 1000f
        val count = 25
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(160, 255, 255, 255) }

        for (i in 0 until count) {
            val px = rand(i * 17) * w
            val speed = 50f + rand(i * 17 + 1) * 100f
            val startY = rand(i * 17 + 2) * h
            // 向上飘：从底部循环
            val py = h + h * 0.1f - ((startY + t * speed) % (h + h * 0.2f))
            val size = (w.coerceAtMost(h)) * (0.01f + rand(i * 17 + 3) * 0.03f)
            val drift = sin(t * 1.1f + i) * w * 0.02f
            paint.color = Color.argb(60, 200, 230, 255)
            paint.style = Paint.Style.FILL
            canvas.drawCircle(px + drift, py, size, paint)
            // 描边
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = size * 0.08f
            paint.color = Color.argb(120, 220, 240, 255)
            canvas.drawCircle(px + drift, py, size, paint)
            // 高光
            canvas.drawCircle(px + drift - size * 0.35f, py - size * 0.35f, size * 0.2f, highlightPaint)
        }
    }

    /** 烟花：多个爆裂点，粒子径向扩散并周期性循环。 */
    private fun drawFireworks(canvas: Canvas, w: Int, h: Int) {
        val t = System.currentTimeMillis() / 1000f
        val centers = arrayOf(
            floatArrayOf(w * 0.3f, h * 0.35f, 0.0f, 2.0f, 0f),
            floatArrayOf(w * 0.7f, h * 0.3f, 1.0f, 2.6f, 1f),
            floatArrayOf(w * 0.5f, h * 0.55f, 0.5f, 3.2f, 2f)
        )
        val colors = intArrayOf(
            Color.rgb(255, 120, 120), Color.rgb(120, 200, 255),
            Color.rgb(255, 220, 120), Color.rgb(180, 255, 140),
            Color.rgb(220, 140, 255), Color.rgb(255, 170, 90)
        )
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val maxRadius = (w.coerceAtMost(h)) * 0.25f

        for (c in centers) {
            val cx = c[0]; val cy = c[1]
            val phase = c[2]; val period = c[3]; val seedOff = c[4].toInt()
            // 当前爆裂周期内的进度 0..1
            val cyclePos = ((t + phase) % period) / period
            if (cyclePos > 0.95f) continue  // 间隔期不绘制
            val progress = cyclePos / 0.95f
            val radius = maxRadius * easeOut(progress)
            val alpha = ((1f - progress) * 230).toInt().coerceIn(0, 230)
            val particles = 28
            for (i in 0 until particles) {
                val ang = (i.toFloat() / particles) * (2 * PI).toFloat()
                val jitter = 0.85f + rand(seedOff * 31 + i) * 0.3f
                val px = cx + cos(ang) * radius * jitter
                val py = cy + sin(ang) * radius * jitter
                // 尾迹（从中心到当前位置的短线）
                paint.color = Color.argb(alpha / 3, 255, 240, 200)
                paint.strokeWidth = (w.coerceAtMost(h)) * 0.003f
                canvas.drawLine(cx, cy, px, py, paint)
                // 粒子点（保留原色，将透明度替换为当前 alpha）
                val baseColor = colors[(seedOff + i) % colors.size]
                paint.color = Color.argb(
                    alpha, Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor)
                )
                canvas.drawCircle(px, py, (w.coerceAtMost(h)) * 0.006f, paint)
            }
        }
    }

    /** 闪电：从顶部下落的锯齿状闪电，带辉光与明灭。 */
    private fun drawLightning(canvas: Canvas, w: Int, h: Int) {
        val t = System.currentTimeMillis()
        // 每 600ms 重新生成一次路径
        val seed = (t / 600).toInt()
        // 闪烁透明度
        val flicker = 0.5f + 0.5f * sin(t / 60.0)
        val alpha = (flicker * 230).toInt().coerceIn(60, 255)

        val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(alpha / 2, 120, 180, 255)
            strokeWidth = w * 0.025f
            style = Paint.Style.STROKE
            maskFilter = BlurMaskFilter(w * 0.02f, BlurMaskFilter.Blur.NORMAL)
        }
        val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(alpha, 230, 240, 255)
            strokeWidth = w * 0.006f
            style = Paint.Style.STROKE
        }

        // 主干路径（从顶部到中部）
        val main = Path()
        var x = w * (0.35f + rand(seed) * 0.3f)
        var y = 0f
        main.moveTo(x, y)
        val segments = 9
        val endY = h * 0.7f
        val stepY = endY / segments
        for (i in 1..segments) {
            y += stepY
            x += (rand(seed * 10 + i) - 0.5f) * w * 0.12f
            main.lineTo(x, y)
            // 分支
            if (i % 3 == 0 && i < segments - 1) {
                val branch = Path()
                branch.moveTo(x, y)
                var bx = x
                var by = y
                val bSteps = 3
                val dir = if (rand(seed * 20 + i) < 0.5f) -1 else 1
                for (b in 1..bSteps) {
                    by += stepY * 0.5f
                    bx += dir * (rand(seed * 30 + i + b) * w * 0.06f + w * 0.02f)
                    branch.lineTo(bx, by)
                }
                canvas.drawPath(branch, glowPaint)
                canvas.drawPath(branch, corePaint)
            }
        }
        canvas.drawPath(main, glowPaint)
        canvas.drawPath(main, corePaint)
        // 整屏微闪
        val flashPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb((alpha * 0.15f).toInt(), 200, 220, 255)
        }
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), flashPaint)
    }

    // ═══════════════════════════════════════════════════════════════
    // 滤镜特效（全屏叠加）
    // ═══════════════════════════════════════════════════════════════

    /** 彩虹：彩虹渐变以 Screen 混合叠加全屏。 */
    private fun drawRainbow(canvas: Canvas, w: Int, h: Int) {
        // 直接以 SCREEN 模式绘制矩形：将彩虹与已绘制的照片按屏幕混合公式叠加。
        // 软件画布上 PorterDuffXfermode 直接作用于已有像素，无需 saveLayer。
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, 0f, h.toFloat(),
                intArrayOf(
                    Color.argb(0, 255, 0, 0), Color.argb(110, 255, 0, 0),
                    Color.argb(110, 255, 127, 0), Color.argb(110, 255, 255, 0),
                    Color.argb(110, 0, 255, 0), Color.argb(110, 0, 150, 255),
                    Color.argb(110, 75, 0, 130), Color.argb(0, 75, 0, 130)
                ),
                null, Shader.TileMode.CLAMP
            )
            xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
        }
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
    }

    /** 银河：星空 + 星云团块。 */
    private fun drawGalaxy(canvas: Canvas, w: Int, h: Int) {
        val t = System.currentTimeMillis() / 1000f
        // 1) 深色夜空底
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, 0f, 0f, h.toFloat(),
                intArrayOf(Color.argb(150, 10, 5, 30), Color.argb(180, 20, 10, 50), Color.argb(150, 5, 0, 25)),
                null, Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), bgPaint)

        // 2) 星云（几团大半径径向渐变）
        val nebulaColors = arrayOf(
            intArrayOf(Color.argb(0, 120, 40, 180), Color.argb(90, 180, 60, 220)),
            intArrayOf(Color.argb(0, 40, 80, 180), Color.argb(90, 60, 120, 220)),
            intArrayOf(Color.argb(0, 200, 60, 140), Color.argb(80, 220, 90, 170))
        )
        val nebulaR = (w.coerceAtMost(h)) * 0.45f
        for (i in nebulaColors.indices) {
            val nx = w * (0.3f + rand(i * 23) * 0.4f)
            val ny = h * (0.25f + rand(i * 23 + 1) * 0.5f)
            val nebulaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(nx, ny, nebulaR, nebulaColors[i], null, Shader.TileMode.CLAMP)
            }
            canvas.drawCircle(nx, ny, nebulaR, nebulaPaint)
        }

        // 3) 星星（密集小点，部分闪烁）
        val starPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        val count = 150
        val minDim = w.coerceAtMost(h)
        for (i in 0 until count) {
            val sx = rand(i * 29) * w
            val sy = rand(i * 29 + 1) * h
            val baseSize = minDim * (0.0015f + rand(i * 29 + 2) * 0.003f)
            val twinkle = if (i % 4 == 0) {
                0.5f + 0.5f * sin(t * 2f + i)
            } else 1f
            val a = ((120 + rand(i * 29 + 3) * 135) * twinkle).toInt().coerceIn(0, 255)
            starPaint.color = Color.argb(a, 255, 255, 245)
            canvas.drawCircle(sx, sy, baseSize, starPaint)
            // 偶尔加十字光芒
            if (i % 17 == 0) {
                starPaint.color = Color.argb(a / 2, 255, 255, 200)
                canvas.drawLine(sx - baseSize * 3, sy, sx + baseSize * 3, sy, starPaint.apply { strokeWidth = baseSize * 0.4f })
                canvas.drawLine(sx, sy - baseSize * 3, sx, sy + baseSize * 3, starPaint)
            }
        }
    }

    /** 极光波段参数（颜色用 Int 精确保存，避免 Float 精度丢失）。 */
    private data class AuroraBand(
        val yRatio: Float,
        val amplitude: Float,
        val speed: Float,
        val topColor: Int,
        val bottomColor: Int
    )

    /** 极光：绿/紫波浪带，随时间流动。 */
    private fun drawAurora(canvas: Canvas, w: Int, h: Int) {
        val t = System.currentTimeMillis() / 1000f
        // 深色夜空底
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, 0f, 0f, h.toFloat(),
                intArrayOf(Color.argb(160, 5, 10, 30), Color.argb(120, 10, 20, 50)),
                null, Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), bgPaint)

        val bands = listOf(
            AuroraBand(0.5f, 80f, 1.1f, Color.rgb(80, 255, 160), Color.rgb(40, 180, 120)),
            AuroraBand(0.42f, 60f, 0.9f, Color.rgb(120, 180, 255), Color.rgb(60, 90, 200)),
            AuroraBand(0.6f, 100f, 1.3f, Color.rgb(180, 120, 255), Color.rgb(110, 60, 180))
        )
        for ((idx, band) in bands.withIndex()) {
            val yCenter = h * band.yRatio
            val amplitude = band.amplitude
            val speed = band.speed
            val topColor = band.topColor
            val bottomColor = band.bottomColor
            val bandHeight = h * 0.18f

            // 波段渐变：上下透明、中部半透明，配合模糊产生柔光带
            val auroraPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    0f, yCenter - bandHeight / 2, 0f, yCenter + bandHeight / 2,
                    intArrayOf(
                        Color.argb(0, Color.red(topColor), Color.green(topColor), Color.blue(topColor)),
                        Color.argb(140, Color.red(topColor), Color.green(topColor), Color.blue(topColor)),
                        Color.argb(120, Color.red(bottomColor), Color.green(bottomColor), Color.blue(bottomColor)),
                        Color.argb(0, Color.red(bottomColor), Color.green(bottomColor), Color.blue(bottomColor))
                    ),
                    floatArrayOf(0f, 0.45f, 0.6f, 1f), Shader.TileMode.CLAMP
                )
                maskFilter = BlurMaskFilter(bandHeight * 0.15f, BlurMaskFilter.Blur.NORMAL)
            }

            // 波浪带：上下两条正弦曲线构成闭合路径
            val path = Path()
            val segs = 40
            val phase = t * speed + idx
            // 上沿
            for (i in 0..segs) {
                val x = w * i.toFloat() / segs
                val y = yCenter - bandHeight / 2 + sin(x / w * (2 * PI).toFloat() * 2f + phase) * amplitude
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            // 下沿（反向）
            for (i in segs downTo 0) {
                val x = w * i.toFloat() / segs
                val y = yCenter + bandHeight / 2 + sin(x / w * (2 * PI).toFloat() * 2f + phase + 1f) * amplitude
                path.lineTo(x, y)
            }
            path.close()
            canvas.drawPath(path, auroraPaint)
        }

        // 星点点缀
        val starPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(180, 255, 255, 240) }
        for (i in 0 until 40) {
            canvas.drawCircle(rand(i * 41) * w, rand(i * 41 + 1) * h * 0.5f,
                (w.coerceAtMost(h)) * 0.0015f, starPaint)
        }
    }

    /** 霓虹光晕：屏幕边缘霓虹辉光（颜色随时间循环）+ 人脸轮廓霓虹（若检测到）。 */
    private fun drawNeonGlow(
        canvas: Canvas,
        w: Int,
        h: Int,
        faceData: FaceLandmarkDetector.FaceData?
    ) {
        val t = System.currentTimeMillis() / 1000f
        val minDim = w.coerceAtMost(h)
        // 颜色随时间在霓虹色之间循环
        val neonColors = intArrayOf(
            Color.rgb(255, 40, 180), Color.rgb(40, 230, 255),
            Color.rgb(180, 60, 255), Color.rgb(80, 255, 140)
        )
        val idx = (t * 0.5f).toInt() % neonColors.size
        val c = neonColors[idx]
        val nextC = neonColors[(idx + 1) % neonColors.size]
        val mix = (t * 0.5f) % 1f
        val color = Color.rgb(
            lerp(Color.red(c), Color.red(nextC), mix),
            lerp(Color.green(c), Color.green(nextC), mix),
            lerp(Color.blue(c), Color.blue(nextC), mix)
        )

        // 1) 边缘霓虹辉光：内缩的描边矩形 + 模糊
        val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.STROKE
            strokeWidth = minDim * 0.04f
            maskFilter = BlurMaskFilter(minDim * 0.06f, BlurMaskFilter.Blur.NORMAL)
            alpha = 160
        }
        val inset = minDim * 0.03f
        canvas.drawRect(RectF(inset, inset, w - inset, h - inset), glowPaint)
        // 内层亮线
        val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.STROKE
            strokeWidth = minDim * 0.005f
            alpha = 220
        }
        canvas.drawRect(RectF(inset, inset, w - inset, h - inset), corePaint)

        // 2) 若检测到人脸，沿脸部画霓虹椭圆环
        if (faceData != null) {
            val m = FaceMetrics(faceData)
            val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color
                style = Paint.Style.STROKE
                strokeWidth = m.faceW * 0.02f
                maskFilter = BlurMaskFilter(m.faceW * 0.04f, BlurMaskFilter.Blur.NORMAL)
                alpha = 200
            }
            val ringCore = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = Color.argb(255, 255, 255, 255)
                style = Paint.Style.STROKE
                strokeWidth = m.faceW * 0.004f
            }
            canvas.save()
            canvas.rotate(m.angleDeg, m.cx, m.cy)
            val rx = m.faceW * 0.62f
            val ry = m.faceH * 0.58f
            canvas.drawOval(RectF(m.cx - rx, m.cy - ry, m.cx + rx, m.cy + ry), ringPaint)
            canvas.drawOval(RectF(m.cx - rx, m.cy - ry, m.cx + rx, m.cy + ry), ringCore)
            // 双眼霓虹圈
            val eyeR = m.eyeDist * 0.28f
            canvas.drawCircle(m.leftEye.x, m.leftEye.y, eyeR, ringPaint)
            canvas.drawCircle(m.leftEye.x, m.leftEye.y, eyeR, ringCore)
            canvas.drawCircle(m.rightEye.x, m.rightEye.y, eyeR, ringPaint)
            canvas.drawCircle(m.rightEye.x, m.rightEye.y, eyeR, ringCore)
            canvas.restore()
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 形状绘制工具
    // ═══════════════════════════════════════════════════════════════

    /** 一朵五瓣花（中心 + 5 片花瓣）。 */
    private fun drawFlower(canvas: Canvas, cx: Float, cy: Float, size: Float, petalColor: Int) {
        val petalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = petalColor }
        val centerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(255, 220, 80) }
        val petalR = size * 0.45f
        for (i in 0 until 5) {
            val ang = (i.toFloat() / 5) * (2 * PI).toFloat() - PI.toFloat() / 2f
            val px = cx + cos(ang) * petalR
            val py = cy + sin(ang) * petalR
            canvas.drawCircle(px, py, petalR, petalPaint)
        }
        canvas.drawCircle(cx, cy, size * 0.25f, centerPaint)
    }

    /** 心形路径。 */
    private fun drawHeartShape(canvas: Canvas, cx: Float, cy: Float, size: Float, paint: Paint) {
        val path = Path().apply {
            moveTo(cx, cy + size * 0.35f)
            cubicTo(cx - size, cy - size * 0.3f, cx - size * 0.6f, cy - size, cx, cy - size * 0.3f)
            cubicTo(cx + size * 0.6f, cy - size, cx + size, cy - size * 0.3f, cx, cy + size * 0.35f)
            close()
        }
        canvas.drawPath(path, paint)
    }

    /** 四芒闪烁星（菱形十字）。 */
    private fun drawSparkleShape(canvas: Canvas, cx: Float, cy: Float, size: Float, paint: Paint) {
        val path = Path().apply {
            moveTo(cx, cy - size)
            lineTo(cx + size * 0.2f, cy - size * 0.2f)
            lineTo(cx + size, cy)
            lineTo(cx + size * 0.2f, cy + size * 0.2f)
            lineTo(cx, cy + size)
            lineTo(cx - size * 0.2f, cy + size * 0.2f)
            lineTo(cx - size, cy)
            lineTo(cx - size * 0.2f, cy - size * 0.2f)
            close()
        }
        canvas.drawPath(path, paint)
    }

    /** 五角星路径。 */
    private fun starPath(cx: Float, cy: Float, outerR: Float, innerR: Float): Path {
        val path = Path()
        val rot = -PI / 2 // 顶点朝上
        for (i in 0 until 10) {
            val r = if (i % 2 == 0) outerR else innerR
            val angle = rot + i * PI / 5
            val x = cx + (r * cos(angle)).toFloat()
            val y = cy + (r * sin(angle)).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return path
    }

    // ═══════════════════════════════════════════════════════════════
    // 数学工具
    // ═══════════════════════════════════════════════════════════════

    /** 基于整数种子的确定性伪随机，返回 0..1。保证动态特效粒子位置在帧间稳定。 */
    private fun rand(seed: Int): Float {
        val v = sin(seed.toDouble() * 12.9898 + 78.233) * 43758.5453
        return (v - Math.floor(v)).toFloat()
    }

    /** 缓出函数，用于烟花扩散减速。 */
    private fun easeOut(x: Float): Float = 1f - (1f - x) * (1f - x)

    /** 线性插值。 */
    private fun lerp(a: Int, b: Int, t: Float): Int = (a + (b - a) * t).toInt()
}

/**
 * AR 特效枚举（按分类组织）。
 */
enum class ArEffect(val displayName: String, val category: String) {
    // 动物脸
    CAT_EARS("猫耳朵", "动物"),
    CAT_NOSE("猫鼻胡须", "动物"),
    DOG_NOSE("狗鼻子", "动物"),
    DOG_EARS("狗耳朵", "动物"),
    BUNNY_EARS("兔耳朵", "动物"),
    PANDA_EYES("熊猫眼", "动物"),
    FOX_EARS("狐狸耳朵", "动物"),
    // 头饰
    CROWN("皇冠", "头饰"),
    FLOWER_CROWN("花环", "头饰"),
    HAT("礼帽", "头饰"),
    CAP("棒球帽", "头饰"),
    HALO("光环", "头饰"),
    // 面部装饰
    GLASSES("眼镜", "装饰"),
    SUNGLASSES("墨镜", "装饰"),
    MASK("面具", "装饰"),
    BUTTERFLY("蝴蝶贴", "装饰"),
    STAR("星星贴", "装饰"),
    // 动态特效
    SPARKLE("闪烁星光", "动态"),
    HEART_RAIN("爱心雨", "动态"),
    PETAL_RAIN("花瓣雨", "动态"),
    SNOW("雪花飘落", "动态"),
    BUBBLES("气泡", "动态"),
    FIREWORKS("烟花", "动态"),
    LIGHTNING("闪电", "动态"),
    // 滤镜特效
    RAINBOW("彩虹", "滤镜"),
    GALAXY("银河", "滤镜"),
    AURORA("极光", "滤镜"),
    NEON_GLOW("霓虹光晕", "滤镜")
}
