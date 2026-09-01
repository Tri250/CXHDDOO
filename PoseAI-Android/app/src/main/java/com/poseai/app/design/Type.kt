package com.poseai.app.design

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.poseai.app.R

/**
 * 统一排版字体体系（Doka 极简风格）。
 *
 * 字体策略：
 *  - 拉丁字母 / 数字 / 英文：使用内置的 Inter 可变字体（无 × 全字重）
 *  - 中日韩文字：自动回退到系统默认（Noto Sans CJK），保持干净利落
 *  - 数字采用等宽/表格数字，计时、评分、倒计时更稳定对齐
 *
 * 字阶从大到小收敛为 8 级，替代原先散落的 10~130sp 硬编码。
 * 全部 Text 组件通过 MaterialTheme 的 defaultFontFamily 自动继承 Inter，
 * 无需逐一传 fontFamily。
 */
object Type {
    /** Inter 可变字体家族：同一字体文件按字重注册，由 Compose 沿 wght 轴插值 */
    val Inter = FontFamily(
        Font(R.font.inter, FontWeight.Light),
        Font(R.font.inter, FontWeight.Normal),
        Font(R.font.inter, FontWeight.Medium),
        Font(R.font.inter, FontWeight.SemiBold),
        Font(R.font.inter, FontWeight.Bold),
        Font(R.font.inter, FontWeight.ExtraBold),
        Font(R.font.inter, FontWeight.Black),
    )

    // 大数字：倒计时 / 快门计数
    val display: TextStyle = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Light,
        fontSize = 112.sp,
        lineHeight = 112.sp,
        letterSpacing = (-2).sp,
    )

    // 超大标题：页面主标题 / 引导页
    val titleLarge: TextStyle = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
    )

    // 标题：区块标题 / 卡片标题
    val title: TextStyle = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 22.sp,
    )

    // 正文强调：按钮 / 强调文本
    val headline: TextStyle = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp,
    )

    // 正文：默认阅读文本
    val body: TextStyle = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 21.sp,
    )

    // 正文次级
    val bodySecondary: TextStyle = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    )

    // 说明 / 标签
    val caption: TextStyle = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    )

    // 极弱标签 / 角标
    val label: TextStyle = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
    )

    /** Material Theme 排版（继承 Inter，供全局 defaultFontFamily 使用） */
    fun material(): Typography = Typography(
        displayLarge = display,
        headlineLarge = titleLarge,
        titleLarge = title,
        titleMedium = headline,
        bodyLarge = body,
        bodyMedium = bodySecondary,
        labelLarge = caption,
        labelSmall = label,
    )
}