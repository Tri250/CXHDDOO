package com.poseai.app.ui

import android.net.Uri
import android.widget.VideoView
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.poseai.app.design.Brand
import com.poseai.app.util.saveVideoToGallery
import com.poseai.app.util.shareVideo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 视频预览页——复刻 iOS VideoPreviewView。
 * 全部功能免费：黑色全屏循环预览播放 + 分享 + 下发相册（无水印/无付费墙）。
 */
@Composable
fun VideoPreviewScreen(
    videoFile: java.io.File,
    onSave: () -> Unit,
    onRetake: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var videoView by remember { mutableStateOf<VideoView?>(null) }
    var saving by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }
    var filterOn by remember { mutableStateOf(false) }

    // 播放器：循环播放
    LaunchedEffect(videoFile) {
        val vv = videoView ?: return@LaunchedEffect
        vv.setVideoURI(Uri.fromFile(videoFile))
        vv.setOnPreparedListener { vv.start() }
        vv.setOnCompletionListener {
            vv.seekTo(0)
            vv.start()
        }
        vv.start()
    }

    // 离开组合时释放播放器
    DisposableEffect(Unit) {
        onDispose {
            videoView?.let { vv ->
                vv.setOnPreparedListener(null)
                vv.setOnCompletionListener(null)
                vv.stopPlayback()
            }
            videoView = null
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // 视频画面
        AndroidView(
            modifier = Modifier.matchParentSize(),
            factory = { ctx ->
                VideoView(ctx).also { vv -> videoView = vv }
            }
        )

        // 顶部关闭
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            contentAlignment = Alignment.TopEnd
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(Brand.Surface.copy(alpha = 0.6f), CircleShape)
                    .border(1.dp, Brand.Hairline, CircleShape)
                    .clickable { onRetake() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Close, contentDescription = "关闭", tint = Color.White)
            }
        }

        // 底部控制区
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.4f))
                .padding(horizontal = 28.dp)
                .padding(bottom = 44.dp, top = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 调色（仅视觉开关，滤镜导出非本页职责）+ 分享
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .border(
                            1.dp,
                            if (filterOn) Brand.Accent.copy(alpha = 0.6f) else Brand.Hairline,
                            CircleShape
                        )
                        .background(if (filterOn) Brand.Accent.copy(alpha = 0.18f) else Brand.Surface.copy(alpha = 0.7f), CircleShape)
                        .clickable { filterOn = !filterOn }
                        .padding(horizontal = 16.dp, vertical = 9.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "调色",
                        color = if (filterOn) Brand.Accent else Brand.TextSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                Box(
                    modifier = Modifier
                        .border(1.dp, Brand.Hairline, CircleShape)
                        .background(Brand.Surface.copy(alpha = 0.7f), CircleShape)
                        .clickable { shareVideo(context, videoFile) }
                        .padding(horizontal = 16.dp, vertical = 9.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("分享", color = Brand.TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
            }

            Spacer(Modifier.height(22.dp))

            // 主操作行
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                // 重拍
                Box(
                    modifier = Modifier
                        .width(120.dp)
                        .height(50.dp)
                        .border(1.dp, Color.White.copy(alpha = 0.85f), CircleShape)
                        .clickable { onRetake() },
                    contentAlignment = Alignment.Center
                ) {
                    Text("重拍", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }

                // 下发相册
                Box(
                    modifier = Modifier
                        .height(50.dp)
                        .width(190.dp)
                        .background(Brand.Accent, CircleShape)
                        .clickable(enabled = !saving && !saved) {
                            scope.launch {
                                saving = true
                                val ok = withContext(Dispatchers.IO) { saveVideoToGallery(context, videoFile) }
                                saving = false
                                if (ok) {
                                    saved = true
                                    hapticPulse(context)
                                    delay(1000)
                                    onSave()
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        saving -> CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        saved -> Text("已保存", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        else -> Text("下发相册", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

/** 保存成功触觉反馈 */
private fun hapticPulse(context: android.content.Context) {
    val vibrator = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
        (context.getSystemService(android.content.Context.VIBRATOR_MANAGER_SERVICE)
            as? android.os.VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? android.os.Vibrator
    }
    vibrator?.let {
        if (it.hasVibrator()) {
            it.vibrate(android.os.VibrationEffect.createOneShot(30, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }
}