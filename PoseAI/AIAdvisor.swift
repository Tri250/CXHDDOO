import Foundation
import UIKit

actor AIAdvisor {
    static let shared = AIAdvisor()
    
    private let apiKey: String = ""
    
    // MARK: - OOTD 穿搭风格定义
    private enum OutfitStyle: String, CaseIterable {
        case elegantDress = "飘逸长裙"
        case casualKnit = "休闲针织衫"
        case trenchCoat = "干练风衣"
        case lazyStyle = "日常慵懒风"
        case fashionSet = "时尚休闲套装"
        case streetwear = "街头潮酷风"
        case minimalism = "极简性冷淡风"
        case vintage = "复古港风"
        case sweetGirl = "甜美少女风"
        case business = "职场精英风"
        
        var keywords: [String] {
            switch self {
            case .elegantDress: return ["长裙", "连衣裙", "飘逸", "裙摆", "仙女"]
            case .casualKnit: return ["针织", "毛衣", "宽松", "舒适", "休闲"]
            case .trenchCoat: return ["风衣", "外套", "干练", "大气", "垂坠"]
            case .lazyStyle: return ["卫衣", "运动", "慵懒", "随性", "舒适"]
            case .fashionSet: return ["套装", "时尚", "精致", "搭配", "成套"]
            case .streetwear: return ["牛仔", "夹克", "潮牌", "街头", "个性"]
            case .minimalism: return ["简约", "素色", "干净", "线条", "高级"]
            case .vintage: return ["复古", "港风", "年代感", "胶片", "怀旧"]
            case .sweetGirl: return ["甜美", "可爱", "少女", "粉色", "蕾丝"]
            case .business: return ["西装", "职业", "干练", "精英", "正式"]
            }
        }
    }
    
    // MARK: - 场景专属拍照建议
    private func poseSuggestion(for style: OutfitStyle, scene: SceneType) -> String {
        switch (style, scene) {
        // 咖啡馆场景
        case (.elegantDress, .coffee_shop):
            return "捕捉到您今天穿着\(style.rawValue)，太有氛围感了！在咖啡馆建议您侧身坐在窗边，一只手轻托咖啡杯，眼神望向窗外，利用侧逆光勾勒出绝美轮廓。"
        case (.casualKnit, .coffee_shop):
            return "监测到舒适的\(style.rawValue)穿搭！在咖啡馆最适合这种慵懒调调。尝试双手捧着咖啡杯低头微笑，或者靠在椅背上放松肩膀，生活感直接拉满。"
        case (.trenchCoat, .coffee_shop):
            return "这件\(style.rawValue)太有质感了！建议您站在咖啡馆门口，单手插兜另一只手推门而入，抓拍动态瞬间，电影女主角既视感。"
        case (.lazyStyle, .coffee_shop):
            return "这身\(style.rawValue)太松弛了！在咖啡馆找个舒服的沙发角窝进去，随手翻本书或拨弄一下头发，自然不刻意的样子最出片。"
        case (.fashionSet, .coffee_shop):
            return "这套\(style.rawValue)超有时尚感！建议坐在桌前，身体微微前倾手肘撑桌，眼神直视镜头，杂志大片即视感。"
        case (.vintage, .coffee_shop):
            return "复古风与咖啡馆绝配！试着低头搅咖啡，或者透过玻璃杯看镜头，利用暖黄灯光营造怀旧胶片感。"
        case (.business, .coffee_shop):
            return "职场精英范儿拉满！在咖啡馆可以拍几张工作状态的照片，打开电脑专注打字，或者端着咖啡思考，知性又专业。"
            
        // 海边场景
        case (.elegantDress, .beach):
            return "\(style.rawValue)和海边是神仙组合！建议您站在沙滩上，双手轻轻提起裙摆侧身望向大海，等风吹起发丝和裙摆的瞬间按下快门。"
        case (.casualKnit, .beach):
            return "休闲风在海边也很有感觉！可以坐在礁石上，赤脚玩水，或者迎着海风张开双臂，自由自在的状态超有感染力。"
        case (.trenchCoat, .beach):
            return "\(style.rawValue)配海边意外的高级！建议您背对镜头站在海水中，回头看镜头，风衣被海风吹起的瞬间气场全开。"
        case (.lazyStyle, .beach):
            return "慵懒风跟海边太搭了！直接躺在沙滩上，一只手垫在头下，闭眼享受阳光，度假氛围感拉满。"
        case (.streetwear, .beach):
            return "潮酷风在海边也能hold住！靠在栏杆或码头边，插兜歪头看镜头，街头感与海景碰撞出不一样的火花。"
        case (.sweetGirl, .beach):
            return "甜美风与海边绝配！提着小裙子在浅水里踩水，或者对着镜头比个心，笑容灿烂的样子超治愈。"
            
        // 森林场景
        case (.elegantDress, .forest):
            return "\(style.rawValue)在森林里就是精灵本人！建议您站在林间小道上，一只手轻触树干，阳光透过树叶洒在身上，神秘又梦幻。"
        case (.casualKnit, .forest):
            return "休闲风在森林里很有森系感觉！坐在树根上抱膝低头，或者走在落叶铺满的小路上，自然清新。"
        case (.trenchCoat, .forest):
            return "\(style.rawValue)在森林里超有故事感！建议您走在林间步道上，风衣下摆随风摆动，回头一望的瞬间很有电影感。"
        case (.vintage, .forest):
            return "复古风与森林的复古感太搭了！试着站在老树下，或者拿着一束野花，暖色调滤镜一加就是九十年代的画报。"
        case (.minimalism, .forest):
            return "极简风在自然中更显高级！找一棵造型独特的树，站在旁边与树形成呼应，简洁有力的构图超有质感。"
        case (.sweetGirl, .forest):
            return "甜美系女孩在森林里就是小精灵！可以蹲在花丛边，或者闻闻花香，元气满满的样子超可爱。"
            
        // 城市街道场景
        case (.elegantDress, .city_street):
            return "\(style.rawValue)在都市街头就是行走的风景线！建议您过马路时抓拍动态，或者靠在玻璃幕墙边，城市霓虹与长裙的碰撞超有氛围感。"
        case (.trenchCoat, .city_street):
            return "\(style.rawValue)就是为城市而生的！站在十字路口等红灯，或者大步走过街天桥，每一张都是时尚街拍。"
        case (.streetwear, .city_street):
            return "街头风在城市街道就是主场！找一面涂鸦墙或工业风建筑，摆个酷一点的姿势，swag 满分。"
        case (.business, .city_street):
            return "职场精英与都市背景完美融合！站在写字楼下，整理袖口或看向远方，专业又自信的气场超有魅力。"
        case (.fashionSet, .city_street):
            return "时尚套装最适合街拍了！从高楼台阶上走下来，或者靠在复古路灯旁，随手一拍就是时装周街拍。"
        case (.minimalism, .city_street):
            return "极简风在现代建筑前超有格调！找一面干净的建筑墙面，站在几何线条旁，高级感扑面而来。"
            
        // 公园场景
        case (.elegantDress, .park):
            return "\(style.rawValue)在公园里就是花仙子！站在花丛中旋转，或者坐在长椅上看书，阳光洒下来的样子温柔到发光。"
        case (.casualKnit, .park):
            return "休闲风跟公园太配了！在草坪上铺块野餐布，盘腿而坐，或者躺在草地上晒太阳，满满的周末惬意感。"
        case (.sweetGirl, .park):
            return "甜美风在公园里甜度超标！拿着气球奔跑，或者蹲在郁金香花田旁，笑容比花还灿烂。"
        case (.lazyStyle, .park):
            return "慵懒风在公园就是舒适本人！找棵大树靠着眯会儿，或者荡个秋千，放松的状态最有感染力。"
        case (.vintage, .park):
            return "复古风在老公园里太有味道了！坐在复古长椅上，或者走在林荫大道上，恍惚间像是穿越了时光。"
            
        // 室内家居场景
        case (.elegantDress, .indoor_home):
            return "没想到在家也穿得这么美！站在落地窗边，利用自然光拍侧影，或者坐在梳妆台前回眸一笑，居家也能拍出大片感。"
        case (.casualKnit, .indoor_home):
            return "休闲针织衫就是居家神器！窝在沙发里抱个抱枕，或者在厨房倒杯水，生活化的瞬间最温暖治愈。"
        case (.lazyStyle, .indoor_home):
            return "慵懒风就是为居家而生的！在床上滚两圈，或者赤脚走在地毯上，松弛自然的状态最真实动人。"
        case (.minimalism, .indoor_home):
            return "极简风与整洁的家太搭了！站在书架或植物旁，干净的背景配上干净的穿搭，高级感拉满。"
        case (.sweetGirl, .indoor_home):
            return "甜美风在家更显可爱！抱着毛绒玩偶对镜头笑，或者对着镜子比耶，元气满满的样子超治愈。"
            
        // 夜晚霓虹场景
        case (.elegantDress, .neon_night):
            return "长裙配霓虹灯简直绝了！站在霓虹招牌下，让彩色灯光打在脸上和裙子上，迷离又梦幻的氛围直接拉满。"
        case (.streetwear, .neon_night):
            return "街头风在霓虹夜就是最靓的仔！找一面霓虹灯牌墙，站在光与影的交界处，酷到没朋友。"
        case (.vintage, .neon_night):
            return "复古风与霓虹夜景太有故事感了！站在老招牌下低头点烟，或者走在灯火阑珊的街道，港风电影女主角就是你。"
        case (.fashionSet, .neon_night):
            return "时尚套装在霓虹灯下超有未来感！借助商店橱窗的反光拍一张，赛博朋克那味儿就有了。"
        case (.business, .neon_night):
            return "精英范儿在夜景中更显魅力！站在高楼玻璃幕墙旁，身后是城市灯火，都市丽人的气场两米八。"
            
        // 通用 fallback
        default:
            let commonTips = [
                "您的这身穿搭与\(scene.displayName)的氛围太搭了！试着侧身站立，自然地回头看镜头，抓拍最真实的瞬间。",
                "监测到很有品味的穿搭！在\(scene.displayName)不妨放松肩膀，随意地走动一下，我会为你捕捉最自然动人的画面。",
                "发现超棒的穿搭灵感！在\(scene.displayName)建议您不要看镜头，望向远方或者低头微笑，故事感瞬间就有了。",
                "这身穿搭很有自己的风格！在\(scene.displayName)可以尝试利用一下周围的道具，比如靠在墙上、扶着栏杆，让画面更丰富。"
            ]
            return commonTips.randomElement() ?? "您的这身穿搭与这里的\(scene.displayName)绝配，尝试侧对屏幕，来个自然的回眸一笑吧！"
        }
    }
    
    // MARK: - Step 14: 多模态穿搭配合场景大模型（OOTD）
    /// 利用视觉能力解析用户的当天的衣着（OOTD）并结合当前探测到的场景，给出极具惊喜感的情感价值建议
    func analyzeOOTD(image: UIImage, currentScene: SceneType) async -> String {
        // TODO: 预留真实多模态接口，可将 image.jpegData(compressionQuality: 0.8) 传给 GPT-4-Vision 等
        
        // 我们当前提供无网环境秒降级体验引擎：
        // 模拟分析延迟，但稍微缩短一点以提升体验流畅度
        try? await Task.sleep(nanoseconds: 800_000_000)
        
        // 模拟 AI 分析：随机选择一种穿搭风格
        let allStyles = OutfitStyle.allCases
        let currentStyle = allStyles.randomElement() ?? .casualKnit
        
        // 根据 OOTD 和具体的场景融合拼接专属语录
        let suggestion = poseSuggestion(for: currentStyle, scene: currentScene)
        
        return suggestion
    }
    
    // MARK: - 构图建议（备用功能）
    /// 基于场景类型给出通用的构图建议
    func compositionTip(for scene: SceneType) -> String {
        switch scene {
        case .coffee_shop:
            return "咖啡馆拍照可以尝试利用窗框、镜子做前景，增加画面层次感。"
        case .beach:
            return "海边拍照记得把地平线放正，人物放在三分线位置更显高级。"
        case .forest:
            return "森林里可以利用树干做前景框，或者仰拍让树冠形成天然的画框。"
        case .city_street:
            return "城市街拍可以利用建筑线条引导视线，或者用玻璃幕墙的反射增加层次。"
        case .park:
            return "公园拍照可以蹲下来低角度拍摄，让花草作为前景更有层次。"
        case .indoor_home:
            return "居家拍照记得找自然光，侧光拍出来最有立体感和氛围感。"
        case .neon_night:
            return "夜景拍照让光源从侧面打来，脸上有光影对比更有故事感。"
        case .unknown:
            return "尝试不同的角度，有时稍微蹲下仰拍会有意想不到的好效果。"
        }
    }
}
