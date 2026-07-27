package com.poseai.app.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Rect
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

object PhotoFilterEngine {

    enum class Filter(val displayName: String) {
        ORIGINAL("原图"),
        VIVID("鲜明"),
        WARM("暖色"),
        COOL("冷色"),
        FADE("褪色"),
        VINTAGE("复古"),
        MONO("黑白"),
        DRAMATIC("戏剧"),
        // P5-4 拍后调色预设：4 套电影级调色
        FILM("胶片感"),      // 青暗部 + 暖高光（复刻柯达调性）
        NOIR("高级黑白"),    // 大反差强锐度黑白
        LIGHT("日系清透"),   // 低对比过曝 + 降饱和
        NEON("城市霓虹"),    // Teal & Orange 青橙赛博朋克

        // ───── 人像 PORTRAIT：8 套，肤色优化 + 色调偏移 ─────
        PORTRAIT_NATURAL("自然美肤"),   // 轻微提亮 + 降饱和 + 暖偏
        PORTRAIT_CREAMY("奶油肌"),      // 提亮 + 暖黄 + 降对比
        PORTRAIT_PEACH("蜜桃"),         // 粉橙偏色 + 提亮
        PORTRAIT_COOL_WHITE("冷白皮"),  // 提亮 + 偏冷 + 降饱和
        PORTRAIT_WARM_YELLOW("暖黄皮"), // 暖黄通道提升
        PORTRAIT_PINK("粉嫩"),          // 粉色偏色 + 提亮 + 降对比
        PORTRAIT_CLEAR("清透"),         // 提亮 + 降对比 + 微冷
        PORTRAIT_ROSY("红润"),          // 红润偏色 + 暖

        // ───── 风景 LANDSCAPE：8 套，色调强化 + 对比 ─────
        LAND_GREEN("青绿"),       // 绿蓝增强 + 偏冷绿
        LAND_AZURE("蔚蓝"),       // 蓝色增强 + 偏冷
        LAND_WARM_SUN("暖阳"),    // 暖色提亮
        LAND_DUSK("暮色"),        // 紫红暮色 + 暗部偏紫
        LAND_EMERALD("翠绿"),     // 深绿 + 对比
        LAND_DEEP_SEA("深海"),    // 深蓝暗调
        LAND_MIST("晨雾"),        // 雾感提亮 + 降饱和 + 冷白
        LAND_AUTUMN("秋意"),      // 暖橙黄落叶调

        // ───── 美食 FOOD：6 套，饱和 + 食欲色 ─────
        FOOD_FRESH("鲜美"),       // 饱和提亮 + 偏暖
        FOOD_RICH("饱满"),        // 高饱和高对比 + 暖
        FOOD_WARM("暖食"),        // 暖通道提升
        FOOD_LIGHT("清新"),       // 提亮降对比 + 微冷
        FOOD_INTENSE("浓郁"),     // 高对比高饱和 + 暗调
        FOOD_DESSERT("甜点"),     // 粉暖提亮 + 降饱和

        // ───── 复古 RETRO：5 套（胶片/褪色/怀旧复用既有）─────
        RETRO_OLD_PHOTO("老照片"), // 黄褐 + 降饱和 + 暗角
        RETRO_KODAK("柯达"),       // 柯达黄红暖 + 对比
        RETRO_FUJI("富士"),        // 富士绿青 + 饱和
        RETRO_POLAROID("宝丽来"),  // 暖黄 + 降对比提亮
        RETRO_DARKROOM("暗房"),    // 暗房红光 + 暗调

        // ───── 电影 CINEMA：5 套（青橙/高对比复用既有）─────
        CINEMATIC("电影感"),       // 对比 + 青暗部 + 暖高光
        CINEMA_DARK("暗调"),       // 降亮 + 对比 + 偏冷
        CINEMA_WARM("暖调电影"),   // 暖橙电影调
        CINEMA_COOL("冷调电影"),   // 冷青电影调
        CINEMA_CYBERPUNK("赛博朋克"), // 霓虹紫青 + 高饱和

        // ───── 黑白 BW：5 套（高反差黑白复用 NOIR）─────
        BW_SOFT("柔和黑白"),       // 低对比暖灰
        BW_SILVER("银盐"),         // 中对比冷灰
        BW_CARBON("碳素"),         // 深黑高对比
        BW_INK("水墨"),            // 灰阶偏冷 + 中间调提亮
        BW_MINIMAL("极简"),        // 高调提亮黑白

        // ───── 艺术 ART：7 套，风格化 ─────
        ART_OIL("油画"),            // 高饱和高对比 + 暖厚
        ART_WATERCOLOR("水彩"),     // 提亮降饱和柔和 + 偏冷
        ART_SKETCH("素描"),         // 高对比灰度 + 色调分离
        ART_NEON("霓虹"),           // 高饱和紫粉
        ART_DREAMY("梦幻"),         // 柔和粉紫 + 降对比
        ART_IMPRESSIONIST("印象派"), // 高饱和暖 + 色调分离
        ART_VAPORWAVE("蒸汽波")      // 紫粉青 + 高饱和
    }

    fun applyFilter(source: Bitmap, filter: Filter): Bitmap {
        return try {
            when (filter) {
                Filter.ORIGINAL -> source.copy(source.config ?: Bitmap.Config.ARGB_8888, true)
                Filter.VIVID -> applySaturation(source, 1.4f)
                Filter.WARM -> applyColorTint(source, 1.1f, 1.0f, 0.85f)
                Filter.COOL -> applyColorTint(source, 0.9f, 1.0f, 1.15f)
                Filter.FADE -> applyFade(source)
                Filter.VINTAGE -> applyVintage(source)
                Filter.MONO -> applyGrayscale(source)
                Filter.DRAMATIC -> applyDramatic(source)
                Filter.FILM -> applyFilmLook(source)
                Filter.NOIR -> applyNoir(source)
                Filter.LIGHT -> applyLight(source)
                Filter.NEON -> applyNeon(source)
                // 扩展滤镜库（44 套）统一路由到 applyExtendedFilter
                else -> applyExtendedFilter(source, filter)
            }
        } catch (e: Exception) {
            source.copy(source.config ?: Bitmap.Config.ARGB_8888, true)
        }
    }

