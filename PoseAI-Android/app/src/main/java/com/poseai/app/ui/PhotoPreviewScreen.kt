package com.poseai.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.poseai.app.design.Brand
import com.poseai.app.filter.PhotoFilterEngine
import com.poseai.app.model.CropRatio
import com.poseai.app.model.PhotoFilter
import com.poseai.app.util.applyCrop
import com.poseai.app.util.shareBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 全屏照片预览/编辑页 —— Android 版 PhotoPreviewView。
 * 支持滤镜、画幅裁切、多图切换、保存与分享。
 */
@Composable
fun PhotoPreviewScreen(
    images: List<android.graphics.Bitmap>,
    onSave: (android.graphics.Bitmap) -> Unit,
    onRetake: () -> Unit
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current

    // 状态：当前选中图、滤镜、画幅、两面板开关、滤镜缩略图缓存
    var selectedIndex by remember { mutableStateOf(0) }
    var selectedFilter by remember { mutableStateOf(PhotoFilter.ORIGINAL) }
    var selectedCropRatio by remember { mutableStateOf(CropRatio.ORIGINAL) }
    var showFilters by remember { mutableStateOf(false) }
    var showCropRatios by remember { mutableStateOf(false) }
    val filterThumbnails = remember { mutableStateMapOf<PhotoFilter, android.graphics.Bitmap>() }

    if (images.isEmpty()) {
        onRetake()
        return
    }
    val currentImage = images.getOrElse(selectedIndex) { images.first() }

    // 展示图 = 滤镜 + 裁切，非阻塞计算
    val displayImage by produceState<android.graphics.Bitmap?>(null, currentImage, selectedFilter, selectedCropRatio) {
        value = withContext(Dispatchers.Default) {
            PhotoFilterEngine.apply(currentImage, selectedFilter).applyCrop(selectedCropRatio)
        }
    }

    // 切换图片时重置滤镜并重建滤镜缩略图
    val currentFilterKey = currentImage
    LaunchedEffect(currentFilterKey) {
        selectedFilter = PhotoFilter.ORIGINAL
        filterThumbnails.clear()
    }

    // 共享/保存用的最终图（滤镜 + 裁切）
    val finalImage by produceState<android.graphics.Bitmap?>(null, currentImage, selectedFilter, selectedCropRatio) {
        value = withContext(Dispatchers.Default) {
            PhotoFilterEngine.apply(currentImage, selectedFilter).applyCrop(selectedCropRatio)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // 当前图全屏显示
        displayImage?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.align(Alignment.Center).fillMaxSize().padding(bottom = 210.dp),
                contentScale = ContentScale.Fit
            )
        }

        // 顶部渐变遮罩
        Box(
            Modifier
                .fillMaxWidth()
                .height(140.dp)
                .align(Alignment.TopCenter)
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent)))
        )

        // 底部渐变遮罩
        Box(
            Modifier
                .fillMaxWidth()
                .height(260.dp)
                .align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.72f))))
        )

        // 顶部返回
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 18.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Brand.Surface.copy(alpha = 0.85f))
                    .border(1.dp, Brand.Hairline, CircleShape)
                    .clickable { onRetake() },
                contentAlignment = Alignment.Center
            ) {
                Text("✕", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.weight(1f))
        }

        // 底部控制区
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 28.dp)
        ) {
            // 滤镜选择器
            if (showFilters) {
                FilterSelector(
                    currentImage = currentImage,
                    selectedFilter = selectedFilter,
                    thumbnails = filterThumbnails,
                    onSelect = { f ->
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        selectedFilter = f
                    }
                )
                Spacer(Modifier.height(12.dp))
            }

            // 画幅选择器
            if (showCropRatios) {
                CropSelector(
                    selected = selectedCropRatio,
                    onSelect = { selectedCropRatio = it }
                )
                Spacer(Modifier.height(12.dp))
            }

            // 多图缩略图条
            if (images.size > 1) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    itemsIndexed(images) { idx, img ->
                        Box(
                            modifier = Modifier
                                .width(50.dp)
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Brand.SurfaceHigh)
                                .border(
                                    2.dp,
                                    if (idx == selectedIndex) Brand.Gold else Brand.Hairline,
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable { selectedIndex = idx },
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                bitmap = img.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
            }

            // 调色/画幅切换按钮
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TogglePill(
                    label = "调色",
                    active = showFilters,
                    onClick = {
                        showFilters = !showFilters
                        if (showFilters) showCropRatios = false
                    }
                )
                Spacer(Modifier.width(14.dp))
                TogglePill(
                    label = "画幅",
                    active = showCropRatios,
                    onClick = {
                        showCropRatios = !showCropRatios
                        if (showCropRatios) showFilters = false
                    }
                )
            }
            Spacer(Modifier.height(22.dp))

            // 主操作栏：重拍 / 保存 / 分享
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 重拍
                Box(
                    modifier = Modifier
                        .size(58.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Brand.SurfaceHigh)
                        .border(1.dp, Brand.Hairline, RoundedCornerShape(14.dp))
                        .clickable { onRetake() },
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("↺", color = Color.White, fontSize = 20.sp)
                        Text("重拍", color = Brand.TextSecondary, fontSize = 10.sp)
                    }
                }

                // 保存（绿色圆形居中）
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(Brand.Success)
                        .border(3.dp, Color.White.copy(alpha = 0.25f), CircleShape)
                        .clickable {
                            finalImage?.let { onSave(it) }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text("保存", color = Color.Black, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }

                // 分享
                Box(
                    modifier = Modifier
                        .size(58.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Brand.SurfaceHigh)
                        .border(1.dp, Brand.Hairline, RoundedCornerShape(14.dp))
                        .clickable {
                            finalImage?.let { shareBitmap(context, it, "分享到") }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("↗", color = Color.White, fontSize = 20.sp)
                        Text("分享", color = Brand.TextSecondary, fontSize = 10.sp)
                    }
                }
            }
        }
    }
}

