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
 */
class SceneClassifier(context: Context) {

    private val labeler = ImageLabeling.getClient(
        ImageLabelerOptions.Builder()
            .setConfidenceThreshold(0.4f)
            .build()
    )

    /** 在帧回调线程做关键词投票 */
    fun classify(bitmap: Bitmap, onResult: (SceneType) -> Unit) {
        val image = InputImage.fromBitmap(bitmap, 0)
        labeler.process(image)
            .addOnSuccessListener { labels ->
                onResult(mapLabels(labels.map { it.text }))
            }
            .addOnFailureListener { onResult(SceneType.UNKNOWN) }
    }

    private fun mapLabels(labels: List<String>): SceneType {
        var votes = Array(7) { 0f }
        val sceneIndex = mapOf(
            SceneType.COFFEE_SHOP to 0, SceneType.BEACH to 1, SceneType.FOREST to 2,
            SceneType.CITY_STREET to 3, SceneType.PARK to 4,
            SceneType.INDOOR_HOME to 5, SceneType.NEON_NIGHT to 6
        )

        // top labels（ML Kit 返回按置信度排序，取前 5 投票，与 iOS 一致）
        val topLabels = labels.take(5)

        for (label in topLabels) {
            val id = label.lowercase().replace(' ', '_')
            if (COFFEE_KEYWORDS.any { id.contains(it) }) votes[0] += 1f
            if (BEACH_KEYWORDS.any { id.contains(it) }) votes[1] += 1f
            if (FOREST_KEYWORDS.any { id.contains(it) }) votes[2] += 1f
            if (CITY_KEYWORDS.any { id.contains(it) }) votes[3] += 1f
            if (PARK_KEYWORDS.any { id.contains(it) }) votes[4] += 1f
            if (INDOOR_KEYWORDS.any { id.contains(it) }) votes[5] += 1f
            if (NEON_KEYWORDS.any { id.contains(it) }) votes[6] += 1f
        }

        var best = SceneType.UNKNOWN
        var bestWeight = 0f
        for ((scene, idx) in sceneIndex) {
            if (votes[idx] > bestWeight) {
                bestWeight = votes[idx]
                best = scene
            }
        }
        // 完全无匹配且无高置信标签时，降级为咖啡馆（场景库最丰富，与 iOS 兜底一致）
        if (best == SceneType.UNKNOWN && topLabels.isNotEmpty()) best = SceneType.COFFEE_SHOP
        return best
    }

    fun close() = labeler.close()

    companion object {
        val COFFEE_KEYWORDS = listOf(
            "coffee", "espresso", "cappuccino", "latte", "cup", "mug", "coffeepot",
            "restaurant", "dining", "cafeteria", "table", "chair", "stool", "bar",
            "bakery", "wine", "beer", "bottle", "plate", "food", "bread", "cake",
            "bookcase", "bookshelf", "library", "desk", "laptop", "computer",
            "vase", "lamp", "pot", "counter"
        )
        val BEACH_KEYWORDS = listOf(
            "beach", "seashore", "sandbar", "ocean", "sea", "shore", "coast",
            "lakeside", "lake", "river", "water", "pool", "wave", "tide",
            "promontory", "breakwater", "dock", "pier", "boat", "ship", "surf",
            "sand", "sunscreen", "umbrella", "swimsuit", "bikini", "horizon",
            "cliff", "rock", "stone"
        )
        val FOREST_KEYWORDS = listOf(
            "forest", "woodland", "jungle", "tree", "rainforest", "pine", "oak",
            "fern", "plant", "leaf", "grass", "flower", "garden",
            "mushroom", "moss", "bark", "branch", "bush", "shrub", "bamboo",
            "mountain", "hill", "valley", "meadow", "wilderness", "spring"
        )
        val CITY_KEYWORDS = listOf(
            "street", "traffic", "car", "taxi", "cab", "bus", "trolleybus",
            "minibus", "ambulance", "police", "fire_engine", "moving_van",
            "pedestrian", "skyscraper", "bridge", "viaduct",
            "billboard", "signboard", "parking", "gas_pump", "mailbox",
            "streetcar", "trolley", "cinema", "theater", "church", "mosque",
            "palace", "castle", "fountain", "monument", "obelisk", "building",
            "office", "tower", "dome", "arch", "steeple",
            "highway", "freeway", "overpass", "intersection", "sidewalk"
        )
        val PARK_KEYWORDS = listOf(
            "park", "bench", "picnic", "playground", "swing", "slide",
            "seesaw", "fountain", "gazebo", "lawn", "path", "trail",
            "field", "jogging", "bicycle", "scooter", "skateboard",
            "kite", "frisbee", "tennis", "soccer", "birdhouse",
            "squirrel", "duck", "goose", "pigeon", "swan",
            "nature", "green", "outdoor"
        )
        val INDOOR_KEYWORDS = listOf(
            "bedroom", "living_room", "bathroom", "kitchen", "wardrobe",
            "television", "monitor", "screen", "bed", "pillow",
            "quilt", "blanket", "sofa", "couch", "studio",
            "interior", "room", "wall", "window", "curtain", "mirror",
            "refrigerator", "microwave", "toaster", "oven",
            "dishwasher", "bathtub", "shower", "washbasin", "toilet",
            "vacuum", "washer", "dryer"
        )
        val NEON_KEYWORDS = listOf(
            "neon", "night", "lantern", "spotlight", "lamppost", "lampshade",
            "stage", "marquee", "torch", "candle", "chandelier",
            "disco", "entertainment", "cocktail", "lounge", "nightclub",
            "electric", "light", "glow", "beacon", "streetlight",
            "dark", "luminous", "fluorescent"
        )
    }
}