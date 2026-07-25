import Foundation
import SwiftData
import CoreGraphics

// 辅助用于 JSON 序列化的点结构体，因为 SwiftData 在处理字典 [String: CGPoint] 时可能较为棘手
struct PointData: Codable {
    let x: CGFloat
    let y: CGFloat
}

// MARK: - 自定义方案模型 (Step 7)
@Model
final class CustomPlan {
    var id: String
    var createdAt: Date
    var poseName: String
    var poseEmoji: String
    var pointsData: Data
    
    // 初始化方法
    init(id: String = UUID().uuidString, 
         createdAt: Date = Date(), 
         poseName: String, 
         poseEmoji: String, 
         points: [String: CGPoint]) {
        
        self.id = id
        self.createdAt = createdAt
        self.poseName = poseName
        self.poseEmoji = poseEmoji
        
        // 序列化点云为 Data 保存
        let encodablePoints = points.mapValues { PointData(x: $0.x, y: $0.y) }
        self.pointsData = (try? JSONEncoder().encode(encodablePoints)) ?? Data()
    }
    
    // 计算属性：动态反序列化为模型所需的格式
    @Transient // 不被 SwiftData 追踪
    var posePoints: [String: CGPoint] {
        guard let decoded = try? JSONDecoder().decode([String: PointData].self, from: pointsData) else {
            return [:]
        }
        return decoded.mapValues { CGPoint(x: $0.x, y: $0.y) }
    }
    
    // 快速生成 ShootingPlan 用于和智能推荐混合渲染
    @Transient
    var asShootingPlan: ShootingPlan {
        ShootingPlan(
            id: self.id,
            poseName: self.poseName,
            poseEmoji: self.poseEmoji.isEmpty ? "🧍" : self.poseEmoji,
            poseDescription: "这是你设计的专属动作",
            composition: .center,   // 默认中心构图打分
            frameRatio: .fullBody,  // 默认全身评价范围
            voiceGuide: "摆出你的自定义姿势：加上一点小调整，完美",
            posePoints: self.posePoints
        )
    }
}
