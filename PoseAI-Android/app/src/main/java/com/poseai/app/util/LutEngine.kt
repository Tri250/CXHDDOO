package com.poseai.app.util

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log

private const val TAG = "LutEngine"

// ═══════════════════════════════════════════════════════════════
// LUT 滤镜引擎
// 提供 Adobe .cube 3D LUT 解析、3D LUT 容器、LUT 应用（含强度混合）、
// 由滤镜函数生成 LUT，以及 10 套内置预设 LUT。
// ═══════════════════════════════════════════════════════════════

/**
 * Adobe .cube LUT 文件解析器。
 *
 * .cube 3D LUT 格式：
 * ```
 * # 注释
 * TITLE "标题"
 * LUT_3D_SIZE N          # 每维采样点数，共 N^3 个数据点
 * DOMAIN_MIN 0 0 0       # 可选，默认 0
 * DOMAIN_MAX 1 1 1       # 可选，默认 1
 * R G B                  # 数据行，浮点 0..1，R 最快变化
 * ...
 * ```
 * 数据行顺序：外层 B，中层 G，内层 R（R 最快）。
 */
class CubeLutParser(private val text: String) {

    /** 解析文本并返回 3D LUT。失败抛 [IllegalArgumentException]。 */
    fun parse(): Lut3D {
        var size = 0
        val dataPoints = ArrayList<FloatArray>(4096)

        text.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#")) return@forEach

            val upper = line.uppercase()
            when {
                upper.startsWith("LUT_3D_SIZE") -> {
                    // LUT_3D_SIZE N —— 大小写不敏感，取关键字后的第一个整数
                    val after = line.substring(upper.indexOf("LUT_3D_SIZE") + "LUT_3D_SIZE".length)
                    size = after.trim().split(Regex("\\s+")).firstOrNull()?.toIntOrNull() ?: 0
                }
                upper.startsWith("LUT_1D_SIZE") -> {
                    throw IllegalArgumentException("仅支持 3D LUT，检测到 LUT_1D_SIZE")
                }
                upper.startsWith("TITLE") -> Unit        // 标题忽略
                upper.startsWith("DOMAIN_MIN") -> Unit   // 本实现固定 0..1 域
                upper.startsWith("DOMAIN_MAX") -> Unit
                else -> {
                    // 数据行：r g b（可能带多余列，只取前 3 个）
                    val parts = line.split(Regex("\\s+")).filter { it.isNotBlank() }
                    if (parts.size >= 3) {
                        val r = parts[0].toFloatOrNull()
                        val g = parts[1].toFloatOrNull()
                        val b = parts[2].toFloatOrNull()
                        if (r != null && g != null && b != null) {
                            dataPoints.add(floatArrayOf(r, g, b))
                        }
                    }
                }
            }
        }

        // 若未显式声明 LUT_3D_SIZE，根据数据点数推断立方根
        if (size <= 0 && dataPoints.isNotEmpty()) {
            val est = Math.cbrt(dataPoints.size.toDouble()).toInt()
            if (est * est * est == dataPoints.size) size = est
        }

        val expected = size * size * size
        require(size > 0 && dataPoints.size >= expected) {
            "LUT 解析失败：LUT_3D_SIZE=$size，数据点=${dataPoints.size}，期望=$expected"
        }

        val lut = Lut3D(size)
        var idx = 0
        // .cube 顺序：B 外层 → G 中层 → R 内层（R 最快）
        for (b in 0 until size) {
            for (g in 0 until size) {
                for (r in 0 until size) {
                    val pt = dataPoints[idx++]
                    lut.set(r, g, b, pt[0], pt[1], pt[2])
                }
            }
        }
        return lut
    }
}

/**
 * 3D LUT 数据容器：size × size × size × 3（RGB），值域 0..1。
 *
 * 索引约定：`data[((b * size + g) * size + r) * 3 + c]`，c=0,1,2 对应 R,G,B。
 * 该顺序与 .cube 文件“R 最快”一致，便于 [CubeLutParser] 直接写入。
 */
class Lut3D(val size: Int) {

    val data: FloatArray = FloatArray(size * size * size * 3)

    private fun index(r: Int, g: Int, b: Int): Int = ((b * size + g) * size + r) * 3

    /** 写入网格点 (r,g,b) 的输出 RGB（0..1） */
    fun set(r: Int, g: Int, b: Int, vr: Float, vg: Float, vb: Float) {
        val i = index(r, g, b)
        data[i] = vr
        data[i + 1] = vg
        data[i + 2] = vb
    }

