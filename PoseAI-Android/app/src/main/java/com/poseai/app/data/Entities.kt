package com.poseai.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 拍摄记录实体——转换自 iOS ShootingRecord (SwiftData @Model)。
 *
 * 增强：
 *  - 支持地理位置信息（经纬度、地点名、城市）用于"在哪拍的"回溯
 *  - 支持光线参数（亮度/色温/曝光）用于暗光/夜景分析
 *  - 支持设备信息（机型、方向）用于多设备适配
 *  - 支持多组索引加速常见查询
 */
@Entity(
    tableName = "shooting_records",
    indices = [
        Index(value = ["createdAt"]),
        Index(value = ["sceneRawValue"]),
        Index(value = ["planId"]),
        Index(value = ["matchScore"])
    ]
)
data class ShootingRecordEntity(
    @PrimaryKey val id: String,
    val createdAt: Long,
    val sceneRawValue: String,
    val planId: String,
    val planName: String,
    val matchScore: Int,
    val localUri: String,
    val appliedFilterRawValue: String?,

    // MARK: - 地理位置（可空，没有定位权限时为 null）
    val latitude: Double? = null,
    val longitude: Double? = null,
    val placeName: String? = null,
    val cityName: String? = null,

    // MARK: - 光线环境（可空，未采集时为 null）
    val lightLevel: Float? = null,      // 0f ~ 1f 归一化亮度
    val colorTemperature: Float? = null, // 色温 0 (冷) ~ 1 (暖)
    val exposureTimeMs: Long? = null,    // 曝光时间（毫秒）
    val isLowLight: Boolean = false,

    // MARK: - 设备信息
    val deviceModel: String? = null,
    val lensFacing: String? = null      // "front" / "back"
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

/** 查询用的投影：仅返回必要字段，避免全表扫描 */
data class SceneStat(
    val sceneRawValue: String,
    val count: Int
)

data class ScoreStat(
    val avgScore: Float,
    val maxScore: Int,
    val count: Int
)
