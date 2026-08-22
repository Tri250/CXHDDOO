package com.poseai.app.ai

import android.content.Context
import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.poseai.app.model.SceneType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * AI 穿搭顾问——转换自 iOS AIAdvisor。
 * 使用 ML Kit ImageLabeling 进行离线 OOTD 分析，并结合场景给出情感价值建议。
 *
 * 关键修复：
 *  - 使用 ML Kit 实际分析图片（而非返回 mock 数据）
 *  - 基于 ImageNet 标签特征分类为 5 种穿搭风格
 *  - 结合场景生成个性化情感文案
 */
object AIAdvisor {

    private var labeler: com.google.mlkit.vision.label.ImageLabeler? = null

    private fun ensureLabeler(): com.google.mlkit.vision.label.ImageLabeler {
        return labeler ?: ImageLabeling.getClient(
            ImageLabelerOptions.Builder().setConfidenceThreshold(0.3f).build()
        ).also { labeler = it }
    }

    /** 穿搭风格枚举（对应 iOS 的 OOTD 分类） */
    private enum class OOTDCategory {
        ELEGANT,   // 飘逸长裙
        CASUAL,    // 休闲针织衫
        BUSINESS,  // 干练风衣
        LOUNGE,    // 日常慵懒风
        STREET,    // 时尚休闲套装
        UNKNOWN
    }

    /** 利用视觉能力解析 OOTD 并结合场景给出情感价值建议 */
    suspend fun analyzeOOTD(image: Bitmap?, currentScene: SceneType): String {
        if (image == null) {
            return getFallbackAdvice(currentScene)
        }

        return withContext(Dispatchers.IO) {
            // 缩放图片加速 ML Kit 处理
            val scaled = scaleDown(image, maxDim = 256)
            val category = detectCategory(scaled)
            val sceneName = currentScene.displayName
            val ootdName = category.displayName()

            buildAdvice(category, sceneName, ootdName)
        }
    }

    /** 从标签列表推断穿搭类别 */
    private suspend fun detectCategory(bitmap: Bitmap): OOTDCategory {
        return try {
            val image = InputImage.fromBitmap(bitmap, 0)
            val labels = suspendCoroutine { cont ->
                ensureLabeler().process(image)
                    .addOnSuccessListener { result -> cont.resume(result) }
                    .addOnFailureListener { cont.resume(emptyList()) }
            }
            val labelTexts = labels.map { it.text.lowercase() }
            scoreCategory(labelTexts)
        } catch (_: Exception) {
            OOTDCategory.UNKNOWN
        }
    }

    /** 基于 ImageNet 标签投票分类穿搭 */
    private fun scoreCategory(labels: List<String>): OOTDCategory {
        var elegant = 0f      // 飘逸长裙
        var casual = 0f       // 休闲针织衫
        var business = 0f     // 干练风衣
        var lounge = 0f       // 慵懒风
        var street = 0f       // 时尚套装

        for (label in labels) {
            when {
                ELEGANT_KEYWORDS.any { label.contains(it) } -> elegant += 1f
                CASUAL_KEYWORDS.any { label.contains(it) } -> casual += 1f
                BUSINESS_KEYWORDS.any { label.contains(it) } -> business += 1f
                LOUNGE_KEYWORDS.any { label.contains(it) } -> lounge += 1f
                STREET_KEYWORDS.any { label.contains(it) } -> street += 1f
            }
        }

        val scores = mapOf(
            OOTDCategory.ELEGANT to elegant,
            OOTDCategory.CASUAL to casual,
            OOTDCategory.BUSINESS to business,
            OOTDCategory.LOUNGE to lounge,
            OOTDCategory.STREET to street
        )
        return scores.maxByOrNull { it.value }?.key ?: OOTDCategory.UNKNOWN
    }