    /**
     * 扩展滤镜派发：FilterLibrary 50+ 滤镜的新增枚举值由此路由到各自独立色彩处理函数。
     * 每个分支使用不同的色彩数学（通道增益 / 饱和度 / 对比度 / 曲线 / 色调偏移）。
     */
    private fun applyExtendedFilter(source: Bitmap, filter: Filter): Bitmap = when (filter) {
        // 人像
        Filter.PORTRAIT_NATURAL -> applyPortraitNatural(source)
        Filter.PORTRAIT_CREAMY -> applyPortraitCreamy(source)
        Filter.PORTRAIT_PEACH -> applyPortraitPeach(source)
        Filter.PORTRAIT_COOL_WHITE -> applyPortraitCoolWhite(source)
        Filter.PORTRAIT_WARM_YELLOW -> applyPortraitWarmYellow(source)
        Filter.PORTRAIT_PINK -> applyPortraitPink(source)
        Filter.PORTRAIT_CLEAR -> applyPortraitClear(source)
        Filter.PORTRAIT_ROSY -> applyPortraitRosy(source)
        // 风景
        Filter.LAND_GREEN -> applyLandGreen(source)
        Filter.LAND_AZURE -> applyLandAzure(source)
        Filter.LAND_WARM_SUN -> applyLandWarmSun(source)
        Filter.LAND_DUSK -> applyLandDusk(source)
        Filter.LAND_EMERALD -> applyLandEmerald(source)
        Filter.LAND_DEEP_SEA -> applyLandDeepSea(source)
        Filter.LAND_MIST -> applyLandMist(source)
        Filter.LAND_AUTUMN -> applyLandAutumn(source)
        // 美食
        Filter.FOOD_FRESH -> applyFoodFresh(source)
        Filter.FOOD_RICH -> applyFoodRich(source)
        Filter.FOOD_WARM -> applyFoodWarm(source)
        Filter.FOOD_LIGHT -> applyFoodLight(source)
        Filter.FOOD_INTENSE -> applyFoodIntense(source)
        Filter.FOOD_DESSERT -> applyFoodDessert(source)
        // 复古
        Filter.RETRO_OLD_PHOTO -> applyRetroOldPhoto(source)
        Filter.RETRO_KODAK -> applyRetroKodak(source)
        Filter.RETRO_FUJI -> applyRetroFuji(source)
        Filter.RETRO_POLAROID -> applyRetroPolaroid(source)
        Filter.RETRO_DARKROOM -> applyRetroDarkroom(source)
        // 电影
        Filter.CINEMATIC -> applyCinematic(source)
        Filter.CINEMA_DARK -> applyCinemaDark(source)
        Filter.CINEMA_WARM -> applyCinemaWarm(source)
        Filter.CINEMA_COOL -> applyCinemaCool(source)
        Filter.CINEMA_CYBERPUNK -> applyCinemaCyberpunk(source)
        // 黑白
        Filter.BW_SOFT -> applyBwSoft(source)
        Filter.BW_SILVER -> applyBwSilver(source)
        Filter.BW_CARBON -> applyBwCarbon(source)
        Filter.BW_INK -> applyBwInk(source)
        Filter.BW_MINIMAL -> applyBwMinimal(source)
        // 艺术
        Filter.ART_OIL -> applyArtOil(source)
        Filter.ART_WATERCOLOR -> applyArtWatercolor(source)
        Filter.ART_SKETCH -> applyArtSketch(source)
        Filter.ART_NEON -> applyArtNeon(source)
        Filter.ART_DREAMY -> applyArtDreamy(source)
        Filter.ART_IMPRESSIONIST -> applyArtImpressionist(source)
        Filter.ART_VAPORWAVE -> applyArtVaporwave(source)
        else -> source.copy(source.config ?: Bitmap.Config.ARGB_8888, true)
    }

    // ═══════════════════════════════════════════════════════════════
    // 扩展滤镜通用工具：逐像素 RGB 变换
    // ═══════════════════════════════════════════════════════════════

    /** 带亮度的逐像素变换：transform 接收 0..255 的 r/g/b 与亮度 lum（0..255），返回变换后的 r/g/b（自动 clamp） */
    private fun applyPerPixelLum(
        source: Bitmap,
        transform: (r: Float, g: Float, b: Float, lum: Float) -> Triple<Float, Float, Float>
    ): Bitmap {
        return try {
            val width = source.width
            val height = source.height
            val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val pixels = IntArray(width * height)
            val outPixels = IntArray(width * height)
            source.getPixels(pixels, 0, width, 0, 0, width, height)
            for (i in pixels.indices) {
                val p = pixels[i]
                val r = Color.red(p).toFloat()
                val g = Color.green(p).toFloat()
                val b = Color.blue(p).toFloat()
                val lum = 0.299f * r + 0.587f * g + 0.114f * b
                val (nr, ng, nb) = transform(r, g, b, lum)
                outPixels[i] = Color.rgb(
                    nr.toInt().coerceIn(0, 255),
                    ng.toInt().coerceIn(0, 255),
                    nb.toInt().coerceIn(0, 255)
                )
            }
            result.setPixels(outPixels, 0, width, 0, 0, width, height)
            result
        } catch (e: Exception) {
            source.copy(source.config ?: Bitmap.Config.ARGB_8888, true)
        }
    }

    /** 围绕灰点 gray 调整饱和度：sat=1 不变，<1 降饱和，>1 提饱和 */
    private fun adjustSat(r: Float, g: Float, b: Float, sat: Float, gray: Float): Triple<Float, Float, Float> =
        Triple(gray + (r - gray) * sat, gray + (g - gray) * sat, gray + (b - gray) * sat)

    /** 亮度转灰度（Rec.601） */
    private fun grayOf(r: Float, g: Float, b: Float): Float = 0.299f * r + 0.587f * g + 0.114f * b

    // ═══════════════════════════════════════════════════════════════
    // 人像 PORTRAIT：8 套，肤色优化 + 色调偏移
    // ═══════════════════════════════════════════════════════════════

    private fun applyPortraitNatural(source: Bitmap) = applyPerPixelLum(source) { r, g, b, _ ->
        // 自然美肤：轻微提亮 1.05 + 降饱和 0.9 + 暖偏（R+5 / B-4）
        val gray = (r + g + b) / 3f
        val (sr, sg, sb) = adjustSat(r, g, b, 0.9f, gray)
        Triple(sr * 1.05f + 5f, sg * 1.05f + 3f, sb * 1.05f - 4f)
    }

    private fun applyPortraitCreamy(source: Bitmap) = applyPerPixelLum(source) { r, g, b, _ ->
        // 奶油肌：提亮 + 暖黄（R*1.04 / G*1.02 / B*0.92）+ 降饱和 0.88
        val gray = (r + g + b) / 3f
        val (sr, sg, sb) = adjustSat(r, g, b, 0.88f, gray)
        Triple(sr * 1.04f + 8f, sg * 1.02f + 6f, sb * 0.92f + 4f)
    }

    private fun applyPortraitPeach(source: Bitmap) = applyPerPixelLum(source) { r, g, b, _ ->
        // 蜜桃：粉橙偏色（R 保留 / G↓ / B↑ → 蜜桃粉）+ 提亮
        val gray = (r + g + b) / 3f
        val sr = gray + (r - gray) * 0.95f
        val sg = gray + (g - gray) * 0.85f
        val sb = gray + (b - gray) * 0.92f
        Triple(sr * 1.05f + 10f, sg + 4f, sb + 6f)
    }

    private fun applyPortraitCoolWhite(source: Bitmap) = applyPerPixelLum(source) { r, g, b, _ ->
        // 冷白皮：提亮 + 偏冷（B*1.08+12）+ 降饱和 0.85
        val gray = (r + g + b) / 3f
        val (sr, sg, sb) = adjustSat(r, g, b, 0.85f, gray)
        Triple(sr + 2f, sg * 1.02f + 6f, sb * 1.08f + 12f)
    }

    private fun applyPortraitWarmYellow(source: Bitmap) = applyPerPixelLum(source) { r, g, b, _ ->
        // 暖黄皮：暖黄通道提升（R*1.06 / G*1.04 / B*0.9）
        Triple(r * 1.06f + 6f, g * 1.04f + 4f, b * 0.9f - 2f)
    }

    private fun applyPortraitPink(source: Bitmap) = applyPerPixelLum(source) { r, g, b, _ ->
        // 粉嫩：粉色偏色（R↑ / B↑ / G↓）+ 提亮 + 降饱和
        val gray = (r + g + b) / 3f
        val sr = gray + (r - gray) * 0.9f
        val sg = gray + (g - gray) * 0.82f
        val sb = gray + (b - gray) * 0.95f
        Triple(sr + 12f, sg + 4f, sb + 8f)
    }

    private fun applyPortraitClear(source: Bitmap) = applyPerPixelLum(source) { r, g, b, _ ->
        // 清透：提亮 +15 + 降对比 0.9 + 降饱和 0.85 + 微冷（B+6）
        val gray = (r + g + b) / 3f
        val (sr, sg, sb) = adjustSat(r, g, b, 0.85f, gray)
        val cr = (sr - 128f) * 0.9f + 128f + 15f
        val cg = (sg - 128f) * 0.9f + 128f + 15f
        val cb = (sb - 128f) * 0.9f + 128f + 21f
        Triple(cr, cg, cb)
    }

