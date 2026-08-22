package com.poseai.app.model

/**
 * 姿势库管理器——转换自 iOS PoseLibrary。内置全部场景的拍摄方案（咖啡馆/海边/森林/城市/公园/室内/霓虹）。
 * Android 端与 iOS 端数据完全一致。
 */
object PoseLibrary {

    fun plansFor(scene: SceneType): List<ShootingPlan> {
        return when (scene) {
            SceneType.COFFEE_SHOP -> coffeePlans
            SceneType.BEACH -> beachPlans
            SceneType.FOREST -> forestPlans
            SceneType.CITY_STREET -> cityStreetPlans
            SceneType.PARK -> parkPlans
            SceneType.INDOOR_HOME -> indoorHomePlans
            SceneType.NEON_NIGHT -> neonNightPlans
            SceneType.UNKNOWN -> emptyList()
        }
    }

    /** 便捷：按 id 查找方案 */
    fun plan(id: String): ShootingPlan? =
        allPlans.firstOrNull { it.id == id }

    val allPlans: List<ShootingPlan> by lazy {
        coffeePlans + beachPlans + forestPlans + cityStreetPlans + parkPlans + indoorHomePlans + neonNightPlans
    }

    private fun p(vararg pairs: Pair<String, NormPoint>): Map<String, NormPoint> = mapOf(*pairs)

