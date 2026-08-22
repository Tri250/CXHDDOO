package com.poseai.app.ml

import android.content.Context
import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.poseai.app.model.SceneType

/**
 * 场景分类提供者——对应 iOS 的 MobileNetV2/Places365 场景识别。
 * Android 端使用 ML Kit Image Labeling（离线 ImageNet 标签），并用与 iOS 一致的关键词投票映射到 SceneType。
 *
 * 关键修复：
 *  - 置信度加权投票（取置信度加权而非简单计数）
 *  - 扩展关键词覆盖（ML Kit 返回的 ImageNet 标签更丰富）
 *  - 双帧确认：连续 N 帧相同才确认，避免误识别抖动
 *  - 兜底：长时间未识别则降级到咖啡馆
 */
class SceneClassifier(context: Context) {

    private val labeler = ImageLabeling.getClient(
        ImageLabelerOptions.Builder()
            .setConfidenceThreshold(0.3f)
            .build()
    )

    // 双帧确认机制
    private var lastCandidate: SceneType = SceneType.UNKNOWN
    private var sameCandidateFrames = 0
    private val confirmThreshold = 2

    /** 置信度加权投票 */
    fun classify(bitmap: Bitmap, onResult: (SceneType) -> Unit) {
        val image = InputImage.fromBitmap(bitmap, 0)
        labeler.process(image)
            .addOnSuccessListener { labels ->
                val result = mapLabels(labels.map { label -> label.text to label.confidence })
                // 双帧确认防抖
                if (result == lastCandidate) {
                    sameCandidateFrames++
                } else {
                    lastCandidate = result
                    sameCandidateFrames = 1
                }
                if (sameCandidateFrames >= confirmThreshold || result == SceneType.UNKNOWN) {
                    onResult(result)
                }
            }
            .addOnFailureListener { onResult(SceneType.UNKNOWN) }
    }

    /** 重置确认状态（外部调用后） */
    fun reset() {
        lastCandidate = SceneType.UNKNOWN
        sameCandidateFrames = 0
    }

    private fun mapLabels(labels: List<Pair<String, Float>>): SceneType {
        val votes = FloatArray(7) // 对应 7 种场景
        val sceneIndex = mapOf(
            SceneType.COFFEE_SHOP to 0, SceneType.BEACH to 1, SceneType.FOREST to 2,
            SceneType.CITY_STREET to 3, SceneType.PARK to 4,
            SceneType.INDOOR_HOME to 5, SceneType.NEON_NIGHT to 6
        )

        // 取前 8 个高置信标签投票（覆盖更多上下文）
        val topLabels = labels.take(8)

        for ((label, confidence) in topLabels) {
            val id = label.lowercase().replace(' ', '_')
            val weight = confidence.coerceAtLeast(0.3f)
            if (COFFEE_KEYWORDS.any { id.contains(it) }) votes[0] += weight
            if (BEACH_KEYWORDS.any { id.contains(it) }) votes[1] += weight
            if (FOREST_KEYWORDS.any { id.contains(it) }) votes[2] += weight
            if (CITY_KEYWORDS.any { id.contains(it) }) votes[3] += weight
            if (PARK_KEYWORDS.any { id.contains(it) }) votes[4] += weight
            if (INDOOR_KEYWORDS.any { id.contains(it) }) votes[5] += weight
            if (NEON_KEYWORDS.any { id.contains(it) }) votes[6] += weight
        }

        // 最大投票数需 >= 1.0 才有效（避免弱信号误判）
        val maxIndex = votes.indices.maxByOrNull { votes[it] } ?: return SceneType.UNKNOWN
        if (votes[maxIndex] < 1.0f) return SceneType.UNKNOWN

        val scene = sceneIndex.entries.firstOrNull { it.value == maxIndex }?.key ?: SceneType.UNKNOWN
        // 完全无匹配时降级为咖啡馆（场景库最丰富）
        return if (scene == SceneType.UNKNOWN && topLabels.isNotEmpty()) SceneType.COFFEE_SHOP else scene
    }

    fun close() = labeler.close()

    companion object {
        val COFFEE_KEYWORDS = listOf(
            "coffee", "espresso", "cappuccino", "latte", "cup", "mug", "coffeepot",
            "restaurant", "dining", "cafeteria", "table", "chair", "stool", "bar",
            "bakery", "wine", "beer", "bottle", "plate", "food", "bread", "cake",
            "bookcase", "bookshelf", "library", "desk", "laptop", "computer",
            "vase", "lamp", "pot", "counter", "tearoom", "brasserie", "pub",
            "pizza", "pasta", "burger", "sandwich", "salad", "menu", "waiter",
            "candelabra", "chandelier", "interior_design", "coffee_mug"
        )
        val BEACH_KEYWORDS = listOf(
            "beach", "seashore", "sandbar", "ocean", "sea", "shore", "coast",
            "lakeside", "lake", "river", "water", "pool", "wave", "tide",
            "promontory", "breakwater", "dock", "pier", "boat", "ship", "surf",
            "sand", "sunscreen", "umbrella", "swimsuit", "bikini", "horizon",
            "cliff", "rock", "stone", "reef", "coral", "shell", "starfish",
            "palm_tree", "seagull", "yacht", "catamaran", "lifeguard"
        )
        val FOREST_KEYWORDS = listOf(
            "forest", "woodland", "jungle", "tree", "rainforest", "pine", "oak",
            "fern", "plant", "leaf", "grass", "flower", "garden",
            "mushroom", "moss", "bark", "branch", "bush", "shrub", "bamboo",
            "mountain", "hill", "valley", "meadow", "wilderness", "spring",
            "ivy", "vine", "cactus", "succulent", "flowerpot", "greenhouse",
            "botanical", "rural", "countryside"
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
            "automobile", "vehicle", "bicycle_lane", "cityscape", "metropolis"
        )
        val PARK_KEYWORDS = listOf(
            "park", "bench", "picnic", "playground", "swing", "slide",
            "seesaw", "fountain", "gazebo", "lawn", "path", "trail",
            "field", "jogging", "bicycle", "scooter", "skateboard",
            "kite", "frisbee", "tennis", "soccer", "birdhouse",
            "squirrel", "duck", "goose", "pigeon", "swan",
            "nature", "green", "outdoor", "sports_field", "stadium",
            "baseball", "basketball", "football", "volleyball",
            "gardening", "flower_bed", "hedge", "walkway"
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
            "countertop", "sink", "tap", "faucet", "tile"
        )
        val NEON_KEYWORDS = listOf(
            "neon", "night", "lantern", "spotlight", "lamppost", "lampshade",
            "stage", "marquee", "torch", "candle", "chandelier",
            "disco", "entertainment", "cocktail", "lounge", "nightclub",
            "electric", "light", "glow", "beacon", "streetlight",
            "dark", "luminous", "fluorescent", "neon_sign", "neon_light",
            "arcade", "arcade_game", "neon_city", "cyberpunk", "neon_glow",
            "dance_floor", "dj", "speaker", "sound_system"
        )
    }
}
