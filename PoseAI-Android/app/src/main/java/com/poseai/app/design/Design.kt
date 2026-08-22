package com.poseai.app.design

import androidx.compose.ui.graphics.Color

/**
 * 品牌设计常量——与 iOS 端保持一致的全暗色高级质感主题
 */
object Brand {
    // 主色
    val Accent = Color(0xFF7F53FF)        // 品牌紫
    val AccentSoft = Color(0xFFB59BFF)
    val Gold = Color(0xFFFFC857)          // 得分/高光金色
    val Coral = Color(0xFFFF6B6B)         // 提示/警示

    // 背景层级
    val Screen = Color(0xFF000000)        // 全黑背景
    val Surface = Color(0xFF12121A)
    val SurfaceHigh = Color(0xFF1E1E2A)
    val SurfaceLow = Color(0xFF0A0A10)

    // 文本
    val TextPrimary = Color(0xFFFFFFFF)
    val TextSecondary = Color(0xFFAAAAAA)
    val TextMuted = Color(0xFF666677)

    // 边框与分割线
    val Hairline = Color(0xFF2A2A38)

    // 反馈
    val Success = Color(0xFF3BD69A)
    val ShutterWarm = Color(0xFFFFF3E0)   // 柔和屏幕补光暖白色

    /** iOS 端与 Android 端的圆角半径体系 */
    object Radius {
        val Small = 8F
        val Medium = 16F
        val Large = 24F
        val Pill = 999F
    }
}