    // MARK: 咖啡馆方案
    private val coffeePlans: List<ShootingPlan> = listOf(
        ShootingPlan(
            id = "coffee_vlog_turn",
            poseName = "[短视频] 氛围转圈",
            poseEmoji = "🎬",
            poseDescription = "这是自动为您导练拼合的专属唯美小短片",
            composition = CompositionRule.CENTER,
            frameRatio = FrameRatio.FULL_BODY,
            voiceGuide = "来，想象你在拍短视频，跟着我的节奏走！准备...",
            posePoints = p(
                "neck" to NormPoint(0.50f, 0.30f),
                "leftShoulder" to NormPoint(0.38f, 0.40f),
                "rightShoulder" to NormPoint(0.62f, 0.40f),
                "leftHip" to NormPoint(0.46f, 0.60f),
                "rightHip" to NormPoint(0.54f, 0.60f)
            ),
            vlogScript = VlogTemplate(
                bgmFilename = null,
                clips = listOf(
                    VlogClip(3.0, "好，向镜头走两步！", "▶ 向前散步..."),
                    VlogClip(4.0, "停！顺着肩膀转个圈", "▶ 轻轻转身回眸"),
                    VlogClip(2.0, "对，保持这个笑容，完美！", "▶ 录制定格")
                )
            )
        ),
        ShootingPlan(
            id = "coffee_multi_angle_test",
            poseName = "[多机位] 咖啡馆质感",
            poseEmoji = "📸",
            poseDescription = "平视拍完后，蹲下仰拍大长腿，体验不同机位带来的视觉冲击",
            composition = CompositionRule.CENTER,
            frameRatio = FrameRatio.HALF_BODY,
            voiceGuide = "手捧咖啡杯看镜头，我们将在这个姿势下尝试平拍和仰拍两个机位。",
            posePoints = p(
                "neck" to NormPoint(0.50f, 0.33f),
                "leftShoulder" to NormPoint(0.38f, 0.42f),
                "rightShoulder" to NormPoint(0.62f, 0.42f),
                "leftElbow" to NormPoint(0.36f, 0.56f),
                "rightElbow" to NormPoint(0.64f, 0.56f),
                "leftWrist" to NormPoint(0.42f, 0.65f),
                "rightWrist" to NormPoint(0.58f, 0.65f),
                "leftHip" to NormPoint(0.44f, 0.62f),
                "rightHip" to NormPoint(0.56f, 0.62f)
            ),
            multiAngles = listOf(
                CameraAngle("默认平拍", "第一拍，轻松捧杯，保持不动。", null, null),
                CameraAngle("下蹲仰拍", "摄影师请蹲下，尝试极其出片的高级仰拍！", 0.2f, null)
            )
        ),
        ShootingPlan(
            id = "coffee_lean",
            poseName = "侧身靠墙",
            poseEmoji = "🧍",
            poseDescription = "一侧靠墙，视线望向远处，展现慵懒文艺气质",
            composition = CompositionRule.GOLDEN_LEFT,
            frameRatio = FrameRatio.HALF_BODY,
            voiceGuide = "侧身靠着墙或椅背，目光看向右侧，黄金分割构图，显气质",
            posePoints = p(
                "neck" to NormPoint(0.48f, 0.32f),
                "leftShoulder" to NormPoint(0.38f, 0.42f),
                "rightShoulder" to NormPoint(0.58f, 0.40f),
                "leftElbow" to NormPoint(0.30f, 0.56f),
                "rightElbow" to NormPoint(0.64f, 0.52f),
                "leftWrist" to NormPoint(0.28f, 0.68f),
                "rightWrist" to NormPoint(0.66f, 0.62f),
                "leftHip" to NormPoint(0.42f, 0.62f),
                "rightHip" to NormPoint(0.56f, 0.61f)
            )
        ),
        ShootingPlan(
            id = "coffee_cup",
            poseName = "双手捧杯",
            poseEmoji = "☕",
            poseDescription = "双手轻托咖啡杯，低头微笑，生活感十足的氛围照",
            composition = CompositionRule.CENTER,
            frameRatio = FrameRatio.HALF_BODY,
            voiceGuide = "双手捧着杯子，微微低头或看向镜头，居中构图，温柔有氛围",
            posePoints = p(
                "neck" to NormPoint(0.50f, 0.33f),
                "leftShoulder" to NormPoint(0.38f, 0.42f),
                "rightShoulder" to NormPoint(0.62f, 0.42f),
                "leftElbow" to NormPoint(0.36f, 0.56f),
                "rightElbow" to NormPoint(0.64f, 0.56f),
                "leftWrist" to NormPoint(0.42f, 0.65f),
                "rightWrist" to NormPoint(0.58f, 0.65f),
                "leftHip" to NormPoint(0.44f, 0.62f),
                "rightHip" to NormPoint(0.56f, 0.62f)
            )
        ),
        ShootingPlan(
            id = "coffee_window",
            poseName = "望向窗外",
            poseEmoji = "🪟",
            poseDescription = "侧身望向窗外自然光，轮廓在逆光下格外迷人",
            composition = CompositionRule.RIGHT_THIRD,
            frameRatio = FrameRatio.FULL_BODY,
            voiceGuide = "身体转向侧面，望向窗外方向，人物偏右构图，光线打亮脸部轮廓",
            posePoints = p(
                "neck" to NormPoint(0.50f, 0.28f),
                "leftShoulder" to NormPoint(0.38f, 0.38f),
                "rightShoulder" to NormPoint(0.62f, 0.38f),
                "leftElbow" to NormPoint(0.30f, 0.52f),
                "rightElbow" to NormPoint(0.70f, 0.52f),
                "leftWrist" to NormPoint(0.28f, 0.65f),
                "rightWrist" to NormPoint(0.72f, 0.50f),
                "leftHip" to NormPoint(0.44f, 0.60f),
                "rightHip" to NormPoint(0.56f, 0.60f),
                "leftKnee" to NormPoint(0.42f, 0.78f),
                "rightKnee" to NormPoint(0.58f, 0.78f)
            )
        )
    )