    /** 读取网格点 (r,g,b) 的第 c 通道（c=0,1,2） */
    private fun channel(r: Int, g: Int, b: Int, c: Int): Float = data[index(r, g, b) + c]

    /**
     * 三线性插值采样。
     * @param r g b 输入通道值，范围 0..1
     * @return 插值后的 RGB（0..1）
     */
    fun sample(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        val n = size - 1
        if (n <= 0) return Triple(channel(0, 0, 0, 0), channel(0, 0, 0, 1), channel(0, 0, 0, 2))

        val rf = r.coerceIn(0f, 1f) * n
        val gf = g.coerceIn(0f, 1f) * n
        val bf = b.coerceIn(0f, 1f) * n
        val r0 = rf.toInt().coerceIn(0, n); val r1 = (r0 + 1).coerceAtMost(n)
        val g0 = gf.toInt().coerceIn(0, n); val g1 = (g0 + 1).coerceAtMost(n)
        val b0 = bf.toInt().coerceIn(0, n); val b1 = (b0 + 1).coerceAtMost(n)
        val fr = rf - r0
        val fg = gf - g0
        val fb = bf - b0

        fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

        // 对单通道做三线性插值：先 R，再 G，再 B
        fun sampleC(c: Int): Float {
            val c00 = lerp(channel(r0, g0, b0, c), channel(r1, g0, b0, c), fr)
            val c10 = lerp(channel(r0, g1, b0, c), channel(r1, g1, b0, c), fr)
            val c01 = lerp(channel(r0, g0, b1, c), channel(r1, g0, b1, c), fr)
            val c11 = lerp(channel(r0, g1, b1, c), channel(r1, g1, b1, c), fr)
            val c0 = lerp(c00, c10, fg)
            val c1 = lerp(c01, c11, fg)
            return lerp(c0, c1, fb)
        }

        return Triple(sampleC(0), sampleC(1), sampleC(2))
    }

    /**
     * 应用 LUT 到单个像素。
     * @param r g b 输入通道值，范围 0..255
     * @return 映射后的 RGB（0..255）
     */
    fun applyToPixel(r: Int, g: Int, b: Int): Triple<Int, Int, Int> {
        val (vr, vg, vb) = sample(r / 255f, g / 255f, b / 255f)
        return Triple(
            (vr * 255f + 0.5f).toInt().coerceIn(0, 255),
            (vg * 255f + 0.5f).toInt().coerceIn(0, 255),
            (vb * 255f + 0.5f).toInt().coerceIn(0, 255)
        )
    }

    /** 应用 LUT 到整张 Bitmap（全强度） */
    fun applyToBitmap(source: Bitmap): Bitmap {
        val width = source.width
        val height = source.height
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            val pixels = IntArray(width * height)
            val outPixels = IntArray(width * height)
            source.getPixels(pixels, 0, width, 0, 0, width, height)
            for (i in pixels.indices) {
                val p = pixels[i]
                val (nr, ng, nb) = applyToPixel(Color.red(p), Color.green(p), Color.blue(p))
                outPixels[i] = Color.rgb(nr, ng, nb)
            }
            result.setPixels(outPixels, 0, width, 0, 0, width, height)
            return result
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply LUT to bitmap", e)
            result.recycle()
            return source.copy(source.config ?: Bitmap.Config.ARGB_8888, true)
        }
    }
}

/**
 * 将 3D LUT 应用到 Bitmap，并按 [intensity] 在原图与 LUT 结果间线性混合。
 * @param bitmap 原始位图
 * @param lut 3D LUT
 * @param intensity 0.0 = 原图，1.0 = 完全 LUT，自动 clamp 到 [0,1]
 */
fun applyLut(bitmap: Bitmap, lut: Lut3D, intensity: Float): Bitmap {
    val w = intensity.coerceIn(0f, 1f)
    if (w <= 0f) return bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, true)
    val width = bitmap.width
    val height = bitmap.height
    val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    try {
        val pixels = IntArray(width * height)
        val outPixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val inv = 1f - w
        for (i in pixels.indices) {
            val p = pixels[i]
            val r = Color.red(p)
            val g = Color.green(p)
            val b = Color.blue(p)
            val (nr, ng, nb) = lut.applyToPixel(r, g, b)
            outPixels[i] = Color.rgb(
                (r * inv + nr * w).toInt().coerceIn(0, 255),
                (g * inv + ng * w).toInt().coerceIn(0, 255),
                (b * inv + nb * w).toInt().coerceIn(0, 255)
            )
        }
        result.setPixels(outPixels, 0, width, 0, 0, width, height)
        return result
    } catch (e: Exception) {
        Log.e(TAG, "Failed to apply LUT with intensity", e)
        result.recycle()
        return bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, true)
    }
}

