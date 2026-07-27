package com.poseai.app.util

import android.graphics.Bitmap

// ═══════════════════════════════════════════════════════════════
// 滤镜库：50+ 滤镜，按 8 个分类组织。
// 每个 FilterInfo 持有一个 PhotoFilterEngine.PhotoFilter 枚举值，
// 实际调色由 PhotoFilterEngine.applyFilter() 完成（每个枚举值对应独立色彩数学）。
// ═══════════════════════════════════════════════════════════════

/**
 * 滤镜分类。
 */
enum class FilterCategory(val displayName: String) {
    ORIGINAL("原图"),
    PORTRAIT("人像"),
    LANDSCAPE("风景"),
    FOOD("美食"),
    RETRO("复古"),
    CINEMA("电影"),
    BW("黑白"),
    ART("艺术")
}

/**
 * 滤镜信息：唯一 id + 显示名 + 分类 + 底层 PhotoFilter。
 */
data class FilterInfo(
    val id: String,
    val name: String,
    val category: FilterCategory,
    val filter: PhotoFilterEngine.Filter
)

/**
 * 滤镜库。共 51 套滤镜，分布在 8 个分类下。
 * 每个滤镜均通过 [PhotoFilterEngine.applyFilter] 应用，使用各不相同的色彩数学实现。
 */
object FilterLibrary {