    // MARK: 海边方案
    private val beachPlans: List<ShootingPlan> = listOf(
        ShootingPlan(
            id = "beach_open",
            poseName = "张开双臂",
            poseEmoji = "🌊",
            poseDescription = "张开双臂拥抱大海，自由奔放，视觉冲击力强",
            composition = CompositionRule.CENTER,
            frameRatio = FrameRatio.FULL_BODY,
            voiceGuide = "站在沙滩上，双臂向两侧平展张开，面朝镜头，居中全身构图，感受自由",
            posePoints = p(
                "neck" to NormPoint(0.50f, 0.28f),
                "leftShoulder" to NormPoint(0.32f, 0.36f),
                "rightShoulder" to NormPoint(0.68f, 0.36f),
                "leftElbow" to NormPoint(0.16f, 0.36f),
                "rightElbow" to NormPoint(0.84f, 0.36f),
                "leftWrist" to NormPoint(0.05f, 0.36f),
                "rightWrist" to NormPoint(0.95f, 0.36f),
                "leftHip" to NormPoint(0.44f, 0.58f),
                "rightHip" to NormPoint(0.56f, 0.58f),
                "leftKnee" to NormPoint(0.42f, 0.76f),
                "rightKnee" to NormPoint(0.58f, 0.76f),
                "leftAnkle" to NormPoint(0.40f, 0.92f),
                "rightAnkle" to NormPoint(0.60f, 0.92f)
            )
        ),
        ShootingPlan(
            id = "beach_sunshield",
            poseName = "单手遮阳",
            poseEmoji = "🌅",
            poseDescription = "单手搭凉篷遮阳，侧脸望远，充满故事感",
            composition = CompositionRule.LEFT_THIRD,
            frameRatio = FrameRatio.HALF_BODY,
            voiceGuide = "一只手遮在额头上遮阳，眼神望向远方，偏左三分法，很有电影感",
            posePoints = p(
                "neck" to NormPoint(0.50f, 0.30f),
                "leftShoulder" to NormPoint(0.38f, 0.40f),
                "rightShoulder" to NormPoint(0.62f, 0.40f),
                "leftElbow" to NormPoint(0.34f, 0.54f),
                "rightElbow" to NormPoint(0.68f, 0.38f),
                "leftWrist" to NormPoint(0.32f, 0.66f),
                "rightWrist" to NormPoint(0.60f, 0.26f),
                "leftHip" to NormPoint(0.44f, 0.62f),
                "rightHip" to NormPoint(0.56f, 0.62f)
            )
        ),
        ShootingPlan(
            id = "beach_tiptoe",
            poseName = "踮脚望远",
            poseEmoji = "🦩",
            poseDescription = "踮起脚尖望向远方，拉长腿部线条，显高显腿长",
            composition = CompositionRule.GOLDEN_RIGHT,
            frameRatio = FrameRatio.FULL_BODY,
            voiceGuide = "踮起脚尖，微微仰头，双腿收紧，偏右黄金分割，线条超美",
            posePoints = p(
                "neck" to NormPoint(0.50f, 0.25f),
                "leftShoulder" to NormPoint(0.40f, 0.34f),
                "rightShoulder" to NormPoint(0.60f, 0.34f),
                "leftElbow" to NormPoint(0.36f, 0.48f),
                "rightElbow" to NormPoint(0.64f, 0.48f),
                "leftWrist" to NormPoint(0.38f, 0.60f),
                "rightWrist" to NormPoint(0.62f, 0.60f),
                "leftHip" to NormPoint(0.44f, 0.55f),
                "rightHip" to NormPoint(0.56f, 0.55f),
                "leftKnee" to NormPoint(0.43f, 0.72f),
                "rightKnee" to NormPoint(0.57f, 0.72f),
                "leftAnkle" to NormPoint(0.43f, 0.88f),
                "rightAnkle" to NormPoint(0.57f, 0.88f)
            )
        )
    )

