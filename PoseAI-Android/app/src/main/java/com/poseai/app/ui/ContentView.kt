package com.poseai.app.ui

import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Mood
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.poseai.app.model.VlogClip
import com.poseai.app.model.VlogTemplate
import com.poseai.app.ui.theme.Accent
import com.poseai.app.ui.theme.BackgroundDark
import com.poseai.app.ui.theme.Error
import com.poseai.app.ui.theme.SurfaceGlass
import com.poseai.app.ui.theme.Success
import com.poseai.app.ui.theme.TextPrimary
import com.poseai.app.ui.theme.TextSecondary
import com.poseai.app.ui.theme.Warning
import com.poseai.app.viewmodel.ShootingViewModel

@Composable
fun ShootingScreen(
    viewModel: ShootingViewModel = viewModel()
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    var previewView by remember { mutableStateOf<PreviewView?>(null) }

    val poseScore by viewModel.poseScore.collectAsState()
    val currentPlan = viewModel.currentPlan
    val currentScene by viewModel.currentScene.collectAsState()
    val isReviewing by viewModel.isReviewingPhoto.collectAsState()
    val lastPhotoPath by viewModel.lastCapturedPhotoPath.collectAsState()
    val smileEnabled by viewModel.smileEnabled.collectAsState()
    val smileStrength by viewModel.smileStrength.collectAsState()
    val gridEnabled by viewModel.gridEnabled.collectAsState()
    val lowLightWarning by viewModel.isLowLightWarning.collectAsState()
    val heatWarning by viewModel.isHeatWarning.collectAsState()
    val batteryLow by viewModel.isBatteryLow.collectAsState()
    val isVlogRecording by viewModel.isVlogRecording.collectAsState()
    val isVlogMerging by viewModel.isVlogMerging.collectAsState()
    val vlogText by viewModel.displayVlogText.collectAsState()
    val activeClipIndex by viewModel.activeVlogClipIndex.collectAsState()
    val activeTemplate by viewModel.activeVlogTemplate.collectAsState()
    val isReviewingVlog by viewModel.isReviewingVlog.collectAsState()
    val vlogPath by viewModel.exportedVlogPath.collectAsState()
    val isAutoCapturing by viewModel.isAutoCapturing.collectAsState()

    var showSettings by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).also { pv ->
                    previewView = pv
                    pv.post {
                        viewModel.initCamera(lifecycleOwner, pv)
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        if (gridEnabled) {
            GridOverlay()
        }

        if (lowLightWarning) {
            LowLightOverlay()
        }

        if (smileEnabled) {
            SmileIndicator(strength = smileStrength)
        }

        currentPlan?.let {
            SilhouetteOverlay(
                posePoints = it.posePoints,
                score = poseScore,
                modifier = Modifier.fillMaxSize()
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            TopBar(
                sceneName = currentScene.displayName,
                planName = currentPlan?.poseName ?: "",
                planIndex = (viewModel.currentPlanIndex.collectAsState().value + 1),
                totalPlans = currentScene.plans.size,
                onSettings = { showSettings = true }
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (heatWarning) {
                WarningBanner(text = "设备过热，已降频优化", color = Warning)
            } else if (batteryLow) {
                WarningBanner(text = "电量低，已进入省电模式", color = Error)
            }

            if (vlogText.isNotEmpty()) {
                VlogSubtitle(text = vlogText)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.Bottom
        ) {
            PoseScoreBar(score = poseScore)

            Spacer(modifier = Modifier.height(16.dp))

            PoseDescription(
                description = currentPlan?.poseDescription ?: "",
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            BottomControls(
                onPrevious = { viewModel.previousPlan() },
                onCapture = { viewModel.takePhoto() },
                onNext = { viewModel.nextPlan() },
                onSwitch = {
                    previewView?.let { pv ->
                        viewModel.switchCamera(lifecycleOwner, pv)
                    }
                },
                onToggleAuto = { viewModel.toggleAutoCapture() },
                onSmile = { viewModel.toggleSmile(!smileEnabled) },
                onGrid = { viewModel.toggleGrid(!gridEnabled) },
                onStartVlog = {
                    // 使用默认 Vlog 模板启动录制
                    val defaultTemplate = VlogTemplate(
                        name = "快速 Vlog",
                        clips = listOf(
                            VlogClip("看镜头微笑", "第1幕", 3f),
                            VlogClip("转个圈展示全身", "第2幕", 3f),
                            VlogClip("挥手告别", "第3幕", 2f)
                        )
                    )
                    viewModel.startVlog(defaultTemplate)
                },
                onStopVlog = { viewModel.stopVlog() },
                isVlogRecording = isVlogRecording,
                isVlogMerging = isVlogMerging,
                isAutoCapturing = isAutoCapturing,
                smileEnabled = smileEnabled,
                gridEnabled = gridEnabled
            )
        }

        if (isVlogRecording || isVlogMerging) {
            VlogStatusIndicator(
                isRecording = isVlogRecording,
                isMerging = isVlogMerging,
                currentClip = activeClipIndex + 1,
                totalClips = activeTemplate?.clips?.size ?: 0,
                onStop = { viewModel.stopVlog() }
            )
        }

        if (isReviewing && lastPhotoPath != null) {
            PhotoReviewDialog(
                photoPath = lastPhotoPath!!,
                onDismiss = { viewModel.closePhotoReview() }
            )
        }

        if (isReviewingVlog && vlogPath != null) {
            VlogReviewDialog(
                videoPath = vlogPath!!,
                onDismiss = { viewModel.closeVlogReview() }
            )
        }

        if (showSettings) {
            SettingsDialog(
                onDismiss = { showSettings = false },
                viewModel = viewModel
            )
        }
    }
}

@Composable
fun TopBar(
    sceneName: String,
    planName: String,
    planIndex: Int,
    totalPlans: Int,
    onSettings: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceGlass, RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = sceneName,
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "$planName  $planIndex/$totalPlans",
                color = TextSecondary,
                fontSize = 13.sp
            )
        }
        IconButton(onClick = onSettings) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "设置",
                tint = TextPrimary,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
fun WarningBanner(text: String, color: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(color.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = color,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun GridOverlay() {
    Box(modifier = Modifier.fillMaxSize()) {
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            drawLine(
                color = Color.White.copy(alpha = 0.3f),
                start = androidx.compose.ui.geometry.Offset(w / 3f, 0f),
                end = androidx.compose.ui.geometry.Offset(w / 3f, h),
                strokeWidth = 1.dp.toPx()
            )
            drawLine(
                color = Color.White.copy(alpha = 0.3f),
                start = androidx.compose.ui.geometry.Offset(2 * w / 3f, 0f),
                end = androidx.compose.ui.geometry.Offset(2 * w / 3f, h),
                strokeWidth = 1.dp.toPx()
            )
            drawLine(
                color = Color.White.copy(alpha = 0.3f),
                start = androidx.compose.ui.geometry.Offset(0f, h / 3f),
                end = androidx.compose.ui.geometry.Offset(w, h / 3f),
                strokeWidth = 1.dp.toPx()
            )
            drawLine(
                color = Color.White.copy(alpha = 0.3f),
                start = androidx.compose.ui.geometry.Offset(0f, 2 * h / 3f),
                end = androidx.compose.ui.geometry.Offset(w, 2 * h / 3f),
                strokeWidth = 1.dp.toPx()
            )
        }
    }
}

@Composable
fun LowLightOverlay() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFFF0E0).copy(alpha = 0.08f))
    )
}

