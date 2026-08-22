package com.poseai.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.poseai.app.data.ShootingRecordEntity
import com.poseai.app.design.Brand
import com.poseai.app.model.SceneType
import kotlin.math.roundToInt

/**
 * 拍摄数据统计——Android 端移植自 iOS StatsView。
 * 全暗色，包含统计卡片、场景偏好分布条形图与姿势评分趋势折线图。
 */
@Composable
fun StatsScreen(
    records: List<ShootingRecordEntity>,
    onBack: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize().background(Brand.Screen)) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopBar(onBack = onBack)

            if (records.isEmpty()) {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("暂无数据支撑", color = Brand.TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "去多拍几张照片，再来看看你的专属摄影报告吧",
                            color = Brand.TextSecondary,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    val count = records.size
                    val avg = (records.map { it.matchScore }.sum().toFloat() / count).roundToInt()
                    val topSceneRaw = records.groupBy { it.sceneRawValue }
                        .maxByOrNull { it.value.size }?.key.orEmpty()

                    StatCards(count = count, avg = avg, topSceneRaw = topSceneRaw)
                    Spacer(Modifier.height(16.dp))

                    SceneDistributionCard(records)
                    Spacer(Modifier.height(16.dp))

                    ScoreTrendCard(records)
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

/** 顶部工具栏：返回 + 标题 */
@Composable
private fun TopBar(onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(Brand.Surface)
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .clickable { onBack() },
            contentAlignment = Alignment.Center
        ) {
            Text("‹ 返回", color = Brand.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.weight(1f))
        Text("拍摄数据", color = Brand.TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.weight(1f))
        Box(Modifier.width(16.dp).height(1.dp)) // 平衡左右
    }
}

/** 三个统计卡片 */
@Composable
private fun StatCards(count: Int, avg: Int, topSceneRaw: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        StatCard(modifier = Modifier.weight(1f), icon = "📷", title = "总拍摄", value = "$count", unit = "张", accent = Brand.Accent)
        StatCard(modifier = Modifier.weight(1f), icon = "🎯", title = "平均得分", value = "$avg", unit = "分", accent = Brand.Success)
        StatCard(
            modifier = Modifier.weight(1f),
            icon = sceneEmoji(topSceneRaw),
            title = "最爱场景",
            value = sceneDisplayName(topSceneRaw),
            unit = "",
            accent = Brand.Accent
        )
    }
}

@Composable
private fun StatCard(
    modifier: Modifier = Modifier,
    icon: String,
    title: String,
    value: String,
    unit: String,
    accent: Color
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(Brand.Radius.Medium))
            .background(Brand.Surface)
            .padding(vertical = 16.dp, horizontal = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(icon, fontSize = 16.sp)
        Text(
            title,
            color = Brand.TextSecondary,
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 6.dp)
        )
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                value,
                color = accent,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                modifier = Modifier.padding(top = 4.dp)
            )
            if (unit.isNotEmpty()) {
                Text(
                    unit,
                    color = Brand.TextSecondary,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(start = 2.dp, bottom = 2.dp)
                )
            }
        }
    }
}

/** 场景偏好分布横条卡片 */
@Composable
private fun SceneDistributionCard(records: List<ShootingRecordEntity>) {
    val sceneCounts = remember(records) {
        records.groupBy { it.sceneRawValue }
            .mapValues { it.value.size }
            .entries.sortedByDescending { it.value }
    }
    val maxCount = sceneCounts.maxOfOrNull { it.value } ?: 1

    Card(title = "场景偏好分布", subtitle = "记录了你在不同环境下触发的打卡频次") {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            sceneCounts.forEach { (raw, count) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        sceneDisplayName(raw),
                        color = Brand.TextSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        modifier = Modifier.width(76.dp)
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(18.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Brand.SurfaceHigh)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(count.toFloat() / maxCount)
                                .background(Brand.Accent, RoundedCornerShape(6.dp))
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text("$count", color = Brand.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/** 姿势评分趋势折线卡片 */
@Composable
private fun ScoreTrendCard(records: List<ShootingRecordEntity>) {
    val sorted = remember(records) { records.sortedBy { it.createdAt } }
    Card(title = "姿势评分趋势", subtitle = "展现你每次拍摄的表现，看看有没有进步") {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .padding(top = 12.dp, bottom = 4.dp)
        ) {
            if (sorted.isEmpty()) return@Canvas
            val n = sorted.size
            val stepX = if (n > 1) size.width / (n - 1) else 0f
            val points = sorted.mapIndexed { i, r ->
                val x = if (n > 1) i * stepX else size.width / 2f
                val y = size.height - (r.matchScore.coerceIn(0, 100) / 100f) * size.height
                Offset(x, y)
            }
            val path = Path()
            points.forEachIndexed { i, p ->
                if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
            }
            drawPath(
                path = path,
                color = Brand.Success,
                style = Stroke(width = 3f, cap = StrokeCap.Round)
            )
            points.forEach { p ->
                drawCircle(color = Brand.Success, radius = 3.5f, center = p)
            }
        }
    }
}

/** 通用卡片容器 */
@Composable
private fun Card(title: String, subtitle: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Brand.Radius.Medium))
            .background(Brand.Surface)
            .padding(16.dp)
    ) {
        Text(title, color = Brand.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Text(subtitle, color = Brand.TextSecondary, fontSize = 11.sp, modifier = Modifier.padding(top = 3.dp))
        Spacer(Modifier.height(14.dp))
        content()
    }
}

/** ---------- 场景映射工具 ---------- */

private fun sceneDisplayName(raw: String): String =
    try {
        SceneType.valueOf(raw).displayName
    } catch (e: Exception) {
        raw
    }

private fun sceneEmoji(raw: String): String =
    try {
        when (SceneType.valueOf(raw)) {
            SceneType.COFFEE_SHOP -> "☕"
            SceneType.BEACH -> "🏖️"
            SceneType.FOREST -> "🌲"
            SceneType.CITY_STREET -> "🏙️"
            SceneType.PARK -> "🌳"
            SceneType.INDOOR_HOME -> "🏠"
            SceneType.NEON_NIGHT -> "🌃"
            SceneType.UNKNOWN -> "📍"
        }
    } catch (e: Exception) {
        "📍"
    }