    // MARK: 森林方案
    private val forestPlans: List<ShootingPlan> = listOf(
        ShootingPlan(
            id = "forest_lean_tree",
            poseName = "倚树而立",
            poseEmoji = "🌲",
            poseDescription = "背靠树干，一手轻搭树，自然随性，与环境融为一体",
            composition = CompositionRule.GOLDEN_RIGHT,
            frameRatio = FrameRatio.FULL_BODY,
            voiceGuide = "找棵树靠着，一只手搭在树上，另一手自然垂放，黄金分割偏右，很自然",
            posePoints = p(
                "neck" to NormPoint(0.50f, 0.28f),
                "leftShoulder" to NormPoint(0.38f, 0.38f),
                "rightShoulder" to NormPoint(0.62f, 0.38f),
                "leftElbow" to NormPoint(0.30f, 0.52f),
                "rightElbow" to NormPoint(0.68f, 0.44f),
                "leftWrist" to NormPoint(0.28f, 0.64f),
                "rightWrist" to NormPoint(0.72f, 0.36f),
                "leftHip" to NormPoint(0.44f, 0.60f),
                "rightHip" to NormPoint(0.56f, 0.60f),
                "leftKnee" to NormPoint(0.43f, 0.77f),
                "rightKnee" to NormPoint(0.57f, 0.77f),
                "leftAnkle" to NormPoint(0.42f, 0.92f),
                "rightAnkle" to NormPoint(0.58f, 0.92f)
            )
        ),
        ShootingPlan(
            id = "forest_squat",
            poseName = "蹲下仰拍",
            poseEmoji = "🍃",
            poseDescription = "双膝微蹲，仰头望向树梢，展现渺小又治愈的氛围感",
            composition = CompositionRule.CENTER,
            frameRatio = FrameRatio.HALF_BODY,
            voiceGuide = "双腿微曲蹲下，头微微仰起，居中构图，上方是树，氛围感超强",
            posePoints = p(
                "neck" to NormPoint(0.50f, 0.35f),
                "leftShoulder" to NormPoint(0.38f, 0.44f),
                "rightShoulder" to NormPoint(0.62f, 0.44f),
                "leftElbow" to NormPoint(0.32f, 0.56f),
                "rightElbow" to NormPoint(0.68f, 0.56f),
                "leftWrist" to NormPoint(0.38f, 0.68f),
                "rightWrist" to NormPoint(0.62f, 0.68f),
                "leftHip" to NormPoint(0.42f, 0.64f),
                "rightHip" to NormPoint(0.58f, 0.64f),
                "leftKnee" to NormPoint(0.38f, 0.80f),
                "rightKnee" to NormPoint(0.62f, 0.80f)
            )
        ),
        ShootingPlan(
            id = "forest_walk",
            poseName = "穿越步伐",
            poseEmoji = "🚶",
            poseDescription = "迈步行走，侧身或背对镜头，动态感十足的森系大片",
            composition = CompositionRule.LEFT_THIRD,
            frameRatio = FrameRatio.FULL_BODY,
            voiceGuide = "面向前方迈步走，可以侧脸或背对镜头，偏左构图，前方留空间，动感十足",
            posePoints = p(
                "neck" to NormPoint(0.50f, 0.26f),
                "leftShoulder" to NormPoint(0.38f, 0.36f),
                "rightShoulder" to NormPoint(0.62f, 0.36f),
                "leftElbow" to NormPoint(0.32f, 0.50f),
                "rightElbow" to NormPoint(0.68f, 0.50f),
                "leftWrist" to NormPoint(0.36f, 0.62f),
                "rightWrist" to NormPoint(0.64f, 0.44f),
                "leftHip" to NormPoint(0.42f, 0.58f),
                "rightHip" to NormPoint(0.58f, 0.58f),
                "leftKnee" to NormPoint(0.38f, 0.74f),
                "rightKnee" to NormPoint(0.60f, 0.70f),
                "leftAnkle" to NormPoint(0.36f, 0.90f),
                "rightAnkle" to NormPoint(0.62f, 0.86f)
            )
        )
    )

