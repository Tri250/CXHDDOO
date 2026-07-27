package com.poseai.app.model

import android.graphics.PointF

/**
 * 姿势模板库
 *
 * 独立于 SceneType 枚举的灵活姿势模板系统，支持：
 * - 20+ 拍摄场景
 * - 100+ 精选姿势
 * - 按场景分类浏览
 * - 热门/节日/季节标签
 *
 * 与 SceneType 互补使用，为用户提供更丰富的姿势选择。
 */
object PoseTemplateLibrary {

    /** 拍摄场景模板 */
    data class SceneTemplate(
        val id: String,
        val name: String,
        val category: String,
        val icon: String,           // 图标标识
        val description: String,
        val tips: List<String>,
        val poses: List<PoseTemplate>
    )

    /** 单个姿势模板 */
    data class PoseTemplate(
        val id: String,
        val name: String,
        val description: String,
        val difficulty: Int,        // 1-5 难度
        val tags: List<String>,     // 标签：热门/新手/高级等
        val posePoints: Map<String, PointF>,
        val composition: CompositionRule = CompositionRule.RULE_OF_THIRDS
    )

    /** 所有场景模板 */
    val ALL_SCENES: List<SceneTemplate> = listOf(
        // ═══════════════════════════════════════════════════════════════
        // 1. 咖啡馆
        // ═══════════════════════════════════════════════════════════════
        SceneTemplate(
            id = "coffee_shop", name = "咖啡馆", category = "室内", icon = "coffee",
            description = "温暖的灯光与咖啡香气，适合文艺清新风格",
            tips = listOf("利用窗边自然光", "借助咖啡杯做道具", "选择靠墙座位有背景层次"),
            poses = listOf(
                pose("cs1", "窗边拿铁", "身体微侧，手持咖啡杯靠近脸颊", 2, listOf("热门", "新手"),
                    mapOf("neck" to p(0.5f,0.25f), "leftShoulder" to p(0.35f,0.32f), "rightShoulder" to p(0.65f,0.32f), "leftElbow" to p(0.25f,0.5f), "rightElbow" to p(0.6f,0.45f), "leftWrist" to p(0.4f,0.35f), "rightWrist" to p(0.55f,0.4f))),
                pose("cs2", "吧台特写", "手肘撑在吧台上，双手托腮", 1, listOf("热门", "新手"),
                    mapOf("neck" to p(0.5f,0.3f), "leftShoulder" to p(0.3f,0.4f), "rightShoulder" to p(0.7f,0.4f), "leftElbow" to p(0.2f,0.6f), "rightElbow" to p(0.8f,0.6f), "leftWrist" to p(0.35f,0.45f), "rightWrist" to p(0.65f,0.45f))),
                pose("cs3", "阅读时光", "侧坐手持书本，低头阅读", 2, listOf("文艺"),
                    mapOf("neck" to p(0.55f,0.28f), "leftShoulder" to p(0.4f,0.35f), "rightShoulder" to p(0.7f,0.35f), "leftElbow" to p(0.35f,0.5f), "rightElbow" to p(0.75f,0.5f), "leftWrist" to p(0.5f,0.42f), "rightWrist" to p(0.6f,0.42f))),
                pose("cs4", "咖啡拉花", "俯拍桌面，手握咖啡杯看镜头", 2, listOf("美食"),
                    mapOf("neck" to p(0.5f,0.2f), "leftShoulder" to p(0.3f,0.35f), "rightShoulder" to p(0.7f,0.35f), "leftWrist" to p(0.4f,0.55f), "rightWrist" to p(0.55f,0.55f))),
                pose("cs5", "吧台凝望", "坐在吧台高脚凳上，单手撑下巴看远方", 3, listOf("高级"),
                    mapOf("neck" to p(0.5f,0.25f), "leftShoulder" to p(0.32f,0.35f), "rightShoulder" to p(0.68f,0.35f), "leftElbow" to p(0.25f,0.5f), "rightElbow" to p(0.72f,0.48f), "leftWrist" to p(0.45f,0.32f), "rightWrist" to p(0.68f,0.55f)))
            )
        ),

        // ═══════════════════════════════════════════════════════════════
        // 2. 街拍
        // ═══════════════════════════════════════════════════════════════
        SceneTemplate(
            id = "street", name = "街拍", category = "室外", icon = "street",
            description = "都市街头风格，自然随性的行走与停驻",
            tips = listOf("选择有特色的背景墙", "行走中抓拍更自然", "利用路边道具增加层次"),
            poses = listOf(
                pose("st1", "行走抓拍", "自然迈步，一只手插口袋", 2, listOf("热门", "新手"),
                    mapOf("neck" to p(0.45f,0.2f), "leftShoulder" to p(0.3f,0.28f), "rightShoulder" to p(0.6f,0.28f), "leftElbow" to p(0.25f,0.5f), "rightElbow" to p(0.65f,0.45f), "leftWrist" to p(0.2f,0.65f), "rightWrist" to p(0.55f,0.55f))),
                pose("st2", "靠墙站", "侧身靠墙，一只腿弯曲", 2, listOf("热门"),
                    mapOf("neck" to p(0.6f,0.22f), "leftShoulder" to p(0.5f,0.3f), "rightShoulder" to p(0.7f,0.3f), "leftElbow" to p(0.45f,0.5f), "rightElbow" to p(0.75f,0.48f), "leftWrist" to p(0.4f,0.6f), "rightWrist" to p(0.78f,0.55f))),
                pose("st3", "过马路", "走在斑马线上，侧身回眸", 3, listOf("高级"),
                    mapOf("neck" to p(0.5f,0.22f), "leftShoulder" to p(0.35f,0.3f), "rightShoulder" to p(0.65f,0.3f), "leftHip" to p(0.4f,0.55f), "rightHip" to p(0.6f,0.55f), "leftKnee" to p(0.35f,0.78f), "rightKnee" to p(0.58f,0.75f), "leftAnkle" to p(0.32f,0.95f), "rightAnkle" to p(0.55f,0.95f))),
                pose("st4", "商店橱窗", "站在橱窗前看展示，侧脸入镜", 2, listOf("文艺"),
                    mapOf("neck" to p(0.4f,0.25f), "leftShoulder" to p(0.25f,0.33f), "rightShoulder" to p(0.55f,0.33f), "leftWrist" to p(0.2f,0.55f), "rightWrist" to p(0.5f,0.5f))),
                pose("st5", "街角转身", "街角处转身看镜头，衣摆飘动", 3, listOf("热门", "高级"),
                    mapOf("neck" to p(0.5f,0.22f), "leftShoulder" to p(0.33f,0.3f), "rightShoulder" to p(0.67f,0.3f), "leftElbow" to p(0.28f,0.5f), "rightElbow" to p(0.72f,0.48f), "leftWrist" to p(0.25f,0.65f), "rightWrist" to p(0.7f,0.6f))),
                pose("st6", "低头看手机", "靠墙低头看手机，自然生活感", 1, listOf("新手", "自然"),
                    mapOf("neck" to p(0.55f,0.28f), "leftShoulder" to p(0.4f,0.35f), "rightShoulder" to p(0.7f,0.35f), "leftElbow" to p(0.35f,0.52f), "rightElbow" to p(0.7f,0.5f), "leftWrist" to p(0.5f,0.48f), "rightWrist" to p(0.58f,0.48f)))
            )
        ),

        // ═══════════════════════════════════════════════════════════════
        // 3. 海边
        // ═══════════════════════════════════════════════════════════════
        SceneTemplate(
            id = "beach", name = "海边", category = "室外", icon = "beach",
            description = "阳光沙滩与海浪，适合清新浪漫风格",
            tips = listOf("逆光拍摄有剪影效果", "利用海风让头发飘动", "赤脚踩水更自然"),
            poses = listOf(
                pose("be1", "海浪背影", "背对镜头站在海边，回头微笑", 2, listOf("热门"),
                    mapOf("neck" to p(0.5f,0.25f), "leftShoulder" to p(0.35f,0.32f), "rightShoulder" to p(0.65f,0.32f), "leftHip" to p(0.4f,0.55f), "rightHip" to p(0.6f,0.55f), "leftAnkle" to p(0.35f,0.95f), "rightAnkle" to p(0.65f,0.95f))),
                pose("be2", "沙滩奔跑", "赤脚在沙滩上奔跑，裙摆飞扬", 3, listOf("热门", "高级"),
                    mapOf("neck" to p(0.5f,0.2f), "leftShoulder" to p(0.35f,0.28f), "rightShoulder" to p(0.65f,0.28f), "leftElbow" to p(0.25f,0.45f), "rightElbow" to p(0.75f,0.45f), "leftKnee" to p(0.4f,0.7f), "rightKnee" to p(0.6f,0.65f), "leftAnkle" to p(0.35f,0.9f), "rightAnkle" to p(0.65f,0.85f))),
                pose("be3", "海边坐姿", "坐在沙滩上，双手后撑看远方", 1, listOf("新手"),
                    mapOf("neck" to p(0.5f,0.3f), "leftShoulder" to p(0.3f,0.4f), "rightShoulder" to p(0.7f,0.4f), "leftElbow" to p(0.15f,0.6f), "rightElbow" to p(0.85f,0.6f), "leftWrist" to p(0.1f,0.7f), "rightWrist" to p(0.9f,0.7f))),
                pose("be4", "踏浪特写", "脚踩浪花近景，水花四溅", 2, listOf("清新"),
                    mapOf("leftAnkle" to p(0.4f,0.7f), "rightAnkle" to p(0.6f,0.7f), "leftKnee" to p(0.38f,0.5f), "rightKnee" to p(0.62f,0.5f))),
                pose("be5", "海风拂面", "侧身站立，闭眼感受海风", 2, listOf("文艺"),
                    mapOf("neck" to p(0.5f,0.25f), "leftShoulder" to p(0.35f,0.33f), "rightShoulder" to p(0.65f,0.33f), "leftWrist" to p(0.42f,0.45f), "rightWrist" to p(0.58f,0.45f))),
                pose("be6", "日落剪影", "逆光站姿，全身剪影", 3, listOf("高级", "热门"),
                    mapOf("neck" to p(0.5f,0.2f), "leftShoulder" to p(0.35f,0.28f), "rightShoulder" to p(0.65f,0.28f), "leftHip" to p(0.4f,0.52f), "rightHip" to p(0.6f,0.52f), "leftAnkle" to p(0.38f,0.95f), "rightAnkle" to p(0.62f,0.95f)))
            )
        ),

        // ═══════════════════════════════════════════════════════════════
        // 4. 公园
        // ═══════════════════════════════════════════════════════════════
        SceneTemplate(
            id = "park", name = "公园", category = "室外", icon = "park",
            description = "绿意盎然的自然环境，适合清新日系风格",
            tips = listOf("利用树木做前景框架", "草地坐姿更放松", "阳光穿过树叶的光斑很美"),
            poses = listOf(
                pose("pk1", "草坪坐", "盘腿坐在草地上，双手撑在身后", 1, listOf("新手"),
                    mapOf("neck" to p(0.5f,0.3f), "leftShoulder" to p(0.3f,0.4f), "rightShoulder" to p(0.7f,0.4f), "leftElbow" to p(0.15f,0.6f), "rightElbow" to p(0.85f,0.6f), "leftWrist" to p(0.1f,0.7f), "rightWrist" to p(0.9f,0.7f))),
                pose("pk2", "树下仰望", "站在大树下仰头看树叶", 2, listOf("文艺"),
                    mapOf("neck" to p(0.5f,0.2f), "leftShoulder" to p(0.35f,0.3f), "rightShoulder" to p(0.65f,0.3f), "leftWrist" to p(0.4f,0.5f), "rightWrist" to p(0.6f,0.5f))),
                pose("pk3", "花丛漫步", "在花丛中漫步，手轻触花朵", 2, listOf("热门"),
                    mapOf("neck" to p(0.45f,0.22f), "leftShoulder" to p(0.3f,0.3f), "rightShoulder" to p(0.6f,0.3f), "leftWrist" to p(0.25f,0.45f), "rightWrist" to p(0.55f,0.42f), "leftKnee" to p(0.35f,0.75f), "rightKnee" to p(0.5f,0.72f), "leftAnkle" to p(0.32f,0.95f), "rightAnkle" to p(0.52f,0.92f))),
                pose("pk4", "长椅休憩", "坐在公园长椅上，翘腿看书", 1, listOf("新手", "文艺"),
                    mapOf("neck" to p(0.5f,0.3f), "leftShoulder" to p(0.32f,0.38f), "rightShoulder" to p(0.68f,0.38f), "leftElbow" to p(0.25f,0.55f), "rightElbow" to p(0.72f,0.5f), "leftWrist" to p(0.45f,0.45f), "rightWrist" to p(0.6f,0.45f))),
                pose("pk5", "草地躺拍", "躺在草地上俯拍，双手张开", 2, listOf("创意"),
                    mapOf("neck" to p(0.5f,0.35f), "leftShoulder" to p(0.3f,0.42f), "rightShoulder" to p(0.7f,0.42f), "leftWrist" to p(0.15f,0.55f), "rightWrist" to p(0.85f,0.55f), "leftAnkle" to p(0.35f,0.85f), "rightAnkle" to p(0.65f,0.85f)))
            )
        ),

        // ═══════════════════════════════════════════════════════════════
        // 5. 居家
        // ═══════════════════════════════════════════════════════════════
        SceneTemplate(
            id = "home", name = "居家", category = "室内", icon = "home",
            description = "温馨的家居环境，适合慵懒日常风格",
            tips = listOf("利用沙发和床做道具", "穿居家服更自然", "暖色调灯光营造氛围"),
            poses = listOf(
                pose("hm1", "沙发慵懒", "侧身窝在沙发里，一只手撑头", 1, listOf("新手"),
                    mapOf("neck" to p(0.55f,0.3f), "leftShoulder" to p(0.4f,0.4f), "rightShoulder" to p(0.7f,0.38f), "leftElbow" to p(0.3f,0.55f), "rightElbow" to p(0.65f,0.5f), "leftWrist" to p(0.45f,0.35f), "rightWrist" to p(0.6f,0.55f))),
                pose("hm2", "地板坐", "盘腿坐在地板上，双手放膝盖", 1, listOf("新手"),
                    mapOf("neck" to p(0.5f,0.25f), "leftShoulder" to p(0.32f,0.35f), "rightShoulder" to p(0.68f,0.35f), "leftWrist" to p(0.38f,0.6f), "rightWrist" to p(0.62f,0.6f))),
                pose("hm3", "窗边阅读", "坐在窗台边看书，自然光照射", 2, listOf("文艺", "热门"),
                    mapOf("neck" to p(0.5f,0.28f), "leftShoulder" to p(0.33f,0.38f), "rightShoulder" to p(0.67f,0.38f), "leftElbow" to p(0.28f,0.55f), "rightElbow" to p(0.7f,0.52f), "leftWrist" to p(0.45f,0.45f), "rightWrist" to p(0.58f,0.45f))),
                pose("hm4", "床上午休", "侧躺在床上，手枕脸颊", 2, listOf("慵懒"),
                    mapOf("neck" to p(0.5f,0.3f), "leftShoulder" to p(0.35f,0.4f), "rightShoulder" to p(0.65f,0.4f), "leftWrist" to p(0.42f,0.32f), "rightWrist" to p(0.6f,0.55f))),
                pose("hm5", "厨房料理", "在厨房做饭，手持食材微笑", 2, listOf("生活"),
                    mapOf("neck" to p(0.5f,0.25f), "leftShoulder" to p(0.32f,0.35f), "rightShoulder" to p(0.68f,0.35f), "leftElbow" to p(0.25f,0.5f), "rightElbow" to p(0.72f,0.5f), "leftWrist" to p(0.4f,0.45f), "rightWrist" to p(0.55f,0.42f)))
            )
        ),

        // ═══════════════════════════════════════════════════════════════
        // 6. 夜晚霓虹
        // ═══════════════════════════════════════════════════════════════
        SceneTemplate(
            id = "night_neon", name = "夜晚霓虹", category = "夜间", icon = "neon",
            description = "霓虹灯光与夜色交织，适合赛博朋克风格",
            tips = listOf("利用霓虹灯做背景光源", "侧脸让彩光照亮面部", "暗调高对比更有氛围"),
            poses = listOf(
                pose("nn1", "霓虹回眸", "背对霓虹灯招牌，回头侧脸", 3, listOf("热门", "高级"),
                    mapOf("neck" to p(0.5f,0.25f), "leftShoulder" to p(0.35f,0.34f), "rightShoulder" to p(0.65f,0.34f), "leftElbow" to p(0.28f,0.52f), "rightElbow" to p(0.7f,0.5f), "leftWrist" to p(0.32f,0.65f), "rightWrist" to p(0.68f,0.62f))),
                pose("nn2", "霓虹倚靠", "侧身倚靠在灯柱旁，抬头看光", 3, listOf("高级"),
                    mapOf("neck" to p(0.6f,0.22f), "leftShoulder" to p(0.48f,0.3f), "rightShoulder" to p(0.72f,0.3f), "leftElbow" to p(0.42f,0.48f), "rightElbow" to p(0.78f,0.45f), "leftWrist" to p(0.5f,0.35f), "rightWrist" to p(0.82f,0.5f))),
                pose("nn3", "雨夜倒影", "雨后路面倒影霓虹灯，低头看", 4, listOf("创意", "高级"),
                    mapOf("neck" to p(0.5f,0.3f), "leftShoulder" to p(0.33f,0.4f), "rightShoulder" to p(0.67f,0.4f), "leftWrist" to p(0.3f,0.6f), "rightWrist" to p(0.7f,0.6f))),
                pose("nn4", "霓虹前行", "走向霓虹灯，正面拍摄带光晕", 3, listOf("热门"),
                    mapOf("neck" to p(0.5f,0.22f), "leftShoulder" to p(0.34f,0.3f), "rightShoulder" to p(0.66f,0.3f), "leftHip" to p(0.4f,0.55f), "rightHip" to p(0.6f,0.55f), "leftAnkle" to p(0.38f,0.95f), "rightAnkle" to p(0.62f,0.95f)))
            )
        ),

        // ═══════════════════════════════════════════════════════════════
        // 7. 办公室
        // ═══════════════════════════════════════════════════════════════
        SceneTemplate(
            id = "office", name = "办公室", category = "室内", icon = "office",
            description = "专业职场环境，适合商务OL风格",
            tips = listOf("利用办公桌做道具", "侧坐显腿长", "手托下巴显知性"),
            poses = listOf(
                pose("of1", "桌面托腮", "坐在办公桌前，单手托腮看镜头", 1, listOf("新手", "职场"),
                    mapOf("neck" to p(0.5f,0.28f), "leftShoulder" to p(0.32f,0.38f), "rightShoulder" to p(0.68f,0.38f), "leftElbow" to p(0.25f,0.55f), "leftWrist" to p(0.42f,0.35f), "rightWrist" to p(0.65f,0.5f))),
                pose("of2", "站姿撑桌", "站在桌边，一只手撑桌面", 2, listOf("职场"),
                    mapOf("neck" to p(0.5f,0.22f), "leftShoulder" to p(0.33f,0.3f), "rightShoulder" to p(0.67f,0.3f), "leftElbow" to p(0.3f,0.48f), "rightElbow" to p(0.7f,0.45f), "leftWrist" to p(0.35f,0.55f), "rightWrist" to p(0.68f,0.5f))),
                pose("of3", "翻阅文件", "手持文件低头看，侧脸入镜", 2, listOf("知性"),
                    mapOf("neck" to p(0.55f,0.26f), "leftShoulder" to p(0.4f,0.35f), "rightShoulder" to p(0.7f,0.35f), "leftElbow" to p(0.35f,0.5f), "rightElbow" to p(0.72f,0.48f), "leftWrist" to p(0.5f,0.42f), "rightWrist" to p(0.6f,0.42f))),
                pose("of4", "靠柜而立", "靠在文件柜旁，双手交叉", 2, listOf("职场", "高级"),
                    mapOf("neck" to p(0.5f,0.24f), "leftShoulder" to p(0.33f,0.32f), "rightShoulder" to p(0.67f,0.32f), "leftWrist" to p(0.45f,0.45f), "rightWrist" to p(0.55f,0.45f))),
                pose("of5", "窗边通话", "站在办公室窗边打电话", 2, listOf("生活", "职场"),
                    mapOf("neck" to p(0.5f,0.22f), "leftShoulder" to p(0.35f,0.3f), "rightShoulder" to p(0.65f,0.3f), "leftWrist" to p(0.42f,0.25f), "rightWrist" to p(0.6f,0.5f)))
            )
        ),

        // ═══════════════════════════════════════════════════════════════
        // 8. 商场
        // ═══════════════════════════════════════════════════════════════
        SceneTemplate(
            id = "mall", name = "商场", category = "室内", icon = "mall",
            description = "现代商业空间，适合时尚潮流风格",
            tips = listOf("利用扶梯做纵深背景", "玻璃幕墙反射有趣", "选有几何感的建筑结构"),
            poses = listOf(
                pose("ml1", "扶梯下行", "在扶梯上向下看镜头", 2, listOf("热门"),
                    mapOf("neck" to p(0.5f,0.25f), "leftShoulder" to p(0.35f,0.33f), "rightShoulder" to p(0.65f,0.33f), "leftWrist" to p(0.3f,0.5f), "rightWrist" to p(0.7f,0.5f))),
                pose("ml2", "玻璃倒影", "站在玻璃幕墙前，利用反射构图", 3, listOf("创意"),
                    mapOf("neck" to p(0.5f,0.25f), "leftShoulder" to p(0.33f,0.33f), "rightShoulder" to p(0.67f,0.33f), "leftWrist" to p(0.35f,0.5f), "rightWrist" to p(0.65f,0.5f))),
                pose("ml3", "购物手持", "手提购物袋行走，侧身回眸", 1, listOf("新手"),
                    mapOf("neck" to p(0.5f,0.22f), "leftShoulder" to p(0.34f,0.3f), "rightShoulder" to p(0.66f,0.3f), "leftWrist" to p(0.28f,0.5f), "rightWrist" to p(0.7f,0.5f), "leftKnee" to p(0.38f,0.75f), "rightKnee" to p(0.6f,0.72f))),
                pose("ml4", "休息区坐", "坐在商场休息区长椅上，翘腿", 2, listOf("时尚"),
                    mapOf("neck" to p(0.5f,0.28f), "leftShoulder" to p(0.32f,0.38f), "rightShoulder" to p(0.68f,0.38f), "leftWrist" to p(0.4f,0.55f), "rightWrist" to p(0.6f,0.5f)))
            )
        ),

        // ═══════════════════════════════════════════════════════════════
        // 9. 天台
        // ═══════════════════════════════════════════════════════════════
        SceneTemplate(
            id = "rooftop", name = "天台", category = "室外", icon = "rooftop",
            description = "开阔的高处视角，适合都市情绪风格",
            tips = listOf("日落时分光线最美", "利用天台围栏做前景", "城市灯光做背景"),
            poses = listOf(
                pose("rt1", "围栏远眺", "双手扶围栏，远眺城市", 2, listOf("热门"),
                    mapOf("neck" to p(0.5f,0.22f), "leftShoulder" to p(0.33f,0.3f), "rightShoulder" to p(0.67f,0.3f), "leftWrist" to p(0.3f,0.5f), "rightWrist" to p(0.7f,0.5f))),
                pose("rt2", "天台坐", "坐在天台边缘，双腿悬空", 3, listOf("高级", "创意"),
                    mapOf("neck" to p(0.5f,0.3f), "leftShoulder" to p(0.32f,0.4f), "rightShoulder" to p(0.68f,0.4f), "leftWrist" to p(0.25f,0.55f), "rightWrist" to p(0.75f,0.55f), "leftAnkle" to p(0.35f,0.8f), "rightAnkle" to p(0.65f,0.8f))),
                pose("rt3", "仰躺看天", "仰躺在天台，俯拍看天空", 2, listOf("文艺"),
                    mapOf("neck" to p(0.5f,0.35f), "leftShoulder" to p(0.3f,0.42f), "rightShoulder" to p(0.7f,0.42f), "leftWrist" to p(0.2f,0.55f), "rightWrist" to p(0.8f,0.55f), "leftAnkle" to p(0.35f,0.88f), "rightAnkle" to p(0.65f,0.88f))),
                pose("rt4", "背影日落", "背对镜头看日落，剪影构图", 3, listOf("高级", "热门"),
                    mapOf("neck" to p(0.5f,0.2f), "leftShoulder" to p(0.35f,0.28f), "rightShoulder" to p(0.65f,0.28f), "leftHip" to p(0.4f,0.55f), "rightHip" to p(0.6f,0.55f), "leftAnkle" to p(0.38f,0.95f), "rightAnkle" to p(0.62f,0.95f)))
            )
        ),

        // ═══════════════════════════════════════════════════════════════
        // 10. 樱花/花海
        // ═══════════════════════════════════════════════════════════════
        SceneTemplate(
            id = "flowers", name = "花海", category = "室外", icon = "flower",
            description = "浪漫花海场景，适合梦幻唯美风格",
            tips = listOf("花瓣做前景虚化", "穿浅色衣服更搭配", "微风时抓拍发丝飘动"),
            poses = listOf(
                pose("fl1", "花间漫步", "在花海中漫步，手轻触花朵", 2, listOf("热门", "浪漫"),
                    mapOf("neck" to p(0.45f,0.22f), "leftShoulder" to p(0.3f,0.3f), "rightShoulder" to p(0.6f,0.3f), "leftWrist" to p(0.25f,0.45f), "rightWrist" to p(0.55f,0.42f), "leftKnee" to p(0.35f,0.75f), "rightKnee" to p(0.5f,0.72f), "leftAnkle" to p(0.32f,0.95f), "rightAnkle" to p(0.52f,0.92f))),
                pose("fl2", "花下仰望", "站在花树下仰头看花", 2, listOf("文艺", "浪漫"),
                    mapOf("neck" to p(0.5f,0.2f), "leftShoulder" to p(0.35f,0.3f), "rightShoulder" to p(0.65f,0.3f), "leftWrist" to p(0.4f,0.5f), "rightWrist" to p(0.6f,0.5f))),
                pose("fl3", "手捧花", "双手捧花靠近脸颊，闭眼闻香", 1, listOf("新手", "浪漫"),
                    mapOf("neck" to p(0.5f,0.25f), "leftShoulder" to p(0.3f,0.35f), "rightShoulder" to p(0.7f,0.35f), "leftElbow" to p(0.25f,0.5f), "rightElbow" to p(0.75f,0.5f), "leftWrist" to p(0.4f,0.4f), "rightWrist" to p(0.6f,0.4f))),
                pose("fl4", "花瓣飘落", "花瓣飘落中旋转，抓拍动态", 4, listOf("高级", "创意"),
                    mapOf("neck" to p(0.5f,0.2f), "leftShoulder" to p(0.3f,0.28f), "rightShoulder" to p(0.7f,0.28f), "leftWrist" to p(0.15f,0.45f), "rightWrist" to p(0.85f,0.45f), "leftAnkle" to p(0.4f,0.92f), "rightAnkle" to p(0.6f,0.92f))),
                pose("fl5", "花丛蹲", "蹲在花丛中，侧脸微笑", 2, listOf("清新"),
                    mapOf("neck" to p(0.5f,0.3f), "leftShoulder" to p(0.35f,0.42f), "rightShoulder" to p(0.65f,0.42f), "leftWrist" to p(0.3f,0.6f), "rightWrist" to p(0.7f,0.6f), "leftKnee" to p(0.38f,0.75f), "rightKnee" to p(0.62f,0.75f)))
            )
        ),

        // ═══════════════════════════════════════════════════════════════
        // 11. 图书馆
        // ═══════════════════════════════════════════════════════════════
        SceneTemplate(
            id = "library", name = "图书馆", category = "室内", icon = "book",
            description = "安静的书香空间，适合知性文艺风格",
            tips = listOf("书架做背景有层次", "手持书本做道具", "侧光拍摄有氛围感"),
            poses = listOf(
                pose("lb1", "书架间站", "站在书架间，侧身看镜头", 2, listOf("文艺", "知性"),
                    mapOf("neck" to p(0.5f,0.22f), "leftShoulder" to p(0.34f,0.3f), "rightShoulder" to p(0.66f,0.3f), "leftWrist" to p(0.3f,0.5f), "rightWrist" to p(0.7f,0.5f))),
                pose("lb2", "阅读特写", "坐在桌前低头看书，侧脸特写", 2, listOf("文艺"),
                    mapOf("neck" to p(0.55f,0.26f), "leftShoulder" to p(0.4f,0.35f), "rightShoulder" to p(0.7f,0.35f), "leftWrist" to p(0.5f,0.45f), "rightWrist" to p(0.6f,0.45f))),
                pose("lb3", "取书架", "抬手从书架取书，侧身入镜", 3, listOf("高级"),
                    mapOf("neck" to p(0.5f,0.22f), "leftShoulder" to p(0.33f,0.3f), "rightShoulder" to p(0.67f,0.3f), "leftElbow" to p(0.3f,0.4f), "rightElbow" to p(0.7f,0.35f), "leftWrist" to p(0.32f,0.25f), "rightWrist" to p(0.68f,0.5f))),
                pose("lb4", "书架倚靠", "靠在书架旁，手持书本翻阅", 1, listOf("新手", "知性"),
                    mapOf("neck" to p(0.55f,0.25f), "leftShoulder" to p(0.4f,0.33f), "rightShoulder" to p(0.7f,0.33f), "leftWrist" to p(0.48f,0.45f), "rightWrist" to p(0.58f,0.45f)))
            )
        ),

        // ═══════════════════════════════════════════════════════════════
        // 12. 健身房
        // ═══════════════════════════════════════════════════════════════
        SceneTemplate(
            id = "gym", name = "健身房", category = "室内", icon = "gym",
            description = "运动活力场景，适合健康阳光风格",
            tips = listOf("运动装备更搭配", "利用器械做道具", "侧光突出肌肉线条"),
            poses = listOf(
                pose("gy1", "器械旁站", "靠在健身器械旁，双手交叉", 2, listOf("运动"),
                    mapOf("neck" to p(0.5f,0.24f), "leftShoulder" to p(0.33f,0.32f), "rightShoulder" to p(0.67f,0.32f), "leftWrist" to p(0.42f,0.45f), "rightWrist" to p(0.58f,0.45f))),
                pose("gy2", "拉伸姿势", "做拉伸动作，展示身体线条", 3, listOf("运动", "高级"),
                    mapOf("neck" to p(0.5f,0.2f), "leftShoulder" to p(0.3f,0.28f), "rightShoulder" to p(0.7f,0.28f), "leftWrist" to p(0.2f,0.15f), "rightWrist" to p(0.8f,0.15f), "leftHip" to p(0.4f,0.55f), "rightHip" to p(0.6f,0.55f))),
                pose("gy3", "坐器械", "坐在器械上，手握把手", 1, listOf("新手", "运动"),
                    mapOf("neck" to p(0.5f,0.28f), "leftShoulder" to p(0.32f,0.38f), "rightShoulder" to p(0.68f,0.38f), "leftWrist" to p(0.28f,0.5f), "rightWrist" to p(0.72f,0.5f))),
                pose("gy4", "镜面自拍", "对着健身镜子，侧身展示线条", 2, listOf("热门"),
                    mapOf("neck" to p(0.5f,0.25f), "leftShoulder" to p(0.34f,0.33f), "rightShoulder" to p(0.66f,0.33f), "leftWrist" to p(0.3f,0.5f), "rightWrist" to p(0.7f,0.5f)))
            )
        ),

        // ═══════════════════════════════════════════════════════════════
        // 13. 楼梯
        // ═══════════════════════════════════════════════════════════════
        SceneTemplate(
            id = "stairs", name = "楼梯", category = "室内", icon = "stairs",
            description = "几何线条感强的空间，适合时尚大片风格",
            tips = listOf("利用楼梯线条做引导线", "坐楼梯上视角更丰富", "仰拍显腿长"),
            poses = listOf(
                pose("sr1", "坐楼梯", "坐在楼梯上，一只腿伸一只腿弯", 2, listOf("热门"),
                    mapOf("neck" to p(0.5f,0.25f), "leftShoulder" to p(0.33f,0.35f), "rightShoulder" to p(0.67f,0.35f), "leftWrist" to p(0.3f,0.55f), "rightWrist" to p(0.7f,0.5f), "leftKnee" to p(0.35f,0.7f), "rightKnee" to p(0.65f,0.6f), "leftAnkle" to p(0.3f,0.85f), "rightAnkle" to p(0.7f,0.75f))),
                pose("sr2", "靠扶手", "靠在楼梯扶手上，侧身看镜头", 2, listOf("时尚"),
                    mapOf("neck" to p(0.6f,0.22f), "leftShoulder" to p(0.48f,0.3f), "rightShoulder" to p(0.72f,0.3f), "leftWrist" to p(0.45f,0.5f), "rightWrist" to p(0.78f,0.45f))),
                pose("sr3", "下楼抓拍", "下楼梯时抓拍，自然迈步", 3, listOf("高级"),
                    mapOf("neck" to p(0.5f,0.2f), "leftShoulder" to p(0.34f,0.28f), "rightShoulder" to p(0.66f,0.28f), "leftKnee" to p(0.4f,0.65f), "rightKnee" to p(0.58f,0.6f), "leftAnkle" to p(0.38f,0.85f), "rightAnkle" to p(0.6f,0.8f))),
                pose("sr4", "仰拍上楼", "从下往上拍上楼梯，显腿长", 3, listOf("高级", "热门"),
                    mapOf("neck" to p(0.5f,0.15f), "leftShoulder" to p(0.33f,0.25f), "rightShoulder" to p(0.67f,0.25f), "leftHip" to p(0.38f,0.5f), "rightHip" to p(0.62f,0.5f), "leftAnkle" to p(0.35f,0.9f), "rightAnkle" to p(0.65f,0.9f)))
            )
        ),

        // ═══════════════════════════════════════════════════════════════
        // 14. 雪景
        // ═══════════════════════════════════════════════════════════════
        SceneTemplate(
            id = "snow", name = "雪景", category = "室外", icon = "snow",
            description = "纯洁白雪世界，适合冬日浪漫风格",
            tips = listOf("穿鲜艳颜色衣服突出", "雪花飘落时抓拍", "逆光有钻石闪光效果"),
            poses = listOf(
                pose("sn1", "雪中站", "站在雪地中，双手插兜", 2, listOf("冬季", "浪漫"),
                    mapOf("neck" to p(0.5f,0.22f), "leftShoulder" to p(0.34f,0.3f), "rightShoulder" to p(0.66f,0.3f), "leftWrist" to p(0.35f,0.5f), "rightWrist" to p(0.65f,0.5f), "leftAnkle" to p(0.4f,0.95f), "rightAnkle" to p(0.6f,0.95f))),
                pose("sn2", "雪地坐", "坐在雪地上，双手后撑", 1, listOf("新手", "冬季"),
                    mapOf("neck" to p(0.5f,0.3f), "leftShoulder" to p(0.3f,0.4f), "rightShoulder" to p(0.7f,0.4f), "leftWrist" to p(0.2f,0.6f), "rightWrist" to p(0.8f,0.6f), "leftAnkle" to p(0.35f,0.9f), "rightAnkle" to p(0.65f,0.9f))),
                pose("sn3", "接雪花", "仰头伸手接雪花，动态抓拍", 3, listOf("创意", "冬季"),
                    mapOf("neck" to p(0.5f,0.15f), "leftShoulder" to p(0.3f,0.25f), "rightShoulder" to p(0.7f,0.25f), "leftWrist" to p(0.2f,0.1f), "rightWrist" to p(0.8f,0.1f))),
                pose("sn4", "雪人旁", "蹲在雪人旁，侧脸微笑", 1, listOf("新手", "可爱"),
                    mapOf("neck" to p(0.5f,0.3f), "leftShoulder" to p(0.35f,0.42f), "rightShoulder" to p(0.65f,0.42f), "leftWrist" to p(0.3f,0.6f), "rightWrist" to p(0.7f,0.6f), "leftKnee" to p(0.38f,0.75f), "rightKnee" to p(0.62f,0.75f))),
                pose("sn5", "雪中行走", "在雪中行走，留下一串脚印", 2, listOf("冬季"),
                    mapOf("neck" to p(0.45f,0.22f), "leftShoulder" to p(0.3f,0.3f), "rightShoulder" to p(0.6f,0.3f), "leftKnee" to p(0.35f,0.75f), "rightKnee" to p(0.5f,0.7f), "leftAnkle" to p(0.32f,0.95f), "rightAnkle" to p(0.52f,0.92f)))
            )
        ),

        // ═══════════════════════════════════════════════════════════════
        // 15. 秋叶
        // ═══════════════════════════════════════════════════════════════
        SceneTemplate(
            id = "autumn", name = "秋叶", category = "室外", icon = "leaf",
            description = "金色秋叶场景，适合温暖复古风格",
            tips = listOf("穿暖色调衣服搭配", "落叶做前景虚化", "逆光拍出金色通透感"),
            poses = listOf(
                pose("au1", "落叶抛洒", "双手捧落叶抛向空中", 3, listOf("创意", "秋季"),
                    mapOf("neck" to p(0.5f,0.2f), "leftShoulder" to p(0.3f,0.28f), "rightShoulder" to p(0.7f,0.28f), "leftWrist" to p(0.15f,0.1f), "rightWrist" to p(0.85f,0.1f))),
                pose("au2", "树下站", "站在黄叶树下，仰头看树冠", 2, listOf("文艺", "秋季"),
                    mapOf("neck" to p(0.5f,0.18f), "leftShoulder" to p(0.34f,0.28f), "rightShoulder" to p(0.66f,0.28f), "leftWrist" to p(0.35f,0.5f), "rightWrist" to p(0.65f,0.5f), "leftAnkle" to p(0.4f,0.95f), "rightAnkle" to p(0.6f,0.95f))),
                pose("au3", "落叶坐", "坐在落叶堆中，手捧落叶", 1, listOf("新手", "秋季"),
                    mapOf("neck" to p(0.5f,0.3f), "leftShoulder" to p(0.3f,0.4f), "rightShoulder" to p(0.7f,0.4f), "leftWrist" to p(0.35f,0.55f), "rightWrist" to p(0.65f,0.55f))),
                pose("au4", "手执落叶", "手持一片落叶遮一只眼", 2, listOf("可爱", "秋季"),
                    mapOf("neck" to p(0.5f,0.25f), "leftShoulder" to p(0.32f,0.35f), "rightShoulder" to p(0.68f,0.35f), "leftWrist" to p(0.45f,0.22f), "rightWrist" to p(0.6f,0.5f))),
                pose("au5", "漫步秋径", "在铺满落叶的小路上漫步", 2, listOf("文艺", "秋季"),
                    mapOf("neck" to p(0.5f,0.2f), "leftShoulder" to p(0.34f,0.28f), "rightShoulder" to p(0.66f,0.28f), "leftKnee" to p(0.4f,0.72f), "rightKnee" to p(0.58f,0.68f), "leftAnkle" to p(0.38f,0.92f), "rightAnkle" to p(0.6f,0.88f)))
            )
        ),

        // ═══════════════════════════════════════════════════════════════
        // 16. 雨天
        // ═══════════════════════════════════════════════════════════════
        SceneTemplate(
            id = "rain", name = "雨天", category = "室外", icon = "rain",
            description = "雨中浪漫氛围，适合情绪感风格",
            tips = listOf("透明雨伞是最佳道具", "雨后水洼有倒影", "暗色调更有氛围"),
            poses = listOf(
                pose("rn1", "撑伞站", "撑透明雨伞站在雨中", 2, listOf("浪漫", "雨天"),
                    mapOf("neck" to p(0.5f,0.22f), "leftShoulder" to p(0.34f,0.3f), "rightShoulder" to p(0.66f,0.3f), "leftWrist" to p(0.35f,0.1f), "rightWrist" to p(0.65f,0.1f), "leftAnkle" to p(0.4f,0.95f), "rightAnkle" to p(0.6f,0.95f))),
                pose("rn2", "水洼倒影", "站在水洼旁，拍摄倒影", 3, listOf("创意", "雨天"),
                    mapOf("neck" to p(0.5f,0.25f), "leftShoulder" to p(0.34f,0.33f), "rightShoulder" to p(0.66f,0.33f), "leftAnkle" to p(0.4f,0.95f), "rightAnkle" to p(0.6f,0.95f))),
                pose("rn3", "雨中行走", "撑伞在雨中行走，侧身入镜", 2, listOf("文艺", "雨天"),
                    mapOf("neck" to p(0.5f,0.2f), "leftShoulder" to p(0.34f,0.28f), "rightShoulder" to p(0.66f,0.28f), "leftWrist" to p(0.35f,0.08f), "rightWrist" to p(0.65f,0.08f), "leftKnee" to p(0.4f,0.72f), "rightKnee" to p(0.58f,0.68f), "leftAnkle" to p(0.38f,0.92f), "rightAnkle" to p(0.6f,0.88f))),
                pose("rn4", "屋檐避雨", "站在屋檐下，伸手接雨滴", 2, listOf("情绪", "雨天"),
                    mapOf("neck" to p(0.5f,0.22f), "leftShoulder" to p(0.33f,0.3f), "rightShoulder" to p(0.67f,0.3f), "leftWrist" to p(0.35f,0.45f), "rightWrist" to p(0.65f,0.45f)))
            )
        ),

        // ═══════════════════════════════════════════════════════════════
        // 17. 日落/黄昏
        // ═══════════════════════════════════════════════════════════════
        SceneTemplate(
            id = "sunset", name = "日落黄昏", category = "室外", icon = "sunset",
            description = "金色温暖光线，适合浪漫情绪风格",
            tips = listOf("日落前30分钟是黄金时段", "逆光拍摄有光晕", "剪影效果很有氛围"),
            poses = listOf(
                pose("ss1", "日落剪影", "逆光站姿，全身剪影", 2, listOf("热门", "高级"),
                    mapOf("neck" to p(0.5f,0.2f), "leftShoulder" to p(0.35f,0.28f), "rightShoulder" to p(0.65f,0.28f), "leftHip" to p(0.4f,0.52f), "rightHip" to p(0.6f,0.52f), "leftAnkle" to p(0.38f,0.95f), "rightAnkle" to p(0.62f,0.95f))),
                pose("ss2", "手遮阳光", "一只手遮挡阳光，从指缝看镜头", 2, listOf("热门"),
                    mapOf("neck" to p(0.5f,0.22f), "leftShoulder" to p(0.34f,0.3f), "rightShoulder" to p(0.66f,0.3f), "leftWrist" to p(0.42f,0.12f), "rightWrist" to p(0.65f,0.5f))),
                pose("ss3", "日落奔跑", "面向日落奔跑，逆光追拍", 4, listOf("高级", "创意"),
                    mapOf("neck" to p(0.5f,0.18f), "leftShoulder" to p(0.3f,0.26f), "rightShoulder" to p(0.7f,0.26f), "leftWrist" to p(0.15f,0.4f), "rightWrist" to p(0.85f,0.4f), "leftKnee" to p(0.35f,0.65f), "rightKnee" to p(0.65f,0.6f))),
                pose("ss4", "背靠背坐", "坐在地上看日落，背影构图", 1, listOf("新手", "浪漫"),
                    mapOf("neck" to p(0.5f,0.3f), "leftShoulder" to p(0.33f,0.4f), "rightShoulder" to p(0.67f,0.4f), "leftAnkle" to p(0.35f,0.85f), "rightAnkle" to p(0.65f,0.85f)))
            )
        ),

        // ═══════════════════════════════════════════════════════════════
        // 18. 废墟/工业风
        // ═══════════════════════════════════════════════════════════════
        SceneTemplate(
            id = "industrial", name = "工业废墟", category = "室外", icon = "industrial",
            description = "粗犷的工业空间，适合暗调酷飒风格",
            tips = listOf("穿深色衣服更搭配", "利用金属管道做道具", "侧光突出质感"),
            poses = listOf(
                pose("id1", "靠墙冷酷", "靠在斑驳墙面上，双手插袋", 2, listOf("酷飒", "高级"),
                    mapOf("neck" to p(0.55f,0.22f), "leftShoulder" to p(0.42f,0.3f), "rightShoulder" to p(0.7f,0.3f), "leftWrist" to p(0.4f,0.5f), "rightWrist" to p(0.68f,0.5f))),
                pose("id2", "管道坐", "坐在管道上，一只腿搭另一只", 3, listOf("酷飒"),
                    mapOf("neck" to p(0.5f,0.25f), "leftShoulder" to p(0.33f,0.35f), "rightShoulder" to p(0.67f,0.35f), "leftWrist" to p(0.3f,0.55f), "rightWrist" to p(0.7f,0.5f), "leftKnee" to p(0.38f,0.7f), "rightKnee" to p(0.62f,0.65f), "leftAnkle" to p(0.4f,0.85f), "rightAnkle" to p(0.6f,0.78f))),
                pose("id3", "铁梯攀", "手扶铁梯，侧身看镜头", 3, listOf("高级", "酷飒"),
                    mapOf("neck" to p(0.6f,0.22f), "leftShoulder" to p(0.48f,0.3f), "rightShoulder" to p(0.72f,0.3f), "leftWrist" to p(0.45f,0.15f), "rightWrist" to p(0.78f,0.45f))),
                pose("id4", "废墟蹲", "蹲在废墟中，低头看地面", 2, listOf("情绪"),
                    mapOf("neck" to p(0.5f,0.3f), "leftShoulder" to p(0.35f,0.42f), "rightShoulder" to p(0.65f,0.42f), "leftWrist" to p(0.32f,0.6f), "rightWrist" to p(0.68f,0.6f), "leftKnee" to p(0.38f,0.72f), "rightKnee" to p(0.62f,0.72f)))
            )
        ),

        // ═══════════════════════════════════════════════════════════════
        // 19. 汽车/车内
        // ═══════════════════════════════════════════════════════════════
        SceneTemplate(
            id = "car", name = "车内", category = "室内", icon = "car",
            description = "私密的车内空间，适合随性生活风格",
            tips = listOf("副驾驶视角最自然", "车窗光做侧光", "后视镜可做道具"),
            poses = listOf(
                pose("cr1", "副驾回眸", "坐在副驾回头看向后排镜头", 1, listOf("新手", "生活"),
                    mapOf("neck" to p(0.5f,0.25f), "leftShoulder" to p(0.33f,0.35f), "rightShoulder" to p(0.67f,0.35f), "leftWrist" to p(0.3f,0.5f), "rightWrist" to p(0.7f,0.5f))),
                pose("cr2", "车窗看外", "侧脸看车窗外风景", 2, listOf("文艺", "生活"),
                    mapOf("neck" to p(0.45f,0.22f), "leftShoulder" to p(0.3f,0.3f), "rightShoulder" to p(0.6f,0.3f), "leftWrist" to p(0.28f,0.5f), "rightWrist" to p(0.55f,0.5f))),
                pose("cr3", "后视镜", "通过后视镜拍反射", 3, listOf("创意", "高级"),
                    mapOf("neck" to p(0.5f,0.25f), "leftShoulder" to p(0.35f,0.35f), "rightShoulder" to p(0.65f,0.35f))),
                pose("cr4", "靠车外站", "靠在车门外面，双手交叉", 2, listOf("时尚"),
                    mapOf("neck" to p(0.5f,0.22f), "leftShoulder" to p(0.33f,0.3f), "rightShoulder" to p(0.67f,0.3f), "leftWrist" to p(0.42f,0.45f), "rightWrist" to p(0.58f,0.45f)))
            )
        ),

        // ═══════════════════════════════════════════════════════════════
        // 20. 酒店
        // ═══════════════════════════════════════════════════════════════
        SceneTemplate(
            id = "hotel", name = "酒店", category = "室内", icon = "hotel",
            description = "精致的酒店空间，适合高级感风格",
            tips = listOf("酒店走廊有纵深感", "大镜子可拍全身", "窗景配合室内光"),
            poses = listOf(
                pose("ht1", "走廊走", "在酒店走廊中行走，纵深构图", 2, listOf("高级", "时尚"),
                    mapOf("neck" to p(0.5f,0.2f), "leftShoulder" to p(0.34f,0.28f), "rightShoulder" to p(0.66f,0.28f), "leftKnee" to p(0.4f,0.72f), "rightKnee" to p(0.58f,0.68f), "leftAnkle" to p(0.38f,0.92f), "rightAnkle" to p(0.6f,0.88f))),
                pose("ht2", "镜前自拍", "站在全身镜前，侧身看镜中", 1, listOf("新手", "热门"),
                    mapOf("neck" to p(0.5f,0.22f), "leftShoulder" to p(0.34f,0.3f), "rightShoulder" to p(0.66f,0.3f), "leftWrist" to p(0.3f,0.5f), "rightWrist" to p(0.7f,0.5f), "leftAnkle" to p(0.4f,0.95f), "rightAnkle" to p(0.6f,0.95f))),
                pose("ht3", "窗边站", "站在酒店落地窗前看城市", 2, listOf("高级"),
                    mapOf("neck" to p(0.5f,0.2f), "leftShoulder" to p(0.34f,0.28f), "rightShoulder" to p(0.66f,0.28f), "leftWrist" to p(0.3f,0.5f), "rightWrist" to p(0.7f,0.5f), "leftAnkle" to p(0.4f,0.95f), "rightAnkle" to p(0.6f,0.95f))),
                pose("ht4", "床边坐", "坐在床边，双手后撑", 1, listOf("新手", "生活"),
                    mapOf("neck" to p(0.5f,0.28f), "leftShoulder" to p(0.3f,0.38f), "rightShoulder" to p(0.7f,0.38f), "leftWrist" to p(0.2f,0.55f), "rightWrist" to p(0.8f,0.55f), "leftAnkle" to p(0.35f,0.9f), "rightAnkle" to p(0.65f,0.9f)))
            )
        ),

        // ═══════════════════════════════════════════════════════════════
        // 21. 地铁/车站
        // ═══════════════════════════════════════════════════════════════
        SceneTemplate(
            id = "subway", name = "地铁车站", category = "室内", icon = "subway",
            description = "都市交通空间，适合故事感风格",
            tips = listOf("站台等车有故事感", "车厢内窗光很好", "利用站台线条做引导"),
            poses = listOf(
                pose("sb1", "站台等车", "站在站台边等车，侧身看轨道", 2, listOf("文艺", "故事"),
                    mapOf("neck" to p(0.5f,0.22f), "leftShoulder" to p(0.34f,0.3f), "rightShoulder" to p(0.66f,0.3f), "leftWrist" to p(0.3f,0.5f), "rightWrist" to p(0.7f,0.5f), "leftAnkle" to p(0.4f,0.95f), "rightAnkle" to p(0.6f,0.95f))),
                pose("sb2", "车厢靠窗", "坐在车厢靠窗位置，看窗外", 1, listOf("新手", "文艺"),
                    mapOf("neck" to p(0.55f,0.25f), "leftShoulder" to p(0.4f,0.35f), "rightShoulder" to p(0.7f,0.35f), "leftWrist" to p(0.35f,0.5f), "rightWrist" to p(0.65f,0.48f))),
                pose("sb3", "站牌前", "站在站牌前看路线图", 2, listOf("生活"),
                    mapOf("neck" to p(0.5f,0.2f), "leftShoulder" to p(0.34f,0.28f), "rightShoulder" to p(0.66f,0.28f), "leftWrist" to p(0.3f,0.15f), "rightWrist" to p(0.7f,0.15f))),
                pose("sb4", "扶梯上", "在扶梯上往下看镜头", 2, listOf("热门"),
                    mapOf("neck" to p(0.5f,0.2f), "leftShoulder" to p(0.34f,0.28f), "rightShoulder" to p(0.66f,0.28f), "leftWrist" to p(0.3f,0.5f), "rightWrist" to p(0.7f,0.5f)))
            )
        ),

        // ═══════════════════════════════════════════════════════════════
        // 22. 田间/草原
        // ═══════════════════════════════════════════════════════════════
        SceneTemplate(
            id = "field", name = "田野草原", category = "室外", icon = "field",
            description = "广阔的自然空间，适合自由清新风格",
            tips = listOf("穿浅色飘逸衣服", "利用风让衣物飘动", "低角度拍天空更多"),
            poses = listOf(
                pose("fd1", "草原张开", "站在草原中双手张开", 1, listOf("新手", "自由"),
                    mapOf("neck" to p(0.5f,0.2f), "leftShoulder" to p(0.3f,0.28f), "rightShoulder" to p(0.7f,0.28f), "leftWrist" to p(0.1f,0.35f), "rightWrist" to p(0.9f,0.35f), "leftAnkle" to p(0.4f,0.95f), "rightAnkle" to p(0.6f,0.95f))),
                pose("fd2", "田间走", "在田间小路上行走", 2, listOf("文艺", "清新"),
                    mapOf("neck" to p(0.5f,0.2f), "leftShoulder" to p(0.34f,0.28f), "rightShoulder" to p(0.66f,0.28f), "leftKnee" to p(0.4f,0.72f), "rightKnee" to p(0.58f,0.68f), "leftAnkle" to p(0.38f,0.92f), "rightAnkle" to p(0.6f,0.88f))),
                pose("fd3", "草丛坐", "坐在草丛中，双手抱膝", 1, listOf("新手", "清新"),
                    mapOf("neck" to p(0.5f,0.3f), "leftShoulder" to p(0.33f,0.4f), "rightShoulder" to p(0.67f,0.4f), "leftWrist" to p(0.4f,0.55f), "rightWrist" to p(0.6f,0.55f), "leftKnee" to p(0.35f,0.65f), "rightKnee" to p(0.65f,0.65f))),
                pose("fd4", "麦浪拂手", "行走时手轻拂麦穗", 3, listOf("高级", "文艺"),
                    mapOf("neck" to p(0.5f,0.2f), "leftShoulder" to p(0.34f,0.28f), "rightShoulder" to p(0.66f,0.28f), "leftWrist" to p(0.25f,0.45f), "rightWrist" to p(0.7f,0.45f), "leftAnkle" to p(0.4f,0.95f), "rightAnkle" to p(0.6f,0.92f))),
                pose("fd5", "草帽遮阳", "手持草帽遮阳，侧脸微笑", 2, listOf("清新", "夏日"),
                    mapOf("neck" to p(0.5f,0.22f), "leftShoulder" to p(0.33f,0.3f), "rightShoulder" to p(0.67f,0.3f), "leftWrist" to p(0.4f,0.1f), "rightWrist" to p(0.65f,0.5f)))
            )
        )
    )