    private fun buildAdvice(category: OOTDCategory, sceneName: String, ootdName: String): String {
        return when (category) {
            OOTDCategory.ELEGANT ->
                "捕捉到您今天穿着${ootdName}，非常绝美！尝试双手微微拎起裙摆，在这个${sceneName}中心旋转一下，我们会抓拍那飞扬的一刻！"
            OOTDCategory.BUSINESS ->
                "这套${ootdName}太有高级质感了。在这个${sceneName}建议您稍微整理一下衣领然后单手插兜，眼神不用看我，看向远方极其出片！"
            OOTDCategory.CASUAL, OOTDCategory.LOUNGE ->
                "监测到了非常舒服的${ootdName}穿搭！在这片${sceneName}不要拘束，像平时伸懒腰一样大幅度伸展双臂，我要抓下这段松弛感。"
            OOTDCategory.STREET ->
                "您的这身${ootdName}与${sceneName}完美搭配！建议插兜走两步，回头看镜头，把街头酷感拿捏到位！"
            OOTDCategory.UNKNOWN ->
                "您的这身穿搭与这里的${sceneName}绝配，尝试侧对屏幕，来个自然的回眸一笑吧！"
        }
    }

    private fun getFallbackAdvice(scene: SceneType): String {
        return when (scene) {
            SceneType.COFFEE_SHOP -> "在咖啡馆里，低头微笑捧杯，或看窗外的风景，氛围拉满。"
            SceneType.BEACH -> "海边建议张开双臂拥抱海风，或背对镜头回眸一笑，留下最灿烂的回忆。"
            SceneType.FOREST -> "森林里轻轻触摸树叶，或抬头仰望阳光透过枝叶，仙气满满。"
            SceneType.CITY_STREET -> "城市街道建议大步流星往前走，突然回头看镜头，最出片。"
            SceneType.PARK -> "公园草地上坐下来轻轻托腮，或伸手比耶，笑容自然就好。"
            SceneType.INDOOR_HOME -> "室内建议靠在沙发或窗边，温柔微笑，家的温馨感拉满。"
            SceneType.NEON_NIGHT -> "霓虹灯下侧身站立，微抬下巴，或抬手挡光，赛博感十足。"
            else -> "摆一个自然的姿势，放松身体，我来帮您捕捉最美好的瞬间。"
        }
    }

    private fun scaleDown(bitmap: Bitmap, maxDim: Int): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val larger = maxOf(w, h)
        if (larger <= maxDim) return bitmap
        val scale = maxDim.toFloat() / larger
        val newW = (w * scale).toInt()
        val newH = (h * scale).toInt()
        return Bitmap.createScaledBitmap(bitmap, newW, newH, true)
    }

    // MARK: - 关键词库（ImageNet 标签）

    private val ELEGANT_KEYWORDS = listOf(
        "dress", "gown", "silk", "lace", "chiffon", "ballgown",
        "evening_gown", "graceful", "elegant", "glamour",
        "high_heels", "stilettos", "fashion_model"
    )
    private val CASUAL_KEYWORDS = listOf(
        "sweater", "pullover", "knitwear", "cardigan", "hoodie",
        "tshirt", "blouse", "shirt", "cotton", "casual"
    )
    private val BUSINESS_KEYWORDS = listOf(
        "coat", "trench_coat", "blazer", "suit", "jacket",
        "business_suit", "pantsuit", "formal", "professional",
        "necktie", "briefcase"
    )
    private val LOUNGE_KEYWORDS = listOf(
        "robe", "bathrobe", "loungewear", "sleepwear", "pajamas",
        "loose_dress", "oversized", "relaxed"
    )
    private val STREET_KEYWORDS = listOf(
        "jeans", "denim", "joggers", "sneakers", "trainers",
        "leather_jacket", "bomber_jacket", "streetwear",
        "sportswear", "athleisure", "skateboarder"
    )

    private fun OOTDCategory.displayName(): String = when (this) {
        OOTDCategory.ELEGANT -> "飘逸长裙"
        OOTDCategory.CASUAL -> "休闲针织衫"
        OOTDCategory.BUSINESS -> "干练风衣"
        OOTDCategory.LOUNGE -> "日常慵懒风"
        OOTDCategory.STREET -> "时尚休闲套装"
        OOTDCategory.UNKNOWN -> "独特穿搭"
    }
}
