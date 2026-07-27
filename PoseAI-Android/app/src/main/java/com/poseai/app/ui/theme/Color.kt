package com.poseai.app.ui.theme

import androidx.compose.ui.graphics.Color

// ── 品牌设计常量（对齐 iOS Design enum）──
// 主题色：青墨绿 + 深空黑

// 共用色（明暗通用）
val Accent = Color(0xFF0D9488)           // #0D9488 青墨绿
val AccentGlow = Color(0x590D9488)       // 青墨绿发光 (35% opacity)
val Success = Color(0xFF1ABF8C)          // #1ABF8C 翡翠绿
val SuccessGlow = Color(0x591ABF8C)      // 翡翠绿发光 (35% opacity)
val Danger = Color(0xFFF25959)           // #F25959 珊瑚红
val Warning = Color(0xFFFFC107)
val Error = Color(0xFFE53935)

// ── 暗色模式 ──
val DeepSpaceBlack = Color(0xFF0A0F0D)   // 深空黑底色
val Surface = Color(0x14FFFFFF)          // white 0.08
val SurfaceStrong = Color(0x26FFFFFF)    // white 0.15
val Border = Color(0x2EFFFFFF)           // white 0.18
val BorderActive = Color(0xBFFFFFFF)     // white 0.75
val TextPrimary = Color(0xFFFFFFFF)      // white
val TextSecondary = Color(0x8CFFFFFF)    // white 0.55
val OverlayBg = Color(0x8C000000)        // black 0.55
val BackgroundDark = Color(0xFF0A0F0D)   // 深空黑背景
val SurfaceDark = Color(0xFF1A1F1D)      // 深空黑表面
val SurfaceGlass = Color(0x33FFFFFF)     // 玻璃态

// ── 亮色模式 ──
val BackgroundLight = Color(0xFFF5F5F5)  // 浅灰白背景
val SurfaceLight = Color(0xFFFFFFFF)      // 纯白表面
val SurfaceLightStrong = Color(0xFFF0F0F0) // 浅灰表面
val BorderLight = Color(0xFFE0E0E0)       // 浅灰边框
val BorderLightActive = Color(0xFF0D9488) // 青墨绿激活边框
val TextPrimaryLight = Color(0xFF1A1A1A)  // 深灰正文
val TextSecondaryLight = Color(0xFF8C8C8C) // 浅灰辅文
val OverlayLightBg = Color(0x8CFFFFFF)     // white 0.55 遮罩
val SurfaceGlassLight = Color(0x1A000000)  // 黑 0.1 玻璃态
