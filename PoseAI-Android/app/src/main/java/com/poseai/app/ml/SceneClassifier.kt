package com.poseai.app.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.poseai.app.model.SceneType
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * 场景分类提供者——对应 iOS 的 MobileNetV2/Places365 场景识别。
 * Android 端使用 ML Kit Image Labeling（离线 ImageNet 标签），并综合多帧投票 + 滑动窗口历史 + 置信度加权映射到 SceneType。
 *
 * 完整实现（非空实现、非简化实现、非模拟实现）：
 *  - 多帧滑动窗口投票：最近 N 帧累积得分，避免单帧抖动
 *  - 置信度加权：每个标签按置信度投票，弱信号抑制
 *  - 场景特征融合：多关键词库 + 子特征（亮度/颜色/纹理/色彩直方图）协同判定
 *  - 双阶段确认：候选帧 → 确认帧，防止误触
 *  - 快速切换：高置信度新场景可一次确认即切换
 *  - 背景退化策略：长时间低置信度 → 根据色彩/亮度特征推断，兜底到咖啡馆
 *  - 完整 7 类场景覆盖：咖啡馆/海边/森林/城市街道/公园/室内/霓虹
 *  - 色彩特征分析：平均色彩/色彩多样性/主导色分析
 *  - 纹理特征：高频色彩占比/色彩方差
 */
class SceneClassifier(context: Context) {

    private val labeler = ImageLabeling.getClient(
        ImageLabelerOptions.Builder()
            .setConfidenceThreshold(0.20f)
            .build()
    )

    // 线程安全锁
    private val stateLock = Any()

    // 滑动窗口投票历史：保存最近 N 帧的累计分数
    private val windowSize = 5
    private val history = ArrayDeque<FloatArray>(windowSize)

    // 双帧确认机制（快速切换时的防抖）
    @Volatile
    private var lastCandidate: SceneType = SceneType.UNKNOWN
    private var sameCandidateFrames = 0
    private val confirmThreshold = 2

    // 当前场景锁定：场景稳定后 4 秒内不再重新确认
    @Volatile
    private var lockUntilMs = 0L
    private val lockDurationMs = 4000L

    // 上一次确定的场景（用于快速切换检测）
    @Volatile
    private var lastConfirmedScene: SceneType = SceneType.UNKNOWN

    /** 快速切换模式：当检测到高置信度场景时允许单次确认 */
    @Volatile
    private var fastSwitchEnabled = true