    // MARK: 城市街道方案
    private val cityStreetPlans: List<ShootingPlan> = listOf(
        ShootingPlan(
            id = "city_walk", poseName = "街头大步走", poseEmoji = "🚶‍♀️",
            poseDescription = "假装不经意走过，抓拍自然动态", composition = CompositionRule.CENTER,
            frameRatio = FrameRatio.FULL_BODY, voiceGuide = "在画面中心大步往前走或横排走，自然甩动手臂",
            posePoints = p(
                "neck" to NormPoint(0.50f, 0.28f), "leftShoulder" to NormPoint(0.40f, 0.38f),
                "rightShoulder" to NormPoint(0.60f, 0.38f), "leftElbow" to NormPoint(0.35f, 0.55f),
                "rightElbow" to NormPoint(0.70f, 0.50f), "leftHip" to NormPoint(0.45f, 0.60f),
                "rightHip" to NormPoint(0.55f, 0.60f), "leftKnee" to NormPoint(0.40f, 0.75f),
                "rightKnee" to NormPoint(0.65f, 0.70f)
            )
        ),
        ShootingPlan(
            id = "city_lean", poseName = "靠路灯柱", poseEmoji = "🚦",
            poseDescription = "侧身倚靠，腿交叉拉长比例", composition = CompositionRule.RIGHT_THIRD,
            frameRatio = FrameRatio.FULL_BODY, voiceGuide = "偏右站立，身体依靠物体，一条腿向镜头前方伸出",
            posePoints = p(
                "neck" to NormPoint(0.65f, 0.30f), "leftShoulder" to NormPoint(0.55f, 0.40f),
                "rightShoulder" to NormPoint(0.75f, 0.40f), "leftHip" to NormPoint(0.60f, 0.60f),
                "rightHip" to NormPoint(0.70f, 0.60f), "leftAnkle" to NormPoint(0.50f, 0.85f),
                "rightAnkle" to NormPoint(0.70f, 0.85f)
            )
        ),
        ShootingPlan(
            id = "city_lookback", poseName = "回眸一笑", poseEmoji = "👀",
            poseDescription = "背对镜头走，突然折返看镜头", composition = CompositionRule.GOLDEN_LEFT,
            frameRatio = FrameRatio.HALF_BODY, voiceGuide = "左侧构图，身体微侧背对镜头，回头看并带一点笑容",
            posePoints = p(
                "neck" to NormPoint(0.35f, 0.30f), "leftShoulder" to NormPoint(0.25f, 0.42f),
                "rightShoulder" to NormPoint(0.45f, 0.38f), "leftHip" to NormPoint(0.30f, 0.65f),
                "rightHip" to NormPoint(0.40f, 0.65f)
            )
        )
    )