/**
 * 由滤镜函数生成 3D LUT。
 * 遍历 [size]^3 个网格点，将 0..255 输入送入 [filterFn]，将其输出归一化为 0..1 存入 LUT。
 *
 * @param filterFn 输入 0..255 的 r,g,b，返回 0..255 的 Triple
 * @param size 每维采样点数（默认 32，越大越精细）
 */
fun generateLutFromFilter(
    filterFn: (r: Int, g: Int, b: Int) -> Triple<Int, Int, Int>,
    size: Int = 32
): Lut3D {
    val lut = Lut3D(size)
    val n = size - 1
    for (b in 0 until size) {
        for (g in 0 until size) {
            for (r in 0 until size) {
                val inR = if (n > 0) r * 255 / n else 0
                val inG = if (n > 0) g * 255 / n else 0
                val inB = if (n > 0) b * 255 / n else 0
                val (outR, outG, outB) = filterFn(inR, inG, inB)
                lut.set(r, g, b, outR / 255f, outG / 255f, outB / 255f)
            }
        }
    }
    return lut
}

/**
 * 内置预设 LUT 集合。全部通过 [generateLutFromFilter] 程序化生成，使用真实色彩数学。
 * 使用 `by lazy` 延迟计算，首次访问时才构建（每张 32^3 ≈ 32k 次采样）。
 */
object BuiltInLuts {

    /** 青橙：暗部 teal（R↓/B↑），高光 orange（R↑/G↑/B↓） */
    val TEAL_ORANGE: Lut3D by lazy { generateLutFromFilter(filterFn = { r, g, b ->
        val lum = (0.299f * r + 0.587f * g + 0.114f * b) / 255f
        val shadow = 1f - lum
        val high = lum
        val nr = (r - 25f * shadow + 30f * high).toInt().coerceIn(0, 255)
        val ng = (g + 8f * high).toInt().coerceIn(0, 255)
        val nb = (b + 45f * shadow - 30f * high).toInt().coerceIn(0, 255)
        Triple(nr, ng, nb)
    }) }

    /** 电影感：对比 1.18 + 暗部偏绿（G↑）+ 高光暖（R↑） */
    val CINEMATIC: Lut3D by lazy { generateLutFromFilter(filterFn = { r, g, b ->
        val lum = (0.299f * r + 0.587f * g + 0.114f * b) / 255f
        val shadow = 1f - lum
        val high = lum
        var nr = r + 12f * high
        var ng = g + 18f * shadow
        var nb = b + 6f * shadow
        // 对比 1.18
        nr = (nr - 128f) * 1.18f + 128f
        ng = (ng - 128f) * 1.18f + 128f
        nb = (nb - 128f) * 1.18f + 128f
        Triple(nr.toInt().coerceIn(0, 255), ng.toInt().coerceIn(0, 255), nb.toInt().coerceIn(0, 255))
    }) }

    /** 复古胶片：褪色（提黑 +20 / 增益 0.9）+ 暖调（R↑/B↓） */
    val VINTAGE_FILM: Lut3D by lazy { generateLutFromFilter(filterFn = { r, g, b ->
        val nr = (r * 0.9f + 22f) * 1.08f
        val ng = (g * 0.9f + 20f) * 1.0f
        val nb = (b * 0.9f + 18f) * 0.85f
        Triple(nr.toInt().coerceIn(0, 255), ng.toInt().coerceIn(0, 255), nb.toInt().coerceIn(0, 255))
    }) }

    /** 黑白 NOIR：高对比 1.6 灰度 */
    val NOIR: Lut3D by lazy { generateLutFromFilter(filterFn = { r, g, b ->
        val gy = 0.299f * r + 0.587f * g + 0.114f * b
        val v = ((gy - 128f) * 1.6f + 128f).toInt().coerceIn(0, 255)
        Triple(v, v, v)
    }) }

    /** 粉彩：柔和 + 低饱和 0.7 + 提亮 +15 */
    val PASTEL: Lut3D by lazy { generateLutFromFilter(filterFn = { r, g, b ->
        val gray = (r + g + b) / 3f
        val sr = gray + (r - gray) * 0.7f
        val sg = gray + (g - gray) * 0.7f
        val sb = gray + (b - gray) * 0.7f
        val cr = (sr - 128f) * 0.9f + 128f + 15f
        val cg = (sg - 128f) * 0.9f + 128f + 15f
        val cb = (sb - 128f) * 0.9f + 128f + 18f
        Triple(cr.toInt().coerceIn(0, 255), cg.toInt().coerceIn(0, 255), cb.toInt().coerceIn(0, 255))
    }) }

