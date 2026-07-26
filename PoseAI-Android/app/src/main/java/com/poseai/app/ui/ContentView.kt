package com.poseai.app.ui

import android.graphics.PointF
import androidx.camera.view.PreviewView
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.poseai.app.model.SceneType
import com.poseai.app.model.ShootingPlan
import com.poseai.app.ui.theme.*
import com.poseai.app.util.PhotoFilterEngine
import com.poseai.app.viewmodel.ShootingViewModel
import kotlinx.coroutines.launch
import kotlin.math.*

// ═══════════════════════════════════════════════════════════════
// 主拍摄界面 — 对齐 iOS ContentView 的 UI/UX
// ═══════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShootingScreen(
    viewModel: ShootingViewModel = viewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = context as? LifecycleOwner ?: error("Context is not a LifecycleOwner")
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
    val screenFillLightEnabled by viewModel.screenFillLightEnabled.collectAsState()
    val screenFillLightIntensity by viewModel.screenFillLightIntensity.collectAsState()
    val detectedPoseLines by viewModel.detectedPoseLines.collectAsState()
    val detectedPosePoints by viewModel.detectedPosePoints.collectAsState()
    val currentFilter by viewModel.currentFilter.collectAsState()
    val showSceneSelector by viewModel.showSceneSelector.collectAsState()
    val showFilterSelector by viewModel.showFilterSelector.collectAsState()
    val showVlogTemplateSelector by viewModel.showVlogTemplateSelector.collectAsState()
    val watermarkEnabled by viewModel.watermarkEnabled.collectAsState()
    val lowLightMode by viewModel.lowLightMode.collectAsState()
    val useSecondaryPose by viewModel.useSecondaryPose.collectAsState()
    val currentSequenceIndex by viewModel.currentSequenceIndex.collectAsState()
    val currentAngleIndex by viewModel.currentAngleIndex.collectAsState()
    val currentSequenceShot = viewModel.getCurrentSequenceShot()
    val currentAngle = viewModel.getCurrentAngle()
    val currentPlanIndex by viewModel.currentPlanIndex.collectAsState()

    var showSettings by remember { mutableStateOf(false) }
    var showExposurePanel by remember { mutableStateOf(false) }
    var showGuide by remember { mutableStateOf(false) }
    val exposureValue by viewModel.exposureValue.collectAsState()

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    val statusBarPadding = WindowInsets.statusBars.asPaddingValues()
    val navBarPadding = WindowInsets.navigationBars.asPaddingValues()

    // 场景扫描动画状态
    val scanPulse = remember { Animatable(0f) }
    val scanRotation = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        launch {
            scanPulse.animateTo(
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1800, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                )
            )
        }
        launch {
            scanRotation.animateTo(
                targetValue = 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1600, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                )
            )
        }
    }

    // 快门呼吸动画
    val breathingScale = remember { Animatable(1f) }
    LaunchedEffect(poseScore >= 80f) {
        if (poseScore >= 80f) {
            breathingScale.animateTo(
                targetValue = 1.5f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1100, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                )
            )
        } else {
            breathingScale.snapTo(1f)
        }
    }

    val isSceneReady = currentScene != SceneType.UNKNOWN && currentPlan != null
    val isAligned = poseScore >= 80f

    Box(modifier = Modifier.fillMaxSize()) {
        // ── 相机预览层 ──
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

        // ── 点击切换沉浸模式（简化：切换 UI 显示/隐藏）──
        // Android 暂不实现沉浸模式，保留点击对焦功能

        // ── 构图辅助线 ──
        if (isSceneReady && gridEnabled) {
            CompositionGuideLines(currentPlan?.composition)
        }

        // ── 场景扫描动画 ──
        if (!isSceneReady) {
            SceneScanningOverlay(
                scanPulse = scanPulse.value,
                scanRotation = scanRotation.value
            )
        }

        // ── 剪影引导叠加层 ──
        if (isSceneReady && currentPlan != null) {
            val targetPoints = if (useSecondaryPose && currentPlan.secondaryPosePoints.isNotEmpty()) {
                currentPlan.secondaryPosePoints
            } else {
                currentPlan.posePoints
            }
            SilhouetteGuideOverlay(
                posePoints = targetPoints,
                isAligned = isAligned,
                detectedPosePoints = detectedPosePoints,
                plan = currentPlan,
                modifier = Modifier.fillMaxSize()
            )
        }

        // ── 检测骨骼叠加 ──
        if (detectedPoseLines.isNotEmpty() || detectedPosePoints.isNotEmpty()) {
            DetectedSkeletonOverlay(
                lines = detectedPoseLines,
                points = detectedPosePoints,
                score = poseScore,
                modifier = Modifier.fillMaxSize()
            )
        }

        // ── 暗光柔边补光带 ──
        if (lowLightWarning && lowLightMode) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Color(0xFFF5F0E0).copy(alpha = 0.08f),
                                Color.Transparent
                            )
                        )
                    )
            )
        }

        // ── 屏幕补光 ──
        if (screenFillLightEnabled) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = screenFillLightIntensity * 0.25f))
            )
        }

        // ── 微笑指示器 ──
        if (smileEnabled) {
            SmileIndicator(strength = smileStrength)
        }

        // ── 顶部信息栏 ──
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = statusBarPadding.calculateTopPadding() + 8.dp)
                .padding(horizontal = 18.dp)
        ) {
            TopBar(
                scene = currentScene,
                plan = currentPlan,
                score = poseScore,
                isSceneReady = isSceneReady,
                isAligned = isAligned,
                currentPlanIndex = currentPlanIndex,
                currentSequenceIndex = currentSequenceIndex,
                currentAngleIndex = currentAngleIndex,
                isVlogRecording = isVlogRecording,
                activeVlogClipIndex = activeClipIndex,
                onHelp = { showGuide = true },
                onSceneClick = { viewModel.toggleSceneSelector() }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 过热/低电量警告
            if (heatWarning) {
                WarningBanner(text = "设备过热，已降频优化", color = Warning)
            } else if (batteryLow) {
                WarningBanner(text = "电量低，已进入省电模式", color = Error)
            }

            // Vlog 字幕
            if (vlogText.isNotEmpty()) {
                VlogSubtitle(text = vlogText)
            }
        }

        // ── 暗光提示 Banner ──
        if (lowLightWarning && isSceneReady && lowLightMode) {
            LowLightBanner(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = statusBarPadding.calculateTopPadding() + 110.dp)
            )
        }

        // ── 曝光面板 ──
        if (showExposurePanel) {
            ExposurePanel(
                exposureValue = exposureValue,
                minExposure = -10,
                maxExposure = 10,
                onDecrease = { viewModel.decreaseExposure() },
                onIncrease = { viewModel.increaseExposure() },
                onDismiss = { showExposurePanel = false },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = statusBarPadding.calculateTopPadding() + 100.dp, end = 16.dp)
            )
        }

        // ── 底部面板 ──
        BottomPanel(
            isSceneReady = isSceneReady,
            isAligned = isAligned,
            currentPlan = currentPlan,
            availablePlans = currentScene.plans,
            currentPlanIndex = currentPlanIndex,
            isVlogRecording = isVlogRecording,
            isVlogMerging = isVlogMerging,
            isAutoCapturing = isAutoCapturing,
            smileEnabled = smileEnabled,
            gridEnabled = gridEnabled,
            screenFillLightEnabled = screenFillLightEnabled,
            currentFilterName = currentFilter.displayName,
            recentRecords = viewModel.getCaptureHistory(),
            sequence = currentPlan?.sequence,
            currentSequenceIndex = currentSequenceIndex,
            currentSequenceShot = currentSequenceShot,
            multiAngles = currentPlan?.multiAngles,
            currentAngle = currentAngle,
            hasSecondaryPose = (currentPlan?.secondaryPosePoints?.isNotEmpty() == true),
            useSecondaryPose = useSecondaryPose,
            hasVlogScript = (currentPlan?.vlogScript != null),
            breathingScale = breathingScale.value,
            navBarPadding = navBarPadding,
            onPlanSelected = { viewModel.selectPlan(it) },
            onCapture = { viewModel.takePhoto() },
            onSwitchCamera = {
                previewView?.let { pv -> viewModel.switchCamera(lifecycleOwner, pv) }
            },
            onPreviousSequence = { viewModel.previousSequenceStep() },
            onNextSequence = { viewModel.nextSequenceStep() },
            onNextAngle = { viewModel.nextAngle() },
            onToggleSecondaryPose = { viewModel.toggleSecondaryPose() },
            onStartVlog = { viewModel.startVlogFromPlan() },
            onToggleAuto = { viewModel.toggleAutoCapture() },
            onToggleSmile = { viewModel.toggleSmile(!smileEnabled) },
            onToggleGrid = { viewModel.toggleGrid(!gridEnabled) },
            onToggleFilter = { viewModel.toggleFilterSelector() },
            onToggleExposure = { showExposurePanel = !showExposurePanel },
            onToggleScreenFillLight = { viewModel.toggleScreenFillLight() },
            onToggleVlog = { viewModel.toggleVlogTemplateSelector() },
            onStopVlog = { viewModel.stopVlog() },
            onOpenGallery = { /* TODO: 打开相册 */ },
            onToggleTimer = { /* TODO: 计时器 */ },
            modifier = Modifier.align(Alignment.BottomCenter)
        )

        // ── Vlog 状态指示器 ──
        if (isVlogRecording || isVlogMerging) {
            VlogStatusIndicator(
                isRecording = isVlogRecording,
                isMerging = isVlogMerging,
                currentClip = activeClipIndex + 1,
                totalClips = activeTemplate?.clips?.size ?: 0,
                onStop = { viewModel.stopVlog() },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = statusBarPadding.calculateTopPadding() + 8.dp, end = 16.dp)
            )
        }

        // ── 照片预览 ──
        if (isReviewing && lastPhotoPath != null) {
            PhotoReviewDialog(
                photoPath = lastPhotoPath!!,
                onDismiss = { viewModel.closePhotoReview() }
            )
        }

        // ── Vlog 预览 ──
        if (isReviewingVlog && vlogPath != null) {
            VlogReviewDialog(
                videoPath = vlogPath!!,
                onDismiss = { viewModel.closeVlogReview() }
            )
        }

        // ── 设置弹窗 ──
        if (showSettings) {
            SettingsDialog(
                onDismiss = { showSettings = false },
                viewModel = viewModel
            )
        }

        // ── 场景选择器 ──
        if (showSceneSelector) {
            SceneSelectorBottomSheetComposable(
                viewModel = viewModel,
                currentScene = currentScene,
                onDismiss = { viewModel.toggleSceneSelector() }
            ) { scene ->
                viewModel.setScene(scene)
                viewModel.toggleSceneSelector()
            }
        }

        // ── 滤镜选择器 ──
        if (showFilterSelector) {
            FilterSelectorBottomSheet(
                currentFilter = currentFilter,
                onFilterSelected = {
                    viewModel.setFilter(it)
                    viewModel.toggleFilterSelector()
                },
                onDismiss = { viewModel.toggleFilterSelector() }
            )
        }

        // ── Vlog 模板选择器 ──
        if (showVlogTemplateSelector) {
            VlogTemplateSelectorBottomSheet(
                templates = viewModel.getVlogTemplates(),
                onTemplateSelected = {
                    viewModel.startVlog(it)
                    viewModel.toggleVlogTemplateSelector()
                },
                onDismiss = { viewModel.toggleVlogTemplateSelector() }
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// 场景扫描动画叠加层
// ═══════════════════════════════════════════════════════════════

@Composable
fun SceneScanningOverlay(
    scanPulse: Float,
    scanRotation: Float
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(bottom = 200.dp)
        ) {
            // 扫描环
            Box(
                modifier = Modifier.size(190.dp),
                contentAlignment = Alignment.Center
            ) {
                // 最外圈脉冲
                val outerRadius = lerp(80.dp, 110.dp, scanPulse)
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val cx = size.width / 2
                    val cy = size.height / 2
                    drawCircle(
                        color = Accent,
                        radius = outerRadius.toPx(),
                        center = Offset(cx, cy),
                        style = Stroke(width = 1.5.dp.toPx())
                    )
                }

                // 第二圈
                val midRadius = lerp(70.dp, 95.dp, (scanPulse + 0.3f) % 1f)
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val cx = size.width / 2
                    val cy = size.height / 2
                    drawCircle(
                        color = Accent.copy(alpha = 0.2f),
                        radius = midRadius.toPx(),
                        center = Offset(cx, cy),
                        style = Stroke(width = 1.dp.toPx())
                    )
                }

                // 主框
                Box(
                    modifier = Modifier
                        .size(width = 140.dp, height = 190.dp)
                        .border(2.dp, Accent.copy(alpha = 0.8f), RoundedCornerShape(20.dp))
                ) {
                    // 四角修饰线
                    ScanCornerLines(
                        modifier = Modifier.fillMaxSize(),
                        color = Accent
                    )

                    // 旋转扫描弧 + 图标
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // 旋转扫描弧
                            Canvas(modifier = Modifier.size(44.dp)) {
                                val cx = size.width / 2
                                val cy = size.height / 2
                                drawArc(
                                    brush = Brush.sweepGradient(
                                        colors = listOf(Accent, Color.Transparent)
                                    ),
                                    startAngle = scanRotation,
                                    sweepAngle = 90f,
                                    useCenter = false,
                                    style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round),
                                    topLeft = Offset(0f, 0f),
                                    size = Size(size.width, size.height)
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "识别场景中…",
                                color = TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 提示文字
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "将镜头对准拍摄背景",
                    color = TextPrimary.copy(alpha = 0.85f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "咖啡馆 · 海边 · 森林",
                    color = Accent.copy(alpha = 0.7f),
                    fontSize = 12.sp,
                    letterSpacing = 2.sp
                )
            }
        }
    }
}

