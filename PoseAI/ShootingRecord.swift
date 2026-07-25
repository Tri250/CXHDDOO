import SwiftUI
import SwiftData

// MARK: - 拍摄记录数据模型 (Step 2)
@Model
final class ShootingRecord {
    // 基础信息
    var id: UUID
    var createdAt: Date

    // 场景与方案
    var sceneRawValue: String
    var planId: String
    var planName: String

    // 匹配信息
    var matchScore: Int

    // 关联的相册图片 ID（用于从系统相册获取原图）
    var localIdentifier: String

    // 是否应用了滤镜
    var appliedFilterRawValue: String?

    // 位置信息（预留，V3 可用于制作"年度影集报告"）
    var latitude: Double?
    var longitude: Double?

    // 计算属性供 UI 使用
    var sceneType: SceneType {
        SceneType(rawValue: sceneRawValue) ?? .unknown
    }

    // 按天分组用的日期字符串（YYYY-MM-DD）
    var dateString: String {
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd"
        return formatter.string(from: createdAt)
    }

    init(
        id: UUID = UUID(),
        createdAt: Date = Date(),
        sceneRawValue: String,
        planId: String,
        planName: String,
        matchScore: Int,
        localIdentifier: String,
        appliedFilterRawValue: String? = nil,
        latitude: Double? = nil,
        longitude: Double? = nil
    ) {
        self.id = id
        self.createdAt = createdAt
        self.sceneRawValue = sceneRawValue
        self.planId = planId
        self.planName = planName
        self.matchScore = matchScore
        self.localIdentifier = localIdentifier
        self.appliedFilterRawValue = appliedFilterRawValue
        self.latitude = latitude
        self.longitude = longitude
    }
}