    private fun applyPortraitRosy(source: Bitmap) = applyPerPixelLum(source) { r, g, b, _ ->
        // 红润：红润偏色（R*1.04+8）+ 暖 + 轻降蓝（B*0.96）
        val gray = (r + g + b) / 3f
        val sr = gray + (r - gray) * 1.05f
        Triple(sr * 1.04f + 8f, g + 2f, b * 0.96f - 2f)
    }

    // ═══════════════════════════════════════════════════════════════
    // 风景 LANDSCAPE：8 套，色调强化 + 对比
    // ═══════════════════════════════════════════════════════════════

    private fun applyLandGreen(source: Bitmap) = applyPerPixelLum(source) { r, g, b, _ ->
        // 青绿：绿蓝增强 + 偏冷绿（G*1.1 / B*1.05 / R*0.95）+ 饱和 1.15
        val gray = (r + g + b) / 3f
        val (sr, sg, sb) = adjustSat(r * 0.95f, g * 1.1f, b * 1.05f, 1.15f, gray)
        Triple(sr, sg, sb)
    }

    private fun applyLandAzure(source: Bitmap) = applyPerPixelLum(source) { r, g, b, _ ->
        // 蔚蓝：蓝色增强（B*1.15）+ 偏冷（R*0.92）+ 饱和 1.12
        val gray = (r + g + b) / 3f
        val (sr, sg, sb) = adjustSat(r * 0.92f, g * 0.98f, b * 1.15f, 1.12f, gray)
        Triple(sr, sg, sb)
    }

    private fun applyLandWarmSun(source: Bitmap) = applyPerPixelLum(source) { r, g, b, _ ->
        // 暖阳：暖色提亮（R*1.08 / G*1.04 / B*0.92）+ 提亮 +8
        Triple(r * 1.08f + 8f, g * 1.04f + 6f, b * 0.92f + 2f)
    }

    private fun applyLandDusk(source: Bitmap) = applyPerPixelLum(source) { r, g, b, lum ->
        // 暮色：紫红暮色 + 暗部偏紫（暗部 R+B↑ / G↓）+ 整体暗调对比 1.1
        val shadow = (1f - lum / 255f).coerceIn(0f, 1f)
        val nr = r + 12f * shadow
        val ng = g - 8f * shadow
        val nb = b + 18f * shadow
        Triple((nr - 128f) * 1.1f + 118f, (ng - 128f) * 1.1f + 118f, (nb - 128f) * 1.1f + 118f)
    }

    private fun applyLandEmerald(source: Bitmap) = applyPerPixelLum(source) { r, g, b, _ ->
        // 翠绿：深绿 + 对比 1.15（G*1.15 / R*0.9 / B*0.95）
        val nr = (r - 128f) * 1.15f + 128f
        val ng = ((g - 128f) * 1.15f + 128f) * 1.15f
        val nb = (b - 128f) * 1.15f + 128f
        Triple(nr * 0.9f, ng, nb * 0.95f)
    }

    private fun applyLandDeepSea(source: Bitmap) = applyPerPixelLum(source) { r, g, b, lum ->
        // 深海：深蓝暗调（B↑ / R↓）+ 暗部偏蓝 + 降饱和 0.9 + 暗调 -10
        val shadow = (1f - lum / 255f).coerceIn(0f, 1f)
        val gray = (r + g + b) / 3f
        val (sr, sg, sb) = adjustSat(r * 0.88f - 10f, g * 0.95f - 8f, b * 1.12f + 6f * shadow, 0.9f, gray)
        Triple(sr, sg, sb)
    }

    private fun applyLandMist(source: Bitmap) = applyPerPixelLum(source) { r, g, b, _ ->
        // 晨雾：雾感提亮 +20 + 降饱和 0.75 + 降对比 0.88 + 冷白（B+8）
        val gray = (r + g + b) / 3f
        val (sr, sg, sb) = adjustSat(r, g, b, 0.75f, gray)
        val cr = (sr - 128f) * 0.88f + 128f + 20f
        val cg = (sg - 128f) * 0.88f + 128f + 22f
        val cb = (sb - 128f) * 0.88f + 128f + 28f
        Triple(cr, cg, cb)
    }

    private fun applyLandAutumn(source: Bitmap) = applyPerPixelLum(source) { r, g, b, _ ->
        // 秋意：暖橙黄落叶调（R*1.1 / G*0.98 / B*0.82）+ 饱和 1.1
        val gray = (r + g + b) / 3f
        val (sr, sg, sb) = adjustSat(r * 1.1f, g * 0.98f, b * 0.82f, 1.1f, gray)
        Triple(sr, sg, sb)
    }

    // ═══════════════════════════════════════════════════════════════
    // 美食 FOOD：6 套，饱和 + 食欲色
    // ═══════════════════════════════════════════════════════════════

    private fun applyFoodFresh(source: Bitmap) = applyPerPixelLum(source) { r, g, b, _ ->
        // 鲜美：饱和 1.3 + 提亮 +8 + 偏暖微（R+4）
        val gray = (r + g + b) / 3f
        val (sr, sg, sb) = adjustSat(r, g, b, 1.3f, gray)
        Triple(sr + 12f, sg + 8f, sb + 4f)
    }

    private fun applyFoodRich(source: Bitmap) = applyPerPixelLum(source) { r, g, b, _ ->
        // 饱满：高饱和 1.4 + 对比 1.2 + 暖（R*1.05 / B*0.98）
        val gray = (r + g + b) / 3f
        val (sr, sg, sb) = adjustSat(r, g, b, 1.4f, gray)
        val cr = (sr - 128f) * 1.2f + 128f
        val cg = (sg - 128f) * 1.2f + 128f
        val cb = (sb - 128f) * 1.2f + 128f
        Triple(cr * 1.05f, cg, cb * 0.98f)
    }

    private fun applyFoodWarm(source: Bitmap) = applyPerPixelLum(source) { r, g, b, _ ->
        // 暖食：暖通道提升（R*1.08 / G*1.04 / B*0.9）+ 提亮 +6
        Triple(r * 1.08f + 6f, g * 1.04f + 4f, b * 0.9f + 2f)
    }

    private fun applyFoodLight(source: Bitmap) = applyPerPixelLum(source) { r, g, b, _ ->
        // 清新：提亮 +12 + 降对比 0.9 + 微冷（B+6）+ 降饱和 0.9
        val gray = (r + g + b) / 3f
        val (sr, sg, sb) = adjustSat(r, g, b, 0.9f, gray)
        val cr = (sr - 128f) * 0.9f + 128f + 12f
        val cg = (sg - 128f) * 0.9f + 128f + 12f
        val cb = (sb - 128f) * 0.9f + 128f + 18f
        Triple(cr, cg, cb)
    }

    private fun applyFoodIntense(source: Bitmap) = applyPerPixelLum(source) { r, g, b, _ ->
        // 浓郁：高对比 1.3 + 高饱和 1.3 + 暗调 -10
        val gray = (r + g + b) / 3f
        val (sr, sg, sb) = adjustSat(r, g, b, 1.3f, gray)
        Triple((sr - 128f) * 1.3f + 118f, (sg - 128f) * 1.3f + 118f, (sb - 128f) * 1.3f + 118f)
    }

