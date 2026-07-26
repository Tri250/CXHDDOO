package com.poseai.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * 字体设计系统（中文友好）
 *
 * 中文排版规范：
 * - 行高 = 字号 × 1.5（中文黄金行高比，避免拥挤）
 * - 字间距 letterSpacing = 0.5sp（中文适度松散，提升呼吸感）
 * - 最小可读字号 12sp（中文笔画密集，低于 12sp 难以辨认）
 * - 标题用 SemiBold/Bold，正文用 Normal/Medium（中文笔画粗，避免 Light）
 */
val PoseAITypography = Typography(
    // ── Display：页面大标题、倒计时 ──
    displayLarge = TextStyle(
        fontSize = 32.sp,
        lineHeight = 48.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.5.sp
    ),
    displayMedium = TextStyle(
        fontSize = 22.sp,
        lineHeight = 33.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.5.sp
    ),
    displaySmall = TextStyle(
        fontSize = 18.sp,
        lineHeight = 27.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.4.sp
    ),

    // ── Headline：区域标题 ──
    headlineLarge = TextStyle(
        fontSize = 24.sp,
        lineHeight = 36.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.4.sp
    ),
    headlineMedium = TextStyle(
        fontSize = 20.sp,
        lineHeight = 30.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.3.sp
    ),
    headlineSmall = TextStyle(
        fontSize = 18.sp,
        lineHeight = 27.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.3.sp
    ),

    // ── Title：卡片标题、强调信息 ──
    titleLarge = TextStyle(
        fontSize = 16.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.2.sp
    ),
    titleMedium = TextStyle(
        fontSize = 14.sp,
        lineHeight = 21.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.2.sp
    ),
    titleSmall = TextStyle(
        fontSize = 13.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.2.sp
    ),

    // ── Body：正文内容 ──
    bodyLarge = TextStyle(
        fontSize = 16.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.3.sp
    ),
    bodyMedium = TextStyle(
        fontSize = 14.sp,
        lineHeight = 21.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.3.sp
    ),
    bodySmall = TextStyle(
        fontSize = 12.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.3.sp
    ),

    // ── Label：标签、按钮、徽章 ──
    labelLarge = TextStyle(
        fontSize = 14.sp,
        lineHeight = 21.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.3.sp
    ),
    labelMedium = TextStyle(
        fontSize = 12.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.3.sp
    ),
    labelSmall = TextStyle(
        fontSize = 12.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.2.sp
    )
)
