package com.poseai.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.poseai.app.design.Brand
import com.poseai.app.design.Type
import com.poseai.app.model.CompositionRule
import com.poseai.app.model.ShootingPlan
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@Immutable
private data class SilLayout(val silW: Float, val silH: Float, val centerX: Float, val centerY: Float)

/**
 * 剪影引导叠加层——复刻 iOS SilhouetteGuideOverlay + PoseSilhouetteShape。
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
        val bottomSafeZone = screenH * 0.08f
        val centerY = detectedMidY.coerceIn(silH / 2f, screenH - silH / 2f - bottomSafeZone)
        return SilLayout(silW, silH, centerX, centerY)
    } else {
        val defaultH = screenH * plan.frameRatio.heightRatio
        val defaultW = defaultH * aspect
        val defaultX = screenW / 2f
        val bottomOffset = screenH * 0.15f
        val defaultY = if (plan.frameRatio.name == "FULL_BODY") screenH - defaultH / 2f - bottomOffset else screenH * 0.42f
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

/**
 * 增强版评分环——复刻 iOS scoreRing。
 * 带外发光、渐变进度弧、对齐时缩放脉冲效果。
 * 48dp 紧凑尺寸适配顶部信息栏。
 */
@Composable
fun ScoreRing(score: Float, isReady: Boolean) {
    val progress by animateFloatAsState(
        targetValue = (score / 100f).coerceIn(0f, 1f),
        animationSpec = tween(120),
        label = "scoreProgress"
    )
    val scaleAnim by animateFloatAsState(
        targetValue = if (isReady) 1.08f else 1.0f,
        animationSpec = spring(dampingRatio = 0.55f),
        label = "scoreScale"
    )
    val glowAlpha by animateFloatAsState(
        targetValue = if (isReady) 0.5f else 0f,
        animationSpec = tween(300),
        label = "glowAlpha"
    )

    Box(
        modifier = Modifier.size(54.dp).graphicsLayer {
            scaleX = scaleAnim
            scaleY = scaleAnim
        },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(54.dp)) {
            val cx = size.width / 2
            val cy = size.height / 2
            val r = size.minDimension / 2 - 4f

            // 外发光（对齐时）
            if (isReady) {
                drawCircle(
                    color = Brand.Success.copy(alpha = glowAlpha),
                    radius = r + 3f,
                    center = Offset(cx, cy),
                    style = Stroke(width = 3f)
                )
            }

            // 底层轨道
            drawArc(
                color = Color.White.copy(alpha = 0.12f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = 3f, cap = StrokeCap.Round)
            )

            // 进度弧 - 使用渐变色
            val progressColor = if (isReady) {
                Brush.sweepGradient(
                    listOf(Brand.Success, Brand.Success.copy(alpha = 0.6f)),
                    center = Offset(cx, cy)
                )
            } else if (score > 60f) {
                Brush.sweepGradient(
                    listOf(Brand.Accent, Brand.Accent.copy(alpha = 0.5f)),
                    center = Offset(cx, cy)
                )
            } else {
                Brush.sweepGradient(
                    listOf(Color.White.copy(alpha = 0.8f), Color.White.copy(alpha = 0.3f)),
                    center = Offset(cx, cy)
                )
            }

            drawArc(
                brush = progressColor,
                startAngle = -90f,
                sweepAngle = 360f * progress,
                useCenter = false,
                style = Stroke(width = 3f, cap = StrokeCap.Round)
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

/**
 * 场景扫描动画覆盖层——复刻 iOS sceneScanningOverlay。
 * 带脉冲环、四角修饰线、旋转扫描弧。
 * @param screenHeightDp 屏幕高度（dp），用于响应式定位底部提示
 */
@Composable
fun SceneScanningOverlay(screenHeightDp: Dp = 800.dp) {
    val infiniteTransition = rememberInfiniteTransition(label = "scanPulse")
    val pulseSize by animateFloatAsState(
        targetValue = 220f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "pulseSize"
    )
    val pulseAlpha by animateFloatAsState(
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    val rotation by animateFloatAsState(
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = androidx.compose.animation.core.LinearEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Restart
        ),
        label = "scanRotation"
    )

    val bottomTipOffset = screenHeightDp * 0.2f

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier.size(pulseSize.dp)
        ) {
            drawCircle(
                color = Brand.Accent.copy(alpha = pulseAlpha),
                style = Stroke(width = 1.5f)
            )
        }

        Canvas(
            modifier = Modifier.size((pulseSize - 30).dp)
        ) {
            drawCircle(
                color = Brand.Accent.copy(alpha = (pulseAlpha * 0.6f).coerceIn(0f, 0.35f)),
                style = Stroke(width = 1f)
            )
        }

        Box(
            modifier = Modifier
                .size(140.dp, 190.dp)
                .border(2.dp, Brand.Accent.copy(alpha = 0.8f), RoundedCornerShape(20.dp))
        )

        ScanCornerLinesModifier()

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.offset(y = (-10).dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .graphicsLayer { rotationZ = rotation }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawArc(
                        brush = Brush.sweepGradient(
                            listOf(Brand.Accent, Color.Transparent),
                            center = Offset(size.width / 2, size.height / 2)
                        ),
                        startAngle = 0f,
                        sweepAngle = 90f,
                        useCenter = false,
                        style = Stroke(width = 2.5f, cap = StrokeCap.Round)
                    )
                }
            }

            Text(
                "识别场景中…",
                style = Type.caption,
                color = Brand.TextPrimary,
                modifier = Modifier.padding(top = 10.dp)
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.offset(y = bottomTipOffset)
        ) {
            Text(
                "将镜头对准拍摄背景",
                style = Type.body,
                color = Brand.TextPrimary.copy(alpha = 0.85f)
            )
            Text(
                "咖啡馆 · 海边 · 森林",
                style = Type.label,
                color = Brand.Accent.copy(alpha = 0.7f),
                letterSpacing = 2.sp,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}

@Composable
private fun ScanCornerLinesModifier() {
    Canvas(modifier = Modifier.size(140.dp, 190.dp)) {
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

/**
 * AR 地面脚印覆盖层——复刻 iOS arFootprintsOverlay。
 * @param bottomPadding 底部偏移（dp），由 ContentScreen 根据屏幕高度计算传入
 */
@Composable
fun ARFootprintsOverlay(bottomPadding: Dp = 220.dp) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter
    ) {
        Row(
            modifier = Modifier.padding(bottom = bottomPadding),
            horizontalArrangement = Arrangement.spacedBy(36.dp)
        ) {
            Text("👣", fontSize = 24.sp, color = Brand.Accent.copy(alpha = 0.25f),
                modifier = Modifier.graphicsLayer { rotationZ = -12f })
            Text("👣", fontSize = 24.sp, color = Brand.Accent.copy(alpha = 0.25f),
                modifier = Modifier.graphicsLayer { rotationZ = 12f })
        }
    }
}

/**
 * 暗光屏幕柔边补光带——国内手机暗光摄影体验。
 * 柔和的暖色边缘补光，不遮挡主体内容。
 */
@Composable
fun LowLightGlowOverlay() {
    // 柔光叠加：低透明度暖色径向渐变，中央透明边缘微亮
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            // 边缘微光
            val maxDim = max(w, h)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.Transparent, Color(0x10FFE8C0)),
                    center = Offset(w / 2, h / 2),
                    radius = maxDim / 2
                ),
                radius = maxDim / 2,
                center = Offset(w / 2, h / 2)
            )
        }
    }
}

