package com.poseai.app.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint

/**
 * 文字叠加样式配置
 *
 * @param text 文本内容（支持多行，以 \n 分隔）
 * @param x 锚点 X 坐标（0-1 归一化，相对于位图宽度）
 * @param y 锚点 Y 坐标（0-1 归一化，相对于位图高度）
 * @param fontSize 字体大小（像素）
 * @param color 文字颜色
 * @param fontFamily 字体族名称，null 表示默认字体
 * @param isBold 是否粗体
 * @param isItalic 是否斜体
 * @param hasShadow 是否有阴影
 * @param shadowColor 阴影颜色
 * @param shadowRadius 阴影模糊半径
 * @param hasStroke 是否有描边
 * @param strokeColor 描边颜色
 * @param strokeWidth 描边宽度
 * @param rotation 旋转角度（度）
 * @param alignment 文字对齐方式
 * @param letterSpacing 字间距（EM 单位）
 * @param lineHeight 行高倍数（1.0 = 默认行高）
 * @param background 背景色，null 表示无背景
 * @param backgroundRadius 背景圆角半径
 */
data class TextStyle(
    val text: String,
    val x: Float,
    val y: Float,
    val fontSize: Float = 32f,
    val color: Int = Color.WHITE,
    val fontFamily: String? = null,
    val isBold: Boolean = false,
    val isItalic: Boolean = false,
    val hasShadow: Boolean = true,
    val shadowColor: Int = Color.BLACK,
    val shadowRadius: Float = 4f,
    val hasStroke: Boolean = false,
    val strokeColor: Int = Color.BLACK,
    val strokeWidth: Float = 2f,
    val rotation: Float = 0f,
    val alignment: Paint.Align = Paint.Align.LEFT,
    val letterSpacing: Float = 0f,
    val lineHeight: Float = 1.2f,
    val background: Int? = null,
    val backgroundRadius: Float = 8f
)

/**
 * 文字叠加引擎
 *
 * 在位图上渲染文字，支持：
 * - 多行文字（StaticLayout 自动换行）
 * - 粗体 / 斜体 / 字体族
 * - 阴影（Paint.setShadowLayer）
 * - 描边（Paint.Style.STROKE 双次绘制）
 * - 旋转（Canvas.rotate）
 * - 对齐方式（左 / 中 / 右，映射到 Layout.Alignment）
 * - 字间距 / 行高
 * - 背景色（圆角矩形）
 */
class TextOverlayEngine {

    /**
     * 在位图上应用文字叠加
     *
     * @param bitmap 原始位图
     * @param styles 文字样式列表
     * @return 叠加文字后的新位图（不修改原图）
     */
    fun applyText(bitmap: Bitmap, styles: List<TextStyle>): Bitmap {
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        val W = result.width.toFloat()
        val H = result.height.toFloat()

        for (style in styles) {
            if (style.text.isEmpty()) continue
            drawSingleText(canvas, style, W, H, result.width)
        }

        return result
    }

    /**
     * 测量文字在位图上的边界矩形
     *
     * @param text 待测量的文字
     * @param style 文字样式
     * @param bitmapWidth 目标位图宽度（用于确定换行宽度上限）
     * @return 文字边界矩形（坐标相对于锚点的偏移，可包含负值）
     */
    fun measureText(text: String, style: TextStyle, bitmapWidth: Int): RectF {
        if (text.isEmpty()) return RectF()
        val fillPaint = buildFillPaint(style)
        val layoutWidth = (bitmapWidth * 0.9f).toInt().coerceAtLeast(1)
        val layout = buildLayout(text, fillPaint, style, layoutWidth)

        val textWidth = layout.width.toFloat()
        val textHeight = layout.height.toFloat()

        // 根据对齐方式计算水平偏移（相对于锚点）
        val offsetX = when (style.alignment) {
            Paint.Align.LEFT -> 0f
            Paint.Align.CENTER -> -textWidth / 2f
            Paint.Align.RIGHT -> -textWidth
        }
        // 垂直方向居中于锚点
        val offsetY = -textHeight / 2f

        return RectF(offsetX, offsetY, offsetX + textWidth, offsetY + textHeight)
    }

    // ═══════════════════════════════════════════════════════════════
    // 内部实现
    // ═══════════════════════════════════════════════════════════════