    /** 全部滤镜（51 套），按分类顺序排列。 */
    val ALL_FILTERS: List<FilterInfo> = listOf(
        // ───── 原图 ORIGINAL ─────
        FilterInfo("original", "原图", FilterCategory.ORIGINAL, PhotoFilterEngine.Filter.ORIGINAL),

        // ───── 人像 PORTRAIT（8）─────
        FilterInfo("portrait_natural", "自然美肤", FilterCategory.PORTRAIT, PhotoFilterEngine.Filter.PORTRAIT_NATURAL),
        FilterInfo("portrait_creamy", "奶油肌", FilterCategory.PORTRAIT, PhotoFilterEngine.Filter.PORTRAIT_CREAMY),
        FilterInfo("portrait_peach", "蜜桃", FilterCategory.PORTRAIT, PhotoFilterEngine.Filter.PORTRAIT_PEACH),
        FilterInfo("portrait_cool_white", "冷白皮", FilterCategory.PORTRAIT, PhotoFilterEngine.Filter.PORTRAIT_COOL_WHITE),
        FilterInfo("portrait_warm_yellow", "暖黄皮", FilterCategory.PORTRAIT, PhotoFilterEngine.Filter.PORTRAIT_WARM_YELLOW),
        FilterInfo("portrait_pink", "粉嫩", FilterCategory.PORTRAIT, PhotoFilterEngine.Filter.PORTRAIT_PINK),
        FilterInfo("portrait_clear", "清透", FilterCategory.PORTRAIT, PhotoFilterEngine.Filter.PORTRAIT_CLEAR),
        FilterInfo("portrait_rosy", "红润", FilterCategory.PORTRAIT, PhotoFilterEngine.Filter.PORTRAIT_ROSY),

        // ───── 风景 LANDSCAPE（8）─────
        FilterInfo("land_green", "青绿", FilterCategory.LANDSCAPE, PhotoFilterEngine.Filter.LAND_GREEN),
        FilterInfo("land_azure", "蔚蓝", FilterCategory.LANDSCAPE, PhotoFilterEngine.Filter.LAND_AZURE),
        FilterInfo("land_warm_sun", "暖阳", FilterCategory.LANDSCAPE, PhotoFilterEngine.Filter.LAND_WARM_SUN),
        FilterInfo("land_dusk", "暮色", FilterCategory.LANDSCAPE, PhotoFilterEngine.Filter.LAND_DUSK),
        FilterInfo("land_emerald", "翠绿", FilterCategory.LANDSCAPE, PhotoFilterEngine.Filter.LAND_EMERALD),
        FilterInfo("land_deep_sea", "深海", FilterCategory.LANDSCAPE, PhotoFilterEngine.Filter.LAND_DEEP_SEA),
        FilterInfo("land_mist", "晨雾", FilterCategory.LANDSCAPE, PhotoFilterEngine.Filter.LAND_MIST),
        FilterInfo("land_autumn", "秋意", FilterCategory.LANDSCAPE, PhotoFilterEngine.Filter.LAND_AUTUMN),

        // ───── 美食 FOOD（6）─────
        FilterInfo("food_fresh", "鲜美", FilterCategory.FOOD, PhotoFilterEngine.Filter.FOOD_FRESH),
        FilterInfo("food_rich", "饱满", FilterCategory.FOOD, PhotoFilterEngine.Filter.FOOD_RICH),
        FilterInfo("food_warm", "暖食", FilterCategory.FOOD, PhotoFilterEngine.Filter.FOOD_WARM),
        FilterInfo("food_light", "清新", FilterCategory.FOOD, PhotoFilterEngine.Filter.FOOD_LIGHT),
        FilterInfo("food_intense", "浓郁", FilterCategory.FOOD, PhotoFilterEngine.Filter.FOOD_INTENSE),
        FilterInfo("food_dessert", "甜点", FilterCategory.FOOD, PhotoFilterEngine.Filter.FOOD_DESSERT),

        // ───── 复古 RETRO（8）─────
        FilterInfo("retro_film", "胶片", FilterCategory.RETRO, PhotoFilterEngine.Filter.FILM),
        FilterInfo("retro_fade", "褪色", FilterCategory.RETRO, PhotoFilterEngine.Filter.FADE),
        FilterInfo("retro_old_photo", "老照片", FilterCategory.RETRO, PhotoFilterEngine.Filter.RETRO_OLD_PHOTO),
        FilterInfo("retro_vintage", "怀旧", FilterCategory.RETRO, PhotoFilterEngine.Filter.VINTAGE),
        FilterInfo("retro_kodak", "柯达", FilterCategory.RETRO, PhotoFilterEngine.Filter.RETRO_KODAK),
        FilterInfo("retro_fuji", "富士", FilterCategory.RETRO, PhotoFilterEngine.Filter.RETRO_FUJI),
        FilterInfo("retro_polaroid", "宝丽来", FilterCategory.RETRO, PhotoFilterEngine.Filter.RETRO_POLAROID),
        FilterInfo("retro_darkroom", "暗房", FilterCategory.RETRO, PhotoFilterEngine.Filter.RETRO_DARKROOM),

        // ───── 电影 CINEMA（7）─────
        FilterInfo("cinema_teal_orange", "青橙", FilterCategory.CINEMA, PhotoFilterEngine.Filter.NEON),
        FilterInfo("cinema_cinematic", "电影感", FilterCategory.CINEMA, PhotoFilterEngine.Filter.CINEMATIC),
        FilterInfo("cinema_dark", "暗调", FilterCategory.CINEMA, PhotoFilterEngine.Filter.CINEMA_DARK),
        FilterInfo("cinema_dramatic", "高对比", FilterCategory.CINEMA, PhotoFilterEngine.Filter.DRAMATIC),
        FilterInfo("cinema_warm", "暖调电影", FilterCategory.CINEMA, PhotoFilterEngine.Filter.CINEMA_WARM),
        FilterInfo("cinema_cool", "冷调电影", FilterCategory.CINEMA, PhotoFilterEngine.Filter.CINEMA_COOL),
        FilterInfo("cinema_cyberpunk", "赛博朋克", FilterCategory.CINEMA, PhotoFilterEngine.Filter.CINEMA_CYBERPUNK),

        // ───── 黑白 BW（6）─────
        FilterInfo("bw_noir", "高反差黑白", FilterCategory.BW, PhotoFilterEngine.Filter.NOIR),
        FilterInfo("bw_soft", "柔和黑白", FilterCategory.BW, PhotoFilterEngine.Filter.BW_SOFT),
        FilterInfo("bw_silver", "银盐", FilterCategory.BW, PhotoFilterEngine.Filter.BW_SILVER),
        FilterInfo("bw_carbon", "碳素", FilterCategory.BW, PhotoFilterEngine.Filter.BW_CARBON),
        FilterInfo("bw_ink", "水墨", FilterCategory.BW, PhotoFilterEngine.Filter.BW_INK),
        FilterInfo("bw_minimal", "极简", FilterCategory.BW, PhotoFilterEngine.Filter.BW_MINIMAL),

        // ───── 艺术 ART（7）─────
        FilterInfo("art_oil", "油画", FilterCategory.ART, PhotoFilterEngine.Filter.ART_OIL),
        FilterInfo("art_watercolor", "水彩", FilterCategory.ART, PhotoFilterEngine.Filter.ART_WATERCOLOR),
        FilterInfo("art_sketch", "素描", FilterCategory.ART, PhotoFilterEngine.Filter.ART_SKETCH),
        FilterInfo("art_neon", "霓虹", FilterCategory.ART, PhotoFilterEngine.Filter.ART_NEON),
        FilterInfo("art_dreamy", "梦幻", FilterCategory.ART, PhotoFilterEngine.Filter.ART_DREAMY),
        FilterInfo("art_impressionist", "印象派", FilterCategory.ART, PhotoFilterEngine.Filter.ART_IMPRESSIONIST),
        FilterInfo("art_vaporwave", "蒸汽波", FilterCategory.ART, PhotoFilterEngine.Filter.ART_VAPORWAVE)
    )

    /** 按分类分组的滤镜（保持 ALL_FILTERS 顺序） */
    val FILTERS_BY_CATEGORY: Map<FilterCategory, List<FilterInfo>> by lazy {
        ALL_FILTERS.groupBy { it.category }
    }

    /** 全部分类（按枚举声明顺序） */
    val CATEGORIES: List<FilterCategory> = FilterCategory.values().toList()

    /** 按 id 查找滤镜 */
    fun findById(id: String): FilterInfo? = ALL_FILTERS.firstOrNull { it.id == id }

    /** 获取指定分类下的全部滤镜 */
    fun filtersByCategory(category: FilterCategory): List<FilterInfo> =
        FILTERS_BY_CATEGORY[category] ?: emptyList()

    /** 应用某个滤镜到 Bitmap，等价于 PhotoFilterEngine.applyFilter(source, info.filter) */
    fun apply(source: Bitmap, info: FilterInfo): Bitmap =
        PhotoFilterEngine.applyFilter(source, info.filter)

    /** 应用某个滤镜到 Bitmap（按 id），id 不存在则返回原图副本 */
    fun applyById(source: Bitmap, id: String): Bitmap {
        val info = findById(id)
            ?: return source.copy(source.config ?: Bitmap.Config.ARGB_8888, true)
        return apply(source, info)
    }
}