    // MARK: 公园方案
    private val parkPlans: List<ShootingPlan> = listOf(
        ShootingPlan(
            id = "park_seq_3", poseName = "[连拍]少女三连", poseEmoji = "🏃‍♀️",
            poseDescription = "包含三个连续动作，自动连拍合成动图", composition = CompositionRule.CENTER,
            frameRatio = FrameRatio.HALF_BODY, voiceGuide = "开启三连拍模式，站在中间，听我口令完成三个动作。",
            posePoints = p(
                "neck" to NormPoint(0.50f, 0.30f), "leftShoulder" to NormPoint(0.40f, 0.40f),
                "rightShoulder" to NormPoint(0.60f, 0.40f)
            ),
            sequence = listOf(
                ActionFrame("✌️", "第一拍: 剪刀手", "第一拍，举起右手比剪刀手",
                    p("neck" to NormPoint(0.50f, 0.30f), "leftShoulder" to NormPoint(0.40f, 0.40f),
                        "rightShoulder" to NormPoint(0.60f, 0.40f), "rightElbow" to NormPoint(0.70f, 0.30f),
                        "rightWrist" to NormPoint(0.60f, 0.15f))),
                ActionFrame("🙆‍♀️", "第二拍: 抱头", "很好，切第二拍，双手抱头",
                    p("neck" to NormPoint(0.50f, 0.30f), "leftShoulder" to NormPoint(0.40f, 0.40f),
                        "rightShoulder" to NormPoint(0.60f, 0.40f), "leftElbow" to NormPoint(0.30f, 0.20f),
                        "rightElbow" to NormPoint(0.70f, 0.20f), "leftWrist" to NormPoint(0.45f, 0.10f),
                        "rightWrist" to NormPoint(0.55f, 0.10f))),
                ActionFrame("🫶", "第三拍: 比心", "最后一张，双手在胸口比心",
                    p("neck" to NormPoint(0.50f, 0.30f), "leftShoulder" to NormPoint(0.40f, 0.40f),
                        "rightShoulder" to NormPoint(0.60f, 0.40f), "leftElbow" to NormPoint(0.45f, 0.50f),
                        "rightElbow" to NormPoint(0.55f, 0.50f), "leftWrist" to NormPoint(0.48f, 0.40f),
                        "rightWrist" to NormPoint(0.52f, 0.40f)))
            )
        ),
        ShootingPlan(
            id = "park_dual_back", poseName = "[双人]草地背靠背", poseEmoji = "👯",
            poseDescription = "两人背靠背坐在草地上，画面温馨。适合情侣或闺蜜。", composition = CompositionRule.CENTER,
            frameRatio = FrameRatio.FULL_BODY, voiceGuide = "两人走到画面中间，背靠着背坐下，手随意放在膝盖上。",
            posePoints = p(
                "neck" to NormPoint(0.40f, 0.45f), "leftShoulder" to NormPoint(0.32f, 0.52f),
                "rightShoulder" to NormPoint(0.43f, 0.52f), "leftHip" to NormPoint(0.38f, 0.70f),
                "rightHip" to NormPoint(0.45f, 0.70f), "leftKnee" to NormPoint(0.25f, 0.85f),
                "rightKnee" to NormPoint(0.40f, 0.85f)
            ),
            secondaryPosePoints = p(
                "neck" to NormPoint(0.60f, 0.45f), "leftShoulder" to NormPoint(0.57f, 0.52f),
                "rightShoulder" to NormPoint(0.68f, 0.52f), "leftHip" to NormPoint(0.55f, 0.70f),
                "rightHip" to NormPoint(0.62f, 0.70f), "leftKnee" to NormPoint(0.60f, 0.85f),
                "rightKnee" to NormPoint(0.75f, 0.85f)
            )
        ),
        ShootingPlan(
            id = "park_sit", poseName = "草坪席地", poseEmoji = "🧘‍♀️",
            poseDescription = "盘腿或屈膝坐在草坪上，元气满满", composition = CompositionRule.CENTER,
            frameRatio = FrameRatio.FULL_BODY, voiceGuide = "在画面中心席地而坐，抱膝或者盘腿，抬头看镜头",
            posePoints = p(
                "neck" to NormPoint(0.50f, 0.45f), "leftShoulder" to NormPoint(0.40f, 0.55f),
                "rightShoulder" to NormPoint(0.60f, 0.55f), "leftHip" to NormPoint(0.45f, 0.75f),
                "rightHip" to NormPoint(0.55f, 0.75f), "leftKnee" to NormPoint(0.35f, 0.85f),
                "rightKnee" to NormPoint(0.65f, 0.85f)
            )
        ),
        ShootingPlan(
            id = "park_tree", poseName = "大树乘凉", poseEmoji = "🌳",
            poseDescription = "躲在树荫下，抬头感受阳光", composition = CompositionRule.LEFT_THIRD,
            frameRatio = FrameRatio.HALF_BODY, voiceGuide = "偏左构图，背靠大树，微微抬头看树叶的缝隙",
            posePoints = p(
                "neck" to NormPoint(0.30f, 0.30f), "leftShoulder" to NormPoint(0.20f, 0.42f),
                "rightShoulder" to NormPoint(0.40f, 0.42f), "leftHip" to NormPoint(0.25f, 0.65f),
                "rightHip" to NormPoint(0.35f, 0.65f)
            )
        ),
        ShootingPlan(
            id = "park_sun", poseName = "手遮阳光", poseEmoji = "☀️",
            poseDescription = "用手挡住刺眼的阳光，氛围感强", composition = CompositionRule.GOLDEN_RIGHT,
            frameRatio = FrameRatio.HALF_BODY, voiceGuide = "右侧黄金分割点，抬起一只手挡在眼睛上方挡阳光",
            posePoints = p(
                "neck" to NormPoint(0.70f, 0.30f), "leftShoulder" to NormPoint(0.60f, 0.40f),
                "rightShoulder" to NormPoint(0.80f, 0.40f), "leftElbow" to NormPoint(0.55f, 0.55f),
                "leftWrist" to NormPoint(0.65f, 0.30f), "rightHip" to NormPoint(0.75f, 0.65f)
            )
        )
    )

