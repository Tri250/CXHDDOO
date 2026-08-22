package com.poseai.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 拍摄记录实体——转换自 iOS ShootingRecord (SwiftData @Model)。
 */
@Entity(tableName = "shooting_records")
data class ShootingRecordEntity(
    @PrimaryKey val id: String,
    val createdAt: Long,
    val sceneRawValue: String,
    val planId: String,
    val planName: String,
    val matchScore: Int,
    val localUri: String,
    val appliedFilterRawValue: String?
)

/**
 * 自定义方案实体——转换自 iOS CustomPlan (SwiftData @Model)。
 * points 以 JSON 字符串保存归一化关键点。
 */
@Entity(tableName = "custom_plans")
data class CustomPlanEntity(
    @PrimaryKey val id: String,
    val createdAt: Long,
    val poseName: String,
    val poseEmoji: String,
    val pointsJson: String
)