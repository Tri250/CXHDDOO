package com.poseai.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.poseai.app.design.Brand
import com.poseai.app.model.PhotoRecord
import com.poseai.app.ui.components.AppTopBar
import com.poseai.app.ui.components.EmptyState
import com.poseai.app.ui.components.LoadingIndicator

private enum class HistoryFilter { ALL, FAVORITE, BY_SCENE }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HistoryGalleryScreen(
    photos: List<PhotoRecord>,
    isLoading: Boolean,
    onBack: () -> Unit,
    onPhotoClick: (Int) -> Unit,
    onFavorite: (Int) -> Unit,
    onDelete: (Int) -> Unit,
    scenes: List<String>
) {
    var filter by remember { mutableStateOf(HistoryFilter.ALL) }
    var selectedScene by remember { mutableStateOf<String?>(null) }

    val filteredPhotos = when (filter) {
        HistoryFilter.ALL -> photos
        HistoryFilter.FAVORITE -> photos.filter { it.isFavorite }
        HistoryFilter.BY_SCENE -> photos.filter { it.sceneName == selectedScene }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brand.Screen)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // iOS 风格顶部栏
            AppTopBar(
                title = "我的相册",
                onLeft = onBack
            )

            // 筛选器
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FilterChip(
                    text = "全部",
                    isSelected = filter == HistoryFilter.ALL,
                    onClick = { filter = HistoryFilter.ALL }
                )
                FilterChip(
                    text = "收藏",
                    isSelected = filter == HistoryFilter.FAVORITE,
                    onClick = { filter = HistoryFilter.FAVORITE }
                )
                FilterChip(
                    text = "按场景",
                    isSelected = filter == HistoryFilter.BY_SCENE,
                    onClick = { filter = HistoryFilter.BY_SCENE }
                )
            }

            // 场景选择
            if (filter == HistoryFilter.BY_SCENE && scenes.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    scenes.forEach { scene ->
                        SceneChip(
                            text = scene,
                            isSelected = selectedScene == scene,
                            onClick = { selectedScene = scene }
                        )
                    }
                }
            }

            // 网格
            if (isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    LoadingIndicator()
                }
            } else if (filteredPhotos.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyState(
                        emoji = "📷",
                        title = when (filter) {
                            HistoryFilter.ALL -> "还没有照片"
                            HistoryFilter.FAVORITE -> "暂无收藏"
                            HistoryFilter.BY_SCENE -> "该场景下没有照片"
                        },
                        subtitle = "拍摄更多 PoseAI 作品吧"
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier
                        .fillMaxSize()
                        .navigationBarsPadding()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(filteredPhotos) { photo ->
                        GridPhotoItem(
                            photo = photo,
                            onClick = { onPhotoClick(photos.indexOf(photo)) },
                            onFavorite = { onFavorite(photos.indexOf(photo)) },
                            onDelete = { onDelete(photos.indexOf(photo)) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterChip(text: String, isSelected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(Brand.Radius.Md))
            .background(
                if (isSelected) Brand.Accent.copy(alpha = 0.9f) else Brand.SurfaceHigh
            )
            .border(
                1.dp,
                if (isSelected) Brand.Accent.copy(alpha = 0.6f) else Brand.Hairline,
                RoundedCornerShape(Brand.Radius.Md)
            )
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (isSelected) Color.White else Brand.TextSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun SceneChip(text: String, isSelected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(Brand.Radius.Sm))
            .background(
                if (isSelected) Brand.SurfaceHigh else Color.Transparent
            )
            .border(
                1.dp,
                if (isSelected) Brand.Accent.copy(alpha = 0.4f) else Brand.Hairline,
                RoundedCornerShape(Brand.Radius.Sm)
            )
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = text,
            color = if (isSelected) Brand.AccentSoft else Brand.TextMuted,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun GridPhotoItem(
    photo: PhotoRecord,
    onClick: () -> Unit,
    onFavorite: () -> Unit,
    onDelete: () -> Unit
) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(Brand.SurfaceHigh)
            .clickable { onClick() }
    ) {
        AsyncImage(
            model = photo.thumbnailPath ?: photo.filePath,
            contentDescription = "Pose ${photo.id}",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        // 收藏标记
        if (photo.isFavorite) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(24.dp)
                    .background(Brand.Coral, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text("♥", color = Color.White, fontSize = 12.sp)
            }
        }

        // 分数标签
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(6.dp)
                .background(Brand.OverlayMedium, RoundedCornerShape(6.dp))
                .padding(horizontal = 6.dp, vertical = 3.dp)
        ) {
            Text(
                text = "${photo.score}",
                color = Brand.Gold,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
