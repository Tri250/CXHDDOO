package com.poseai.app.ui

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.poseai.app.data.ShootingRecordEntity
import com.poseai.app.design.Brand
import com.poseai.app.model.PhotoFilter
import com.poseai.app.model.SceneType
import com.poseai.app.util.loadBitmapFromUri
import com.poseai.app.util.loadThumbnailFromUri
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 历史图库——Android 端移植自 iOS HistoryGalleryView。
 * 全暗色网格 + 月份筛选 + 日分组 + 明细全屏覆盖层。
 */
@Composable
fun HistoryGalleryScreen(
    records: List<ShootingRecordEntity>,
    onClose: () -> Unit,
    onShowStats: () -> Unit
) {
    val context = LocalContext.current
    var selectedRecord by remember { mutableStateOf<ShootingRecordEntity?>(null) }
    var selectedMonth by remember { mutableStateOf<String?>(null) }

    val sortedRecords = remember(records) { records.sortedByDescending { it.createdAt } }
    val availableMonths = remember(records) {
        records.asSequence()
            .map { monthKey(it.createdAt) }
            .distinct()
            .sortedDescending()
            .toList()
    }
    val filteredRecords = remember(sortedRecords, selectedMonth) {
        if (selectedMonth == null) sortedRecords
        else sortedRecords.filter { monthKey(it.createdAt) == selectedMonth }
    }

    Box(modifier = Modifier.fillMaxSize().background(Brand.Screen)) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopBar(
                title = "历史图库",
                leftText = "📊",
                rightText = "完成",
                onLeft = onShowStats,
                onRight = onClose
            )

            if (records.isEmpty()) {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🖼️", fontSize = 48.sp)
                        Text(
                            "暂无拍摄记录",
                            color = Brand.TextPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 16.dp)
                        )
                        Text(
                            "去拍几张照片，它们会出现在这里",
                            color = Brand.TextSecondary,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            } else {
                if (availableMonths.size > 1) {
                    MonthFilterRow(
                        months = availableMonths,
                        selected = selectedMonth,
                        onSelect = { selectedMonth = it }
                    )
                }

                val grouped = remember(filteredRecords) {
                    filteredRecords.groupBy { dayKey(it.createdAt) }
                        .map { (key, list) ->
                            DaySection(key, dayDisplay(list.first().createdAt), list)
                        }
                        .sortedByDescending { it.key }
                }

                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    grouped.forEach { section ->
                        item(key = "header_${section.key}") {
                            DayHeader(section.display)
                        }
                        section.records.chunked(3).forEachIndexed { idx, row ->
                            item(key = "row_${section.key}_$idx") {
                                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                                    row.forEach { record ->
                                        GalleryCell(
                                            modifier = Modifier.weight(1f).padding(3.dp).aspectRatio(1f),
                                            record = record,
                                            context = context,
                                            onTap = { selectedRecord = record }
                                        )
                                    }
                                    repeat(3 - row.size) {
                                        Spacer(Modifier.weight(1f).aspectRatio(1f).padding(3.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        selectedRecord?.let { record ->
            DetailOverlay(
                record = record,
                context = context,
                onClose = { selectedRecord = null }
            )
        }
    }
}

/** 顶部工具栏：左按钮 + 居中标题 + 右按钮 */
@Composable
private fun TopBar(
    title: String,
    leftText: String,
    rightText: String,
    onLeft: () -> Unit,
    onRight: () -> Unit
) {
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
                .clickable { onLeft() },
            contentAlignment = Alignment.Center
        ) {
            Text(leftText, color = Brand.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.weight(1f))
        Text(title, color = Brand.TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.weight(1f))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(Brand.Surface)
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .clickable { onRight() },
            contentAlignment = Alignment.Center
        ) {
            Text(rightText, color = Brand.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** 月份筛选胶囊（LazyRow） */
@Composable
private fun MonthFilterRow(
    months: List<String>,
    selected: String?,
    onSelect: (String?) -> Unit
) {
    LazyRow(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
    ) {
        item(key = "all") {
            FilterCapsule(
                label = "全部",
                isActive = selected == null,
                onTap = { onSelect(null) }
            )
        }
        items(months, key = { it }) { month ->
            FilterCapsule(
                label = monthDisplay(month),
                isActive = month == selected,
                onTap = { onSelect(month) }
            )
        }
    }
}

@Composable
private fun FilterCapsule(label: String, isActive: Boolean, onTap: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(Brand.Radius.Pill))
            .background(if (isActive) Brand.Accent else Brand.Surface)
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .clickable { onTap() }
    ) {
        Text(
            label,
            color = if (isActive) Color.White else Brand.TextSecondary,
            fontSize = 12.sp,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium
        )
    }
}

/** 某天的标题行 */
@Composable
private fun DayHeader(text: String) {
    Text(
        text = text,
        color = Brand.TextPrimary,
        fontSize = 15.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 18.dp, top = 12.dp, bottom = 8.dp)
    )
}

/** 单个缩略图网格单元 */
@Composable
private fun GalleryCell(
    modifier: Modifier = Modifier,
    record: ShootingRecordEntity,
    context: Context,
    onTap: () -> Unit
) {
    val thumb by produceState<Bitmap?>(initialValue = null, record.localUri) {
        value = loadThumbnailFromUri(context, record.localUri, 320)
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Brand.Surface)
            .clickable { onTap() }
    ) {
        if (thumb != null) {
            Image(
                bitmap = thumb!!.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("🖼️", fontSize = 22.sp)
            }
        }
        // 得分徽标（>=80 高亮绿色）
        Box(Modifier.fillMaxWidth().padding(6.dp), contentAlignment = Alignment.BottomEnd) {
            val high = record.matchScore >= 80
            Text(
                text = "${record.matchScore}",
                color = Color.Black,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (high) Brand.Success else Brand.TextMuted)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
        // 位置徽标（左下角）
        val locationLine = shortLocation(record)
        if (locationLine != null) {
            Box(Modifier.fillMaxWidth().padding(start = 6.dp, bottom = 6.dp), contentAlignment = Alignment.BottomStart) {
                Text(
                    text = locationLine,
                    color = Color.White,
                    fontSize = 9.sp,
                    maxLines = 1,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black.copy(alpha = 0.55f))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                )
            }
        }
    }
}

private fun shortLocation(record: ShootingRecordEntity): String? {
    val city = record.cityName
    val place = record.placeName
    return when {
        city != null -> city
        place != null -> place
        else -> null
    }
}

/** 全屏明细覆盖层 */
@Composable
private fun DetailOverlay(
    record: ShootingRecordEntity,
    context: Context,
    onClose: () -> Unit
) {
    val big by produceState<Bitmap?>(initialValue = null, record.localUri) {
        value = loadBitmapFromUri(context, record.localUri)
    }

    // 离开时回收大图
    DisposableEffect(record.localUri) {
        onDispose {
            val bmp = big
            if (bmp != null && !bmp.isRecycled) runCatching { bmp.recycle() }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (big != null) {
            Image(
                bitmap = big!!.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("图片加载中…", color = Brand.TextSecondary, fontSize = 14.sp)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            // 左上角关闭
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Brand.Surface.copy(alpha = 0.85f))
                    .clickable { onClose() },
                contentAlignment = Alignment.Center
            ) {
                Text("✕", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.weight(1f))
            // 右上角信息卡
            DetailInfoCard(record)
        }
    }
}

@Composable
private fun DetailInfoCard(record: ShootingRecordEntity) {
    val high = record.matchScore >= 80
    Column(
        modifier = Modifier
            .width(170.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Brand.Surface.copy(alpha = 0.9f))
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Text(sceneDisplayName(record.sceneRawValue), color = Brand.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Text(record.planName, color = Brand.TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
        Text(
            "得分 ${record.matchScore}",
            color = if (high) Brand.Success else Brand.TextMuted,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 4.dp)
        )
        record.appliedFilterRawValue?.let { raw ->
            val filter = PhotoFilter.fromName(raw)
            Text(
                "${filterEmoji(filter)} ${filter.displayName}",
                color = Brand.TextSecondary,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        // 位置信息（城市优先，地点次之）
        val locationLine = buildLocationLine(record)
        if (locationLine != null) {
            Text(
                locationLine,
                color = Brand.TextSecondary,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        // 光线标签
        if (record.isLowLight) {
            Text(
                "🌙 暗光",
                color = Brand.TextMuted,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 3.dp)
            )
        }
    }
}

/** 构造位置描述行：优先 cityName，其次 placeName，再次经纬度 */
private fun buildLocationLine(record: ShootingRecordEntity): String? {
    val city = record.cityName
    val place = record.placeName
    val lat = record.latitude
    val lng = record.longitude
    return when {
        city != null && place != null -> "📍 $place, $city"
        city != null -> "📍 $city"
        place != null -> "📍 $place"
        lat != null && lng != null -> "📍 ${String.format("%.3f, %.3f", lat, lng)}"
        else -> null
    }
}

/** ---------- 日期格式工具（线程安全） ---------- */

private data class DaySection(val key: String, val display: String, val records: List<ShootingRecordEntity>)

private fun monthKey(ts: Long): String {
    return java.text.SimpleDateFormat("yyyy-MM", Locale.CHINA).apply { isLenient = true }.format(Date(ts))
}

private fun monthDisplay(month: String): String {
    return try {
        val fmt = java.text.SimpleDateFormat("yyyy-MM", Locale.CHINA)
        val d = fmt.parse(month)
        if (d != null) java.text.SimpleDateFormat("yyyy年M月", Locale.CHINA).format(d) else month
    } catch (_: Exception) {
        month
    }
}

private fun dayKey(ts: Long): String =
    java.text.SimpleDateFormat("yyyyMMdd", Locale.CHINA).format(Date(ts))

private fun dayDisplay(ts: Long): String =
    java.text.SimpleDateFormat("yyyy年M月d日", Locale.CHINA).format(Date(ts))

private fun sceneDisplayName(raw: String): String =
    try {
        SceneType.valueOf(raw).displayName
    } catch (e: Exception) {
        raw
    }

private fun filterEmoji(filter: PhotoFilter): String = when (filter) {
    PhotoFilter.ORIGINAL -> "🖼️"
    PhotoFilter.FILM -> "🎞️"
    PhotoFilter.BW -> "⬛"
    PhotoFilter.LIGHT -> "🌤️"
    PhotoFilter.NEON -> "✨"
}