package com.poseai.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.poseai.app.design.Brand
import com.poseai.app.ui.components.AppTopBar
import com.poseai.app.ui.components.PrimaryButton
import com.poseai.app.ui.components.SecondaryButton

@Composable
fun VideoPreviewScreen(
    videoPath: String,
    isPlaying: Boolean,
    playbackProgress: Float,
    playbackPosition: Long,
    duration: Long,
    onBack: () -> Unit,
    onPlayPause: () -> Unit,
    onSeek: (Float) -> Unit,
    onShare: () -> Unit,
    onSaveToGallery: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brand.Screen)
    ) {
        // 视频播放器视图
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            // 使用 AndroidView 播放视频
            VideoPlayerView(
                videoPath = videoPath,
                isPlaying = isPlaying,
                modifier = Modifier.fillMaxSize()
            )
        }

        // iOS 风格顶部栏
        AppTopBar(
            title = "视频预览",
            onLeft = onBack
        )

        // 底部控制区 - iOS 风格
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Brand.Screen.copy(alpha = 0.95f))
                    )
                )
                .padding(top = 24.dp)
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 播放进度条
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Brand.SurfaceHigh)
                    .clickable { /* 点击跳转到对应位置 */ }
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(playbackProgress)
                        .height(6.dp)
                        .background(Brand.Accent)
                )
            }

            // 时间显示
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatDuration(playbackPosition),
                    color = Brand.TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = formatDuration(duration),
                    color = Brand.TextTertiary,
                    fontSize = 11.sp
                )
            }

            // 控制按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 播放/暂停
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(Brand.Accent, CircleShape)
                        .clickable { onPlayPause() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isPlaying) "⏸" else "▶",
                        color = Color.White,
                        fontSize = 24.sp
                    )
                }
            }

            // 操作按钮行
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SecondaryButton(
                    text = "保存到相册",
                    onClick = onSaveToGallery,
                    modifier = Modifier.weight(1f)
                )
                PrimaryButton(
                    text = "分享",
                    onClick = onShare,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun VideoPlayerView(
    videoPath: String,
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    // 使用 ExoPlayer 或 MediaPlayer 播放视频
    Box(
        modifier = modifier.background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        // 显示视频文件的占位信息
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("🎬", fontSize = 48.sp)
            Text(
                text = videoPath.substringAfterLast('/'),
                color = Brand.TextTertiary,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 12.dp)
            )
        }
    }
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}