/** 滤镜选择行：每个 60dp 缩略图 + 名称。 */
@Composable
private fun FilterSelector(
    currentImage: android.graphics.Bitmap,
    selectedFilter: PhotoFilter,
    thumbnails: MutableMap<PhotoFilter, android.graphics.Bitmap>,
    onSelect: (PhotoFilter) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        items(PhotoFilter.entries) { filter ->
            val thumb by produceState(thumbnails[filter], currentImage, filter) {
                value = withContext(Dispatchers.Default) {
                    PhotoFilterEngine.thumbnail(currentImage, filter).also { thumbnails[filter] = it }
                }
            }
            Column(
                modifier = Modifier
                    .width(60.dp)
                    .clickable { onSelect(filter) },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Brand.SurfaceHigh)
                        .border(
                            2.dp,
                            if (filter == selectedFilter) Brand.Gold else Brand.Hairline,
                            RoundedCornerShape(10.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    thumb?.let {
                        Image(
                            bitmap = it.asImageBitmap(),
                            contentDescription = filter.displayName,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } ?: Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("…", color = Brand.TextMuted, fontSize = 14.sp)
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    filter.displayName,
                    color = if (filter == selectedFilter) Brand.Gold else Brand.TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/** 画幅选择行：每个条目显示图标 + 名称。 */
@Composable
private fun CropSelector(
    selected: CropRatio,
    onSelect: (CropRatio) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        items(CropRatio.entries) { ratio ->
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (ratio == selected) Brand.Gold.copy(alpha = 0.18f) else Brand.SurfaceHigh,
                        RoundedCornerShape(12.dp)
                    )
                    .border(
                        1.5f.dp,
                        if (ratio == selected) Brand.Gold else Brand.Hairline,
                        RoundedCornerShape(12.dp)
                    )
                    .clickable { onSelect(ratio) }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    ratio.icon.ifEmpty { "▭" },
                    color = if (ratio == selected) Brand.Gold else Brand.TextSecondary,
                    fontSize = 12.sp
                )
                Text(
                    ratio.displayName,
                    color = if (ratio == selected) Brand.Gold else Brand.TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

/** 调色/画幅 丸形开关按钮 */
@Composable
private fun TogglePill(label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(
                if (active) Brand.Gold.copy(alpha = 0.22f) else Brand.SurfaceHigh.copy(alpha = 0.9f),
                CircleShape
            )
            .border(
                1.5f.dp,
                if (active) Brand.Gold else Brand.Hairline,
                CircleShape
            )
            .clickable { onClick() }
            .padding(horizontal = 22.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (active) Brand.Gold else Brand.TextSecondary,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}