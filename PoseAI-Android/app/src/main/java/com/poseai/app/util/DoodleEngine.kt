package com.poseai.app.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * 笔刷类型
 */
enum class BrushType { SOLID, SPRAY, CALLIGRAPHY, NEON, PENCIL, MARKER }

/**
 * 涂鸦轨迹点（含压感）
 *
 * @param x X 坐标（像素）
 * @param y Y 坐标（像素）
 * @param pressure 压感 [0,1]，1.0 为最大压力
 */
data class DoodlePoint(val x: Float, val y: Float, val pressure: Float = 1f)

/**
 * 涂鸦笔画
 *
 * @param points 轨迹点序列
 * @param color 颜色（ARGB）
 * @param width 笔触宽度（像素）
 * @param isEraser 是否为橡皮擦（使用 CLEAR 模式擦除像素）
 * @param brushType 笔刷类型
 */
data class DoodleStroke(
    val points: List<DoodlePoint>,
    val color: Int,
    val width: Float,
    val isEraser: Boolean = false,
    val brushType: BrushType = BrushType.SOLID
)

/**
 * 涂鸦引擎
 *
 * 提供交互式涂鸦绘制与状态管理：
 * - 6 种笔刷类型，每种渲染算法不同
 * - 撤销 / 重做 / 清空
 * - 橡皮擦（PorterDuff.Mode.CLEAR）
 *
 * 笔刷算法说明：
 * - SOLID: Path + 二次贝塞尔平滑曲线
 * - SPRAY: 沿轨迹随机散点
 * - CALLIGRAPHY: 根据运动方向变宽的填充四边形
 * - NEON: 多层叠加（外发光 + 中间层 + 高亮中心）
 * - PENCIL: 基础笔画 + 多条带噪声的偏移笔画
 * - MARKER: 半透明粗笔触
 */
class DoodleEngine {

    /** 已提交的笔画列表 */
    private val strokes = mutableListOf<DoodleStroke>()
    /** 撤销栈（用于重做） */
    private val redoStack = mutableListOf<DoodleStroke>()
    /** 当前正在绘制的笔画配置 */
    private var currentStroke: DoodleStroke? = null
    /** 当前笔画的点序列 */
    private val currentPoints = mutableListOf<DoodlePoint>()

    /**
     * 将笔画列表渲染到位图上
     *
     * @param bitmap 原始位图
     * @param strokes 要渲染的笔画列表
     * @return 绘制后的新位图（强制 ARGB_8888 以支持橡皮擦透明度）
     */
    fun applyDoodle(bitmap: Bitmap, strokes: List<DoodleStroke>): Bitmap {
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        for (stroke in strokes) {
            drawStroke(canvas, stroke)
        }
        return result
    }

    /**
     * 开始一条新笔画
     *
     * @param x 起点像素 X
     * @param y 起点像素 Y
     * @param color 笔触颜色
     * @param width 笔触宽度
     * @param brushType 笔刷类型
     */
    fun startStroke(x: Float, y: Float, color: Int, width: Float, brushType: BrushType) {
        currentPoints.clear()
        currentPoints.add(DoodlePoint(x, y))
        currentStroke = DoodleStroke(
            points = emptyList(),
            color = color,
            width = width,
            brushType = brushType
        )
        // 开始新笔画时清空重做栈
        redoStack.clear()
    }

    /**
     * 向当前笔画追加一个点
     *
     * @param x 像素 X
     * @param y 像素 Y
     * @param pressure 压感 [0,1]
     */
    fun addToStroke(x: Float, y: Float, pressure: Float = 1f) {
        currentPoints.add(DoodlePoint(x, y, pressure))
    }

    /**
     * 结束当前笔画并提交到笔画列表
     */
    fun endStroke() {
        val stroke = currentStroke ?: return
        if (currentPoints.isNotEmpty()) {
            val completed = stroke.copy(points = currentPoints.toList())
            strokes.add(completed)
        }
        currentStroke = null
        currentPoints.clear()
    }

    /**
     * 撤销上一笔
     */
    fun undo() {
        if (strokes.isNotEmpty()) {
            redoStack.add(strokes.removeAt(strokes.lastIndex))
        }
    }

    /**
     * 重做（恢复撤销的笔画）
     */
    fun redo() {
        if (redoStack.isNotEmpty()) {
            strokes.add(redoStack.removeAt(redoStack.lastIndex))
        }
    }

    /**
     * 清空所有笔画与重做栈
     */
    fun clear() {
        strokes.clear()
        redoStack.clear()
        currentStroke = null
        currentPoints.clear()
    }

    /**
     * 获取当前已提交的笔画列表（只读副本）
     */
    fun getStrokes(): List<DoodleStroke> = strokes.toList()

