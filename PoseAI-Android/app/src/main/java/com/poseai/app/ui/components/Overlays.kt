package com.poseai.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.poseai.app.design.Brand
import com.poseai.app.model.CompositionRule
import com.poseai.app.model.ShootingPlan
import kotlin.math.roundToInt

@Immutable
private data class SilLayout(val silW: Float, val silH: Float, val centerX: Float, val centerY: Float)

/**
 * 剪影引导叠加层——复刻 iOS SilhouetteGuideOverlay + PoseSilhouetteShape。
 * 传入画面像素尺寸与人体归一化包围盒，自动计算剪影尺寸与位置。
 */
@Composable
fun SilhouetteGuideOverlay(
    isAligned: Boolean,
    plan: ShootingPlan,
    bodyBoundingBox: Rect?,
    screenW: Float,
    screenH: Float,
    forceOffset: Float? = null
) {
    val layout = resolveLayout(screenW, screenH, bodyBoundingBox, plan)
    val hOffset = forceOffset ?: plan.composition.offset
    val centerX = layout.centerX + hOffset
    val centerY = layout.centerY
    val left = (centerX - layout.silW / 2f).roundToInt()
    val top = (centerY - layout.silH / 2f).roundToInt()

    val dash by animateFloatAsState(
        targetValue = if (isAligned) 0f else 1f, animationSpec = tween(300), label = "dash"
    )

    Column(
        modifier = Modifier
            .offset { IntOffset(left, top) }
    ) {
        Canvas(
            modifier = Modifier.size(layout.silW.dp, layout.silH.dp)
        ) {
            val path = poseSilhouettePath(size.width, size.height)
            drawPath(
                path = path,
                color = if (isAligned) Brand.Success.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.12f),
                style = Fill
            )
            drawPath(
                path = path,
                brush = if (isAligned) {
                    Brush.verticalGradient(listOf(Brand.Success, Brand.Success.copy(alpha = 0.6f)))
                } else {
                    Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.7f), Color.White.copy(alpha = 0.3f)))
                },
                style = Stroke(
                    width = if (isAligned) 3f else 1.8f,
                    cap = StrokeCap.Round,
                    pathEffect = if (isAligned) null else PathEffect.dashPathEffect(floatArrayOf(10f, 7f))
                )
            )
        }
        // 距离提示
        if (!isAligned) {
            Text(
                text = plan.frameRatio.distanceHint,
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.offset(x = (layout.silW / 2f).roundToInt().dp)
                    .padding(top = 6.dp)
            )
        }
    }
}

private fun resolveLayout(
    screenW: Float, screenH: Float,
    bbox: Rect?, plan: ShootingPlan
): SilLayout {
    val aspect = 0.52f
    if (bbox != null && bbox.height > 0.05f) {
        val paddingTop = 0.10f
        val paddingH = 0.05f
        val paddingSide = 0.08f
        val bboxH = minOf(bbox.height + paddingTop + paddingH, 0.95f)
        var rawH = bboxH * screenH
        val minH = screenH * plan.frameRatio.heightRatio * 0.5f
        val maxH = screenH * plan.frameRatio.heightRatio * 1.3f
        rawH = maxOf(minH, minOf(maxH, rawH))
        val silH = rawH
        val silW = silH * aspect
        val detectedCenterX = (bbox.center.x + paddingSide / 2f) * screenW
        val centerX = detectedCenterX.coerceIn(silW / 2f, screenW - silW / 2f)
        val detectedMidY = (bbox.top - paddingTop / 2f + bboxH / 2f) * screenH
        val centerY = detectedMidY.coerceIn(silH / 2f, screenH - silH / 2f - 40f)
        return SilLayout(silW, silH, centerX, centerY)
    } else {
        val defaultH = screenH * plan.frameRatio.heightRatio
        val defaultW = defaultH * aspect
        val defaultX = screenW / 2f
        val defaultY = if (plan.frameRatio.name == "FULL_BODY") screenH - defaultH / 2f - 140f else screenH * 0.42f
        return SilLayout(defaultW, defaultH, defaultX, defaultY)
    }
}

/** 绘制人形剪影 Path——复刻 iOS PoseSilhouetteShape */
fun poseSilhouettePath(w: Float, h: Float): Path {
    val path = Path()
    val headSize = w * 0.24f
    path.addOval(Rect(w * 0.38f, h * 0.02f, w * 0.38f + headSize, h * 0.02f + headSize * 1.15f))

    path.moveTo(w * 0.45f, h * 0.14f + headSize * 1.15f)
    path.quadraticBezierTo(w * 0.28f, h * 0.22f, w * 0.18f, h * 0.28f)
    path.quadraticBezierTo(w * 0.08f, h * 0.38f, w * 0.12f, h * 0.52f)
    path.cubicTo(w * 0.18f, h * 0.56f, w * 0.22f, h * 0.48f, w * 0.28f, h * 0.43f)
    path.lineTo(w * 0.33f, h * 0.50f)
    path.quadraticBezierTo(w * 0.27f, h * 0.72f, w * 0.24f, h * 0.93f)
    path.lineTo(w * 0.40f, h * 0.93f)
    path.quadraticBezierTo(w * 0.44f, h * 0.74f, w * 0.48f, h * 0.58f)
    path.quadraticBezierTo(w * 0.54f, h * 0.74f, w * 0.63f, h * 0.93f)
    path.lineTo(w * 0.79f, h * 0.93f)
    path.quadraticBezierTo(w * 0.79f, h * 0.73f, w * 0.70f, h * 0.52f)
    path.lineTo(w * 0.64f, h * 0.48f)
    path.quadraticBezierTo(w * 0.74f, h * 0.52f, w * 0.83f, h * 0.43f)
    path.quadraticBezierTo(w * 0.94f, h * 0.33f, w * 0.78f, h * 0.24f)
    path.quadraticBezierTo(w * 0.67f, h * 0.23f, w * 0.55f, h * 0.14f + headSize * 1.15f)
    path.close()
    return path
}