    // MARK: 室内家居方案
    private val indoorHomePlans: List<ShootingPlan> = listOf(
        ShootingPlan(
            id = "home_sofa", poseName = "沙发慵懒", poseEmoji = "🛋️",
            poseDescription = "靠在沙发里，随意放松", composition = CompositionRule.CENTER,
            frameRatio = FrameRatio.HALF_BODY, voiceGuide = "居中构图，在沙发上找个舒服的姿势靠着，很放松地看镜头",
            posePoints = p(
                "neck" to NormPoint(0.50f, 0.35f), "leftShoulder" to NormPoint(0.35f, 0.45f),
                "rightShoulder" to NormPoint(0.65f, 0.45f), "leftElbow" to NormPoint(0.25f, 0.50f),
                "rightElbow" to NormPoint(0.75f, 0.50f), "leftHip" to NormPoint(0.45f, 0.70f),
                "rightHip" to NormPoint(0.55f, 0.70f)
            )
        ),
        ShootingPlan(
            id = "home_window", poseName = "窗台托腮", poseEmoji = "🪟",
            poseDescription = "趴在窗台上看风景，宁静自在", composition = CompositionRule.LEFT_THIRD,
            frameRatio = FrameRatio.HALF_BODY, voiceGuide = "在画面左侧，手肘撑在台面上托住下巴",
            posePoints = p(
                "neck" to NormPoint(0.35f, 0.40f), "leftShoulder" to NormPoint(0.25f, 0.50f),
                "rightShoulder" to NormPoint(0.45f, 0.50f), "leftElbow" to NormPoint(0.30f, 0.65f),
                "leftWrist" to NormPoint(0.32f, 0.45f)
            )
        ),
        ShootingPlan(
            id = "home_hug", poseName = "抱枕卖萌", poseEmoji = "🧸",
            poseDescription = "抱紧抱枕或宠物，增加亲近感", composition = CompositionRule.GOLDEN_RIGHT,
            frameRatio = FrameRatio.HALF_BODY, voiceGuide = "在偏右的位置，双手抱住一个软萌物体在胸前",
            posePoints = p(
                "neck" to NormPoint(0.70f, 0.30f), "leftShoulder" to NormPoint(0.60f, 0.42f),
                "rightShoulder" to NormPoint(0.80f, 0.42f), "leftWrist" to NormPoint(0.65f, 0.55f),
                "rightWrist" to NormPoint(0.75f, 0.55f)
            )
        )
    )

    // MARK: 夜晚霓虹方案
    private val neonNightPlans: List<ShootingPlan> = listOf(
        ShootingPlan(
            id = "neon_back", poseName = "霓虹背影", poseEmoji = "🌃",
            poseDescription = "留出大片夜景，人物作为剪影点缀", composition = CompositionRule.CENTER,
            frameRatio = FrameRatio.FULL_BODY, voiceGuide = "背对镜头站立，面朝前方的灯光，中心构图",
            posePoints = p(
                "neck" to NormPoint(0.50f, 0.40f), "leftShoulder" to NormPoint(0.40f, 0.48f),
                "rightShoulder" to NormPoint(0.60f, 0.48f), "leftHip" to NormPoint(0.45f, 0.65f),
                "rightHip" to NormPoint(0.55f, 0.65f), "leftKnee" to NormPoint(0.40f, 0.80f),
                "rightKnee" to NormPoint(0.60f, 0.80f)
            )
        ),
        ShootingPlan(
            id = "neon_lookback", poseName = "借光回望", poseEmoji = "✨",
            poseDescription = "让店面的霓虹灯照亮侧脸", composition = CompositionRule.GOLDEN_LEFT,
            frameRatio = FrameRatio.HALF_BODY, voiceGuide = "站到画面左边，侧脸借旁边霓虹的灯光，有电影女主角的感觉",
            posePoints = p(
                "neck" to NormPoint(0.30f, 0.30f), "leftShoulder" to NormPoint(0.20f, 0.42f),
                "rightShoulder" to NormPoint(0.40f, 0.42f), "leftHip" to NormPoint(0.25f, 0.65f),
                "rightHip" to NormPoint(0.35f, 0.65f)
            )
        ),
        ShootingPlan(
            id = "neon_umbrella", poseName = "夜雨撑伞", poseEmoji = "☔️",
            poseDescription = "如果是雨夜，透明伞是绝佳道具", composition = CompositionRule.RIGHT_THIRD,
            frameRatio = FrameRatio.FULL_BODY, voiceGuide = "右侧站立，单手假装或真的撑伞，肩颈放松",
            posePoints = p(
                "neck" to NormPoint(0.70f, 0.35f), "leftShoulder" to NormPoint(0.60f, 0.45f),
                "rightShoulder" to NormPoint(0.80f, 0.45f), "rightWrist" to NormPoint(0.85f, 0.25f),
                "leftHip" to NormPoint(0.65f, 0.60f), "rightHip" to NormPoint(0.75f, 0.60f)
            )
        )
    )
}