    /**
     * 绘制单个文字样式
     * 流程：保存画布 → 平移到锚点 → 旋转 → 绘制背景 → 绘制描边 → 绘制填充 → 恢复画布
     */
    private fun drawSingleText(
        canvas: Canvas,
        style: TextStyle,
        bitmapW: Float,
        bitmapH: Float,
        bitmapWidth: Int
    ) {
        // 归一化坐标转像素坐标
        val anchorX = style.x * bitmapW
        val anchorY = style.y * bitmapH

        // 构建填充画笔与布局
        val fillPaint = buildFillPaint(style)
        val layoutWidth = (bitmapWidth * 0.9f).toInt().coerceAtLeast(1)
        val fillLayout = buildLayout(style.text, fillPaint, style, layoutWidth)

        val textWidth = fillLayout.width.toFloat()
        val textHeight = fillLayout.height.toFloat()

        // 根据对齐方式计算绘制偏移（相对于锚点）
        val offsetX = when (style.alignment) {
            Paint.Align.LEFT -> 0f
            Paint.Align.CENTER -> -textWidth / 2f
            Paint.Align.RIGHT -> -textWidth
        }
        // 垂直方向居中于锚点
        val offsetY = -textHeight / 2f

        canvas.save()
        // 平移到锚点
        canvas.translate(anchorX, anchorY)
        // 围绕锚点旋转
        canvas.rotate(style.rotation)

        // 绘制背景（圆角矩形，包裹文字）
        if (style.background != null) {
            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = style.background
                this.style = Paint.Style.FILL
            }
            val pad = fillPaint.textSize * 0.25f
            val bgRect = RectF(
                offsetX - pad,
                offsetY - pad,
                offsetX + textWidth + pad,
                offsetY + textHeight + pad
            )
            canvas.drawRoundRect(bgRect, style.backgroundRadius, style.backgroundRadius, bgPaint)
        }

        // 平移到文字绘制原点（StaticLayout 从左上角开始绘制）
        canvas.translate(offsetX, offsetY)

        // 先绘制描边（位于填充下方）
        if (style.hasStroke) {
            val strokePaint = buildStrokePaint(style)
            val strokeLayout = buildLayout(style.text, strokePaint, style, layoutWidth)
            strokeLayout.draw(canvas)
        }

        // 再绘制填充文字（可能带阴影）
        fillLayout.draw(canvas)

        canvas.restore()
    }

    /**
     * 构建填充画笔（含阴影、字间距、字体等配置）
     */
    private fun buildFillPaint(style: TextStyle): TextPaint {
        return TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = style.color
            textSize = style.fontSize
            typeface = resolveTypeface(style)
            letterSpacing = style.letterSpacing
            if (style.hasShadow) {
                // setShadowLayer: radius 越大越模糊，dx/dy 为偏移
                setShadowLayer(style.shadowRadius, 2f, 2f, style.shadowColor)
            }
        }
    }

    /**
     * 构建描边画笔（STROKE 模式，无阴影）
     */
    private fun buildStrokePaint(style: TextStyle): TextPaint {
        return TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = style.strokeColor
            textSize = style.fontSize
            typeface = resolveTypeface(style)
            letterSpacing = style.letterSpacing
            this.style = Paint.Style.STROKE
            strokeWidth = style.strokeWidth
            // 描边不带阴影，避免与填充阴影叠加产生模糊
        }
    }

    /**
     * 构建 StaticLayout（多行文字布局）
     */
    private fun buildLayout(
        text: String,
        paint: TextPaint,
        style: TextStyle,
        width: Int
    ): StaticLayout {
        return StaticLayout.Builder
            .obtain(text, 0, text.length, paint, width)
            .setAlignment(alignToLayout(style.alignment))
            .setLineSpacing(0f, style.lineHeight)
            .setIncludePad(false)
            .build()
    }

    /**
     * Paint.Align 映射到 Layout.Alignment
     */
    private fun alignToLayout(align: Paint.Align): Layout.Alignment {
        return when (align) {
            Paint.Align.LEFT -> Layout.Alignment.ALIGN_NORMAL
            Paint.Align.CENTER -> Layout.Alignment.ALIGN_CENTER
            Paint.Align.RIGHT -> Layout.Alignment.ALIGN_OPPOSITE
        }
    }

    /**
     * 解析字体族 + 粗体/斜体样式，返回最终 Typeface
     */
    private fun resolveTypeface(style: TextStyle): Typeface {
        val styleFlag = when {
            style.isBold && style.isItalic -> Typeface.BOLD_ITALIC
            style.isBold -> Typeface.BOLD
            style.isItalic -> Typeface.ITALIC
            else -> Typeface.NORMAL
        }
        return if (style.fontFamily != null) {
            // Typeface.create 在字体族不存在时返回默认字体，不会抛异常
            Typeface.create(style.fontFamily, styleFlag)
        } else {
            Typeface.create(Typeface.DEFAULT, styleFlag)
        }
    }
}