@Composable
fun SmileIndicator(strength: Float) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .background(
                    Accent.copy(alpha = strength * 0.3f),
                    CircleShape
                )
                .padding(4.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Mood,
                contentDescription = "微笑",
                tint = if (strength > 0.5f) Accent else TextSecondary,
                modifier = Modifier.size(40.dp)
            )
        }
    }
}

@Composable
fun PoseScoreBar(score: Float) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "姿势评分",
            color = TextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.width(70.dp)
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(8.dp)
                .background(SurfaceGlass, CircleShape)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction = (score / 100f).coerceIn(0f, 1f))
                    .height(8.dp)
                    .background(
                        if (score >= 80f) Success else if (score >= 60f) Warning else Error,
                        CircleShape
                    )
            )
        }
        Text(
            text = "${score.toInt()}",
            color = TextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(40.dp),
        )
    }
}

@Composable
fun PoseDescription(description: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .padding(horizontal = 16.dp)
            .background(SurfaceGlass, RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = description,
            color = TextPrimary,
            fontSize = 14.sp,
            lineHeight = 20.sp
        )
    }
}

@Composable
fun BottomControls(
    onPrevious: () -> Unit,
    onCapture: () -> Unit,
    onNext: () -> Unit,
    onSwitch: () -> Unit,
    onToggleAuto: () -> Unit,
    onSmile: () -> Unit,
    onGrid: () -> Unit,
    onStartVlog: () -> Unit,
    onStopVlog: () -> Unit,
    isVlogRecording: Boolean,
    isVlogMerging: Boolean,
    isAutoCapturing: Boolean,
    smileEnabled: Boolean,
    gridEnabled: Boolean
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onSmile) {
                Icon(
                    imageVector = Icons.Default.Mood,
                    contentDescription = "微笑快门",
                    tint = if (smileEnabled) Accent else TextSecondary,
                    modifier = Modifier.size(28.dp)
                )
            }
            IconButton(onClick = onGrid) {
                Icon(
                    imageVector = Icons.Default.GridOn,
                    contentDescription = "网格",
                    tint = if (gridEnabled) Accent else TextSecondary,
                    modifier = Modifier.size(28.dp)
                )
            }
            IconButton(onClick = onToggleAuto) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = "自动抓拍",
                    tint = if (isAutoCapturing) Accent else TextSecondary,
                    modifier = Modifier.size(28.dp)
                )
            }
            IconButton(onClick = onSwitch) {
                Icon(
                    imageVector = Icons.Default.Cameraswitch,
                    contentDescription = "切换摄像头",
                    tint = TextPrimary,
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onPrevious,
                modifier = Modifier.size(56.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "上一个姿势",
                    tint = TextPrimary,
                    modifier = Modifier.size(32.dp)
                )
            }

            if (isVlogRecording || isVlogMerging) {
                IconButton(
                    onClick = onStopVlog,
                    modifier = Modifier
                        .size(88.dp)
                        .background(Error, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = "停止录制",
                        tint = Color.White,
                        modifier = Modifier.size(40.dp)
                    )
                }
            } else {
                IconButton(
                    onClick = onCapture,
                    modifier = Modifier
                        .size(88.dp)
                        .background(Accent, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "拍照",
                        tint = Color.White,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }

            IconButton(
                onClick = onNext,
                modifier = Modifier.size(56.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "下一个姿势",
                    tint = TextPrimary,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}

@Composable
fun VlogStatusIndicator(
    isRecording: Boolean,
    isMerging: Boolean,
    currentClip: Int,
    totalClips: Int,
    onStop: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        contentAlignment = Alignment.TopEnd
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .background(SurfaceGlass, RoundedCornerShape(20.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            if (isRecording) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(Error, CircleShape)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "录制中 $currentClip/$totalClips",
                    color = TextPrimary,
                    fontSize = 13.sp
                )
            } else if (isMerging) {
                Text(
                    text = "合成中...",
                    color = TextPrimary,
                    fontSize = 13.sp
                )
            }
        }
    }
}

@Composable
fun VlogSubtitle(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                .padding(horizontal = 20.dp, vertical = 12.dp)
        )
    }
}