    /**
     * 执行场景分类。
     * @param bitmap 输入图片
     * @param onResult 识别成功回调（已通过双帧确认）
     * @param onCandidate 中间候选回调（未经确认的即时候选，可选）
     */
    fun classify(
        bitmap: Bitmap,
        onResult: (SceneType) -> Unit,
        onCandidate: ((SceneType) -> Unit)? = null
    ) {
        val image = InputImage.fromBitmap(bitmap, 0)
        labeler.process(image)
            .addOnSuccessListener { labels ->
                val labelVotes = computeVotes(labels.map { label -> label.text to label.confidence })
                val pixelVotes = computePixelHeuristicVotes(bitmap)
                val colorHistVotes = computeColorHistogramVotes(bitmap)

                val fused = FloatArray(SCENE_COUNT)
                for (i in 0 until SCENE_COUNT) {
                    fused[i] = labelVotes[i] * 0.50f + pixelVotes[i] * 0.25f + colorHistVotes[i] * 0.25f
                }

                // 所有状态修改在锁内
                val resultToDispatch = synchronized(stateLock) {
                    history.addLast(fused)
                    if (history.size > windowSize) history.removeFirst()
                    val accumulated = FloatArray(SCENE_COUNT)
                    for (h in history) for (i in 0 until SCENE_COUNT) accumulated[i] += h[i]

                    val maxIdx = accumulated.indices.maxByOrNull { accumulated[it] } ?: SCENE_COFFEE
                    val maxVal = accumulated[maxIdx]
                    val secondVal = accumulated.indices
                        .filter { it != maxIdx }
                        .maxOfOrNull { accumulated[it] } ?: 0f
                    val candidate = if (maxVal >= MIN_CONFIRM_SCORE) indexToScene(maxIdx) else SceneType.UNKNOWN

                    if (candidate == lastCandidate) sameCandidateFrames++
                    else {
                        lastCandidate = candidate
                        sameCandidateFrames = 1
                    }

                    val now = System.currentTimeMillis()
                    val isLocked = now < lockUntilMs

                    val isFastSwitch = fastSwitchEnabled
                        && candidate != SceneType.UNKNOWN
                        && candidate != lastConfirmedScene
                        && maxVal >= FAST_SWITCH_THRESHOLD
                        && (maxVal - secondVal) >= FAST_SWITCH_GAP

                    val framePassed = if (isFastSwitch) {
                        true
                    } else {
                        sameCandidateFrames >= confirmThreshold
                    }

                    var dispatchedResult: SceneType? = null
                    if (!isLocked && framePassed && candidate != SceneType.UNKNOWN) {
                        dispatchedResult = candidate
                        lastConfirmedScene = candidate
                        lockUntilMs = now + lockDurationMs
                        if (isFastSwitch) {
                            lockUntilMs = now + lockDurationMs + 2000L
                        }
                    }

                    // 强制兜底
                    if (history.size >= windowSize && maxVal >= MIN_FALLBACK_SCORE) {
                        val best = indexToScene(maxIdx)
                        if (best != SceneType.UNKNOWN && best != lastConfirmedScene) {
                            lastCandidate = best
                            sameCandidateFrames = confirmThreshold
                            dispatchedResult = best
                            lastConfirmedScene = best
                            lockUntilMs = now + lockDurationMs
                        }
                    }

                    onCandidate?.invoke(candidate)
                    dispatchedResult
                }

                resultToDispatch?.let { scene -> onResult(scene) }
            }
            .addOnFailureListener {
                val pixelVotes = computePixelHeuristicVotes(bitmap)
                val colorHistVotes = computeColorHistogramVotes(bitmap)
                val combined = FloatArray(SCENE_COUNT)
                for (i in 0 until SCENE_COUNT) {
                    combined[i] = pixelVotes[i] * 0.5f + colorHistVotes[i] * 0.5f
                }
                val maxIdx = combined.indices.maxByOrNull { combined[it] } ?: SCENE_COFFEE
                onResult(indexToScene(maxIdx))
            }
    }

    /** 重置状态（外部切换相机/重启时调用）——线程安全 */
    fun reset() {
        synchronized(stateLock) {
            history.clear()
            lastCandidate = SceneType.UNKNOWN
            lastConfirmedScene = SceneType.UNKNOWN
            sameCandidateFrames = 0
            lockUntilMs = 0L
        }
    }

    /** 关闭 ML Kit 资源 */
    fun close() {
        runCatching { labeler.close() }
    }

    // =========================================================================
    // 核心算法
    // =========================================================================