    private fun applyFoodDessert(source: Bitmap) = applyPerPixelLum(source) { r, g, b, _ ->
        // 甜点：粉暖（R+8 / B+6）+ 提亮 +10 + 降饱和 0.85
        val gray = (r + g + b) / 3f
        val (sr, sg, sb) = adjustSat(r, g, b, 0.85f, gray)
        Triple(sr + 18f, sg + 12f, sb + 16f)
    }

    // ═══════════════════════════════════════════════════════════════
    // 复古 RETRO：5 套
    // ═══════════════════════════════════════════════════════════════

    private fun applyRetroOldPhoto(source: Bitmap) = applyPerPixelLum(source) { r, g, b, lum ->
        // 老照片：黄褐 sepia（R*1.1 / G*0.92 / B*0.7）+ 降饱和 0.6 + 对比 1.1 + 暗角（暗部再压）
        val vignette = (lum / 255f).coerceIn(0f, 1f)
        val factor = 0.7f + 0.3f * vignette
        val nr = r * 1.1f * factor
        val ng = g * 0.92f * factor
        val nb = b * 0.7f * factor
        val gray = (nr + ng + nb) / 3f
        val (sr, sg, sb) = adjustSat(nr, ng, nb, 0.6f, gray)
        Triple(sr, sg, sb)
    }

    private fun applyRetroKodak(source: Bitmap) = applyPerPixelLum(source) { r, g, b, _ ->
        // 柯达：黄红暖（R*1.08 / G*1.03 / B*0.88）+ 对比 1.1 + 饱和 1.1
        val gray = (r + g + b) / 3f
        val (sr, sg, sb) = adjustSat(r * 1.08f, g * 1.03f, b * 0.88f, 1.1f, gray)
        Triple((sr - 128f) * 1.1f + 128f, (sg - 128f) * 1.1f + 128f, (sb - 128f) * 1.1f + 128f)
    }

    private fun applyRetroFuji(source: Bitmap) = applyPerPixelLum(source) { r, g, b, _ ->
        // 富士：绿青（G*1.06 / B*1.05 / R*0.95）+ 饱和 1.15 + 对比 1.05
        val gray = (r + g + b) / 3f
        val (sr, sg, sb) = adjustSat(r * 0.95f, g * 1.06f, b * 1.05f, 1.15f, gray)
        Triple((sr - 128f) * 1.05f + 128f, (sg - 128f) * 1.05f + 128f, (sb - 128f) * 1.05f + 128f)
    }

    private fun applyRetroPolaroid(source: Bitmap) = applyPerPixelLum(source) { r, g, b, _ ->
        // 宝丽来：暖黄（R*1.06 / B*0.9）+ 降对比 0.9 + 提亮 +8 + 降饱和 0.9
        val gray = (r + g + b) / 3f
        val (sr, sg, sb) = adjustSat(r, g, b, 0.9f, gray)
        val cr = (sr - 128f) * 0.9f + 128f + 8f
        val cg = (sg - 128f) * 0.9f + 128f + 8f
        val cb = ((sb - 128f) * 0.9f + 128f + 8f) * 0.9f
        Triple(cr * 1.06f, cg, cb)
    }

    private fun applyRetroDarkroom(source: Bitmap) = applyPerPixelLum(source) { r, g, b, _ ->
        // 暗房：暗房红光（R*1.12）+ 暗调 -20 + 降饱和 0.7 + 偏红
        val gray = (r + g + b) / 3f
        val (sr, sg, sb) = adjustSat(r, g, b, 0.7f, gray)
        Triple(sr * 1.12f - 20f, sg - 22f, sb * 0.85f - 24f)
    }

    // ═══════════════════════════════════════════════════════════════
    // 电影 CINEMA：5 套
    // ═══════════════════════════════════════════════════════════════

    private fun applyCinematic(source: Bitmap) = applyPerPixelLum(source) { r, g, b, lum ->
        // 电影感：对比 1.15 + 青暗部（暗部 R↓B↑）+ 暖高光（高光 R↑G↑）+ 饱和 1.1
        val nL = lum / 255f
        val shadow = 1f - nL
        val high = nL
        val nr = r - 10f * shadow + 10f * high
        val ng = g + 4f * high
        val nb = b + 14f * shadow - 8f * high
        val gray = (nr + ng + nb) / 3f
        val (sr, sg, sb) = adjustSat(nr, ng, nb, 1.1f, gray)
        Triple((sr - 128f) * 1.15f + 128f, (sg - 128f) * 1.15f + 128f, (sb - 128f) * 1.15f + 128f)
    }

    private fun applyCinemaDark(source: Bitmap) = applyPerPixelLum(source) { r, g, b, _ ->
        // 暗调：降亮 -20 + 对比 1.2 + 偏冷（B+6）+ 降饱和 0.95
        val gray = (r + g + b) / 3f
        val (sr, sg, sb) = adjustSat(r, g, b, 0.95f, gray)
        Triple((sr - 128f) * 1.2f + 108f, (sg - 128f) * 1.2f + 108f, (sb - 128f) * 1.2f + 114f)
    }

    private fun applyCinemaWarm(source: Bitmap) = applyPerPixelLum(source) { r, g, b, _ ->
        // 暖调电影：暖橙（R*1.08 / G*1.03 / B*0.88）+ 对比 1.1 + 饱和 1.1
        val gray = (r + g + b) / 3f
        val (sr, sg, sb) = adjustSat(r * 1.08f, g * 1.03f, b * 0.88f, 1.1f, gray)
        Triple((sr - 128f) * 1.1f + 128f, (sg - 128f) * 1.1f + 128f, (sb - 128f) * 1.1f + 128f)
    }

    private fun applyCinemaCool(source: Bitmap) = applyPerPixelLum(source) { r, g, b, _ ->
        // 冷调电影：冷青（R*0.9 / G*0.98 / B*1.12）+ 对比 1.1 + 饱和 1.05
        val gray = (r + g + b) / 3f
        val (sr, sg, sb) = adjustSat(r * 0.9f, g * 0.98f, b * 1.12f, 1.05f, gray)
        Triple((sr - 128f) * 1.1f + 128f, (sg - 128f) * 1.1f + 128f, (sb - 128f) * 1.1f + 128f)
    }

    private fun applyCinemaCyberpunk(source: Bitmap) = applyPerPixelLum(source) { r, g, b, lum ->
        // 赛博朋克：霓虹紫青 + 高饱和 1.3（暗部偏紫 R+B↑，高光偏青 G+B↑）
        val nL = lum / 255f
        val shadow = 1f - nL
        val high = nL
        val nr = r + 18f * shadow
        val ng = g + 8f * high
        val nb = b + 22f * shadow + 12f * high
        val gray = (nr + ng + nb) / 3f
        val (sr, sg, sb) = adjustSat(nr, ng, nb, 1.3f, gray)
        Triple(sr, sg, sb)
    }

    // ═══════════════════════════════════════════════════════════════
    // 黑白 BW：5 套
    // ═══════════════════════════════════════════════════════════════

    private fun applyBwSoft(source: Bitmap) = applyPerPixelLum(source) { r, g, b, _ ->
        // 柔和黑白：低对比 0.85 + 暖灰（R+5 / B-4）
        val v = (grayOf(r, g, b) - 128f) * 0.85f + 128f
        Triple(v + 5f, v, v - 4f)
    }

    private fun applyBwSilver(source: Bitmap) = applyPerPixelLum(source) { r, g, b, _ ->
        // 银盐：中对比 1.2 + 冷灰（B+5 / R-4）
        val v = (grayOf(r, g, b) - 128f) * 1.2f + 128f
        Triple(v - 4f, v, v + 5f)
    }