    /** 森林：绿色偏调（G↑ / B↑ / R↓）+ 饱和 1.1 */
    val FOREST: Lut3D by lazy { generateLutFromFilter(filterFn = { r, g, b ->
        val gray = (r + g + b) / 3f
        val nr = r * 0.9f
        val ng = g * 1.12f
        val nb = b * 1.03f
        val sr = gray + (nr - gray) * 1.1f
        val sg = gray + (ng - gray) * 1.1f
        val sb = gray + (nb - gray) * 1.1f
        Triple(sr.toInt().coerceIn(0, 255), sg.toInt().coerceIn(0, 255), sb.toInt().coerceIn(0, 255))
    }) }

    /** 沙漠：暖沙色（R↑/G↑/B↓）+ 降饱和 0.9 */
    val DESERT: Lut3D by lazy { generateLutFromFilter(filterFn = { r, g, b ->
        val gray = (r + g + b) / 3f
        val nr = r * 1.1f
        val ng = g * 1.05f
        val nb = b * 0.85f
        val sr = gray + (nr - gray) * 0.9f
        val sg = gray + (ng - gray) * 0.9f
        val sb = gray + (nb - gray) * 0.9f
        Triple(sr.toInt().coerceIn(0, 255), sg.toInt().coerceIn(0, 255), sb.toInt().coerceIn(0, 255))
    }) }

    /** 赛博朋克：霓虹紫青（暗部紫 R+B↑ / 高光青 G+B↑）+ 高饱和 1.3 */
    val CYBERPUNK: Lut3D by lazy { generateLutFromFilter(filterFn = { r, g, b ->
        val lum = (0.299f * r + 0.587f * g + 0.114f * b) / 255f
        val shadow = 1f - lum
        val high = lum
        val nr = r + 20f * shadow
        val ng = g + 10f * high
        val nb = b + 25f * shadow + 14f * high
        val gray = (nr + ng + nb) / 3f
        val sr = gray + (nr - gray) * 1.3f
        val sg = gray + (ng - gray) * 1.3f
        val sb = gray + (nb - gray) * 1.3f
        Triple(sr.toInt().coerceIn(0, 255), sg.toInt().coerceIn(0, 255), sb.toInt().coerceIn(0, 255))
    }) }

    /** 哑光 Matte：提黑 +25 + 降对比 0.85 + 降饱和 0.85 */
    val MATTE: Lut3D by lazy { generateLutFromFilter(filterFn = { r, g, b ->
        val gray = (r + g + b) / 3f
        val sr = gray + (r - gray) * 0.85f
        val sg = gray + (g - gray) * 0.85f
        val sb = gray + (b - gray) * 0.85f
        val cr = (sr - 128f) * 0.85f + 128f + 25f
        val cg = (sg - 128f) * 0.85f + 128f + 25f
        val cb = (sb - 128f) * 0.85f + 128f + 25f
        Triple(cr.toInt().coerceIn(0, 255), cg.toInt().coerceIn(0, 255), cb.toInt().coerceIn(0, 255))
    }) }

    /** 黄金时刻：暖金调（R↑/G↑/B↓）+ 提亮 +10 */
    val GOLDEN_HOUR: Lut3D by lazy { generateLutFromFilter(filterFn = { r, g, b ->
        val nr = r * 1.1f + 10f
        val ng = g * 1.05f + 8f
        val nb = b * 0.82f + 4f
        Triple(nr.toInt().coerceIn(0, 255), ng.toInt().coerceIn(0, 255), nb.toInt().coerceIn(0, 255))
    }) }

    /** 全部内置 LUT，便于遍历/批量生成缩略图 */
    val ALL: List<Pair<String, Lut3D>> by lazy {
        listOf(
            "TEAL_ORANGE" to TEAL_ORANGE,
            "CINEMATIC" to CINEMATIC,
            "VINTAGE_FILM" to VINTAGE_FILM,
            "NOIR" to NOIR,
            "PASTEL" to PASTEL,
            "FOREST" to FOREST,
            "DESERT" to DESERT,
            "CYBERPUNK" to CYBERPUNK,
            "MATTE" to MATTE,
            "GOLDEN_HOUR" to GOLDEN_HOUR
        )
    }
}
