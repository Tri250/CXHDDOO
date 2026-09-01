package com.poseai.app.design

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.AlignHorizontalCenter
import androidx.compose.material.icons.filled.AlignHorizontalLeft
import androidx.compose.material.icons.filled.AlignHorizontalRight
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BeachAccess
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.FilterCenterFocus
import androidx.compose.material.icons.filled.Forest
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.LocationCity
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Park
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.ui.graphics.vector.ImageVector
import com.poseai.app.model.CompositionRule
import com.poseai.app.model.FrameRatio
import com.poseai.app.model.SceneType

/**
 * 统一图标映射 —— 拍摄页 (Android) 的矢量图标中枢。
 *
 * 背景：模型层 [SceneType.icon] / [CompositionRule.icon] / [FrameRatio.icon] 沿用了
 * iOS SF Symbol 字符串 (如 "cup.and.saucer.fill")。Android 端若直接用 Text 渲染这些
 * 字符串,会显示成原始文本,严重不专业。本文件将这些 SF Symbol 名映射到 Material
 * 矢量图标,并对拍摄页用到的 UI 动作图标做集中维护,使整体视觉风格对齐国内主流相机
 * APP (美图 / B612 / 醒图 / 无他) 的矢量图标体系。
 *
 * 注意:不修改模型层枚举字符串,以保持与 iOS 端共享的 [Enums.kt] 一致。
 */
object AppIcons {
    // ─── 顶部栏 / 底部控制行 动作图标 ───
    val History = Icons.Outlined.PhotoLibrary       // 历史相册入口
    val FlipCamera = Icons.Filled.Cameraswitch      // 前后摄像头切换
    val Timer = Icons.Outlined.Timer                // 倒计时
    val Help = Icons.Outlined.HelpOutline           // 帮助 / 引导
    val ShutterReady = Icons.Filled.PhotoCamera    // 快门就绪时显示的相机图标
    val RecordCustom = Icons.Filled.AddAPhoto      // 录制专属方案入口
    val RecordingDot = Icons.Filled.FiberManualRecord // 录制中圆点

    // ─── 浮层提示图标 ───
    val AiSparkle = Icons.Filled.AutoAwesome        // AI 灵感
    val Lightbulb = Icons.Filled.Lightbulb          // 暗光提示
    val Footprint = Icons.Filled.DirectionsWalk     // AR 站位脚印
    val Warning = Icons.Filled.Warning              // 俯仰机位警告
}

/** 场景类型 → Material 矢量图标 (替代 iOS "cup.and.saucer.fill" 等 SF Symbol 字符串) */
val SceneType.materialIcon: ImageVector
    get() = when (this) {
        SceneType.COFFEE_SHOP -> Icons.Filled.LocalCafe
        SceneType.BEACH -> Icons.Filled.BeachAccess
        SceneType.FOREST -> Icons.Filled.Forest
        SceneType.CITY_STREET -> Icons.Filled.LocationCity
        SceneType.PARK -> Icons.Filled.Park
        SceneType.INDOOR_HOME -> Icons.Filled.Home
        SceneType.NEON_NIGHT -> Icons.Filled.NightsStay
        SceneType.UNKNOWN -> Icons.Filled.FilterCenterFocus
    }

/** 人物比例 → Material 矢量图标 (替代 "figure.stand" 等) */
val FrameRatio.materialIcon: ImageVector
    get() = when (this) {
        FrameRatio.FULL_BODY -> Icons.Filled.AccessibilityNew
        FrameRatio.HALF_BODY -> Icons.Filled.Accessibility
        FrameRatio.PORTRAIT -> Icons.Filled.AccountCircle
    }

/** 构图规则 → Material 矢量图标 (替代 "rectangle.center.inset.filled" 等) */
val CompositionRule.materialIcon: ImageVector
    get() = when (this) {
        CompositionRule.CENTER -> Icons.Filled.CenterFocusStrong
        CompositionRule.LEFT_THIRD,
        CompositionRule.GOLDEN_LEFT -> Icons.Filled.AlignHorizontalLeft
        CompositionRule.RIGHT_THIRD,
        CompositionRule.GOLDEN_RIGHT -> Icons.Filled.AlignHorizontalRight
    }