    // ═══════════════════════════════════════════════════════════════
    // 笔刷渲染实现
    // ═══════════════════════════════════════════════════════════════

    private fun drawStroke(canvas: Canvas, stroke: DoodleStroke) {
        if (stroke.points.isEmpty()) return

        // 橡皮擦：无论笔刷类型，统一使用 CLEAR 模式沿路径擦除
        if (stroke.isEraser) {
            drawEraser(canvas, stroke)
            return
        }

        when (stroke.brushType) {
            BrushType.SOLID -> drawSolid(canvas, stroke)
            BrushType.SPRAY -> drawSpray(canvas, stroke)
            BrushType.CALLIGRAPHY -> drawCalligraphy(canvas, stroke)
            BrushType.NEON -> drawNeon(canvas, stroke)
            BrushType.PENCIL -> drawPencil(canvas, stroke)
            BrushType.MARKER -> drawMarker(canvas, stroke)
        }
    }

    /**
     * 橡皮擦：PorterDuff.Mode.CLEAR 沿路径擦除像素
     */
    private fun drawEraser(canvas: Canvas, stroke: DoodleStroke) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
            color = Color.TRANSPARENT
            strokeWidth = stroke.width
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val path = buildSmoothPath(stroke.points)
        canvas.drawPath(path, paint)
    }

    /**
     * SOLID 笔刷：平滑实线
     * 使用 Path + 二次贝塞尔曲线在相邻中点之间插值，消除折线锯齿
     */
    private fun drawSolid(canvas: Canvas, stroke: DoodleStroke) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = stroke.color
            strokeWidth = stroke.width
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val path = buildSmoothPath(stroke.points)
        canvas.drawPath(path, paint)
    }

    /**
     * SPRAY 笔刷：喷雾效果
     * 沿轨迹在每个点周围随机散布小圆点
     */
    private fun drawSpray(canvas: Canvas, stroke: DoodleStroke) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = stroke.color
            style = Paint.Style.FILL
        }
        // 使用笔画点的哈希作为种子，保证同一笔画渲染结果一致（撤销/重做一致）
        val random = Random(stroke.points.hashCode().toLong())
        val radius = stroke.width * 1.5f
        // 每个点散布的圆点数量与笔触宽度正相关
        val density = (stroke.width * 2f).toInt().coerceIn(5, 30)

        for (point in stroke.points) {
            repeat(density) {
                // 极坐标随机分布
                val angle = random.nextFloat() * 2f * PI.toFloat()
                val r = random.nextFloat() * radius
                val dx = cos(angle) * r
                val dy = sin(angle) * r
                canvas.drawCircle(point.x + dx, point.y + dy, stroke.width * 0.12f, paint)
            }
        }
    }

    /**
     * CALLIGRAPHY 笔刷：书法笔
     * 笔触宽度随运动方向变化：垂直方向运动时较宽，水平方向运动时较窄
     * 通过相邻点构建填充四边形实现
     */
    private fun drawCalligraphy(canvas: Canvas, stroke: DoodleStroke) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = stroke.color
            style = Paint.Style.FILL
        }
        if (stroke.points.size == 1) {
            val p = stroke.points[0]
            canvas.drawCircle(p.x, p.y, stroke.width / 2f, paint)
            return
        }
        for (i in 1 until stroke.points.size) {
            val p0 = stroke.points[i - 1]
            val p1 = stroke.points[i]
            val dx = p1.x - p0.x
            val dy = p1.y - p0.y
            val len = sqrt(dx * dx + dy * dy)
            if (len < 0.001f) continue
            // 运动方向角度
            val angle = atan2(dy, dx)
            // 垂直运动（sin 大）→ 宽；水平运动（sin 小）→ 窄
            val dirFactor = abs(sin(angle))
            val w = stroke.width * (0.3f + 0.7f * dirFactor)
            // 法向量（垂直于运动方向）
            val nx = -dy / len
            val ny = dx / len
            val halfW = w / 2f
            val quad = Path().apply {
                moveTo(p0.x + nx * halfW, p0.y + ny * halfW)
                lineTo(p1.x + nx * halfW, p1.y + ny * halfW)
                lineTo(p1.x - nx * halfW, p1.y - ny * halfW)
                lineTo(p0.x - nx * halfW, p0.y - ny * halfW)
                close()
            }
            canvas.drawPath(quad, paint)
        }
    }

    /**
     * NEON 笔刷：霓虹发光
     * 三层叠加：
     * 1. 外层发光（宽、低透明度）
     * 2. 中间层（中等宽度、中等透明度）
     * 3. 高亮中心（窄、亮色，向白色混合）
     */
    private fun drawNeon(canvas: Canvas, stroke: DoodleStroke) {
        val path = buildSmoothPath(stroke.points)
        val baseColor = stroke.color

        // 外层发光
        val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = baseColor
            strokeWidth = stroke.width * 3f
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            alpha = 60
        }
        // 中间层
        val midPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = baseColor
            strokeWidth = stroke.width * 1.8f
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            alpha = 120
        }
        // 高亮中心：基色与白色混合 70%
        val centerColor = blendColor(baseColor, Color.WHITE, 0.7f)
        val centerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = centerColor
            strokeWidth = stroke.width * 0.6f
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        canvas.drawPath(path, glowPaint)
        canvas.drawPath(path, midPaint)
        canvas.drawPath(path, centerPaint)
    }

    /**
     * PENCIL 笔刷：铅笔纹理
     * 基础笔画 + 多条带随机噪声的偏移笔画，模拟铅笔颗粒感
     */
    private fun drawPencil(canvas: Canvas, stroke: DoodleStroke) {
        val random = Random(stroke.points.hashCode().toLong() xor 0x5EEDL)
        val path = buildSmoothPath(stroke.points)

        // 基础笔画（较细、半透明）
        val basePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = stroke.color
            strokeWidth = stroke.width * 0.6f
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            alpha = 180
        }
        canvas.drawPath(path, basePaint)

        // 噪声笔画：多次偏移绘制
        val noisePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = stroke.color
            strokeWidth = stroke.width * 0.3f
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            alpha = 100
        }
        val iterations = 5
        for (it in 0 until iterations) {
            val noisyPath = Path()
            val first = stroke.points.first()
            noisyPath.moveTo(
                first.x + (random.nextFloat() - 0.5f) * stroke.width * 0.6f,
                first.y + (random.nextFloat() - 0.5f) * stroke.width * 0.6f
            )
            for (i in 1 until stroke.points.size) {
                val p = stroke.points[i]
                val jx = (random.nextFloat() - 0.5f) * stroke.width * 0.6f
                val jy = (random.nextFloat() - 0.5f) * stroke.width * 0.6f
                noisyPath.lineTo(p.x + jx, p.y + jy)
            }
            canvas.drawPath(noisyPath, noisePaint)
        }
    }

    /**
     * MARKER 笔刷：马克笔
     * 半透明粗笔触，方头，模拟荧光笔效果
     */
    private fun drawMarker(canvas: Canvas, stroke: DoodleStroke) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = stroke.color
            strokeWidth = stroke.width * 1.5f
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.SQUARE
            strokeJoin = Paint.Join.BEVEL
            alpha = 140
        }
        val path = buildSmoothPath(stroke.points)
        canvas.drawPath(path, paint)
    }

    // ═══════════════════════════════════════════════════════════════
    // 工具函数
    // ═══════════════════════════════════════════════════════════════

    /**
     * 构建平滑 Path
     * 使用二次贝塞尔：在相邻点的中点处作为曲线终点，原点作为控制点
     * 这样消除折线锯齿，同时保持曲线经过每两个点的中点
     */
    private fun buildSmoothPath(points: List<DoodlePoint>): Path {
        val path = Path()
        if (points.isEmpty()) return path
        val first = points[0]
        path.moveTo(first.x, first.y)
        if (points.size == 1) {
            // 单点：画一个小圆点
            path.lineTo(first.x + 0.01f, first.y)
            return path
        }
        if (points.size == 2) {
            path.lineTo(points[1].x, points[1].y)
            return path
        }
        // 从第二个点开始，在 p[i] 与 p[i+1] 的中点处用 quadTo
        for (i in 1 until points.size - 1) {
            val p1 = points[i]
            val p2 = points[i + 1]
            val midX = (p1.x + p2.x) / 2f
            val midY = (p1.y + p2.y) / 2f
            path.quadTo(p1.x, p1.y, midX, midY)
        }
        // 最后一段直线连到终点
        val last = points.last()
        path.lineTo(last.x, last.y)
        return path
    }

    /**
     * 线性混合两种颜色
     * @param c1 起始颜色
     * @param c2 目标颜色
     * @param t 混合因子 [0,1]，0 返回 c1，1 返回 c2
     */
    private fun blendColor(c1: Int, c2: Int, t: Float): Int {
        val r = (Color.red(c1) * (1f - t) + Color.red(c2) * t).toInt().coerceIn(0, 255)
        val g = (Color.green(c1) * (1f - t) + Color.green(c2) * t).toInt().coerceIn(0, 255)
        val b = (Color.blue(c1) * (1f - t) + Color.blue(c2) * t).toInt().coerceIn(0, 255)
        val a = (Color.alpha(c1) * (1f - t) + Color.alpha(c2) * t).toInt().coerceIn(0, 255)
        return Color.argb(a, r, g, b)
    }
}
