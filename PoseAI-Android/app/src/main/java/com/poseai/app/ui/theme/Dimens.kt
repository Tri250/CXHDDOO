package com.poseai.app.ui.theme

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 设计系统 Token（对齐国内手机摄影 App 审美规范）
 *
 * 设计原则：
 * 1. 中文最小可读字号 12sp，关键信息 13sp+
 * 2. 间距采用 4dp 基准栅格，关键留白用 8/12/16/20/24
 * 3. 圆角统一为大圆角风格（国内主流：12/16/20/24）
 * 4. 行高 = 字号 × 1.5（中文黄金行高比）
 */
object Dimens {

    // ── 间距 Spacing（4dp 栅格）──
    /** 微间距：图标与文字间 */
    val spacingXs = 4.dp
    /** 小间距：标签内边距、紧凑行间距 */
    val spacingSm = 8.dp
    /** 中间距：卡片内边距、组件间距 */
    val spacingMd = 12.dp
    /** 标准间距：区块间距、列表项间距 */
    val spacingLg = 16.dp
    /** 大间距：区块间留白 */
    val spacingXl = 20.dp
    /** 特大间距：屏幕水平边距 */
    val spacingXxl = 24.dp
    /** 屏幕水平安全边距 */
    val screenMarginH = 20.dp

    // ── 圆角 Radius ──
    /** 小圆角：徽章、小标签 */
    val radiusSm = 8.dp
    /** 中圆角：卡片、输入框 */
    val radiusMd = 12.dp
    /** 大圆角：面板、底部弹窗内容 */
    val radiusLg = 16.dp
    /** 特大圆角：大卡片、沉浸容器 */
    val radiusXl = 20.dp
    /** 全圆角：胶囊形按钮、药丸标签 */
    val radiusFull = 9999.dp

    // ── 字号 FontSize（中文友好）──
    /** 辅助说明文字（最小可读） */
    val fontCaption = 12.sp
    /** 标签、徽章文字 */
    val fontLabel = 13.sp
    /** 正文、按钮文字 */
    val fontBody = 14.sp
    /** 标题、强调正文 */
    val fontTitle = 16.sp
    /** 大标题 */
    val fontHeadline = 18.sp
    /** 页面标题 */
    val fontDisplay = 22.sp
    /** 倒计时大数字 */
    val fontCountdown = 120.sp

    // ── 行高 LineHeight（字号 × 1.5 中文黄金比）──
    val lineHeightCaption = 18.sp   // 12 × 1.5
    val lineHeightLabel = 20.sp     // 13 × 1.5
    val lineHeightBody = 21.sp      // 14 × 1.5
    val lineHeightTitle = 24.sp     // 16 × 1.5
    val lineHeightHeadline = 27.sp  // 18 × 1.5
    val lineHeightDisplay = 33.sp   // 22 × 1.5

    // ── 图标尺寸 IconSize ──
    /** 小图标：徽章内 */
    val iconXs = 14.dp
    /** 中图标：按钮内 */
    val iconSm = 18.dp
    /** 标准图标：工具栏 */
    val iconMd = 20.dp
    /** 大图标：功能入口 */
    val iconLg = 24.dp

    // ── 组件尺寸 ComponentSize ──
    /** 工具栏圆形按钮 */
    val buttonIcon = 40.dp
    /** 底部功能按钮 */
    val buttonAction = 44.dp
    /** 快门按钮（标准屏） */
    val shutterSize = 88.dp
    /** 快门内圆 */
    val shutterInner = 68.dp
    /** 相册缩略图 */
    val thumbSize = 50.dp
    /** 分数环 */
    val scoreRingSize = 54.dp

    // ── 边框宽度 StrokeWidth ──
    val strokeThin = 1.dp
    val strokeRegular = 1.5.dp
    val strokeThick = 2.dp
    val strokeBold = 2.5.dp

    // ── 动画时长 Duration（ms）──
    /** 微交互：颜色切换、状态变化 */
    val durationFast = 200
    /** 标准过渡：显隐、位移 */
    val durationNormal = 300
    /** 强调动画：进场、弹性 */
    val durationSlow = 450
    /** 呼吸/脉冲周期 */
    val durationBreathing = 1100
    val durationPulse = 1800
}