/**
 * AI 构图灵感浮层——复刻 iOS aiAdvisorBanner。
 * 适配刘海屏，使用 statusBarsPadding。
 * @param topPadding 顶部偏移（dp），由 ContentScreen 根据屏幕高度计算传入
 */
@Composable
fun AiAdvisorBanner(text: String, topPadding: Dp = 95.dp) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(top = topPadding)
            .padding(horizontal = 24.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brand.Surface,
                    RoundedCornerShape(18.dp)
                )
                .border(
                    1.5.dp,
                    Brush.linearGradient(
                        listOf(Brand.AI_Purple.copy(alpha = 0.6f), Color.Transparent),
                        start = androidx.compose.ui.geometry.Offset(0f, 0f),
                        end = androidx.compose.ui.geometry.Offset(1f, 1f)
                    ),
                    RoundedCornerShape(18.dp)
                )
                .padding(horizontal = 18.dp, vertical = 14.dp),
        ) {
            Text("✨", fontSize = 18.sp, modifier = Modifier.padding(top = 2.dp))
            Column(modifier = Modifier.padding(start = 12.dp)) {
                Text("AI 构图灵感", style = Type.label, color = Brand.AI_Purple)
                Text(text, style = Type.bodySecondary, color = Brand.TextPrimary)
            }
        }
    }
}

