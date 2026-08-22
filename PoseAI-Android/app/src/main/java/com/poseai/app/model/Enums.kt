package com.poseai.app.model

import kotlin.math.roundToInt

/**
 * 人物在画面中的比例（与 iOS FrameRatio 一致）
 */
enum class FrameRatio(val heightRatio: Float, val displayName: String, val icon: String, val distanceHint: String) {
    FULL_BODY(0.80f, "全身", "figure.stand", "站远点，让镜头能拍到全身"),
    HALF_BODY(0.50f, "半身", "figure.arms.open", "站近些，拍到腰部以上"),
    PORTRAIT(0.35f, "特写", "person.crop.circle", "靠近镜头，拍胸部以上")
}

/**
 * 构图规则（与 iOS CompositionRule 一致）
 */
enum class CompositionRule(val offset: Float, val displayName: String, val reason: String, val voiceHint: String, val icon: String) {
    CENTER(0f, "居中", "居中对称，稳重大气，适合正式感强的场景", "居中站位", "rectangle.center.inset.filled"),
    LEFT_THIRD(-80f, "三分左", "三分法构图，人物偏左，右侧留白给视线延伸空间", "站到画面左侧", "rectangle.lefthalf.inset.filled"),
    RIGHT_THIRD(80f, "三分右", "三分法构图，人物偏右，左侧留白富有层次感", "站到画面右侧", "rectangle.righthalf.inset.filled"),
    GOLDEN_LEFT(-55f, "黄金左", "黄金分割比例，视觉最舒适的天然比例，偏左站位", "稍微往左站", "align.horizontal.left"),
    GOLDEN_RIGHT(55f, "黄金右", "黄金分割比例，视觉最舒适的天然比例，偏右站位", "稍微往右站", "align.horizontal.right")
}

/**
 * 场景类型（与 iOS SceneType 一致）
 */
enum class SceneType(val displayName: String, val icon: String) {
    COFFEE_SHOP("咖啡馆", "cup.and.saucer.fill"),
    BEACH("海边", "figure.pool.swim"),
    FOREST("森林", "tree.fill"),
    CITY_STREET("城市街道", "building.2.fill"),
    PARK("公园", "leaf.fill"),
    INDOOR_HOME("室内家居", "house.fill"),
    NEON_NIGHT("夜晚霓虹", "moon.stars.fill"),
    UNKNOWN("未知", "viewfinder");

    val plans: List<ShootingPlan>
        get() = PoseLibrary.plansFor(this)
}

/**
 * 照片裁切比例（与 iOS CropRatio 一致）
 */
enum class CropRatio(val displayName: String, val icon: String, val targetRatio: Float) {
    ORIGINAL("原比例", "rectangle", 0f),
    SQUARE("正方形", "square", 1f),
    FOUR_THREE("4:3", "rectangle.ratio.4.to.3", 4f / 3f),
    SIXTEEN_NINE("16:9", "rectangle.ratio.16.to.9", 16f / 9f),
    CINEMA("电影宽幅", "pano.fill", 2.35f)
}

/**
 * 滤镜预设（与 iOS PhotoFilter 一致）
 */
enum class PhotoFilter(val displayName: String, val icon: String) {
    ORIGINAL("原图", "photo"),
    FILM("胶片", "camera.filters"),
    BW("黑白", "circle.lefthalf.filled"),
    LIGHT("日系", "sun.max"),
    NEON("霓虹", "sparkles");

    val rawValue: String
        get() = when (this) {
            ORIGINAL -> "original"
            FILM -> "film"
            BW -> "bw"
            LIGHT -> "light"
            NEON -> "neon"
        }

    companion object {
        fun fromName(name: String?): PhotoFilter =
            entries.firstOrNull { it.rawValue == name } ?: ORIGINAL
    }
}

/** 把归一化点用给定宽高换算为整数像素 */
fun NormPoint.px(w: Float, h: Float) = android.graphics.PointF(x * w, (1f - y) * h)
fun Float.roundToPercent() = (this * NORM_SCALE).roundToInt()
const val NORM_SCALE = 1000f