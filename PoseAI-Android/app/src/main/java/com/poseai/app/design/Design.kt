package com.poseai.app.design

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * 品牌设计常量——与 iOS 端保持一致的全暗色高级质感主题。
 * 主色调：暖金 (#FFD073) + 深黑背景，复刻 iOS Design 常量。
 */
object Brand {
    // ─── 主色（对齐 iOS Design.accent: 暖金 #FFD073） ───
    val Accent = Color(0xFFFFD073)        // 品牌暖金 (iOS Design.accent)
    val AccentGlow = Color(0x55FFD073)    // 发光效果 (iOS Design.accentGlow, 35% 透明度)
    val AccentSoft = Color(0xFFFFE4A0)    // 柔金色
    val AccentDeep = Color(0xFFE6B84A)    // 深金色
    val Gold = Color(0xFFFFC857)          // 得分/高光金色
    val Coral = Color(0xFFFF6161)         // 提示/警示 (iOS Design.danger)

    // ─── 反馈色（对齐 iOS Design） ───
    val Success = Color(0xFF59F299)       // iOS Design.success 亮绿
    val SuccessGlow = Color(0x5559F299)   // iOS Design.successGlow 35%
    val Danger = Color(0xFFFF6161)        // iOS Design.danger 火红
    val Warning = Color(0xFFFFC857)      // iOS systemYellow
    val ShutterWarm = Color(0xFFFFF3E0)  // 柔和屏幕补光暖白色

    // ─── 背景层级 (iOS systemBackground 映射) ───
    val Screen = Color(0xFF000000)        // iOS .black
    val Surface = Color(0x14FFFFFF)       // iOS Design.surface (white 8%)
    val SurfaceStrong = Color(0x26FFFFFF) // iOS Design.surfaceStrong (white 15%)
    val SurfaceHigh = Color(0xFF1E1E2A)   // iOS .tertiarySystemBackground
    val SurfaceLow = Color(0xFF0A0A10)    // 更深的背景层

    // ─── 边框与分割线 ───
    val Border = Color(0x2EFFFFFF)       // iOS Design.border (white 18%)
    val BorderActive = Color(0xBFFFFFFF)  // iOS Design.borderActive (white 75%)
    val Hairline = Color(0xFF2A2A38)      // iOS .separator
    val HairlineOpaque = Color(0xFF38384A) // iOS .opaqueSeparator

    // ─── 文本颜色 (iOS label 体系) ───
    val TextPrimary = Color(0xFFFFFFFF)   // iOS Design.textPrimary
    val TextSecondary = Color(0x8CFFFFFF) // iOS Design.textSecondary (white 55%)
    val TextTertiary = Color(0xFF7A7A8C)  // iOS .tertiaryLabel
    val TextMuted = Color(0xFF666677)     // iOS .quaternaryLabel

    // ─── 半透明遮罩色 ───
    val OverlayDark = Color(0x8C000000)   // iOS Design.overlayBg (black 55%)
    val OverlayMedium = Color(0x99000000) // 60% 黑
    val OverlayLight = Color(0x66000000)  // 40% 黑

    // ─── AI 紫色（iOS AI Advisor 紫） ───
    val AI_Purple = Color(0xFFBF99FF)     // iOS AI 高级紫光
    val AI_PurpleGlow = Color(0x26BF99FF) // AI 紫光发光

    /** iOS 端与 Android 端的圆角半径体系 — 对应 iOS UIRCornerRadius */
    object Radius {
        val ExtraSmall = 6F    // iOS 4px / 6pt
        val Small = 10F        // iOS 8px / 10pt
        val Medium = 14F       // iOS 12px / 14pt
        val Large = 18F        // iOS Design.cornerCard (18pt)
        val ExtraLarge = 24F   // iOS 20px / 24pt
        val Pill = 999F

        /** Material 对应尺寸 (dp) */
        val Xs = ExtraSmall.dp
        val Sm = Small.dp
        val Md = Medium.dp
        val Lg = Large.dp
        val Xl = ExtraLarge.dp

        fun smallShape(): Shape = RoundedCornerShape(Small.dp)
        fun mediumShape(): Shape = RoundedCornerShape(Medium.dp)
        fun largeShape(): Shape = RoundedCornerShape(Large.dp)
    }

    /** iOS 风格间距体系 — 对应 iOS layout constants */
    object Spacing {
        val Xs = 2.dp    // 4pt
        val Sm = 4.dp    // 8pt
        val Md = 8.dp    // 12pt
        val Lg = 12.dp   // 16pt
        val Xl = 16.dp   // 20pt
        val Xxl = 24.dp  // 28pt
        val Section = 32.dp
    }

    /** iOS 动画时长体系 — 对应 iOS UIKit 动画曲线 */
    object Duration {
        val Instant = 50   // 即时反馈
        val Fast = 150     // 快速过渡
        val Normal = 250   // 标准过渡 (iOS default)
        val Slow = 400     // 慢速过渡
        val Emphasis = 500 // 强调动画
    }
}