// ── 扫描框四角修饰线 ──
@Composable
fun ScanCornerLines(modifier: Modifier = Modifier, color: Color = Accent) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val len = 18.dp.toPx()
        val thick = 2.5.dp.toPx()

        val corners = listOf(
            Triple(Offset(0f, len), Offset(0f, 0f), Offset(len, 0f)),
            Triple(Offset(w - len, 0f), Offset(w, 0f), Offset(w, len)),
            Triple(Offset(0f, h - len), Offset(0f, h), Offset(len, h)),
            Triple(Offset(w - len, h), Offset(w, h), Offset(w, h - len))
        )

        corners.forEach { (a, b, c) ->
            drawLine(color, a, b, thick, cap = StrokeCap.Round)
            drawLine(color, b, c, thick, cap = StrokeCap.Round)
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// 顶部信息栏
// ═══════════════════════════════════════════════════════════════

@Composable
fun TopBar(
    scene: SceneType,
    plan: ShootingPlan?,
    score: Float,
    isSceneReady: Boolean,
    isAligned: Boolean,
    currentPlanIndex: Int,
    currentSequenceIndex: Int,
    currentAngleIndex: Int,
    isVlogRecording: Boolean,
    activeVlogClipIndex: Int,
    onHelp: () -> Unit,
    onSceneClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 左侧：场景 + 方案信息
        if (isSceneReady && plan != null) {
            Row(
                modifier = Modifier
                    .background(Surface, RoundedCornerShape(18.dp))
                    .border(1.dp, Border, RoundedCornerShape(18.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 场景图标
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(Surface, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = Accent,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = scene.displayName,
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                    // 根据当前模式显示不同信息
                    if (plan.vlogScript != null && isVlogRecording) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(
                                        if (isVlogRecording) Color.Red else Color.Gray,
                                        CircleShape
                                    )
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Vlog [分镜 ${activeVlogClipIndex + 1}/${plan.vlogScript!!.clips.size}]",
                                color = Danger,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    } else if (plan.sequence.isNotEmpty()) {
                        Text(
                            text = "[${currentSequenceIndex + 1}/${plan.sequence.size}] ${plan.sequence[currentSequenceIndex].title}",
                            color = Success,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    } else if (plan.multiAngles.isNotEmpty()) {
                        Text(
                            text = "[${currentAngleIndex + 1}/${plan.multiAngles.size}] ${plan.multiAngles[currentAngleIndex].title}",
                            color = Danger,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        Text(
                            text = plan.poseName,
                            color = TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // 右侧：分数环 + 帮助按钮
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (isSceneReady) {
                ScoreRing(score = score, isAligned = isAligned)
            }
            // 帮助按钮
            IconButton(
                onClick = onHelp,
                modifier = Modifier
                    .size(40.dp)
                    .background(Surface, CircleShape)
                    .border(1.dp, Border, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.QuestionMark,
                    contentDescription = "帮助",
                    tint = TextPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

// ── 分数环 ──
@Composable
fun ScoreRing(score: Float, isAligned: Boolean) {
    val scoreColor = if (isAligned) Success else {
        when {
            score >= 80f -> Success
            score >= 60f -> Warning
            else -> Error
        }
    }
    val animatedColor by animateColorAsState(
        targetValue = scoreColor,
        animationSpec = tween(200),
        label = "scoreColor"
    )

    Box(
        modifier = Modifier.size(54.dp),
        contentAlignment = Alignment.Center
    ) {
        // 外发光（对齐时）
        if (isAligned) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(
                    color = SuccessGlow,
                    radius = 27.dp.toPx(),
                    center = center,
                    style = Stroke(width = 10.dp.toPx())
                )
            }
        }

        // 底层轨道
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                color = Color.White.copy(alpha = 0.12f),
                radius = 23.dp.toPx(),
                center = center,
                style = Stroke(width = 3.5.dp.toPx())
            )
        }

        // 进度弧
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawArc(
                color = animatedColor,
                startAngle = -90f,
                sweepAngle = (score / 100f * 360f).coerceIn(0f, 360f),
                useCenter = false,
                topLeft = Offset(
                    center.x - 23.dp.toPx(),
                    center.y - 23.dp.toPx()
                ),
                size = Size(46.dp.toPx(), 46.dp.toPx()),
                style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round)
            )
        }

        // 分数文字
        Text(
            text = "${score.toInt()}",
            color = TextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Black
        )
    }
}

// ═══════════════════════════════════════════════════════════════
// 底部面板
// ═══════════════════════════════════════════════════════════════

@Composable
fun BottomPanel(
    isSceneReady: Boolean,
    isAligned: Boolean,
    currentPlan: ShootingPlan?,
    availablePlans: List<ShootingPlan>,
    currentPlanIndex: Int,
    isVlogRecording: Boolean,
    isVlogMerging: Boolean,
    isAutoCapturing: Boolean,
    smileEnabled: Boolean,
    gridEnabled: Boolean,
    screenFillLightEnabled: Boolean,
    currentFilterName: String,
    recentRecords: kotlinx.coroutines.flow.Flow<List<com.poseai.app.data.ShootingRecord>>,
    sequence: List<com.poseai.app.model.SequenceShot>?,
    currentSequenceIndex: Int,
    currentSequenceShot: com.poseai.app.model.SequenceShot?,
    multiAngles: List<com.poseai.app.model.MultiAngle>?,
    currentAngle: com.poseai.app.model.MultiAngle?,
    hasSecondaryPose: Boolean,
    useSecondaryPose: Boolean,
    hasVlogScript: Boolean,
    breathingScale: Float,
    navBarPadding: PaddingValues,
    onPlanSelected: (Int) -> Unit,
    onCapture: () -> Unit,
    onSwitchCamera: () -> Unit,
    onPreviousSequence: () -> Unit,
    onNextSequence: () -> Unit,
    onNextAngle: () -> Unit,
    onToggleSecondaryPose: () -> Unit,
    onStartVlog: () -> Unit,
    onToggleAuto: () -> Unit,
    onToggleSmile: () -> Unit,
    onToggleGrid: () -> Unit,
    onToggleFilter: () -> Unit,
    onToggleExposure: () -> Unit,
    onToggleScreenFillLight: () -> Unit,
    onToggleVlog: () -> Unit,
    onStopVlog: () -> Unit,
    onOpenGallery: () -> Unit,
    onToggleTimer: () -> Unit,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val isCompactHeight = configuration.screenHeightDp < 600

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color.Black.copy(alpha = 0.55f)
                    )
                )
            )
            .padding(bottom = navBarPadding.calculateBottomPadding() + 16.dp)
    ) {
        // ── 方案选择器 ──
        if (isSceneReady && availablePlans.isNotEmpty()) {
            PlanPicker(
                plans = availablePlans,
                currentPlanIndex = currentPlanIndex,
                onPlanSelected = onPlanSelected,
                modifier = Modifier.padding(top = 10.dp)
            )
        }

        // ── 扩展控制栏（序列/多角度/备选姿势/Vlog）──
        currentPlan?.let { plan ->
            if (plan.sequence.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                SequenceIndicator(
                    currentIndex = currentSequenceIndex,
                    totalSteps = plan.sequence.size,
                    stepName = currentSequenceShot?.title ?: "",
                    onPrevious = onPreviousSequence,
                    onNext = onNextSequence
                )
            }
            if (plan.multiAngles.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                AngleIndicator(
                    currentAngleName = currentAngle?.title ?: "",
                    angleCount = plan.multiAngles.size,
                    onNextAngle = onNextAngle
                )
            }
            if (plan.secondaryPosePoints.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                SecondaryPoseToggle(
                    isSecondary = useSecondaryPose,
                    onToggle = onToggleSecondaryPose
                )
            }
            if (plan.vlogScript != null) {
                Spacer(modifier = Modifier.height(8.dp))
                PlanVlogButton(onClick = onStartVlog)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // ── 主控制行 ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左：相册缩略图
            GalleryThumbnailButton(
                recentRecords = recentRecords,
                onClick = onOpenGallery
            )

            // 中：快门按钮
            ShutterButton(
                isAligned = isAligned,
                isVlogRecording = isVlogRecording,
                isVlogMerging = isVlogMerging,
                breathingScale = breathingScale,
                onCapture = onCapture,
                onStopVlog = onStopVlog,
                isCompactHeight = isCompactHeight
            )

            // 右：切换摄像头 + 计时器
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconButton(
                    onClick = onSwitchCamera,
                    modifier = Modifier
                        .size(44.dp)
                        .background(Surface, CircleShape)
                        .border(1.dp, Border, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Cameraswitch,
                        contentDescription = "切换摄像头",
                        tint = TextPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                IconButton(
                    onClick = onToggleTimer,
                    modifier = Modifier
                        .size(44.dp)
                        .background(Surface, CircleShape)
                        .border(1.dp, Border, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Timer,
                        contentDescription = "计时器",
                        tint = TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

// ── 方案选择器 ──
@Composable
fun PlanPicker(
    plans: List<ShootingPlan>,
    currentPlanIndex: Int,
    onPlanSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier.padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(plans.size) { index ->
            val plan = plans[index]
            val isSelected = index == currentPlanIndex

            PlanCard(
                plan = plan,
                isSelected = isSelected,
                onClick = { onPlanSelected(index) }
            )
        }
    }
}

// ── 方案卡片 ──
@Composable
fun PlanCard(
    plan: ShootingPlan,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isSelected)
                    Brush.linearGradient(
                        colors = listOf(Accent.copy(alpha = 0.22f), Accent.copy(alpha = 0.08f))
                    )
                else
                    Brush.verticalGradient(
                        colors = listOf(Color.Black.copy(alpha = 0.45f), Color.Black.copy(alpha = 0.3f))
                    )
            )
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = if (isSelected) Accent.copy(alpha = 0.75f) else Border,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 13.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(
                text = plan.poseName,
                color = TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }

        if (isSelected) {
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                TagBadge(
                    text = when (plan.composition) {
                        com.poseai.app.model.CompositionRule.CENTER -> "居中"
                        com.poseai.app.model.CompositionRule.RULE_OF_THIRDS -> "三分法"
                        com.poseai.app.model.CompositionRule.DIAGONAL -> "对角线"
                        com.poseai.app.model.CompositionRule.FRAME_WITHIN_FRAME -> "框架"
                    },
                    active = true
                )
            }
        }
    }
}

// ── 标签徽章 ──
@Composable
fun TagBadge(text: String, active: Boolean = false) {
    Box(
        modifier = Modifier
            .background(
                if (active) Accent.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.1f),
                RoundedCornerShape(50)
            )
            .border(
                width = if (active) 1.dp else 0.dp,
                color = if (active) Accent.copy(alpha = 0.4f) else Color.Transparent,
                shape = RoundedCornerShape(50)
            )
            .padding(horizontal = 7.dp, vertical = 3.5.dp)
    ) {
        Text(
            text = text,
            color = if (active) Accent else TextPrimary.copy(alpha = 0.7f),
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

// ── 相册缩略图按钮 ──
@Composable
fun GalleryThumbnailButton(
    recentRecords: kotlinx.coroutines.flow.Flow<List<com.poseai.app.data.ShootingRecord>>,
    onClick: () -> Unit
) {
    val records by recentRecords.collectAsState(initial = emptyList())

    Box(
        modifier = Modifier
            .size(50.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Surface)
            .border(1.dp, Border, RoundedCornerShape(12.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (records.isNotEmpty()) {
            // 显示最新照片缩略图
            coil.compose.AsyncImage(
                model = records.first().imagePath,
                contentDescription = "相册",
                modifier = Modifier.fillMaxSize(),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop
            )
        } else {
            Icon(
                imageVector = Icons.Default.PhotoLibrary,
                contentDescription = "相册",
                tint = TextPrimary,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

// ── 快门按钮 ──
@Composable
fun ShutterButton(
    isAligned: Boolean,
    isVlogRecording: Boolean,
    isVlogMerging: Boolean,
    breathingScale: Float,
    onCapture: () -> Unit,
    onStopVlog: () -> Unit,
    isCompactHeight: Boolean
) {
    val buttonSize = if (isCompactHeight) 82.dp else 92.dp

    Box(
        modifier = Modifier.size(buttonSize),
        contentAlignment = Alignment.Center
    ) {
        if (isVlogRecording || isVlogMerging) {
            // 录制中：红色停止按钮
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .background(Danger, CircleShape)
                    .clickable { onStopVlog() },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .background(Color.White, RoundedCornerShape(4.dp))
                )
            }
        } else {
            // 对齐时呼吸动效
            if (isAligned) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawCircle(
                        color = SuccessGlow,
                        radius = 41.dp.toPx(),
                        center = center,
                        style = Stroke(width = 22.dp.toPx())
                    )
                }
            }

            // 外圈轨道
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(
                    color = if (isAligned) Success.copy(alpha = 0.9f) else Color.White.copy(alpha = 0.55f),
                    radius = 41.dp.toPx(),
                    center = center,
                    style = Stroke(width = 2.5.dp.toPx())
                )
            }

            // 内圆主体
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(CircleShape)
                    .background(
                        if (isAligned)
                            Brush.linearGradient(
                                colors = listOf(Success, Color(0xFF33D970)),
                                start = Offset(0f, 0f),
                                end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                            )
                        else
                            Brush.verticalGradient(
                                colors = listOf(Color.White.copy(alpha = 0.92f), Color.White.copy(alpha = 0.78f))
                            )
                    )
                    .clickable { onCapture() },
                contentAlignment = Alignment.Center
            ) {
                if (isAligned) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = "拍照",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .background(Color.Black.copy(alpha = 0.08f), CircleShape)
                    )
                }
            }
        }
    }
}

// ── 暗光提示 Banner ──
@Composable
fun LowLightBanner(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(Surface, RoundedCornerShape(50))
            .border(1.dp, Color.Yellow.copy(alpha = 0.4f), RoundedCornerShape(50))
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Lightbulb,
                contentDescription = null,
                tint = Color.Yellow,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = "光线不足，移到明亮处效果更好",
                color = TextPrimary.copy(alpha = 0.9f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// 构图辅助线
// ═══════════════════════════════════════════════════════════════

@Composable
fun CompositionGuideLines(
    composition: com.poseai.app.model.CompositionRule?
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        if (composition == com.poseai.app.model.CompositionRule.RULE_OF_THIRDS) {
            // 三分法
            drawLine(
                color = Color.White.copy(alpha = 0.06f),
                start = Offset(w / 3f, 0f),
                end = Offset(w / 3f, h),
                strokeWidth = 1.dp.toPx()
            )
            drawLine(
                color = Color.White.copy(alpha = 0.06f),
                start = Offset(2 * w / 3f, 0f),
                end = Offset(2 * w / 3f, h),
                strokeWidth = 1.dp.toPx()
            )
            drawLine(
                color = Color.White.copy(alpha = 0.06f),
                start = Offset(0f, h / 3f),
                end = Offset(w, h / 3f),
                strokeWidth = 1.dp.toPx()
            )
            drawLine(
                color = Color.White.copy(alpha = 0.06f),
                start = Offset(0f, 2 * h / 3f),
                end = Offset(w, 2 * h / 3f),
                strokeWidth = 1.dp.toPx()
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// 剪影引导叠加层（对齐 iOS SilhouetteGuideOverlay）
// ═══════════════════════════════════════════════════════════════

@Composable
fun SilhouetteGuideOverlay(
    posePoints: Map<String, PointF>,
    isAligned: Boolean,
    detectedPosePoints: Map<String, PointF>,
    plan: ShootingPlan,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp.dp
    val screenHeightDp = configuration.screenHeightDp.dp

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // 剪影宽高比：人体自然比例约 0.52:1
        val aspectRatio = 0.52f

        // 如果检测到人体，使用检测到的包围盒；否则使用默认
        val hasDetection = detectedPosePoints.isNotEmpty()

        val silH: Float
        val silW: Float
        val centerX: Float
        val centerY: Float

        if (hasDetection) {
            // 计算检测到的人体包围盒
            val points = detectedPosePoints.values
            if (points.isNotEmpty()) {
                val minX = points.minOf { it.x }
                val maxX = points.maxOf { it.x }
                val minY = points.minOf { it.y }
                val maxY = points.maxOf { it.y }
                val bboxW = maxX - minX
                var bboxH = maxY - minY + 0.15f // 补偿头部空间
                bboxH = bboxH.coerceIn(0.3f, 0.95f)

                var rawH = bboxH * h
                val defaultH = h * 0.55f
                rawH = rawH.coerceIn(defaultH * 0.5f, defaultH * 1.3f)

                val silHeight = rawH
                val silWidth = silHeight * aspectRatio

                val detectedCenterX = ((minX + maxX) / 2f) * w
                val clampedCenterX = detectedCenterX.coerceIn(silWidth / 2f, w - silWidth / 2f)

                val detectedMidY = ((minY + bboxH / 2f) - 0.05f) * h
                val clampedCenterY = detectedMidY.coerceIn(silHeight / 2f, h - silHeight / 2f - 40f)

                silH = silHeight
                silW = silWidth
                centerX = clampedCenterX
                centerY = clampedCenterY
            } else {
                // fallback to default
                silH = h * 0.55f
                silW = silH * aspectRatio
                centerX = w / 2f
                centerY = h - silH / 2f - 140f
            }
        } else {
            silH = h * 0.55f
            silW = silH * aspectRatio
            centerX = w / 2f
            centerY = h - silH / 2f - 140f
        }

        // 绘制剪影
        val silColor = if (isAligned) Success else Color.White
        val silAlpha = if (isAligned) 0.22f else 0.12f
        val strokeAlpha = if (isAligned) 1f else 0.35f
        val strokeWidth = if (isAligned) 3.dp.toPx() else 1.8.dp.toPx()

        val left = centerX - silW / 2f
        val top = centerY - silH / 2f

        // 绘制人体剪影形状
        drawSilhouetteShape(
            left = left,
            top = top,
            width = silW,
            height = silH,
            fillColor = silColor.copy(alpha = silAlpha),
            strokeColor = silColor.copy(alpha = strokeAlpha),
            strokeWidth = strokeWidth,
            isDashed = !isAligned
        )

        // 对齐时发光效果
        if (isAligned) {
            drawCircle(
                color = SuccessGlow,
                radius = silW * 0.8f,
                center = Offset(centerX, centerY),
                style = Stroke(width = 14.dp.toPx())
            )
        }
    }
}

// ── 绘制人体剪影形状 ──
private fun DrawScope.drawSilhouetteShape(
    left: Float, top: Float, width: Float, height: Float,
    fillColor: Color, strokeColor: Color, strokeWidth: Float, isDashed: Boolean
) {
    val w = width
    val h = height
    val path = Path().apply {
        // 头部
        val headSize = w * 0.24f
        addOval(
            androidx.compose.ui.geometry.Rect(
                left + w * 0.38f, top + h * 0.02f,
                left + w * 0.38f + headSize, top + h * 0.02f + headSize * 1.15f
            )
        )

        // 身体
        moveTo(left + w * 0.45f, top + h * 0.14f + headSize * 1.15f)
        cubicTo(left + w * 0.28f, top + h * 0.22f, left + w * 0.28f, top + h * 0.22f, left + w * 0.18f, top + h * 0.28f)
        cubicTo(left + w * 0.08f, top + h * 0.38f, left + w * 0.08f, top + h * 0.38f, left + w * 0.12f, top + h * 0.52f)
        cubicTo(left + w * 0.18f, top + h * 0.56f, left + w * 0.22f, top + h * 0.48f, left + w * 0.28f, top + h * 0.43f)
        lineTo(left + w * 0.33f, top + h * 0.50f)
        cubicTo(left + w * 0.27f, top + h * 0.72f, left + w * 0.27f, top + h * 0.72f, left + w * 0.24f, top + h * 0.93f)
        lineTo(left + w * 0.40f, top + h * 0.93f)
        cubicTo(left + w * 0.44f, top + h * 0.74f, left + w * 0.44f, top + h * 0.74f, left + w * 0.48f, top + h * 0.58f)
        cubicTo(left + w * 0.54f, top + h * 0.74f, left + w * 0.54f, top + h * 0.74f, left + w * 0.63f, top + h * 0.93f)
        lineTo(left + w * 0.79f, top + h * 0.93f)
        cubicTo(left + w * 0.79f, top + h * 0.73f, left + w * 0.79f, top + h * 0.73f, left + w * 0.70f, top + h * 0.52f)
        lineTo(left + w * 0.64f, top + h * 0.48f)
        cubicTo(left + w * 0.74f, top + h * 0.52f, left + w * 0.74f, top + h * 0.52f, left + w * 0.83f, top + h * 0.43f)
        cubicTo(left + w * 0.94f, top + h * 0.33f, left + w * 0.94f, top + h * 0.33f, left + w * 0.78f, top + h * 0.24f)
        cubicTo(left + w * 0.67f, top + h * 0.23f, left + w * 0.67f, top + h * 0.23f, left + w * 0.55f, top + h * 0.14f + headSize * 1.15f)
        close()
    }

    drawPath(path, fillColor)

    val pathEffect = if (isDashed) {
        PathEffect.dashPathEffect(floatArrayOf(10f, 7f), 0f)
    } else null

    drawPath(
        path, strokeColor,
        style = Stroke(width = strokeWidth, pathEffect = pathEffect)
    )
}

// ═══════════════════════════════════════════════════════════════
// 检测骨骼叠加层
// ═══════════════════════════════════════════════════════════════

@Composable
fun DetectedSkeletonOverlay(
    lines: List<Pair<PointF, PointF>>,
    points: Map<String, PointF>,
    score: Float,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        lines.forEach { (pa, pb) ->
            drawLine(
                color = Color.Green,
                start = Offset(pa.x * w, pa.y * h),
                end = Offset(pb.x * w, pb.y * h),
                strokeWidth = 4.dp.toPx(),
                alpha = 0.85f
            )
        }

        points.values.forEach { point ->
            val cx = point.x * w
            val cy = point.y * h
            drawCircle(
                color = Color.Green,
                radius = 6.dp.toPx(),
                center = Offset(cx, cy),
                alpha = 0.9f
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// 以下组件从原文件保留，或微调以匹配新设计
// ═══════════════════════════════════════════════════════════════

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
fun VlogStatusIndicator(
    isRecording: Boolean,
    isMerging: Boolean,
    currentClip: Int,
    totalClips: Int,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .background(Surface, RoundedCornerShape(20.dp))
            .border(1.dp, Border, RoundedCornerShape(20.dp))
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
        Spacer(modifier = Modifier.width(8.dp))
        IconButton(
            onClick = onStop,
            modifier = Modifier.size(24.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Stop,
                contentDescription = "停止",
                tint = Error,
                modifier = Modifier.size(16.dp)
            )
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
                    .background(Surface, CircleShape)
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
                    .background(Surface, CircleShape)
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
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Accent,
                checkedTrackColor = Accent.copy(alpha = 0.5f)
            )
        )
    }
}

@Composable
fun ExposurePanel(
    exposureValue: Int,
    minExposure: Int,
    maxExposure: Int,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(Surface, RoundedCornerShape(16.dp))
            .border(1.dp, Border, RoundedCornerShape(16.dp))
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        IconButton(onClick = onIncrease, modifier = Modifier.size(32.dp)) {
            Text("+", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        Text(
            text = "$exposureValue",
            color = TextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(vertical = 4.dp)
        )
        IconButton(onClick = onDecrease, modifier = Modifier.size(32.dp)) {
            Text("-", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SceneSelectorBottomSheetComposable(
    viewModel: ShootingViewModel,
    currentScene: SceneType,
    onDismiss: () -> Unit,
    onSceneSelected: (SceneType) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SurfaceDark,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "选择场景",
                color = TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            androidx.compose.foundation.lazy.LazyColumn {
                items(SceneType.values().filter { it != SceneType.UNKNOWN }) { scene ->
                    SceneItem(
                        scene = scene,
                        isSelected = scene == currentScene,
                        onClick = { onSceneSelected(scene) }
                    )
                }
            }
        }
    }
}

@Composable
fun SceneItem(scene: SceneType, isSelected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp)
            .background(
                if (isSelected) Accent.copy(alpha = 0.15f) else Color.Transparent,
                RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = scene.displayName,
                color = TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "${scene.plans.size} 个姿势方案",
                color = TextSecondary,
                fontSize = 13.sp
            )
        }
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = null,
                tint = Accent,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterSelectorBottomSheet(
    currentFilter: PhotoFilterEngine.Filter,
    onFilterSelected: (PhotoFilterEngine.Filter) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SurfaceDark,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "选择滤镜",
                color = TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(PhotoFilterEngine.Filter.values()) { filter ->
                    FilterItem(
                        filter = filter,
                        isSelected = filter == currentFilter,
                        onClick = { onFilterSelected(filter) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun FilterItem(
    filter: PhotoFilterEngine.Filter,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(80.dp)
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    when (filter) {
                        PhotoFilterEngine.Filter.ORIGINAL -> Color.Gray
                        PhotoFilterEngine.Filter.VIVID -> Color(0xFFFFB74D)
                        PhotoFilterEngine.Filter.WARM -> Color(0xFFFFCC80)
                        PhotoFilterEngine.Filter.COOL -> Color(0xFF81D4FA)
                        PhotoFilterEngine.Filter.FADE -> Color(0xFFE0E0E0)
                        PhotoFilterEngine.Filter.VINTAGE -> Color(0xFFD7CCC8)
                        PhotoFilterEngine.Filter.MONO -> Color(0xFF757575)
                        PhotoFilterEngine.Filter.DRAMATIC -> Color(0xFF424242)
                    }
                )
                .border(
                    width = if (isSelected) 3.dp else 0.dp,
                    color = if (isSelected) Accent else Color.Transparent,
                    shape = RoundedCornerShape(12.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = filter.displayName.take(1),
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = filter.displayName,
            color = if (isSelected) Accent else TextSecondary,
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VlogTemplateSelectorBottomSheet(
    templates: List<com.poseai.app.model.VlogTemplate>,
    onTemplateSelected: (com.poseai.app.model.VlogTemplate) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SurfaceDark,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "选择 Vlog 模板",
                color = TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            androidx.compose.foundation.lazy.LazyColumn {
                items(templates) { template ->
                    VlogTemplateItem(
                        template = template,
                        onClick = { onTemplateSelected(template) }
                    )
                }
            }
        }
    }
}

@Composable
fun VlogTemplateItem(template: com.poseai.app.model.VlogTemplate, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp)
            .background(Surface, RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(Accent.copy(alpha = 0.2f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Videocam,
                contentDescription = null,
                tint = Accent,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = template.name,
                color = TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "${template.clips.size} 个分镜 · 约 ${template.clips.sumOf { it.duration.toInt() }} 秒",
                color = TextSecondary,
                fontSize = 13.sp
            )
        }
        Icon(
            imageVector = Icons.Default.PlayArrow,
            contentDescription = "开始",
            tint = Accent,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
fun SequenceIndicator(
    currentIndex: Int,
    totalSteps: Int,
    stepName: String,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .background(Surface, RoundedCornerShape(12.dp))
            .border(1.dp, Border, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPrevious, modifier = Modifier.size(32.dp)) {
            Icon(
                imageVector = Icons.Default.ChevronLeft,
                contentDescription = "上一步",
                tint = TextPrimary,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "分镜 ${currentIndex + 1}/$totalSteps",
                color = TextSecondary,
                fontSize = 11.sp
            )
            Text(
                text = stepName,
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }
        IconButton(onClick = onNext, modifier = Modifier.size(32.dp)) {
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "下一步",
                tint = TextPrimary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun AngleIndicator(
    currentAngleName: String,
    angleCount: Int,
    onNextAngle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .background(Surface, RoundedCornerShape(12.dp))
            .border(1.dp, Border, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.RotateRight,
            contentDescription = null,
            tint = Accent,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "视角: $currentAngleName",
            color = TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
        TextButton(onClick = onNextAngle) {
            Text(
                text = "切换",
                color = Accent,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
fun SecondaryPoseToggle(
    isSecondary: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .background(Surface, RoundedCornerShape(12.dp))
            .border(1.dp, Border, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Flip,
            contentDescription = null,
            tint = Accent,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = if (isSecondary) "目标姿势: 备选" else "目标姿势: 主姿势",
            color = TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = isSecondary,
            onCheckedChange = { onToggle() },
            colors = SwitchDefaults.colors(
                checkedThumbColor = Accent,
                checkedTrackColor = Accent.copy(alpha = 0.3f)
            )
        )
    }
}

@Composable
fun PlanVlogButton(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clickable { onClick() }
            .background(Accent.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
            .border(1.dp, Border, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.MovieFilter,
            contentDescription = null,
            tint = Accent,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "用当前姿势拍 Vlog",
            color = Accent,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector = Icons.Default.PlayArrow,
            contentDescription = null,
            tint = Accent,
            modifier = Modifier.size(18.dp)
        )
    }
}