@Composable
fun SilhouetteOverlay(
    posePoints: Map<String, android.graphics.PointF>,
    score: Float,
    modifier: Modifier = Modifier
) {
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val color = if (score >= 80f) Success.toArgb() else Accent.toArgb()

        posePoints.forEach { (_, point) ->
            val cx = point.x * w
            val cy = point.y * h
            drawCircle(
                color = androidx.compose.ui.graphics.Color(color),
                radius = 8.dp.toPx(),
                center = androidx.compose.ui.geometry.Offset(cx, cy),
                alpha = 0.6f
            )
        }

        val connections = listOf(
            "leftShoulder" to "rightShoulder",
            "leftShoulder" to "leftElbow",
            "leftElbow" to "leftWrist",
            "rightShoulder" to "rightElbow",
            "rightElbow" to "rightWrist",
            "leftShoulder" to "leftHip",
            "rightShoulder" to "rightHip",
            "leftHip" to "rightHip",
            "leftHip" to "leftKnee",
            "leftKnee" to "leftAnkle",
            "rightHip" to "rightKnee",
            "rightKnee" to "rightAnkle"
        )

        connections.forEach { (a, b) ->
            val pa = posePoints[a]
            val pb = posePoints[b]
            if (pa != null && pb != null) {
                drawLine(
                    color = androidx.compose.ui.graphics.Color(color),
                    start = androidx.compose.ui.geometry.Offset(pa.x * w, pa.y * h),
                    end = androidx.compose.ui.geometry.Offset(pb.x * w, pb.y * h),
                    strokeWidth = 3.dp.toPx(),
                    alpha = 0.5f
                )
            }
        }
    }
}

