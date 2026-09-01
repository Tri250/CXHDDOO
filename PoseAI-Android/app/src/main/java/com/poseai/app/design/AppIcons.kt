package com.poseai.app.design

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Accessibility
import androidx.compose.material.icons.outlined.AccessibilityNew
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AlignHorizontalCenter
import androidx.compose.material.icons.outlined.AlignHorizontalLeft
import androidx.compose.material.icons.outlined.AlignHorizontalRight
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Cameraswitch
import androidx.compose.material.icons.outlined.CenterFocusStrong
import androidx.compose.material.icons.outlined.CropFree
import androidx.compose.material.icons.outlined.CropSquare
import androidx.compose.material.icons.outlined.Eco
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.FiberManualRecord
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.LocalCafe
import androidx.compose.material.icons.outlined.LocationCity
import androidx.compose.material.icons.outlined.NightsStay
import androidx.compose.material.icons.outlined.Park
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.RadioButtonChecked
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.outlined.Waves
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * SF Symbols → Material 矢量图标映射。
 *
 * 数据模型中的 icon 字段（SceneType / CompositionRule / FrameRatio 等）沿用 iOS 的
 * SF Symbols 名称，Android 端不能直接渲染为文本。统一在此映射为 Material 矢量图标，
 * 保证拍摄页及组件图标清晰、专业且与国内主流相机 App 视觉一致。
 */
object AppIcons {

    /** 场景图标映射 */
    fun scene(name: String): ImageVector = when (name) {
        "cup.and.saucer.fill" -> Icons.Outlined.LocalCafe
        "figure.pool.swim" -> Icons.Outlined.Waves
        "tree.fill" -> Icons.Outlined.Park
        "building.2.fill" -> Icons.Outlined.LocationCity
        "leaf.fill" -> Icons.Outlined.Eco
        "house.fill" -> Icons.Outlined.Home
        "moon.stars.fill" -> Icons.Outlined.NightsStay
        "viewfinder" -> Icons.Outlined.CenterFocusStrong
        else -> Icons.Outlined.PhotoCamera
    }

    /** 构图规则图标映射 */
    fun composition(name: String): ImageVector = when (name) {
        "rectangle.center.inset.filled" -> Icons.Outlined.CropSquare
        "rectangle.lefthalf.inset.filled" -> Icons.Outlined.AlignHorizontalLeft
        "rectangle.righthalf.inset.filled" -> Icons.Outlined.AlignHorizontalRight
        "align.horizontal.left" -> Icons.Outlined.AlignHorizontalLeft
        "align.horizontal.right" -> Icons.Outlined.AlignHorizontalRight
        else -> Icons.Outlined.AlignHorizontalCenter
    }

    /** 画幅比例图标映射 */
    fun frameRatio(name: String): ImageVector = when (name) {
        "figure.stand" -> Icons.Outlined.Accessibility
        "figure.arms.open" -> Icons.Outlined.AccessibilityNew
        "person.crop.circle" -> Icons.Outlined.Face
        else -> Icons.Outlined.Person
    }

    // ─── 拍摄页固定功能图标 ───

    val Gallery get() = Icons.Outlined.PhotoLibrary          // 相册 / 历史缩略图
    val FlipCamera get() = Icons.Outlined.Cameraswitch       // 前后置切换
    val Timer get() = Icons.Outlined.Timer                   // 倒计时
    val Help get() = Icons.Outlined.HelpOutline              // 拍摄指引
    val AiInspiration get() = Icons.Outlined.AutoAwesome     // AI 构图灵感
    val LowLight get() = Icons.Outlined.Lightbulb            // 暗光提示
    val PitchWarning get() = Icons.Outlined.WarningAmber     // 俯拍警告
    val SpaceTip get() = Icons.Outlined.CropFree             // 留白提示
    val AddPlan get() = Icons.Outlined.Add                   // 录制专属入口
    val Recording get() = Icons.Outlined.FiberManualRecord   // 录制中状态
    val RecordingReady get() = Icons.Outlined.RadioButtonChecked // 可录制状态
}
