package com.poseai.app.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Typeface

/**
 * 已放置的贴纸配置
 *
 * @param stickerBitmap 贴纸位图
 * @param x 中心 X 坐标（0-1 归一化，相对于位图宽度）
 * @param y 中心 Y 坐标（0-1 归一化，相对于位图高度）
 * @param scale 缩放倍数（1.0 = 原始尺寸）
 * @param rotation 旋转角度（度）
 * @param opacity 不透明度 [0,1]
 * @param flipH 是否水平翻转
 */
data class PlacedSticker(
    val stickerBitmap: Bitmap,
    val x: Float,
    val y: Float,
    val scale: Float = 1f,
    val rotation: Float = 0f,
    val opacity: Float = 1f,
    val flipH: Boolean = false
)

/**
 * 贴纸放置引擎
 *
 * 在位图上放置贴纸（图片 / Emoji / 文字），支持：
 * - 位置（归一化中心坐标）
 * - 缩放（含水平翻转）
 * - 旋转
 * - 不透明度（PorterDuff.Mode.SRC_OVER + alpha）
 *
 * 变换通过 Matrix 实现：平移到中心 → 缩放（含翻转）→ 旋转 → 平移到目标位置
 */
class StickerPlacementEngine {

    /**
     * 在位图上叠加多个贴纸
     *
     * @param bitmap 原始位图
     * @param stickers 贴纸列表
     * @return 叠加后的新位图（不修改原图）
     */
    fun applyStickers(bitmap: Bitmap, stickers: List<PlacedSticker>): Bitmap {
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        val W = result.width.toFloat()
        val H = result.height.toFloat()

        for (sticker in stickers) {
            drawSticker(canvas, sticker, W, H)
        }
        return result
    }

    /**
     * 从 Emoji 生成贴纸位图
     * 使用系统字体渲染彩色 Emoji 到透明背景位图
     *
     * @param emoji Emoji 字符（如 "😀"）
     * @param size 输出位图边长（像素）
     * @return ARGB_8888 贴纸位图
     */
    fun createEmojiSticker(emoji: String, size: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size * 0.8f
            typeface = Typeface.DEFAULT
        }
        // 居中绘制
        val textWidth = paint.measureText(emoji)
        val fm = paint.fontMetrics
        val textHeight = fm.descent - fm.ascent
        val x = (size - textWidth) / 2f
        val y = (size - textHeight) / 2f - fm.ascent
        canvas.drawText(emoji, x, y, paint)
        return bitmap
    }

    /**
     * 从文字生成贴纸位图
     * 带阴影，背景透明
     *
     * @param text 文字内容
     * @param color 文字颜色
     * @param size 字体大小（像素）
     * @return ARGB_8888 贴纸位图（尺寸自适应文字）
     */
    fun createTextSticker(text: String, color: Int, size: Int): Bitmap {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            textSize = size.toFloat()
            typeface = Typeface.DEFAULT_BOLD
            // 阴影增强可读性
            setShadowLayer(size * 0.1f, 2f, 2f, Color.BLACK)
        }
        val textWidth = paint.measureText(text)
        val fm = paint.fontMetrics
        val textHeight = fm.descent - fm.ascent
        val padding = size * 0.2f

        val bitmap = Bitmap.createBitmap(
            (textWidth + padding * 2).toInt().coerceAtLeast(1),
            (textHeight + padding * 2).toInt().coerceAtLeast(1),
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        // baseline 位于 padding - fm.ascent（使文字顶部留出 padding）
        canvas.drawText(text, padding, padding - fm.ascent, paint)
        return bitmap
    }

    /**
     * 内置 Emoji 贴纸列表
     * 涵盖表情、手势、爱心、装饰等常用类别
     */
    fun getEmojiStickers(): List<String> {
        return listOf(
            // 表情
            "😀", "😍", "🥰", "😎", "🤩", "😂", "🥳", "😇",
            "🤔", "😴", "🤯", "🥺", "😏", "😜", "🤗", "😑",
            // 手势
            "👍", "👏", "🙌", "💪", "🤝", "✌️", "🤞", "🙏",
            "👌", "🤟", "👋", "✋", "👏", "🫶", "🤙", "👈",
            // 爱心与符号
            "❤️", "💔", "💕", "💖", "💯", "🔥", "⭐", "✨",
            "💫", "💥", "💦", "💨", "🎵", "🎶", "💬", "💢",
            // 庆祝与物品
            "🎉", "🎊", "🎈", "🎁", "🏆", "🥇", "👑", "💎",
            "🎯", "🌟", "🌈", "☀️", "🌙", "⚡", "❄️", "🌸",
            // 自然
            "🌺", "🌻", "🌹", "🍀", "🌿", "🌴", "🍃", "🌷",
            "🐶", "🐱", "🦄", "🦋", "🐝", "🐢", "🦊", "🐰"
        )
    }

    // ═══════════════════════════════════════════════════════════════
    // 内部实现
    // ═══════════════════════════════════════════════════════════════

    /**
     * 绘制单个贴纸
     *
     * Matrix 变换顺序（后乘 = 先应用）：
     * 1. 平移到贴纸中心负值（使贴纸中心位于原点）
     * 2. 缩放（含水平翻转）
     * 3. 旋转
     * 4. 平移到目标位置
     */
    private fun drawSticker(
        canvas: Canvas,
        sticker: PlacedSticker,
        bitmapW: Float,
        bitmapH: Float
    ) {
        val cx = sticker.x * bitmapW
        val cy = sticker.y * bitmapH
        val stickerBmp = sticker.stickerBitmap

        val matrix = Matrix()
        // 1. 平移到贴纸中心（使旋转/缩放围绕中心进行）
        matrix.postTranslate(
            -stickerBmp.width / 2f,
            -stickerBmp.height / 2f
        )
        // 2. 缩放（水平翻转时 X 取负）
        val sx = if (sticker.flipH) -sticker.scale else sticker.scale
        matrix.postScale(sx, sticker.scale)
        // 3. 旋转
        matrix.postRotate(sticker.rotation)
        // 4. 平移到目标位置
        matrix.postTranslate(cx, cy)

        // 不透明度通过 alpha 实现（PorterDuff.Mode.SRC_OVER 为默认模式）
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        paint.alpha = (sticker.opacity.coerceIn(0f, 1f) * 255).toInt()
        // 显式设置混合模式（默认即 SRC_OVER，此处仅为表明使用 PorterDuff）
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_OVER)

        canvas.drawBitmap(stickerBmp, matrix, paint)
    }
}
