import Foundation
import UIKit

actor AIAdvisor {
    static let shared = AIAdvisor()
    
    // 如果有 OpenAI Key 可以在这里配置（建议通过后端中转以策安全）
    private let apiKey: String = ""
    
    // MARK: - Step 14: 多模态穿搭配合场景大模型（OOTD）
    /// 利用视觉能力解析用户的当天的衣着（OOTD）并结合当前探测到的场景，给出极具惊现感的情感价值建议
    func analyzeOOTD(image: UIImage, currentScene: SceneType) async -> String {
        // TODO: 预留真实多模态接口，可将 image.jpegData(compressionQuality: 0.8) 传给 GPT-4-Vision 等
        
        // 我们当前提供无网环境秒降级体验引擎：
        try? await Task.sleep(nanoseconds: 1_200_000_000) // 模拟大尺寸图片上传延迟
        
        let mockOOTDS = ["飘逸长裙", "休闲针织衫", "干练风衣", "日常慵懒风", "时尚休闲套装"]
        let currentOOTD = mockOOTDS.randomElement()!
        
        // 根据 OOTD 和具体的场景融合拼接专属语录
        let hint: String
        switch currentOOTD {
        case "飘逸长裙":
            hint = "捕捉到您今天穿着\(currentOOTD)，非常绝美！尝试双手微微拎起裙摆，在这个\(currentScene.displayName)中心旋转一下，我们会抓拍那飞扬的一刻！"
        case "干练风衣":
            hint = "这套\(currentOOTD)太有高级质感了。在这个\(currentScene.displayName)建议您稍微整理一下衣领然后单手插兜，眼神不用看我，看向远方极其出片！"
        case "休闲针织衫", "日常慵懒风", "时尚休闲套装":
            hint = "监测到了非常舒服的\(currentOOTD)穿搭！在这片\(currentScene.displayName)不要拘束，像平时伸懒腰一样大幅度伸展双臂，我要抓下这段松弛感。"
        default:
            hint = "您的这身穿搭与这里的\(currentScene.displayName)绝配，尝试侧对屏幕，来个自然的回眸一笑吧！"
        }
        
        return hint
    }
}