    /** 获取所有姿势数量 */
    val totalPoseCount: Int get() = ALL_SCENES.sumOf { it.poses.size }

    /** 按分类获取场景 */
    fun getScenesByCategory(category: String): List<SceneTemplate> {
        return ALL_SCENES.filter { it.category == category }
    }

    /** 获取所有分类 */
    fun getAllCategories(): List<String> {
        return ALL_SCENES.map { it.category }.distinct()
    }

    /** 搜索姿势 */
    fun searchPoses(query: String): List<PoseTemplate> {
        return ALL_SCENES.flatMap { it.poses }.filter {
            it.name.contains(query, ignoreCase = true) ||
            it.description.contains(query, ignoreCase = true) ||
            it.tags.any { tag -> tag.contains(query, ignoreCase = true) }
        }
    }

    /** 获取热门姿势 */
    fun getHotPoses(): List<PoseTemplate> {
        return ALL_SCENES.flatMap { it.poses }.filter { "热门" in it.tags }
    }

    /** 获取新手姿势 */
    fun getBeginnerPoses(): List<PoseTemplate> {
        return ALL_SCENES.flatMap { it.poses }.filter { "新手" in it.tags }
    }

    // ── 辅助构造函数 ──

    private fun p(x: Float, y: Float) = PointF(x, y)

    private fun pose(
        id: String,
        name: String,
        description: String,
        difficulty: Int,
        tags: List<String>,
        posePoints: Map<String, PointF>,
        composition: CompositionRule = CompositionRule.RULE_OF_THIRDS
    ) = PoseTemplate(id, name, description, difficulty, tags, posePoints, composition)
}