/**
 * Vlog 提词器覆盖层——复刻 iOS vlogTextOverlay。
 * 位置自适应，基于屏幕高度动态计算底部偏移。
 * @param screenHeightDp 屏幕高度（dp），用于响应式定位
 */
@Composable
fun VlogTextOverlay(text: String, isRecording: Boolean, screenHeightDp: Dp = 800.dp) {
    val bottomOffset = (screenHeightDp * 0.33f).coerceAtLeast(280.dp)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = bottomOffset),
        verticalArrangement = Arrangement.Bottom,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                .padding(horizontal = 24.dp, vertical = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Black
            )
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
            .padding(horizontal = 9.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(icon, fontSize = 11.sp)
        Text(text, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
            color = if (active) Brand.Accent else Color.White.copy(alpha = 0.7f))
    }
}

/** 方案选择卡片（紧凑 pill）——复刻 iOS PlanCard */
@Composable
fun PlanCard(plan: ShootingPlan, isSelected: Boolean, onClick: () -> Unit) {
    val glowAlpha by animateFloatAsState(
        targetValue = if (isSelected) 0.4f else 0f,
        animationSpec = tween(300),
        label = "cardGlow"
    )
    val scaleAnim by animateFloatAsState(
        targetValue = if (isSelected) 1.03f else 1.0f,
        animationSpec = spring(dampingRatio = 0.68f),
        label = "cardScale"
    )

    Column(
        modifier = Modifier
            .graphicsLayer {
                scaleX = scaleAnim
                scaleY = scaleAnim
            }
            .background(
                if (isSelected) {
                    Brush.linearGradient(
                        listOf(Brand.Accent.copy(alpha = 0.22f), Brand.Accent.copy(alpha = 0.08f)),
                        start = androidx.compose.ui.geometry.Offset(0f, 0f),
                        end = androidx.compose.ui.geometry.Offset(0f, 1f)
                    )
                } else {
                    Brush.linearGradient(
                        listOf(Color.Black.copy(alpha = 0.45f), Color.Black.copy(alpha = 0.3f)),
                        start = androidx.compose.ui.geometry.Offset(0f, 0f),
                        end = androidx.compose.ui.geometry.Offset(0f, 1f)
                    )
                },
                RoundedCornerShape(12.dp)
            )
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = if (isSelected) Brand.Accent.copy(alpha = 0.75f) else Brand.Border,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(plan.poseEmoji, fontSize = 16.sp)
            Text(
                plan.poseName,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1
            )
        }
        if (isSelected) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TagBadge(icon = plan.composition.icon, text = plan.composition.displayName, active = true)
                TagBadge(icon = plan.frameRatio.icon, text = plan.frameRatio.displayName, active = true)
            }
        }
    }
}

// ─── ─── ─── ─── 对焦指示框 ─── ─── ─── ───

/**
 * 点击对焦指示动画——国内手机相机标准体验。
 * 十字对焦框 + 脉冲动画，1.5秒后自动消失。
 * 使用实际屏幕尺寸进行边界保护。
 */