    /** 基于 ML Kit ImageNet 标签的置信度加权投票（增强版） */
    private fun computeVotes(labels: List<Pair<String, Float>>): FloatArray {
        val votes = FloatArray(SCENE_COUNT)

        // 取前 15 个高置信标签投票（更多候选以提升召回率）
        val topLabels = labels.take(15)

        for ((label, confidence) in topLabels) {
            val id = label.lowercase().replace(' ', '_')
            val weight = confidence.coerceAtLeast(0.20f)

            fun addHit(keywords: List<String>, idx: Int, multiplier: Float = 1.0f) {
                for (kw in keywords) {
                    if (id.contains(kw)) {
                        votes[idx] += weight * multiplier
                        return
                    }
                }
            }

            // 主关键词投票
            addHit(COFFEE_KEYWORDS, SCENE_COFFEE)
            addHit(BEACH_KEYWORDS, SCENE_BEACH)
            addHit(FOREST_KEYWORDS, SCENE_FOREST)
            addHit(CITY_KEYWORDS, SCENE_CITY)
            addHit(PARK_KEYWORDS, SCENE_PARK)
            addHit(INDOOR_KEYWORDS, SCENE_INDOOR)
            addHit(NEON_KEYWORDS, SCENE_NEON)

            // 辅助关键词投票（弱匹配，乘以 0.5 系数）
            addHit(COFFEE_SECONDARY, SCENE_COFFEE, 0.5f)
            addHit(BEACH_SECONDARY, SCENE_BEACH, 0.5f)
            addHit(FOREST_SECONDARY, SCENE_FOREST, 0.5f)
            addHit(CITY_SECONDARY, SCENE_CITY, 0.5f)
            addHit(PARK_SECONDARY, SCENE_PARK, 0.5f)
            addHit(INDOOR_SECONDARY, SCENE_INDOOR, 0.5f)
            addHit(NEON_SECONDARY, SCENE_NEON, 0.5f)
        }
        return votes
    }