    private fun applyBwCarbon(source: Bitmap) = applyPerPixelLum(source) { r, g, b, lum ->
        // 碳素：深黑高对比 1.5 + 暗部进一步压暗
        val shadow = (1f - lum / 255f).coerceIn(0f, 1f)
        val v = (grayOf(r, g, b) - 128f) * 1.5f + 128f - 10f * shadow
        Triple(v, v, v)
    }

    private fun applyBwInk(source: Bitmap) = applyPerPixelLum(source) { r, g, b, _ ->
        // 水墨：灰阶偏冷 + 中间调 S 曲线轻微提亮 + 低饱和
        val n = grayOf(r, g, b) / 255f
        val s = n + 0.05f * Math.sin(2.0 * Math.PI * (n - 0.5)).toFloat()
        val v = s * 255f
        Triple(v - 3f, v, v + 4f)
    }

    private fun applyBwMinimal(source: Bitmap) = applyPerPixelLum(source) { r, g, b, _ ->
        // 极简：高调提亮 +20 + 降对比 0.95
        val v = (grayOf(r, g, b) - 128f) * 0.95f + 148f
        Triple(v, v, v)
    }

    // ═══════════════════════════════════════════════════════════════
    // 艺术 ART：7 套，风格化
    // ═══════════════════════════════════════════════════════════════

    private fun applyArtOil(source: Bitmap) = applyPerPixelLum(source) { r, g, b, lum ->
        // 油画：高饱和 1.4 + 对比 1.2 + 暖（R*1.05）+ 暗部压厚（暗部 -10）
        val shadow = (1f - lum / 255f).coerceIn(0f, 1f)
        val gray = (r + g + b) / 3f
        val (sr, sg, sb) = adjustSat(r, g, b, 1.4f, gray)
        val cr = (sr - 128f) * 1.2f + 128f - 10f * shadow
        val cg = (sg - 128f) * 1.2f + 128f - 10f * shadow
        val cb = (sb - 128f) * 1.2f + 128f - 10f * shadow
        Triple(cr * 1.05f, cg, cb * 0.96f)
    }

    private fun applyArtWatercolor(source: Bitmap) = applyPerPixelLum(source) { r, g, b, _ ->
        // 水彩：提亮 +12 + 降饱和 0.8 + 柔和降对比 0.9 + 偏冷（B+8）
        val gray = (r + g + b) / 3f
        val (sr, sg, sb) = adjustSat(r, g, b, 0.8f, gray)
        Triple((sr - 128f) * 0.9f + 128f + 12f, (sg - 128f) * 0.9f + 128f + 14f, (sb - 128f) * 0.9f + 128f + 20f)
    }

    private fun applyArtSketch(source: Bitmap) = applyPerPixelLum(source) { r, g, b, _ ->
        // 素描：高对比 1.4 灰度 + 4 级色调分离（posterize）形成笔触
        var v = (grayOf(r, g, b) - 128f) * 1.4f + 128f
        v = (v / 64f).toInt() * 64f + 32f
        Triple(v, v, v)
    }

    private fun applyArtNeon(source: Bitmap) = applyPerPixelLum(source) { r, g, b, _ ->
        // 霓虹：高饱和 1.5 + 偏紫粉（R*1.1 / B*1.1 / G*0.92）
        val gray = (r + g + b) / 3f
        val (sr, sg, sb) = adjustSat(r * 1.1f, g * 0.92f, b * 1.1f, 1.5f, gray)
        Triple(sr, sg, sb)
    }

    private fun applyArtDreamy(source: Bitmap) = applyPerPixelLum(source) { r, g, b, _ ->
        // 梦幻：柔和提亮 +10 + 粉紫偏色（R+6 / B+10）+ 降对比 0.9 + 降饱和 0.85
        val gray = (r + g + b) / 3f
        val (sr, sg, sb) = adjustSat(r, g, b, 0.85f, gray)
        Triple((sr - 128f) * 0.9f + 128f + 16f, (sg - 128f) * 0.9f + 128f + 12f, (sb - 128f) * 0.9f + 128f + 20f)
    }

    private fun applyArtImpressionist(source: Bitmap) = applyPerPixelLum(source) { r, g, b, _ ->
        // 印象派：高饱和 1.3 + 暖（R*1.05）+ 对比 1.1 + 8 级色调分离（笔触感）
        val gray = (r + g + b) / 3f
        val (sr, sg, sb) = adjustSat(r, g, b, 1.3f, gray)
        var cr = (sr - 128f) * 1.1f + 128f
        var cg = (sg - 128f) * 1.1f + 128f
        var cb = (sb - 128f) * 1.1f + 128f
        cr = (cr / 32f).toInt() * 32f + 16f
        cg = (cg / 32f).toInt() * 32f + 16f
        cb = (cb / 32f).toInt() * 32f + 16f
        Triple(cr * 1.05f, cg, cb * 0.97f)
    }

    private fun applyArtVaporwave(source: Bitmap) = applyPerPixelLum(source) { r, g, b, lum ->
        // 蒸汽波：紫粉青 + 高饱和 1.3（暗部紫 R+B↑，高光青 G+B↑）
        val nL = lum / 255f
        val shadow = 1f - nL
        val high = nL
        val nr = r + 16f * shadow
        val ng = g + 10f * high
        val nb = b + 18f * shadow + 14f * high
        val gray = (nr + ng + nb) / 3f
        val (sr, sg, sb) = adjustSat(nr, ng, nb, 1.3f, gray)
        Triple(sr, sg, sb)
    }

    fun applyLowLightDenoise(source: Bitmap): Bitmap {
        return try {
            val meanLuma = computeMeanLuminance(source)
            if (meanLuma >= 60f) {
                return source.copy(source.config ?: Bitmap.Config.ARGB_8888, true)
            }

            var downscaled: Bitmap? = null
            var blurred: Bitmap? = null
            var blended: Bitmap? = null
            try {
                downscaled = Bitmap.createScaledBitmap(
                    source,
                    (source.width / 2).coerceAtLeast(1),
                    (source.height / 2).coerceAtLeast(1),
                    true
                )
                blurred = Bitmap.createScaledBitmap(
                    downscaled,
                    source.width,
                    source.height,
                    true
                )
                blended = blendBitmaps(source, blurred, originalWeight = 0.4f)
                val lifted = applyShadowLift(blended, lift = 15f)
                if (lifted !== blended) {
                    blended.recycle()
                    blended = null
                }
                return lifted
            } finally {
                downscaled?.recycle()
                blurred?.recycle()
                blended?.recycle()
            }
        } catch (e: Exception) {
            source.copy(source.config ?: Bitmap.Config.ARGB_8888, true)
        }
    }

    fun applySmartCrop(source: Bitmap, targetRatio: Float = 4f / 5f): Bitmap {
        return try {
            val srcRatio = source.width.toFloat() / source.height.toFloat()
            val targetW: Int
            val targetH: Int
            val x: Int
            val y: Int

            if (srcRatio > targetRatio) {
                targetH = source.height
                targetW = (targetH * targetRatio).toInt()
                x = (source.width - targetW) / 2
                y = 0
            } else {
                targetW = source.width
                targetH = (targetW / targetRatio).toInt()
                x = 0
                y = (source.height - targetH) / 3
            }

            Bitmap.createBitmap(source, x, y, targetW, targetH)
        } catch (e: Exception) {
            source.copy(source.config ?: Bitmap.Config.ARGB_8888, true)
        }
    }

    fun addWatermark(source: Bitmap, text: String = "PoseAI"): Bitmap {
        return try {
            val result = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(result)
            canvas.drawBitmap(source, 0f, 0f, null)
            val paint = Paint().apply {
                color = Color.WHITE
                textSize = source.width * 0.04f
                isAntiAlias = true
                alpha = 180
            }
            val padding = source.width * 0.04f
            val x = padding
            val y = source.height - padding
            canvas.drawText(text, x, y, paint)
            result
        } catch (e: Exception) {
            source.copy(source.config ?: Bitmap.Config.ARGB_8888, true)
        }
    }