@Composable
fun FocusIndicator(point: Offset) {
    val infiniteTransition = rememberInfiniteTransition(label = "focusPulse")
    val scaleAnim by animateFloatAsState(
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "focusScale"
    )
    val alphaAnim by animateFloatAsState(
        targetValue = 0.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "focusAlpha"
    )

    // 获取屏幕尺寸用于边界保护
    val configuration = LocalConfiguration.current
    val localDensity = LocalDensity.current
    val screenWidthPx = with(localDensity) { configuration.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(localDensity) { configuration.screenHeightDp.dp.toPx() }

    val indicatorSizePx = with(localDensity) { 70.dp.toPx() }
    val halfSize = indicatorSizePx / 2f

    // 边界保护：确保对焦框不会超出屏幕
    val clampedX = point.x.coerceIn(halfSize, screenWidthPx - halfSize)
    val clampedY = point.y.coerceIn(halfSize, screenHeightPx - halfSize)

    Box(
        modifier = Modifier
            .offset { IntOffset((clampedX - halfSize).toInt(), (clampedY - halfSize).toInt()) }
            .size(70.dp)
            .graphicsLayer {
                scaleX = scaleAnim
                scaleY = scaleAnim
                alpha = alphaAnim
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val lineLen = 14.dp.toPx()
            val strokeW = 2.dp.toPx()
            val cornerGap = 4.dp.toPx()

            // 四角标记
            drawLine(Brand.Accent, Offset(cornerGap, cornerGap), Offset(cornerGap + lineLen, cornerGap), strokeW)
            drawLine(Brand.Accent, Offset(cornerGap, cornerGap), Offset(cornerGap, cornerGap + lineLen), strokeW)
            drawLine(Brand.Accent, Offset(w - cornerGap, cornerGap), Offset(w - cornerGap - lineLen, cornerGap), strokeW)
            drawLine(Brand.Accent, Offset(w - cornerGap, cornerGap), Offset(w - cornerGap, cornerGap + lineLen), strokeW)
            drawLine(Brand.Accent, Offset(cornerGap, h - cornerGap), Offset(cornerGap + lineLen, h - cornerGap), strokeW)
            drawLine(Brand.Accent, Offset(cornerGap, h - cornerGap), Offset(cornerGap, h - cornerGap - lineLen), strokeW)
            drawLine(Brand.Accent, Offset(w - cornerGap, h - cornerGap), Offset(w - cornerGap - lineLen, h - cornerGap), strokeW)
            drawLine(Brand.Accent, Offset(w - cornerGap, h - cornerGap), Offset(w - cornerGap, h - cornerGap - lineLen), strokeW)

            // 中心十字
            drawLine(Brand.Accent.copy(alpha = 0.7f), Offset(w / 2 - 12.dp.toPx(), h / 2), Offset(w / 2 + 12.dp.toPx(), h / 2), 1.dp.toPx())
            drawLine(Brand.Accent.copy(alpha = 0.7f), Offset(w / 2, h / 2 - 12.dp.toPx()), Offset(w / 2, h / 2 + 12.dp.toPx()), 1.dp.toPx())
        }
    }
}

// ─── ─── ─── ─── 变焦指示条 ─── ─── ─── ───

/**
 * 变焦水平指示条——模拟国内主流手机相机的变焦体验。
 * 支持手动拖动调整变焦倍率，预设 0.5x / 1x / 2x / 3x 快捷按钮。
 */
@Composable
fun ZoomLevelIndicator(
    currentZoom: Float,
    onZoomChange: (Float) -> Unit
) {
    val presets = listOf(0.5f, 1f, 2f, 3f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 36.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 变焦档位
        presets.forEach { preset ->
            val isActive = abs(currentZoom - preset) < 0.05f
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        if (isActive) Brand.Accent.copy(alpha = 0.2f)
                        else Color.White.copy(alpha = 0.08f),
                        CircleShape
                    )
                    .border(
                        1.dp,
                        if (isActive) Brand.Accent.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.15f),
                        CircleShape
                    )
                    .clickable { onZoomChange(preset) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (preset < 1) "${preset}x" else "${preset.toInt()}x",
                    color = if (isActive) Brand.Accent else Color.White.copy(alpha = 0.7f),
                    fontSize = 13.sp,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium
                )
            }
        }
    }
}

// ─── ─── ─── ─── 录制进度条 ─── ─── ─── ───

/**
 * Vlog/多机位录制进度条——国内视频录制体验。
 * 显示当前片段进度和总体进度。
 * @param topOffsetDp 顶部偏移（dp），由 ContentScreen 根据屏幕高度计算传入
 */
@Composable
fun RecordingProgressBar(current: Int, total: Int, label: String, topOffsetDp: Dp = 128.dp) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(top = topOffsetDp)
    ) {
        Column(
            modifier = Modifier
                .background(Brand.Surface.copy(alpha = 0.9f), RoundedCornerShape(14.dp))
                .border(1.dp, Brand.Coral.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(Brand.Coral, CircleShape)
                    )
                    Text(
                        text = " ● $label",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = "$current/$total",
                    color = Brand.TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            Spacer(Modifier.height(6.dp))
            // 总体进度点
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                repeat(total) { idx ->
                    Box(
                        modifier = Modifier
                            .then(if (idx == 0) Modifier else Modifier)
                            .height(2.dp)
                            .background(
                                if (idx < current) Brand.Coral
                                else Color.White.copy(alpha = 0.2f),
                                RoundedCornerShape(2.dp)
                            )
                            .fillMaxWidth()
                    )
                }
            }
        }
    }
}
