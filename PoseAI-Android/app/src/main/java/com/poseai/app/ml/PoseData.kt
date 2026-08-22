package com.poseai.app.ml

import android.graphics.RectF
import com.poseai.app.model.NormPoint

/**
 * 单个人物的姿态数据——转换自 iOS VisionService.PoseData。
 * points: 关节归一化坐标(0~1)，x 向右，y 向上
 * isHalfBody: 是否半身
 * bbox: 人体在画面中的归一化包围盒 (x,y,w,h)，可能为 null
 */
data class PoseData(
    val points: Map<String, NormPoint>,
    val isHalfBody: Boolean,
    val bbox: RectF?
)