/** 构图辅助线（三分法 / 黄金螺旋） */
@Composable
fun CompositionGuideLines(composition: CompositionRule?) {
    if (composition == null) return
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        if (composition == CompositionRule.GOLDEN_LEFT || composition == CompositionRule.GOLDEN_RIGHT) {
            val isRight = composition == CompositionRule.GOLDEN_RIGHT
            val pivotX = if (isRight) w * 0.618f else w * 0.382f
            val pivotY = h * 0.382f
            val path = Path()
            path.moveTo(if (isRight) 0f else w, h)
            path.quadraticBezierTo(
                if (isRight) w * 0.2f else w * 0.8f,
                pivotY + h * 0.1f,
                pivotX, pivotY
            )
            drawPath(path, Brand.Accent.copy(alpha = 0.3f), style = Stroke(width = 1.5f))
            drawCircle(Brand.Accent.copy(alpha = 0.5f), radius = 4f, center = Offset(pivotX, pivotY), style = Stroke(1f))
        } else {
            val line = Color.White.copy(alpha = 0.06f)
            drawLine(line, Offset(w / 3, 0f), Offset(w / 3, h), 1f)
            drawLine(line, Offset(w * 2 / 3, 0f), Offset(w * 2 / 3, h), 1f)
            drawLine(line, Offset(0f, h / 3), Offset(w, h / 3), 1f)
            drawLine(line, Offset(0f, h * 2 / 3), Offset(w, h * 2 / 3), 1f)
        }
    }
}

/** 评分环——复刻 iOS scoreRing */
@Composable
fun ScoreRing(score: Float, isReady: Boolean) {
    val progress by animateFloatAsState(score / 100f, tween(120), label = "score")
    Box(modifier = Modifier.size(54.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(46.dp)) {
            if (isReady) {
                drawCircle(Brand.Success.copy(alpha = 0.35f), radius = size.minDimension / 2 + 4f)
            }
            drawArc(
                color = Color.White.copy(alpha = 0.12f),
                startAngle = -90f, sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = 3.5f, cap = StrokeCap.Round)
            )
            drawArc(
                color = if (isReady) Brand.Success else Brand.Accent,
                startAngle = -90f,
                sweepAngle = 360f * progress.coerceIn(0f, 1f),
                useCenter = false,
                style = Stroke(width = 3.5f, cap = StrokeCap.Round)
            )
        }
        Text(
            text = "${score.toInt()}",
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Black
        )
    }
}

/** 扫描框四角修饰线——复刻 iOS ScanCornerLines */
@Composable
fun ScanCornerLines() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val len = 18.dp.toPx()
        val thick = 2.5.dp.toPx()
        val corners = listOf(
            listOf(Offset(0f, len), Offset(0f, 0f), Offset(len, 0f)),
            listOf(Offset(w - len, 0f), Offset(w, 0f), Offset(w, len)),
            listOf(Offset(0f, h - len), Offset(0f, h), Offset(len, h)),
            listOf(Offset(w - len, h), Offset(w, h), Offset(w, h - len))
        )
        corners.forEach { (a, b, c) ->
            val p = Path().apply {
                moveTo(a.x, a.y); lineTo(b.x, b.y); lineTo(c.x, c.y)
            }
            drawPath(p, Brand.Accent, style = Stroke(width = thick, cap = StrokeCap.Round))
        }
    }
}

/** 方案小标签——复刻 iOS TagBadge */
@Composable
private fun TagBadge(icon: String, text: String, active: Boolean) {
    Row(
        modifier = Modifier
            .background(
                if (active) Brand.Accent.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.1f),
                CircleShape
            )
            .padding(horizontal = 7.dp, vertical = 3.5.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(icon, fontSize = 9.sp)
        Text(text, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = if (active) Brand.Accent else Color.White.copy(alpha = 0.7f))
    }
}

/** 方案选择卡片（紧凑 pill）——复刻 iOS PlanCard */
@Composable
fun PlanCard(plan: ShootingPlan, isSelected: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .background(
                if (isSelected) {
                    Brush.linearGradient(listOf(Brand.Accent.copy(alpha = 0.22f), Brand.Accent.copy(alpha = 0.08f)))
                } else {
                    Brush.linearGradient(listOf(Color.Black.copy(alpha = 0.45f), Color.Black.copy(alpha = 0.3f)))
                },
                RoundedCornerShape(12)
            )
            .border(1.dp, if (isSelected) Brand.Accent.copy(alpha = 0.75f) else Brand.Hairline, RoundedCornerShape(12))
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(plan.poseEmoji, fontSize = 17.sp)
            Text(
                plan.poseName,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1
            )
        }
        if (isSelected) {
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                TagBadge(icon = plan.composition.icon, text = plan.composition.displayName, active = true)
                TagBadge(icon = plan.frameRatio.icon, text = plan.frameRatio.displayName, active = true)
            }
        }
    }
}