    fun computeMeanLuminance(bitmap: Bitmap): Float {
        var small: Bitmap? = null
        return try {
            small = Bitmap.createScaledBitmap(bitmap, 64, 64, true)
            val width = small.width
            val height = small.height
            val pixels = IntArray(width * height)
            small.getPixels(pixels, 0, width, 0, 0, width, height)
            var sum = 0f
            var count = 0
            for (i in pixels.indices) {
                val pixel = pixels[i]
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)
                val lum = 0.299f * r + 0.587f * g + 0.114f * b
                sum += lum
                count++
            }
            if (count > 0) sum / count else 0f
        } catch (e: Exception) {
            128f
        } finally {
            small?.recycle()
        }
    }

    fun blendBitmaps(original: Bitmap, blurred: Bitmap, originalWeight: Float): Bitmap {
        return try {
            val result = Bitmap.createBitmap(original.width, original.height, Bitmap.Config.ARGB_8888)
            val blurWeight = 1f - originalWeight
            val width = original.width
            val height = original.height
            val origPixels = IntArray(width * height)
            val blurPixels = IntArray(width * height)
            val outPixels = IntArray(width * height)
            original.getPixels(origPixels, 0, width, 0, 0, width, height)
            blurred.getPixels(blurPixels, 0, width, 0, 0, width, height)
            for (i in origPixels.indices) {
                val op = origPixels[i]
                val bp = blurPixels[i]
                val r = (Color.red(op) * originalWeight + Color.red(bp) * blurWeight).toInt()
                val g = (Color.green(op) * originalWeight + Color.green(bp) * blurWeight).toInt()
                val b = (Color.blue(op) * originalWeight + Color.blue(bp) * blurWeight).toInt()
                outPixels[i] = Color.rgb(
                    r.coerceIn(0, 255),
                    g.coerceIn(0, 255),
                    b.coerceIn(0, 255)
                )
            }
            result.setPixels(outPixels, 0, width, 0, 0, width, height)
            result
        } catch (e: Exception) {
            original.copy(original.config ?: Bitmap.Config.ARGB_8888, true)
        }
    }

    fun applyShadowLift(source: Bitmap, lift: Float): Bitmap {
        return try {
            val width = source.width
            val height = source.height
            val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val pixels = IntArray(width * height)
            val outPixels = IntArray(width * height)
            source.getPixels(pixels, 0, width, 0, 0, width, height)
            for (i in pixels.indices) {
                val pixel = pixels[i]
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)
                val lum = 0.299f * r + 0.587f * g + 0.114f * b
                val shadowFactor = (1f - lum / 255f).coerceIn(0f, 1f)
                val liftAmount = lift * shadowFactor
                outPixels[i] = Color.rgb(
                    (r + liftAmount).toInt().coerceIn(0, 255),
                    (g + liftAmount).toInt().coerceIn(0, 255),
                    (b + liftAmount).toInt().coerceIn(0, 255)
                )
            }
            result.setPixels(outPixels, 0, width, 0, 0, width, height)
            result
        } catch (e: Exception) {
            source.copy(source.config ?: Bitmap.Config.ARGB_8888, true)
        }
    }

    private fun applySaturation(source: Bitmap, saturation: Float): Bitmap {
        return try {
            val result = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(result)
            val paint = Paint()
            val cm = ColorMatrix()
            cm.setSaturation(saturation)
            paint.colorFilter = ColorMatrixColorFilter(cm)
            canvas.drawBitmap(source, 0f, 0f, paint)
            result
        } catch (e: Exception) {
            source.copy(source.config ?: Bitmap.Config.ARGB_8888, true)
        }
    }

    private fun applyColorTint(source: Bitmap, rMul: Float, gMul: Float, bMul: Float): Bitmap {
        return try {
            val width = source.width
            val height = source.height
            val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val pixels = IntArray(width * height)
            val outPixels = IntArray(width * height)
            source.getPixels(pixels, 0, width, 0, 0, width, height)
            for (i in pixels.indices) {
                val p = pixels[i]
                outPixels[i] = Color.rgb(
                    (Color.red(p) * rMul).toInt().coerceIn(0, 255),
                    (Color.green(p) * gMul).toInt().coerceIn(0, 255),
                    (Color.blue(p) * bMul).toInt().coerceIn(0, 255)
                )
            }
            result.setPixels(outPixels, 0, width, 0, 0, width, height)
            result
        } catch (e: Exception) {
            source.copy(source.config ?: Bitmap.Config.ARGB_8888, true)
        }
    }

    private fun applyGrayscale(source: Bitmap): Bitmap {
        return try {
            val result = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(result)
            val paint = Paint()
            val cm = ColorMatrix()
            cm.setSaturation(0f)
            paint.colorFilter = ColorMatrixColorFilter(cm)
            canvas.drawBitmap(source, 0f, 0f, paint)
            result
        } catch (e: Exception) {
            source.copy(source.config ?: Bitmap.Config.ARGB_8888, true)
        }
    }

    private fun applyFade(source: Bitmap): Bitmap {
        return try {
            val width = source.width
            val height = source.height
            val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val pixels = IntArray(width * height)
            val outPixels = IntArray(width * height)
            source.getPixels(pixels, 0, width, 0, 0, width, height)
            for (i in pixels.indices) {
                val p = pixels[i]
                val r = Color.red(p) * 0.9f + 20
                val g = Color.green(p) * 0.9f + 20
                val b = Color.blue(p) * 0.95f + 20
                outPixels[i] = Color.rgb(
                    r.toInt().coerceIn(0, 255),
                    g.toInt().coerceIn(0, 255),
                    b.toInt().coerceIn(0, 255)
                )
            }
            result.setPixels(outPixels, 0, width, 0, 0, width, height)
            result
        } catch (e: Exception) {
            source.copy(source.config ?: Bitmap.Config.ARGB_8888, true)
        }
    }

    private fun applyVintage(source: Bitmap): Bitmap {
        return try {
            val width = source.width
            val height = source.height
            val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val pixels = IntArray(width * height)
            val outPixels = IntArray(width * height)
            source.getPixels(pixels, 0, width, 0, 0, width, height)
            for (i in pixels.indices) {
                val p = pixels[i]
                val r = Color.red(p) * 1.1f
                val g = Color.green(p) * 0.95f
                val b = Color.blue(p) * 0.8f
                outPixels[i] = Color.rgb(
                    r.toInt().coerceIn(0, 255),
                    g.toInt().coerceIn(0, 255),
                    b.toInt().coerceIn(0, 255)
                )
            }
            result.setPixels(outPixels, 0, width, 0, 0, width, height)
            result
        } catch (e: Exception) {
            source.copy(source.config ?: Bitmap.Config.ARGB_8888, true)
        }
    }

    private fun applyDramatic(source: Bitmap): Bitmap {
        return try {
            val width = source.width
            val height = source.height
            val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val pixels = IntArray(width * height)
            val outPixels = IntArray(width * height)
            source.getPixels(pixels, 0, width, 0, 0, width, height)
            val contrast = 1.3f
            val offset = -30f
            for (i in pixels.indices) {
                val p = pixels[i]
                val r = Color.red(p)
                val g = Color.green(p)
                val b = Color.blue(p)
                outPixels[i] = Color.rgb(
                    ((r - 128) * contrast + 128 + offset).toInt().coerceIn(0, 255),
                    ((g - 128) * contrast + 128 + offset).toInt().coerceIn(0, 255),
                    ((b - 128) * contrast + 128 + offset).toInt().coerceIn(0, 255)
                )
            }
            result.setPixels(outPixels, 0, width, 0, 0, width, height)
            result
        } catch (e: Exception) {
            source.copy(source.config ?: Bitmap.Config.ARGB_8888, true)
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // P5-4 拍后调色预设：4 套电影级调色（端侧实现，对应 iOS CIFilter 方案）
    // 使用 Android ColorMatrix + 像素级处理达到等效效果
    // ═══════════════════════════════════════════════════════════════

    /**
     * 胶片感 Film：青暗部 + 暖高光（复刻柯达 Portra 调性）
     * 实现原理：
     * - 暗部偏青：低亮度区域 R 通道降低、B 通道提升
     * - 高光偏暖：高亮度区域 R/G 通道提升
     * - 整体对比微降 + 颗粒感
     */
    private fun applyFilmLook(source: Bitmap): Bitmap {
        return try {
            val width = source.width
            val height = source.height
            val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val pixels = IntArray(width * height)
            val outPixels = IntArray(width * height)
            source.getPixels(pixels, 0, width, 0, 0, width, height)

            for (i in pixels.indices) {
                val p = pixels[i]
                val r = Color.red(p).toFloat()
                val g = Color.green(p).toFloat()
                val b = Color.blue(p).toFloat()
                val lum = 0.299f * r + 0.587f * g + 0.114f * b

                // 暗部青调：暗部区域 R 略降、B 略升
                val shadowFactor = (1f - lum / 255f).coerceIn(0f, 1f)
                val shadowTealR = r * (1f - 0.08f * shadowFactor)
                val shadowTealB = b + 12f * shadowFactor

                // 高光暖调：高光区域 R/G 略升
                val highlightFactor = (lum / 255f).coerceIn(0f, 1f)
                val warmR = shadowTealR + 8f * highlightFactor
                val warmG = g + 4f * highlightFactor

                // 整体对比微降（胶片柔和）+ 轻微颗粒
                val grain = (Math.random() - 0.5).toFloat() * 6f

                outPixels[i] = Color.rgb(
                    (warmR + grain).toInt().coerceIn(0, 255),
                    (warmG + grain).toInt().coerceIn(0, 255),
                    (shadowTealB + grain).toInt().coerceIn(0, 255)
                )
            }
            result.setPixels(outPixels, 0, width, 0, 0, width, height)
            result
        } catch (e: Exception) {
            source.copy(source.config ?: Bitmap.Config.ARGB_8888, true)
        }
    }

    /**
     * 高级黑白 B&W：大反差强锐度黑白
     * 对应 iOS CIPhotoEffectNoir + CISharpenLuminance
     * 实现：
     * - 灰度转换 + 高对比度
     * - 暗部进一步压暗，高光提亮
     * - 锐化（Sobel 卷积增强边缘）
     */
    private fun applyNoir(source: Bitmap): Bitmap {
        return try {
            val width = source.width
            val height = source.height
            // 先做高对比黑白
            val grayBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(grayBitmap)
            val paint = Paint()
            // 高对比黑白 ColorMatrix
            val cm = ColorMatrix().apply {
                setSaturation(0f)
            }
            val contrastMatrix = ColorMatrix(
                floatArrayOf(
                    1.6f, 0f, 0f, 0f, -76.8f,  // R: contrast 1.6
                    1.6f, 0f, 0f, 0f, -76.8f,  // G
                    1.6f, 0f, 0f, 0f, -76.8f,  // B
                    0f, 0f, 0f, 1f, 0f          // A
                )
            )
            cm.postConcat(contrastMatrix)
            paint.colorFilter = ColorMatrixColorFilter(cm)
            canvas.drawBitmap(source, 0f, 0f, paint)

            // 锐化：3x3 卷积核
            val sharpenKernel = floatArrayOf(
                0f, -1f, 0f,
                -1f, 5.4f, -1f,
                0f, -1f, 0f
            )
            applyConvolution(grayBitmap, sharpenKernel, 1f)
        } catch (e: Exception) {
            source.copy(source.config ?: Bitmap.Config.ARGB_8888, true)
        }
    }

    /**
     * 日系清透 Light：低对比过曝 + 降饱和
     * 对应 iOS CIExposureAdjust(+0.3) + CIVibrance(-0.2)
     * 实现：
     * - 整体提亮 +30（过曝感）
     * - 降低对比度
     * - 降低饱和度 0.8
     * - 偏冷色（B 通道 +5）
     */
    private fun applyLight(source: Bitmap): Bitmap {
        return try {
            val width = source.width
            val height = source.height
            val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(result)
            val paint = Paint()

            // ColorMatrix: 提亮 + 降对比 + 降饱和 + 偏冷
            val exposure = 30f  // 整体提亮
            val saturation = 0.8f  // 降饱和
            val contrast = 0.85f   // 降对比
            val cm = ColorMatrix().apply {
                setSaturation(saturation)
            }
            val contrastMatrix = ColorMatrix(
                floatArrayOf(
                    contrast, 0f, 0f, 0f, exposure + (1 - contrast) * 128,
                    0f, contrast, 0f, 0f, exposure + (1 - contrast) * 128,
                    0f, 0f, contrast, 0f, exposure + (1 - contrast) * 128 + 5f,  // B +5 偏冷
                    0f, 0f, 0f, 1f, 0f
                )
            )
            cm.postConcat(contrastMatrix)
            paint.colorFilter = ColorMatrixColorFilter(cm)
            canvas.drawBitmap(source, 0f, 0f, paint)
            result
        } catch (e: Exception) {
            source.copy(source.config ?: Bitmap.Config.ARGB_8888, true)
        }
    }

    /**
     * 城市霓虹 Neon：Teal & Orange 青橙赛博朋克
     * 对应 iOS CIColorMatrix Teal & Orange
     * 实现：
     * - 暗部偏青 Teal（蓝绿）
     * - 高光偏橙 Orange（红黄）
     * - 中间调保持
     * - 整体饱和度提升
     */
    private fun applyNeon(source: Bitmap): Bitmap {
        return try {
            val width = source.width
            val height = source.height
            val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val pixels = IntArray(width * height)
            val outPixels = IntArray(width * height)
            source.getPixels(pixels, 0, width, 0, 0, width, height)

            for (i in pixels.indices) {
                val p = pixels[i]
                val r = Color.red(p).toFloat()
                val g = Color.green(p).toFloat()
                val b = Color.blue(p).toFloat()
                val lum = 0.299f * r + 0.587f * g + 0.114f * b
                val normLum = lum / 255f

                // Teal & Orange 经典映射：根据亮度决定色调
                // 暗部 → Teal (R 低, G 中, B 高)
                // 高光 → Orange (R 高, G 中, B 低)
                val tealWeight = 1f - normLum  // 暗部权重
                val orangeWeight = normLum     // 高光权重

                // 增强 Teal & Orange 效果
                val newR = r + (orangeWeight - 0.5f) * 60f - tealWeight * 25f
                val newG = g + (normLum - 0.5f) * 20f
                val newB = b + tealWeight * 50f - orangeWeight * 35f

                // 整体饱和度提升 1.2
                val centerR = 128f
                val centerG = 128f
                val centerB = 128f
                val satR = centerR + (newR - centerR) * 1.2f
                val satG = centerG + (newG - centerG) * 1.2f
                val satB = centerB + (newB - centerB) * 1.2f

                outPixels[i] = Color.rgb(
                    satR.toInt().coerceIn(0, 255),
                    satG.toInt().coerceIn(0, 255),
                    satB.toInt().coerceIn(0, 255)
                )
            }
            result.setPixels(outPixels, 0, width, 0, 0, width, height)
            result
        } catch (e: Exception) {
            source.copy(source.config ?: Bitmap.Config.ARGB_8888, true)
        }
    }

    /**
     * 3x3 卷积应用：用于 NOIR 锐化处理
     * @param bitmap 输入 Bitmap（会被原地修改）
     * @param kernel 3x3 卷积核（长度 9）
     * @param factor 增强因子
     */
    private fun applyConvolution(bitmap: Bitmap, kernel: FloatArray, factor: Float): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        val outPixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                var sumR = 0f
                var sumG = 0f
                var sumB = 0f
                var ki = 0
                for (ky in -1..1) {
                    for (kx in -1..1) {
                        val px = pixels[(y + ky) * width + (x + kx)]
                        val w = kernel[ki++] * factor
                        sumR += Color.red(px) * w
                        sumG += Color.green(px) * w
                        sumB += Color.blue(px) * w
                    }
                }
                outPixels[y * width + x] = Color.rgb(
                    sumR.toInt().coerceIn(0, 255),
                    sumG.toInt().coerceIn(0, 255),
                    sumB.toInt().coerceIn(0, 255)
                )
            }
        }
        // 复制边缘像素
        for (x in 0 until width) {
            outPixels[x] = pixels[x]
            outPixels[(height - 1) * width + x] = pixels[(height - 1) * width + x]
        }
        for (y in 0 until height) {
            outPixels[y * width] = pixels[y * width]
            outPixels[y * width + width - 1] = pixels[y * width + width - 1]
        }
        bitmap.setPixels(outPixels, 0, width, 0, 0, width, height)
        return bitmap
    }

    // ═══════════════════════════════════════════════════════════════
    // P5-2 智能裁切 + 社交画幅预设
    // 画幅：4:5（默认） / 16:9 / 4:3 / 1:1 / 2.35:1
    // ═══════════════════════════════════════════════════════════════

    /**
     * 社交画幅预设：根据不同平台比例裁切
     */
    enum class AspectRatio(val displayName: String, val ratio: Float) {
        RATIO_4_5("4:5 竖屏", 4f / 5f),       // Instagram 推荐
        RATIO_16_9("16:9 横屏", 16f / 9f),    // YouTube / 视频封面
        RATIO_4_3("4:3 经典", 4f / 3f),       // 相机原始比例
        RATIO_1_1("1:1 方形", 1f),            // Instagram 头像 / 方形
        RATIO_2_35("2.35:1 电影", 2.35f)      // 电影画幅
    }

    /**
     * 按指定画幅裁切：保持人物居中
     */
    fun applySmartCropByRatio(source: Bitmap, ratio: AspectRatio): Bitmap {
        return try {
            applySmartCrop(source, ratio.ratio)
        } catch (e: Exception) {
            source.copy(source.config ?: Bitmap.Config.ARGB_8888, true)
        }
    }

    /**
     * 智能裁切双底片：根据人像 bbox 生成全身原图 + 胸腰特写
     * 对应 iOS P5-2 Auto-Crop
     * @param source 原始图片
     * @param bodyBbox 人像 bbox（归一化坐标 [0,1]），可为 null 时使用画面中心
     * @return Pair<全身原图, 胸腰特写>
     */
    fun applyDualCrop(
        source: Bitmap,
        bodyBbox: RectF? = null
    ): Pair<Bitmap, Bitmap> {
        val full = source.copy(source.config ?: Bitmap.Config.ARGB_8888, true)
        val closeUp = try {
            val width = source.width
            val height = source.height
            // bbox 归一化转像素
            val bbox = bodyBbox ?: RectF(
                width * 0.2f,
                height * 0.1f,
                width * 0.8f,
                height * 0.7f
            )
            // 胸腰特写：bbox 上半部分 + 适当扩展
            val cropTop = (bbox.top + (bbox.bottom - bbox.top) * 0.05f).toInt()
            val cropBottom = (bbox.top + (bbox.bottom - bbox.top) * 0.6f).toInt()
            val cropLeft = (bbox.left - (bbox.right - bbox.left) * 0.1f).toInt().coerceAtLeast(0)
            val cropRight = (bbox.right + (bbox.right - bbox.left) * 0.1f).toInt().coerceAtMost(width)
            val cropW = (cropRight - cropLeft).coerceAtLeast(width / 4)
            val cropH = (cropBottom - cropTop).coerceAtLeast(height / 4)
            Bitmap.createBitmap(source, cropLeft, cropTop, cropW, cropH)
        } catch (e: Exception) {
            source.copy(source.config ?: Bitmap.Config.ARGB_8888, true)
        }
        return Pair(full, closeUp)
    }

    /**
     * 归一化矩形数据类
     */
    data class RectF(val left: Float, val top: Float, val right: Float, val bottom: Float)

    // ═══════════════════════════════════════════════════════════════
    // P1-3 画质设置：软件 HDR 色调映射
    // 实现：暗部提亮 + 高光压缩 + 局部对比增强，模拟 HDR 效果
    // 所有设备可用（不依赖 CameraX Extensions）
    // ═══════════════════════════════════════════════════════════════

    /**
     * 软件 HDR 色调映射
     * - 暗部（lum < 80）：提亮 1.4x，恢复阴影细节
     * - 中间调：S 曲线增强对比
     * - 高光（lum > 180）：压缩 0.85x，防止过曝
     * - 整体饱和度微提 1.1x
     */
    fun applyHdrToneMapping(source: Bitmap): Bitmap {
        return try {
            val width = source.width
            val height = source.height
            val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val pixels = IntArray(width * height)
            val outPixels = IntArray(width * height)
            source.getPixels(pixels, 0, width, 0, 0, width, height)

            for (i in pixels.indices) {
                val p = pixels[i]
                val r = Color.red(p).toFloat()
                val g = Color.green(p).toFloat()
                val b = Color.blue(p).toFloat()
                val lum = 0.299f * r + 0.587f * g + 0.114f * b
                val normLum = lum / 255f

                // 分段色调映射
                val mappedLum = when {
                    lum < 80f -> {
                        // 暗部提亮：1.4x，但不超过 80
                        (lum * 1.4f).coerceAtMost(80f)
                    }
                    lum > 180f -> {
                        // 高光压缩：0.85x + 38（保持 > 180）
                        (lum * 0.85f + 38f).coerceIn(180f, 255f)
                    }
                    else -> {
                        // 中间调：S 曲线增强对比
                        // S(x) = x + 0.15 * sin(2π * (x-0.5))，在 0.5 处斜率最大
                        val s = normLum + 0.06f * Math.sin(2.0 * Math.PI * (normLum - 0.5)).toFloat()
                        (s * 255f).coerceIn(80f, 180f)
                    }
                }

                // 按亮度比例调整 RGB
                val ratio = if (lum > 0.1f) mappedLum / lum else 1f
                var newR = r * ratio
                var newG = g * ratio
                var newB = b * ratio

                // 整体饱和度微提 1.1x（围绕灰点）
                val gray = (newR + newG + newB) / 3f
                newR = gray + (newR - gray) * 1.1f
                newG = gray + (newG - gray) * 1.1f
                newB = gray + (newB - gray) * 1.1f

                outPixels[i] = Color.rgb(
                    newR.toInt().coerceIn(0, 255),
                    newG.toInt().coerceIn(0, 255),
                    newB.toInt().coerceIn(0, 255)
                )
            }
            result.setPixels(outPixels, 0, width, 0, 0, width, height)
            result
        } catch (e: Exception) {
            source.copy(source.config ?: Bitmap.Config.ARGB_8888, true)
        }
    }
}
