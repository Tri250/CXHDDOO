package com.poseai.app.model

import kotlin.math.roundToInt

/**
 * 二维点，坐标归一化到 [0,1]，x 向右，y 向上（与 iOS 一致：Vision 坐标系 y 向上）
 */
data class NormPoint(val x: Float, val y: Float)

/** 自定义方案：用户可以保存自己的拍摄方案 */
data class CustomPlan(
    val id: String = "",
    val name: String,
    val emoji: String,
    val description: String,
    val composition: CompositionRule,
    val frameRatio: FrameRatio,
    val voiceGuide: String,
    val posePoints: Map<String, NormPoint>
)

/** 一次性拍摄记录 */
data class ShotResult(
    val fileUri: String,
    val thumbnailUri: String,
    val score: Int,
    val filterName: String,
    val shotAt: Long,
    val ratioName: String
)

/** Step 10: 动作连拍序列帧 */
data class ActionFrame(
    val emoji: String,
    val title: String,
    val voiceHint: String,
    val posePoints: Map<String, NormPoint>
)

/** Step 12: 多机位拍摄角度 */
data class CameraAngle(
    val title: String,
    val voiceHint: String,
    val requiredPitch: Float?,   // 要求的俯仰值，null 表示无约束
    val posePoints: Map<String, NormPoint>?
)

/** Step 13: Vlog 录播引擎分镜 */
data class VlogClip(
    val durationSeconds: Double,
    val voiceCommand: String,
    val overlayText: String
)

data class VlogTemplate(
    val bgmFilename: String?,
    val clips: List<VlogClip>
)

/** 完整拍摄方案 */
data class ShootingPlan(
    val id: String,
    val poseName: String,
    val poseEmoji: String,
    val poseDescription: String,
    val composition: CompositionRule,
    val frameRatio: FrameRatio,
    val voiceGuide: String,
    val posePoints: Map<String, NormPoint>,
    val secondaryPosePoints: Map<String, NormPoint>? = null,
    val sequence: List<ActionFrame>? = null,
    val multiAngles: List<CameraAngle>? = null,
    val vlogScript: VlogTemplate? = null
)