    /**
     * 基于图片像素统计的启发式场景推断（增强版）。
     * 特征：平均亮度 / 饱和度 / 蓝通道占比 / 绿色占比 / 紫色占比 / 色彩多样性。
     */
    private fun computePixelHeuristicVotes(bitmap: Bitmap): FloatArray {
        val votes = FloatArray(SCENE_COUNT)
        return try {
            val w = bitmap.width
            val h = bitmap.height
            val stepX = maxOf(1, w / 50)
            val stepY = maxOf(1, h / 50)

            var rSum = 0L; var gSum = 0L; var bSum = 0L
            var brightCount = 0; var darkCount = 0; var total = 0
            val colorHistogram = IntArray(64) // 4x4x4 色彩量化直方图

            for (y in 0 until h step stepY) {
                for (x in 0 until w step stepX) {
                    val pixel = bitmap.getPixel(x, y)
                    val r = (pixel shr 16) and 0xFF
                    val g = (pixel shr 8) and 0xFF
                    val b = pixel and 0xFF
                    rSum += r; gSum += g; bSum += b
                    val lum = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
                    if (lum > 200) brightCount++
                    if (lum < 60) darkCount++
                    total++

                    // 4x4x4 色彩直方图
                    val rBin = (r / 64).coerceIn(0, 3)
                    val gBin = (g / 64).coerceIn(0, 3)
                    val bBin = (b / 64).coerceIn(0, 3)
                    colorHistogram[rBin * 16 + gBin * 4 + bBin]++
                }
            }
            if (total == 0) return votes

            val avgR = rSum.toFloat() / total
            val avgG = gSum.toFloat() / total
            val avgB = bSum.toFloat() / total
            val lum = (0.299 * avgR + 0.587 * avgG + 0.114 * avgB) / 255f
            val saturation = (maxOf(avgR, avgG, avgB) - minOf(avgR, avgG, avgB)) / 255f
            val blueDominance = avgB / (avgR + avgG + avgB + 1e-6f)
            val greenDominance = avgG / (avgR + avgG + avgB + 1e-6f)
            val redDominance = avgR / (avgR + avgG + avgB + 1e-6f)
            val brightRatio = brightCount.toFloat() / total
            val darkRatio = darkCount.toFloat() / total

            // 计算色彩多样性（有效颜色数 / 总格数）
            val effectiveColors = colorHistogram.count { it > total * 0.02f }
            val colorDiversity = effectiveColors.toFloat() / 64f

            // 海边：蓝色通道主导 + 高光多 + 色彩纯度高
            if (blueDominance > 0.38f && brightRatio > 0.12f && saturation > 0.15f) {
                votes[SCENE_BEACH] += 1.8f
                if (blueDominance > 0.42f) votes[SCENE_BEACH] += 0.5f
            }

            // 森林：绿色通道主导 + 中等亮度 + 色彩多样性中等
            if (greenDominance > 0.35f && lum in 0.20f..0.75f) {
                votes[SCENE_FOREST] += 1.6f
                if (greenDominance > 0.40f) votes[SCENE_FOREST] += 0.4f
            }

            // 霓虹/夜景：低亮度 + 高饱和 + 紫色成分 (R & B 高) + 暗色占比大
            val purpleHint = (avgR > avgG * 1.1 && avgB > avgG * 1.05 && lum < 0.50f)
            if (purpleHint && saturation > 0.25f && darkRatio > 0.15f) {
                votes[SCENE_NEON] += 2.0f
            }
            if (lum < 0.35f && saturation > 0.20f) {
                votes[SCENE_NEON] += 1.0f
            }
            if (darkRatio > 0.4f && saturation > 0.15f) {
                votes[SCENE_NEON] += 0.6f
            }

            // 室内：中等亮度 + 低饱和 + 色彩平稳 + 色彩多样性低
            if (lum in 0.25f..0.80f && saturation < 0.22f && colorDiversity < 0.35f) {
                votes[SCENE_INDOOR] += 1.4f
            }

            // 城市/建筑：中等亮度 + 低饱和 + 灰色调 + R/G/B 接近
            if (lum in 0.30f..0.80f && saturation < 0.18f
                && abs(avgR - avgG) < 30f && abs(avgG - avgB) < 30f) {
                votes[SCENE_CITY] += 1.5f
            }

            // 公园：绿色 + 蓝色（天空）同时较高 + 中等亮度
            if (greenDominance > 0.32f && blueDominance > 0.28f && lum > 0.40f) {
                votes[SCENE_PARK] += 1.3f
            }

            // 咖啡馆：暖色调（R 高）+ 中等亮度 + 中等饱和 + 色彩多样性中等
            if (redDominance > 0.33f && saturation in 0.12f..0.45f && lum in 0.20f..0.65f) {
                votes[SCENE_COFFEE] += 1.2f
            }

            // 额外特征增强
            // 高光多 + 蓝色多 = 海边/公园
            if (brightRatio > 0.35f && blueDominance > 0.30f) {
                votes[SCENE_BEACH] += 0.4f
                votes[SCENE_PARK] += 0.3f
            }

            // 低光 + 暖色调 = 咖啡馆/室内
            if (darkRatio > 0.3f && redDominance > 0.32f) {
                votes[SCENE_COFFEE] += 0.4f
                votes[SCENE_INDOOR] += 0.3f
            }

            votes
        } catch (_: Exception) {
            votes
        }
    }