@Composable
fun PhotoReviewDialog(photoPath: String, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            coil.compose.AsyncImage(
                model = photoPath,
                contentDescription = "照片预览",
                modifier = Modifier.fillMaxSize()
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .padding(16.dp)
                    .align(Alignment.TopEnd)
                    .background(SurfaceGlass, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "关闭",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

@Composable
fun VlogReviewDialog(videoPath: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var player by remember { mutableStateOf<androidx.media3.exoplayer.ExoPlayer?>(null) }

    DisposableEffect(videoPath) {
        val p = androidx.media3.exoplayer.ExoPlayer.Builder(context).build()
        val mediaItem = androidx.media3.common.MediaItem.fromUri(
            android.net.Uri.parse(videoPath)
        )
        p.setMediaItem(mediaItem)
        p.prepare()
        p.playWhenReady = true
        player = p
        onDispose {
            p.stop()
            p.release()
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            AndroidView(
                factory = { ctx ->
                    androidx.media3.ui.PlayerView(ctx).also { view ->
                        view.player = player
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
            IconButton(
                onClick = {
                    player?.stop()
                    player?.release()
                    onDismiss()
                },
                modifier = Modifier
                    .padding(16.dp)
                    .align(Alignment.TopEnd)
                    .background(SurfaceGlass, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "关闭",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

@Composable
fun SettingsDialog(
    onDismiss: () -> Unit,
    viewModel: ShootingViewModel
) {
    val smileEnabled by viewModel.smileEnabled.collectAsState()
    val watermarkEnabled by viewModel.watermarkEnabled.collectAsState()
    val gridEnabled by viewModel.gridEnabled.collectAsState()
    val lowLightEnabled by viewModel.lowLightMode.collectAsState()

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(BackgroundDark, RoundedCornerShape(20.dp))
                .padding(20.dp)
        ) {
            Text(
                text = "设置",
                color = TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))
            SettingToggle(
                title = "微笑快门",
                description = "检测到微笑时自动拍照",
                checked = smileEnabled,
                onCheckedChange = { viewModel.toggleSmile(it) }
            )
            SettingToggle(
                title = "网格线",
                description = "显示三分构图网格",
                checked = gridEnabled,
                onCheckedChange = { viewModel.toggleGrid(it) }
            )
            SettingToggle(
                title = "暗光优化",
                description = "自动提亮降噪",
                checked = lowLightEnabled,
                onCheckedChange = { viewModel.toggleLowLight(it) }
            )
            SettingToggle(
                title = "Pro 水印",
                description = "照片右下角添加 PoseAI 标识",
                checked = watermarkEnabled,
                onCheckedChange = { viewModel.toggleWatermark(it) }
            )
        }
    }
}

@Composable
fun SettingToggle(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(text = description, color = TextSecondary, fontSize = 12.sp)
        }
        androidx.compose.material3.Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = androidx.compose.material3.SwitchDefaults.colors(
                checkedThumbColor = Accent,
                checkedTrackColor = Accent.copy(alpha = 0.5f)
            )
        )
    }
}
