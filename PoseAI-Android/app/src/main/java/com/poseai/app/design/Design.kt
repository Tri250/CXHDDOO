package com.poseai.app.design

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * Doka 风格设计语言 —— 极简、纯粹、少即是多。
 *
 * 与 iOS 端保持一致的全暗色主题，但配色由「暖金 + 玻璃拟态」转向：
 *   - 中性黑白灰（近黑背景、纯白文本、细腻灰阶）
 *   - 仅用少量功能性信号色（绿=对齐就绪 / 红=录制警示 / 琥珀=暗光提示）
 *   - 去除花哨发光与半透明玻璃，改用实心灰阶层级
 */
object Brand {
    // ─── 主色：中性白作为唯一的强调色（Doka 主 CTA = 单色白） ───
    val Accent = Color(0xFFFAFAFA)        // 中性白：快门 / 主按钮 / 激活态
    val AccentGlow = Color(0x1AFAFAFA)    // 极淡氛围光（去发光，仅保留微弱边缘）
    val AccentSoft = Color(0xFFE6E6EA)    // 柔白
    val AccentDeep = Color(0xFFC2C2C8)    // 深白灰
    val Gold = Color(0xFFEDEDED)          // 得分/高光（中性亮灰，替代暖金）

    // ─── 功能性信号色（克制、去饱和） ───
    val Success = Color(0xFF5ECFA0)       // 对齐就绪：去饱和绿
    val SuccessGlow = Color(0x335ECFA0)
    val Danger = Color(0xFFFF6B6B)        // 录制/危险：去饱和红
    val Warning = Color(0xFFE0B76A)       // 暗光提示：去饱和琥珀
    val Coral = Color(0xFFFF7A7A)         // 录制中 / 提示
    val ShutterWarm = Color(0xFFFFF6EA)   // 柔和屏幕补光暖白

    // ─── 背景层级：实心灰阶（替换原 white-alpha 玻璃层） ───
    val Screen = Color(0xFF000000)        // 纯黑
    val Surface = Color(0xFF151518)       // 一级实心面板
    val SurfaceStrong = Color(0xFF1C1C20) // 二级面板
    val SurfaceHigh = Color(0xFF252529)   // 三级面板 / 输入框
    val SurfaceLow = Color(0xFF0A0A0C)    // 更深背景层

    // ─── 边框与分割线：实心细线 ───
    val Border = Color(0xFF2A2A2E)        // 标准描边
    val BorderActive = Color(0xFF3F3F44)  // 激活/强调描边
    val Hairline = Color(0xFF232327)      // 分割线
    val HairlineOpaque = Color(0xFF2E2E32)

    // ─── 文本颜色：中性灰阶标签体系 ───
    val TextPrimary = Color(0xFFFFFFFF)   // 主文本
    val TextSecondary = Color(0xFF9A9AA0) // 次级文本
    val TextTertiary = Color(0xFF6E6E74)  // 三级文本
    val TextMuted = Color(0xFF4A4A4E)     // 弱化文本

    // ─── 半透明遮罩色 ───
    val OverlayDark = Color(0x8C000000)   // 55% 黑
    val OverlayMedium = Color(0x99000000) // 60% 黑
    val OverlayLight = Color(0x66000000)  // 40% 黑

    // ─── AI 紫色：去饱和的高级紫（保持品牌识别但更克制） ───
    val AI_Purple = Color(0xFFB9A6E8)
    val AI_PurpleGlow = Color(0x26B9A6E8)

    /** 圆角半径体系 */
    object Radius {
        val ExtraSmall = 6F
        val Small = 10F
        val Medium = 14F
        val Large = 18F
        val ExtraLarge = 24F
        val Pill = 999F

        val Xs = ExtraSmall.dp
        val Sm = Small.dp
        val Md = Medium.dp
        val Lg = Large.dp
        val Xl = ExtraLarge.dp

        fun smallShape(): Shape = RoundedCornerShape(Small.dp)
        fun mediumShape(): Shape = RoundedCornerShape(Medium.dp)
        fun largeShape(): Shape = RoundedCornerShape(Large.dp)
    }

    /** 间距体系 */
    object Spacing {
        val Xs = 2.dp
        val Sm = 4.dp
        val Md = 8.dp
        val Lg = 12.dp
        val Xl = 16.dp
        val Xxl = 24.dp
        val Section = 32.dp
    }

    /** 动画时长体系（Doka 克制：短、稳、不拖沓） */
    object Duration {
        val Instant = 50
        val Fast = 150
        val Normal = 220
        val Slow = 340
        val Emphasis = 480
    }

    /** 统一定义缓动曲线，替代散落的默认缓动 */
    object Easing {
        val Standard: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)      // 标准进出（Material）
        val Emphasized: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f) // 强调减速
        val Decelerate: Easing = CubicBezierEasing(0f, 0f, 0.2f, 1f)      // 减速入场
        val Accelerate: Easing = CubicBezierEasing(0.3f, 0f, 1f, 1f)      // 加速离场
    }
}