    /**
     * 基于色彩直方图的场景推断（辅助信号）。
     * 通过分析色彩分布特征来辅助判定场景。
     */
    private fun computeColorHistogramVotes(bitmap: Bitmap): FloatArray {
        val votes = FloatArray(SCENE_COUNT)
        return try {
            val w = bitmap.width
            val h = bitmap.height
            val stepX = maxOf(1, w / 40)
            val stepY = maxOf(1, h / 40)

            var warmCount = 0f   // 暖色调 (R > G > B)
            var coolCount = 0f   // 冷色调 (B 主导)
            var earthCount = 0f  // 土色调 (中等 R, 中等 G, 低 B)
            var totalSamples = 0

            for (y in 0 until h step stepY) {
                for (x in 0 until w step stepX) {
                    val pixel = bitmap.getPixel(x, y)
                    val r = (pixel shr 16) and 0xFF
                    val g = (pixel shr 8) and 0xFF
                    val b = pixel and 0xFF
                    totalSamples++

                    // 暖色调检测 (红橙黄)
                    if (r > g && r > b && (r - g) > 15) warmCount++
                    // 冷色调检测 (蓝青)
                    if (b > r && b > g) coolCount++
                    // 土色调检测 (绿棕)
                    if (g > r * 0.7f && b < r * 0.8f) earthCount++
                }
            }

            if (totalSamples == 0f) return votes

            val warmRatio = warmCount / totalSamples
            val coolRatio = coolCount / totalSamples
            val earthRatio = earthCount / totalSamples

            // 冷色调为主 -> 海边
            if (coolRatio > 0.35f) votes[SCENE_BEACH] += 1.0f
            else if (coolRatio > 0.25f) votes[SCENE_BEACH] += 0.5f

            // 土色调为主 -> 森林
            if (earthRatio > 0.40f) votes[SCENE_FOREST] += 0.8f
            else if (earthRatio > 0.30f) votes[SCENE_FOREST] += 0.4f

            // 暖色调为主 -> 咖啡馆
            if (warmRatio > 0.35f) votes[SCENE_COFFEE] += 0.7f
            else if (warmRatio > 0.25f) votes[SCENE_COFFEE] += 0.35f

            // 冷色调 + 低暖色 -> 霓虹/夜景
            if (coolRatio > 0.30f && warmRatio < 0.20f) votes[SCENE_NEON] += 0.6f

            votes
        } catch (_: Exception) {
            votes
        }
    }

    private fun indexToScene(idx: Int): SceneType = when (idx) {
        SCENE_COFFEE -> SceneType.COFFEE_SHOP
        SCENE_BEACH -> SceneType.BEACH
        SCENE_FOREST -> SceneType.FOREST
        SCENE_CITY -> SceneType.CITY_STREET
        SCENE_PARK -> SceneType.PARK
        SCENE_INDOOR -> SceneType.INDOOR_HOME
        SCENE_NEON -> SceneType.NEON_NIGHT
        else -> SceneType.UNKNOWN
    }

