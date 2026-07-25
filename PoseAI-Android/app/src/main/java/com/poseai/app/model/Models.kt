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
                composition = CompositionRule.RULE_OF_THIRDS
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
                composition = CompositionRule.RULE_OF_THIRDS
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
    )
}

enum class CompositionRule {
    CENTER,
    RULE_OF_THIRDS,
    DIAGONAL,
    FRAME_WITHIN_FRAME
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
