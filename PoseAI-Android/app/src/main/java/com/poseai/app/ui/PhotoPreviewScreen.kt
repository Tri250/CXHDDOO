package com.poseai.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.poseai.app.ui.components.CloseButton
import com.poseai.app.ui.components.LoadingIndicator
import com.poseai.app.ui.components.PrimaryButton
import com.poseai.app.ui.components.SecondaryButton

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PhotoPreviewScreen(
    photos: List<PhotoRecord>,
    initialIndex: Int,
    onBack: () -> Unit,
    onRetake: () -> Unit,
    onAddToCollection: () -> Unit,
    onShare: (String) -> Unit,
    onSetWallpaper: (String) -> Unit,
    onSetCover: (String) -> Unit
) {
    val pagerState = rememberPagerState(initialPage = initialIndex, pageCount = { photos.size })
    val currentPhoto = photos.getOrNull(pagerState.currentPage)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brand.Screen)
    ) {
        // 图片全屏展示
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val photo = photos[page]
            AsyncImage(
                model = photo.thumbnailPath ?: photo.filePath,
                contentDescription = "照片 $page",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        }

        // 顶部栏 - iOS 风格
        AppTopBar(
            title = "${pagerState.currentPage + 1} / ${photos.size}",
            onLeft = onBack,
            rightText = "",
            showRight = false
        )

        // 底部操作区 - iOS 风格带安全区
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        listOf(Color.Transparent, Brand.Screen.copy(alpha = 0.95f))
                    )
                )
                .padding(top = 24.dp)
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            currentPhoto?.let { photo ->
                // 照片信息
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "POSE ${photo.score} · ${photo.sceneName}",
                        color = Brand.TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                // 操作按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SecondaryButton(
                        text = "重拍",
                        onClick = onRetake,
                        modifier = Modifier.weight(1f)
                    )
                    PrimaryButton(
                        text = "加入收藏",
                        onClick = onAddToCollection,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SecondaryButton(
                        text = "设为壁纸",
                        onClick = { onSetWallpaper(photo.filePath) },
                        modifier = Modifier.weight(1f)
                    )
                    SecondaryButton(
                        text = "分享",
                        onClick = { onShare(photo.filePath) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}