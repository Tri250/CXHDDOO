package com.poseai.app.model

enum class SceneType(
    val displayName: String,
    val plans: List<ShootingPlan>
) {
    UNKNOWN(
        "未知",
        emptyList()
    ),
    COFFEE_SHOP(
        "咖啡馆",
        listOf(
            ShootingPlan(
                poseName = "窗边拿铁",
                poseDescription = "身体微侧，手持咖啡杯靠近脸颊，眼神看向窗外",
                posePoints = mapOf(
                    "neck" to android.graphics.PointF(0.5f, 0.25f),
                    "leftShoulder" to android.graphics.PointF(0.35f, 0.32f),
                    "rightShoulder" to android.graphics.PointF(0.65f, 0.32f),
                    "leftElbow" to android.graphics.PointF(0.25f, 0.5f),
                    "rightElbow" to android.graphics.PointF(0.6f, 0.45f),
                    "leftWrist" to android.graphics.PointF(0.4f, 0.35f),
                    "rightWrist" to android.graphics.PointF(0.55f, 0.4f)
                ),
                composition = CompositionRule.RULE_OF_THIRDS
            ),
            ShootingPlan(
                poseName = "吧台特写",
                poseDescription = "手肘撑在吧台上，双手托腮，微笑看镜头",
                posePoints = mapOf(
                    "neck" to android.graphics.PointF(0.5f, 0.3f),
                    "leftShoulder" to android.graphics.PointF(0.3f, 0.4f),
                    "rightShoulder" to android.graphics.PointF(0.7f, 0.4f),
                    "leftElbow" to android.graphics.PointF(0.2f, 0.6f),
                    "rightElbow" to android.graphics.PointF(0.8f, 0.6f),
                    "leftWrist" to android.graphics.PointF(0.35f, 0.45f),
                    "rightWrist" to android.graphics.PointF(0.65f, 0.45f)
                ),
                composition = CompositionRule.CENTER
            )
        )
    ),
    STREET(
        "街拍",
        listOf(
            ShootingPlan(
                poseName = "行走抓拍",
                poseDescription = "自然迈步，一只手插口袋，另一只手自然摆动",
                posePoints = mapOf(
                    "neck" to android.graphics.PointF(0.45f, 0.2f),
                    "leftShoulder" to android.graphics.PointF(0.3f, 0.28f),
                    "rightShoulder" to android.graphics.PointF(0.6f, 0.28f),
                    "leftElbow" to android.graphics.PointF(0.25f, 0.5f),
                    "rightElbow" to android.graphics.PointF(0.65f, 0.45f),
                    "leftWrist" to android.graphics.PointF(0.2f, 0.65f),
                    "rightWrist" to android.graphics.PointF(0.55f, 0.55f),
                    "leftHip" to android.graphics.PointF(0.38f, 0.55f),
                    "rightHip" to android.graphics.PointF(0.52f, 0.55f),
                    "leftKnee" to android.graphics.PointF(0.35f, 0.75f),
                    "rightKnee" to android.graphics.PointF(0.5f, 0.8f),
                    "leftAnkle" to android.graphics.PointF(0.32f, 0.95f),
                    "rightAnkle" to android.graphics.PointF(0.55f, 0.95f)
                ),
                secondaryPosePoints = mapOf(
                    "neck" to android.graphics.PointF(0.55f, 0.2f),
                    "leftShoulder" to android.graphics.PointF(0.4f, 0.28f),
                    "rightShoulder" to android.graphics.PointF(0.7f, 0.28f),
                    "leftElbow" to android.graphics.PointF(0.35f, 0.5f),
                    "rightElbow" to android.graphics.PointF(0.75f, 0.45f),
                    "leftWrist" to android.graphics.PointF(0.3f, 0.65f),
                    "rightWrist" to android.graphics.PointF(0.65f, 0.55f),
                    "leftHip" to android.graphics.PointF(0.48f, 0.55f),
                    "rightHip" to android.graphics.PointF(0.62f, 0.55f),
                    "leftKnee" to android.graphics.PointF(0.45f, 0.75f),
                    "rightKnee" to android.graphics.PointF(0.6f, 0.8f),
                    "leftAnkle" to android.graphics.PointF(0.42f, 0.95f),
                    "rightAnkle" to android.graphics.PointF(0.65f, 0.95f)
                ),
                composition = CompositionRule.RULE_OF_THIRDS,
                sequence = listOf(
                    SequenceShot("准备姿势", "站立放松，看向镜头"),
                    SequenceShot("行走中", "自然迈步，抓拍动态"),
                    SequenceShot("回眸一笑", "停下脚步回头微笑")
                ),
                multiAngles = listOf(
                    MultiAngle("正面视角", 0),
                    MultiAngle("侧面视角", 45),
                    MultiAngle("背影回眸", 90)
                ),
                vlogScript = VlogTemplate(
                    name = "街拍日记",
                    clips = listOf(
                        VlogClip("看镜头微笑打招呼", "开场问候", 3f),
                        VlogClip("自然行走抓拍", "展示街景", 4f),
                        VlogClip("展示今天的穿搭", "穿搭分享", 3f),
                        VlogClip("挥手说再见", "结尾", 2f)
                    )
                )
            ),
            ShootingPlan(
                poseName = "靠墙站",
                poseDescription = "侧身靠墙，一只腿弯曲脚踩墙面，看镜头",
                posePoints = mapOf(
                    "neck" to android.graphics.PointF(0.6f, 0.22f),
                    "leftShoulder" to android.graphics.PointF(0.5f, 0.3f),
                    "rightShoulder" to android.graphics.PointF(0.7f, 0.3f),
                    "leftElbow" to android.graphics.PointF(0.45f, 0.5f),
                    "rightElbow" to android.graphics.PointF(0.75f, 0.48f),
                    "leftWrist" to android.graphics.PointF(0.4f, 0.6f),
                    "rightWrist" to android.graphics.PointF(0.78f, 0.55f)
                ),
                secondaryPosePoints = mapOf(
                    "neck" to android.graphics.PointF(0.4f, 0.22f),
                    "leftShoulder" to android.graphics.PointF(0.3f, 0.3f),
                    "rightShoulder" to android.graphics.PointF(0.5f, 0.3f),
                    "leftElbow" to android.graphics.PointF(0.25f, 0.5f),
                    "rightElbow" to android.graphics.PointF(0.55f, 0.48f),
                    "leftWrist" to android.graphics.PointF(0.22f, 0.6f),
                    "rightWrist" to android.graphics.PointF(0.58f, 0.55f)
                ),
                composition = CompositionRule.RULE_OF_THIRDS,
                sequence = listOf(
                    SequenceShot("侧身站", "身体贴墙，双手插袋"),
                    SequenceShot("单腿弯曲", "一只脚踩墙，增加层次"),
                    SequenceShot("低头微笑", "自然放松的表情")
                ),
                multiAngles = listOf(
                    MultiAngle("侧面特写", 45),
                    MultiAngle("全身照", 0)
                )
            )
        )
    ),
    BEACH(
        "海边",
        listOf(
            ShootingPlan(
                poseName = "海浪背影",
                poseDescription = "背对镜头站在海边，回头微笑看镜头",
                posePoints = mapOf(
                    "neck" to android.graphics.PointF(0.5f, 0.25f),
                    "leftShoulder" to android.graphics.PointF(0.35f, 0.32f),
                    "rightShoulder" to android.graphics.PointF(0.65f, 0.32f),
                    "leftHip" to android.graphics.PointF(0.4f, 0.55f),
                    "rightHip" to android.graphics.PointF(0.6f, 0.55f),
                    "leftKnee" to android.graphics.PointF(0.38f, 0.75f),
                    "rightKnee" to android.graphics.PointF(0.62f, 0.75f),
                    "leftAnkle" to android.graphics.PointF(0.35f, 0.95f),
                    "rightAnkle" to android.graphics.PointF(0.65f, 0.95f)
                ),
                composition = CompositionRule.CENTER
            )
        )
    ),
    PARK(
        "公园",
        listOf(
            ShootingPlan(
                poseName = "草坪坐",
                poseDescription = "盘腿坐在草地上，双手撑在身后，身体微仰",
                posePoints = mapOf(
                    "neck" to android.graphics.PointF(0.5f, 0.3f),
                    "leftShoulder" to android.graphics.PointF(0.3f, 0.4f),
                    "rightShoulder" to android.graphics.PointF(0.7f, 0.4f),
                    "leftElbow" to android.graphics.PointF(0.15f, 0.6f),
                    "rightElbow" to android.graphics.PointF(0.85f, 0.6f),
                    "leftWrist" to android.graphics.PointF(0.1f, 0.7f),
                    "rightWrist" to android.graphics.PointF(0.9f, 0.7f)
                ),
                composition = CompositionRule.CENTER
            )
        )
    ),
    HOME(
        "居家",
        listOf(
            ShootingPlan(
                poseName = "沙发慵懒",
                poseDescription = "侧身窝在沙发里，一只手撑头，放松自然",
                posePoints = mapOf(
                    "neck" to android.graphics.PointF(0.55f, 0.3f),
                    "leftShoulder" to android.graphics.PointF(0.4f, 0.4f),
                    "rightShoulder" to android.graphics.PointF(0.7f, 0.38f),
                    "leftElbow" to android.graphics.PointF(0.3f, 0.55f),
                    "rightElbow" to android.graphics.PointF(0.65f, 0.5f),
                    "leftWrist" to android.graphics.PointF(0.45f, 0.35f),
                    "rightWrist" to android.graphics.PointF(0.6f, 0.55f)
                ),
                composition = CompositionRule.RULE_OF_THIRDS
            )
        )
    ),
    NIGHT_NEON(
        "夜晚霓虹",
        listOf(
            ShootingPlan(
                poseName = "霓虹回眸",
                poseDescription = "背对霓虹灯招牌，回头侧脸被彩光照亮，眼神带故事感",
                posePoints = mapOf(
                    "neck" to android.graphics.PointF(0.5f, 0.25f),
                    "leftShoulder" to android.graphics.PointF(0.35f, 0.34f),
                    "rightShoulder" to android.graphics.PointF(0.65f, 0.34f),
                    "leftElbow" to android.graphics.PointF(0.28f, 0.52f),
                    "rightElbow" to android.graphics.PointF(0.7f, 0.5f),
                    "leftWrist" to android.graphics.PointF(0.32f, 0.65f),
                    "rightWrist" to android.graphics.PointF(0.68f, 0.62f),
                    "leftHip" to android.graphics.PointF(0.42f, 0.56f),
                    "rightHip" to android.graphics.PointF(0.58f, 0.56f),
                    "leftKnee" to android.graphics.PointF(0.4f, 0.78f),
                    "rightKnee" to android.graphics.PointF(0.6f, 0.78f),
                    "leftAnkle" to android.graphics.PointF(0.38f, 0.95f),
                    "rightAnkle" to android.graphics.PointF(0.62f, 0.95f)
                ),
                secondaryPosePoints = mapOf(
                    "neck" to android.graphics.PointF(0.45f, 0.25f),
                    "leftShoulder" to android.graphics.PointF(0.3f, 0.34f),
                    "rightShoulder" to android.graphics.PointF(0.6f, 0.34f),
                    "leftElbow" to android.graphics.PointF(0.22f, 0.52f),
                    "rightElbow" to android.graphics.PointF(0.65f, 0.5f),
                    "leftWrist" to android.graphics.PointF(0.25f, 0.65f),
                    "rightWrist" to android.graphics.PointF(0.63f, 0.62f),
                    "leftHip" to android.graphics.PointF(0.37f, 0.56f),
                    "rightHip" to android.graphics.PointF(0.53f, 0.56f),
                    "leftKnee" to android.graphics.PointF(0.35f, 0.78f),
                    "rightKnee" to android.graphics.PointF(0.55f, 0.78f),
                    "leftAnkle" to android.graphics.PointF(0.33f, 0.95f),
                    "rightAnkle" to android.graphics.PointF(0.57f, 0.95f)
                ),
                composition = CompositionRule.RULE_OF_THIRDS,
                sequence = listOf(
                    SequenceShot("背对光源", "站在霓虹灯前，背影入镜"),
                    SequenceShot("侧脸回眸", "转身侧脸，让彩光照亮面部"),
                    SequenceShot("光影特写", "靠近光源，拍摄光影特写")
                ),
                multiAngles = listOf(
                    MultiAngle("低位仰拍", 15),
                    MultiAngle("平视回眸", 0),
                    MultiAngle("高位俯拍", -15)
                ),
                vlogScript = VlogTemplate(
                    name = "霓虹之夜",
                    clips = listOf(
                        VlogClip("走进霓虹街区", "开场氛围", 3f),
                        VlogClip("背对霓虹招牌转身", "回眸杀", 4f),
                        VlogClip("侧脸靠近光源特写", "光影质感", 3f),
                        VlogClip("远景全身霓虹剪影", "结尾氛围", 2f)
                    )
                )
            ),
            ShootingPlan(
                poseName = "霓虹倚靠",
                poseDescription = "侧身倚靠在霓虹灯柱旁，一只手轻触灯柱，抬头看光",
                posePoints = mapOf(
                    "neck" to android.graphics.PointF(0.6f, 0.22f),
                    "leftShoulder" to android.graphics.PointF(0.48f, 0.3f),
                    "rightShoulder" to android.graphics.PointF(0.72f, 0.3f),
                    "leftElbow" to android.graphics.PointF(0.42f, 0.48f),
                    "rightElbow" to android.graphics.PointF(0.78f, 0.45f),
                    "leftWrist" to android.graphics.PointF(0.5f, 0.35f),
                    "rightWrist" to android.graphics.PointF(0.82f, 0.5f),
                    "leftHip" to android.graphics.PointF(0.5f, 0.55f),
                    "rightHip" to android.graphics.PointF(0.66f, 0.55f),
                    "leftKnee" to android.graphics.PointF(0.48f, 0.76f),
                    "rightKnee" to android.graphics.PointF(0.64f, 0.78f),
                    "leftAnkle" to android.graphics.PointF(0.46f, 0.95f),
                    "rightAnkle" to android.graphics.PointF(0.62f, 0.95f)
                ),
                composition = CompositionRule.DIAGONAL,
                sequence = listOf(
                    SequenceShot("靠墙站", "侧身倚靠，手自然下垂"),
                    SequenceShot("抬头看光", "抬头望向霓虹灯源"),
                    SequenceShot("低头沉思", "低头收下巴，氛围感拉满")
                ),
                multiAngles = listOf(
                    MultiAngle("正面构图", 0),
                    MultiAngle("侧面剪影", 45)
                )
            )
        )
    )
}

enum class CompositionRule {
    CENTER,
    RULE_OF_THIRDS,
    DIAGONAL,
    FRAME_WITHIN_FRAME,
    // P5-6 黄金螺旋线构图：基于斐波那契数列的螺旋曲线
    GOLDEN_SPIRAL
}

data class SequenceShot(
    val title: String,
    val description: String
)

data class MultiAngle(
    val title: String,
    val requiredPitch: Int
)

data class VlogClip(
    val voiceCommand: String,
    val overlayText: String,
    val duration: Float
)

data class VlogTemplate(
    val name: String,
    val clips: List<VlogClip>,
    val bgmFilename: String? = null
)

data class ShootingPlan(
    val poseName: String,
    val poseDescription: String,
    val posePoints: Map<String, android.graphics.PointF>,
    val secondaryPosePoints: Map<String, android.graphics.PointF> = emptyMap(),
    val composition: CompositionRule = CompositionRule.RULE_OF_THIRDS,
    val sequence: List<SequenceShot> = emptyList(),
    val multiAngles: List<MultiAngle> = emptyList(),
    val vlogScript: VlogTemplate? = null
)