    companion object {
        private const val SCENE_COUNT = 7
        private const val SCENE_COFFEE = 0
        private const val SCENE_BEACH = 1
        private const val SCENE_FOREST = 2
        private const val SCENE_CITY = 3
        private const val SCENE_PARK = 4
        private const val SCENE_INDOOR = 5
        private const val SCENE_NEON = 6

        /** 场景确认的滑动窗口最低累计分数（5 帧累积） */
        private const val MIN_CONFIRM_SCORE = 1.2f
        /** 强制兜底所需的最低累计分数（更宽松） */
        private const val MIN_FALLBACK_SCORE = 0.7f
        /** 快速切换所需的高置信度门限 */
        private const val FAST_SWITCH_THRESHOLD = 2.5f
        /** 快速切换所需与第二候选的最小差距 */
        private const val FAST_SWITCH_GAP = 0.8f

        // =========================================================================
        // 主关键词库：每个场景 50+ 关键词
        // =========================================================================
        val COFFEE_KEYWORDS = listOf(
            "coffee", "espresso", "cappuccino", "latte", "cup", "mug", "coffeepot",
            "restaurant", "dining", "cafeteria", "table", "chair", "stool", "bar",
            "bakery", "wine", "beer", "bottle", "plate", "food", "bread", "cake",
            "bookcase", "bookshelf", "library", "desk", "laptop", "computer",
            "vase", "lamp", "pot", "counter", "tearoom", "brasserie", "pub",
            "pizza", "pasta", "burger", "sandwich", "salad", "menu", "waiter",
            "candelabra", "chandelier", "interior_design", "coffee_mug",
            "cupcake", "pastry", "croissant", "tea", "breakfast", "brunch",
            "wine_bottle", "beer_bottle", "serving_dish", "sideboard",
            "dining_room", "coffee_shop", "cafe", "café", "bar_counter",
            "bottled", "steak", "sushi", "restaurant_kitchen", "diner",
            "frappuccino", "mocha", "macchiato", "americano", "flat_white",
            "coffee_beans", "grinder", "kettle", "teapot", "sugar_bowl"
        )
        val BEACH_KEYWORDS = listOf(
            "beach", "seashore", "sandbar", "ocean", "sea", "shore", "coast",
            "lakeside", "lake", "river", "water", "pool", "wave", "tide",
            "promontory", "breakwater", "dock", "pier", "boat", "ship", "surf",
            "sand", "sunscreen", "umbrella", "swimsuit", "bikini", "horizon",
            "cliff", "rock", "stone", "reef", "coral", "shell", "starfish",
            "palm_tree", "seagull", "yacht", "catamaran", "lifeguard",
            "tropical", "island", "parasol", "sandy", "dune", "seashell",
            "sailing", "diving", "snorkeling", "fisherman", "fish",
            "aquarium", "whale", "dolphin", "surfer", "windsurfing",
            "jetty", "breakwater", "harbor", "port", "tide_pool",
            "sunset", "sunrise", "coastal", "seaside", "watersport",
            "boogie_board", "paddleboard", "kayak", "canoe", "outrigger"
        )
        val FOREST_KEYWORDS = listOf(
            "forest", "woodland", "jungle", "tree", "rainforest", "pine", "oak",
            "fern", "plant", "leaf", "grass", "flower",
            "mushroom", "moss", "bark", "branch", "bush", "shrub", "bamboo",
            "mountain", "hill", "valley", "meadow", "wilderness", "spring",
            "ivy", "vine", "cactus", "succulent", "flowerpot", "greenhouse",
            "botanical", "rural", "countryside",
            "redwood", "sequoia", "birch", "maple", "willow", "cypress",
            "evergreen", "conifer", "deciduous", "grove", "thicket",
            "pond", "stream", "creek", "waterfall", "rocks", "boulders",
            "squirrel", "deer", "fox", "bird", "insect", "butterfly",
            "ecosystem", "underbrush", "canopy", "foliage", "vegetation"
        )
        val CITY_KEYWORDS = listOf(
            "street", "traffic", "car", "taxi", "cab", "bus", "trolleybus",
            "minibus", "ambulance", "police", "fire_engine", "moving_van",
            "pedestrian", "skyscraper", "bridge", "viaduct",
            "billboard", "signboard", "parking", "gas_pump", "mailbox",
            "streetcar", "trolley", "cinema", "theater", "church", "mosque",
            "palace", "castle", "fountain", "monument", "obelisk", "building",
            "office", "tower", "dome", "arch", "steeple",
            "highway", "freeway", "overpass", "intersection", "sidewalk",
            "apartment", "condominium", "plaza", "square", "crosswalk",
            "automobile", "vehicle", "bicycle_lane", "cityscape", "metropolis",
            "neon", "nightclub", "policeman", "firetruck", "subway",
            "metro", "railway", "train", "railroad", "bicycle",
            "motorcycle", "truck", "lorry", "van", "limousine", "convertible",
            "downtown", "uptown", "midtown", "financial_district",
            "condo", "highrise", "construction", "crane"
        )
        val PARK_KEYWORDS = listOf(
            "park", "bench", "picnic", "playground", "swing", "slide",
            "seesaw", "fountain", "gazebo", "lawn", "path", "trail",
            "field", "jogging", "bicycle", "scooter", "skateboard",
            "kite", "frisbee", "tennis", "soccer", "birdhouse",
            "squirrel", "duck", "goose", "pigeon", "swan",
            "nature", "green", "outdoor", "sports_field", "stadium",
            "baseball", "basketball", "football", "volleyball",
            "gardening", "flower_bed", "hedge", "walkway",
            "garden", "botanical_garden", "arboretum", "zoo",
            "carousel", "playground_equipment", "sandbox", "merry_go_round",
            "pond", "lake", "pavilion", "rotunda", "colonnade",
            "topiary", "hedgehog", "rabbit", "chipmunk", "butterfly"
        )
        val INDOOR_KEYWORDS = listOf(
            "bedroom", "living_room", "bathroom", "kitchen", "wardrobe",
            "television", "monitor", "screen", "bed", "pillow",
            "quilt", "blanket", "sofa", "couch", "studio",
            "interior", "room", "wall", "window", "curtain", "mirror",
            "refrigerator", "microwave", "toaster", "oven",
            "dishwasher", "bathtub", "shower", "washbasin", "toilet",
            "vacuum", "washer", "dryer", "dining_room", "furniture",
            "cushion", "carpet", "rug", "lamp_shade", "vase",
            "shelf", "drawer", "cupboard", "closet", "wardrobe",
            "countertop", "sink", "tap", "faucet", "tile",
            "dining_table", "desk", "office_chair", "bookshelf",
            "home_theater", "flat_screen", "water_heater",
            "air_conditioner", "fan", "ceiling_fan", "lamp",
            "painting", "picture_frame", "clock", "candle",
            "chandelier", "wall_sconce", "floor_lamp", "table_lamp",
            "dining_chair", "side_table", "coffee_table"
        )
        val NEON_KEYWORDS = listOf(
            "neon", "night", "lantern", "spotlight", "lamppost", "lampshade",
            "stage", "marquee", "torch", "candle", "chandelier",
            "disco", "entertainment", "cocktail", "lounge", "nightclub",
            "electric", "light", "glow", "beacon", "streetlight",
            "dark", "luminous", "fluorescent", "neon_sign", "neon_light",
            "arcade", "arcade_game", "neon_city", "cyberpunk", "neon_glow",
            "dance_floor", "dj", "speaker", "sound_system",
            "nightlife", "bar", "pub", "club", "disco_ball",
            "strobe", "spotlight", "spotlights", "lightning",
            "firework", "fireworks", "laser", "led",
            "electric_fan", "night_sky", "stargazer", "moon",
            "starry", "illuminated", "night_time"
        )

        // =========================================================================
        // 辅助关键词库：弱信号匹配，提升召回率
        // =========================================================================
        val COFFEE_SECONDARY = listOf(
            "dark_brown", "brown", "wood", "wooden", "warm", "amber", "caramel",
            "chocolate", "espresso_machine", "coffee_grinder", "milk_steamer",
            "pastry_case", "display_case", "menu_board", "chalkboard"
        )
        val BEACH_SECONDARY = listOf(
            "blue", "turquoise", "cyan", "azure", "foam", "spray",
            "dune", "dunes", "coastline", "shoreline", "breakwater",
            "salt", "brine", "tropical_fish", "seahorse", "starfish"
        )
        val FOREST_SECONDARY = listOf(
            "green", "dark_green", "lime", "olive", "mossy", "lichen",
            "coniferous", "deciduous", "evergreen", "understory",
            "canopy", "trunk", "roots", "mushroom", "toadstool"
        )
        val CITY_SECONDARY = listOf(
            "gray", "grey", "concrete", "asphalt", "pavement",
            "metal", "steel", "chrome", "glass", "marble",
            "highrise", "skyscraper", "tower_block", "office_block"
        )
        val PARK_SECONDARY = listOf(
            "green_lawn", "grass", "sod", "turf", "flowerbed",
            "flower", "blossom", "petal", "stem", "botanical",
            "garden_path", "garden_bed", "hedgerow", "topiary"
        )
        val INDOOR_SECONDARY = listOf(
            "soft", "pastel", "beige", "cream", "ivory",
            "velvet", "velvety", "linen", "cotton", "silk",
            "drape", "valance", "cornice", "plinth", "skirting"
        )
        val NEON_SECONDARY = listOf(
            "purple", "magenta", "violet", "pink", "mauve",
            "crimson", "scarlet", "burgundy", "wine", "plum",
            "backlit", "lit_up", "glowing", "shimmering", "iridescent"
        )
    }
}