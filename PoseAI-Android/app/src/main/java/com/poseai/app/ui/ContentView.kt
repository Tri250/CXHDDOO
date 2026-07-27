package com.poseai.app.ui

import android.graphics.PointF
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.graphicsLayer
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import com.poseai.app.util.ShareEngine
import com.poseai.app.ui.theme.Dimens
import com.poseai.app.viewmodel.ShootingViewModel
import kotlinx.coroutines.launch
import kotlin.math.*

// ═══════════════════════════════════════════════════════════════
// 主拍摄界面 — 对齐 iOS ContentView 的 UI/UX
// ═══════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShootingScreen(
    viewModel: ShootingViewModel = viewModel(),
    onNavigateToGallery: () -> Unit = {}
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
    val filterIntensity by viewModel.filterIntensity.collectAsState()
    val beautyEnabled by viewModel.beautyEnabled.collectAsState()
    val smoothingLevel by viewModel.smoothingLevel.collectAsState()
    val whiteningLevel by viewModel.whiteningLevel.collectAsState()
    val slimmingLevel by viewModel.slimmingLevel.collectAsState()
    val currentSticker by viewModel.currentSticker.collectAsState()
    val showSceneSelector by viewModel.showSceneSelector.collectAsState()
    val showFilterSelector by viewModel.showFilterSelector.collectAsState()
    val showShareSheet by viewModel.showShareSheet.collectAsState()
    val sharePhotoPath by viewModel.sharePhotoPath.collectAsState()
    val watermarkStyle by viewModel.watermarkStyle.collectAsState()
    val watermarkPosition by viewModel.watermarkPosition.collectAsState()
    val shareUsername by viewModel.shareUsername.collectAsState()
    val shareLocation by viewModel.shareLocation.collectAsState()
    val shareTopics by viewModel.shareTopics.collectAsState()
    val shareCaption by viewModel.shareCaption.collectAsState()
    val customPoses by viewModel.customPoses.collectAsState()
    val showCustomPoseSheet by viewModel.showCustomPoseSheet.collectAsState()
    val activeCustomPoseId by viewModel.activeCustomPoseId.collectAsState()
    val showVlogTemplateSelector by viewModel.showVlogTemplateSelector.collectAsState()
    val watermarkEnabled by viewModel.watermarkEnabled.collectAsState()
    val lowLightMode by viewModel.lowLightMode.collectAsState()
    val useSecondaryPose by viewModel.useSecondaryPose.collectAsState()
    val currentSequenceIndex by viewModel.currentSequenceIndex.collectAsState()
    val currentAngleIndex by viewModel.currentAngleIndex.collectAsState()
    val currentSequenceShot = viewModel.getCurrentSequenceShot()
    val currentAngle = viewModel.getCurrentAngle()
    val currentPlanIndex by viewModel.currentPlanIndex.collectAsState()

    // 新增状态：倒计时 / 闪光 / 沉浸 / 俯拍 / 错误提示
    val timerSeconds by viewModel.timerSeconds.collectAsState()
    val countdownValue by viewModel.countdownValue.collectAsState()
    val showShutterFlash by viewModel.showShutterFlash.collectAsState()
    val isImmersiveMode by viewModel.isImmersiveMode.collectAsState()
    val isTopDownWarning by viewModel.isTopDownWarning.collectAsState()
    val vlogErrorMessage by viewModel.vlogErrorMessage.collectAsState()
    val distanceHint by viewModel.distanceHint.collectAsState()
    val photoSaveError by viewModel.photoSaveError.collectAsState()
    val focusIndicator by viewModel.focusIndicator.collectAsState()
    val isHeadroomWarning by viewModel.isHeadroomWarning.collectAsState()
    val isBurstMode by viewModel.isBurstMode.collectAsState()
    val recommendedPlanIndex by viewModel.recommendedPlanIndex.collectAsState()
    val autoRecommendEnabled by viewModel.autoRecommendEnabled.collectAsState()
    val shouldShowReviewPrompt by viewModel.shouldShowReviewPrompt.collectAsState()
    val isTorchOn by viewModel.isTorchOn.collectAsState()
    // 闪光灯模式（0=关闭, 1=自动, 2=常亮）激活 cycleFlashMode/setFlashMode 死代码
    val currentFlashMode by viewModel.currentFlashMode.collectAsState()
    val zoomLevel by viewModel.zoomLevel.collectAsState()
    val currentAspectRatio by viewModel.currentAspectRatio.collectAsState()
    // 拍照计数（激活 captureCount 死代码：在顶栏显示已拍摄张数）
    val captureCount by viewModel.captureCount.collectAsState()
    // 连拍结果列表（激活 burstPhotos StateFlow 死代码：连拍后展示缩略图横幅）
    val burstPhotos by viewModel.burstPhotos.collectAsState()

    var showSettings by remember { mutableStateOf(false) }
    var showExposurePanel by remember { mutableStateOf(false) }
    var showGuide by remember { mutableStateOf(false) }
    var showFilterShortcuts by remember { mutableStateOf(false) }
    val exposureValue by viewModel.exposureValue.collectAsState()

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }

    val statusBarPadding = WindowInsets.statusBars.asPaddingValues()
    val navBarPadding = WindowInsets.navigationBars.asPaddingValues()
    val density = LocalDensity.current

    // 距离提示随评分变化更新
    LaunchedEffect(poseScore, currentPlan) {
        viewModel.updateDistanceHint(poseScore, currentPlan)
    }

    // 拍照保存失败提示：自动 Snackbar
    LaunchedEffect(photoSaveError) {
        val err = photoSaveError ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(
            message = err,
            duration = androidx.compose.material3.SnackbarDuration.Short
        )
        viewModel.clearPhotoSaveError()
    }

    // Vlog 失败兜底提示
    LaunchedEffect(vlogErrorMessage) {
        val err = vlogErrorMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(
            message = err,
            duration = androidx.compose.material3.SnackbarDuration.Long
        )
        viewModel.clearVlogError()
    }

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

    // Vlog 红点闪烁动画
    val vlogDotAlpha = remember { Animatable(0f) }
    LaunchedEffect(isVlogRecording) {
        if (isVlogRecording) {
            vlogDotAlpha.animateTo(
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(800, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                )
            )
        } else {
            vlogDotAlpha.snapTo(1f)
        }
    }

    val isSceneReady = currentScene != SceneType.UNKNOWN && currentPlan != null
    val isAligned = poseScore >= 80f

    // 对齐达成强反馈：记录上一次对齐状态，变化时触发触觉+粒子动画
    val haptics = com.poseai.app.util.Haptics.rememberHapticController()
    var showAlignmentCelebration by remember { mutableStateOf(false) }
    val prevAligned = remember { mutableStateOf(isAligned) }
    LaunchedEffect(isAligned) {
        if (isAligned && !prevAligned.value) {
            haptics.perform(com.poseai.app.util.Haptics.Level.SUCCESS)
            showAlignmentCelebration = true
            kotlinx.coroutines.delay(1200)
            showAlignmentCelebration = false
        }
        prevAligned.value = isAligned
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // ── 相机预览层（含点击对焦 + 双击沉浸 + 双指缩放）──
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).also { pv ->
                    previewView = pv
                    pv.post {
                        viewModel.initCamera(lifecycleOwner, pv)
                    }
                }
            },
            // 双击切换沉浸模式，单击对焦，双指缩放
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = { offset ->
                            // 双击：切换沉浸模式（避免与单击对焦冲突）
                            viewModel.toggleImmersiveMode()
                        },
                        onTap = { offset ->
                            // 单击：触发对焦
                            val pv = previewView
                            if (pv != null) {
                                viewModel.tapToFocus(offset.x, offset.y, pv)
                            }
                        }
                    )
                }
                .pointerInput(Unit) {
                    // 双指缩放：激活 setZoom 死代码
                    detectTransformGestures { _, _, zoomChange, _ ->
                        if (zoomChange != 1f) {
                            val newZoom = (zoomLevel * zoomChange).coerceIn(1f, 5f)
                            viewModel.setZoom(newZoom)
                        }
                    }
                }
        )

        // ── 变焦指示器（缩放 > 1 时显示）──
        if (zoomLevel > 1.01f) {
            ZoomIndicator(
                zoomLevel = zoomLevel,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 16.dp)
            )
        }

        // ── 构图辅助线（沉浸模式隐藏）──
        if (isSceneReady && gridEnabled && !isImmersiveMode) {
            CompositionGuideLines(currentPlan?.composition)
        }

        // ── 场景扫描动画 ──
        if (!isSceneReady) {
            SceneScanningOverlay(
                scanPulse = scanPulse.value,
                scanRotation = scanRotation.value
            )
        }

        // ── 剪影引导叠加层（沉浸模式降低透明度）──
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
                isImmersiveMode = isImmersiveMode,
                modifier = Modifier.fillMaxSize()
            )
        }

        // ── 检测骨骼叠加（沉浸模式隐藏）──
        if (!isImmersiveMode && (detectedPoseLines.isNotEmpty() || detectedPosePoints.isNotEmpty())) {
            DetectedSkeletonOverlay(
                lines = detectedPoseLines,
                points = detectedPosePoints,
                score = poseScore,
                modifier = Modifier.fillMaxSize()
            )
        }

        // ── 距离提示（未对齐时，剪影下方）──
        if (isSceneReady && !isAligned && distanceHint != null && !isImmersiveMode) {
            DistanceHintText(
                text = distanceHint!!,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(top = 280.dp)
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

        // ── 微笑指示器（沉浸模式隐藏）──
        if (smileEnabled && !isImmersiveMode) {
            SmileIndicator(strength = smileStrength)
        }

        // ── 顶部信息栏（沉浸模式隐藏，slide+fade 过渡更柔和）──
        AnimatedVisibility(
            visible = !isImmersiveMode,
            enter = fadeIn(animationSpec = tween(220)) +
                slideInVertically(
                    initialOffsetY = { -it / 4 },
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                ),
            exit = fadeOut(animationSpec = tween(180)) +
                slideOutVertically(
                    targetOffsetY = { -it / 4 },
                    animationSpec = tween(200, easing = FastOutLinearInEasing)
                ),
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = statusBarPadding.calculateTopPadding() + Dimens.spacingXs)
                    .padding(horizontal = Dimens.screenMarginH)
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
                    timerSeconds = timerSeconds,
                    captureCount = captureCount,
                    onHelp = { showGuide = true },
                    onSceneClick = { viewModel.toggleSceneSelector() },
                    onSettings = { showSettings = true }
                )

                Spacer(modifier = Modifier.height(8.dp))

                // 沉浸模式下隐藏次要警告（过热/电量），保留俯拍警告独立显示
                if (!isImmersiveMode) {
                    if (heatWarning) {
                        WarningBanner(text = "设备过热，已降频优化", color = Warning)
                    } else if (batteryLow) {
                        WarningBanner(text = "电量低，已进入省电模式", color = Error)
                    }
                }
            }
        }

        // ── 俯拍警告（独立于沉浸模式，始终显示）──
        if (isTopDownWarning && isSceneReady) {
            WarningBanner(
                text = "请平行或低角度拍摄，显腿更长",
                color = Danger,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = statusBarPadding.calculateTopPadding() + 70.dp)
                    .padding(horizontal = 18.dp)
            )
        }

        // ── Vlog 字幕（独立于沉浸模式，始终显示）──
        if (vlogText.isNotEmpty()) {
            VlogSubtitle(
                text = vlogText,
                isRecording = isVlogRecording,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = if (isImmersiveMode) statusBarPadding.calculateTopPadding() + 16.dp else statusBarPadding.calculateTopPadding() + 120.dp)
            )
        }

        // ── 留白智能提醒（头部上方空间过多）──
        if (isHeadroomWarning && isSceneReady && !isImmersiveMode) {
            WarningBanner(
                text = "上方留白过多，请将人物上移",
                color = Warning,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = statusBarPadding.calculateTopPadding() + 150.dp)
                    .padding(horizontal = 18.dp)
            )
        }

        // ── 姿势亲近度自动推荐提示 ──
        if (autoRecommendEnabled && recommendedPlanIndex >= 0 && recommendedPlanIndex != currentPlanIndex && isSceneReady && !isImmersiveMode) {
            val recPlan = currentScene.plans.getOrNull(recommendedPlanIndex)
            if (recPlan != null) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 260.dp)
                        .background(Accent.copy(alpha = 0.9f), RoundedCornerShape(Dimens.radiusXl))
                        .clickable { viewModel.acceptRecommendedPlan() }
                        .padding(horizontal = Dimens.spacingXl, vertical = Dimens.spacingSm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(Dimens.iconXs)
                    )
                    Spacer(modifier = Modifier.width(Dimens.spacingSm))
                    Text(
                        text = "推荐方案: ${recPlan.poseName}",
                        color = Color.Black,
                        fontSize = Dimens.fontLabel,
                        fontWeight = FontWeight.Bold,
                        lineHeight = Dimens.lineHeightLabel
                    )
                }
            }
        }

        // ── Review Prompt 评价引导 ──
        if (shouldShowReviewPrompt) {
            ReviewPromptDialog(
                onDismiss = { viewModel.dismissReviewPrompt() }
            )
        }

        // ── 暗光提示 Banner（沉浸模式隐藏）──
        if (lowLightWarning && isSceneReady && lowLightMode && !isImmersiveMode) {
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

        // ── 底部面板（沉浸模式仅保留快门）──
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
            timerSeconds = timerSeconds,
            isImmersiveMode = isImmersiveMode,
            isBurstMode = isBurstMode,
            isTorchOn = isTorchOn,
            currentFlashMode = currentFlashMode,
            currentAspectRatioName = currentAspectRatio.displayName.substringBefore(" "),
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
            onOpenGallery = onNavigateToGallery,
            onToggleTimer = { viewModel.cycleTimer() },
            onToggleBurstMode = { viewModel.toggleBurstMode() },
            onToggleTorch = { viewModel.toggleTorch() },
            onCycleFlashMode = { viewModel.cycleFlashMode() },
            onCycleAspectRatio = { viewModel.cycleAspectRatio() },
            modifier = Modifier.align(Alignment.BottomCenter)
        )

        // ── Vlog 状态指示器（沉浸模式隐藏）──
        if ((isVlogRecording || isVlogMerging) && !isImmersiveMode) {
            VlogStatusIndicator(
                isRecording = isVlogRecording,
                isMerging = isVlogMerging,
                currentClip = activeClipIndex + 1,
                totalClips = activeTemplate?.clips?.size ?: 0,
                dotAlpha = vlogDotAlpha.value,
                onStop = { viewModel.stopVlog() },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = statusBarPadding.calculateTopPadding() + 8.dp, end = 16.dp)
            )
        }

        // ── 倒计时大数字覆盖 ──
        if (countdownValue > 0) {
            CountdownOverlay(seconds = countdownValue)
        }

        // ── 点击对焦指示器 ──
        focusIndicator?.let { point ->
            FocusIndicatorOverlay(x = point.x, y = point.y)
        }

        // ── 快门闪光覆盖 ──
        if (showShutterFlash) {
            ShutterFlashOverlay()
        }

        // ── 连拍结果横幅（连拍完成后短暂展示，激活 burstPhotos StateFlow）──
        if (burstPhotos.isNotEmpty()) {
            BurstResultBanner(
                photoPaths = burstPhotos,
                onDismiss = { viewModel.clearBurstPhotos() },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = statusBarPadding.calculateTopPadding() + 60.dp)
            )
        }

        // ── Snackbar 错误提示 ──
        androidx.compose.material3.SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 180.dp)
        )

        // ── 照片预览 ──
        if (isReviewing && lastPhotoPath != null) {
            PhotoReviewDialog(
                photoPath = lastPhotoPath!!,
                onDismiss = { viewModel.closePhotoReview() },
                onShare = {
                    viewModel.openShareSheet(lastPhotoPath!!)
                },
                onDelete = {
                    viewModel.deletePhoto(lastPhotoPath!!)
                    viewModel.closePhotoReview()
                },
                onRetake = {
                    viewModel.closePhotoReview()
                }
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

        // ── 帮助引导弹窗（PoseGuideSheet）──
        if (showGuide) {
            PoseGuideSheet(
                plan = currentPlan,
                onDismiss = { showGuide = false }
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
                filterIntensity = filterIntensity,
                beautyEnabled = beautyEnabled,
                smoothingLevel = smoothingLevel,
                whiteningLevel = whiteningLevel,
                slimmingLevel = slimmingLevel,
                currentSticker = currentSticker,
                onFilterSelected = {
                    viewModel.setFilter(it)
                    viewModel.toggleFilterSelector()
                },
                onFilterIntensityChanged = { viewModel.setFilterIntensity(it) },
                onBeautyEnabledChanged = { viewModel.setBeautyEnabled(it) },
                onSmoothingChanged = { viewModel.setSmoothingLevel(it) },
                onWhiteningChanged = { viewModel.setWhiteningLevel(it) },
                onSlimmingChanged = { viewModel.setSlimmingLevel(it) },
                onStickerSelected = { viewModel.setSticker(it) },
                onQuickBeauty = { viewModel.applyQuickBeauty() },
                onDisableBeauty = { viewModel.disableBeauty() },
                onDismiss = { viewModel.toggleFilterSelector() }
            )
        }

        // ── 分享面板 ──
        if (showShareSheet) {
            ShareBottomSheet(
                photoPath = sharePhotoPath,
                watermarkStyle = watermarkStyle,
                watermarkPosition = watermarkPosition,
                username = shareUsername,
                location = shareLocation,
                topics = shareTopics,
                caption = shareCaption,
                onWatermarkStyleChanged = { viewModel.setWatermarkStyle(it) },
                onWatermarkPositionChanged = { viewModel.setWatermarkPosition(it) },
                onUsernameChanged = { viewModel.setShareUsername(it) },
                onLocationChanged = { viewModel.setShareLocation(it) },
                onCaptionChanged = { viewModel.setShareCaption(it) },
                onAddTopic = { viewModel.addTopic(it) },
                onRemoveTopic = { viewModel.removeTopic(it) },
                onShare = {
                    scope.launch {
                        val ok = viewModel.executeShare(context)
                        if (ok) {
                            viewModel.closeShareSheet()
                        }
                    }
                },
                onDismiss = { viewModel.closeShareSheet() }
            )
        }

        // ── 自定义姿势面板 ──
        if (showCustomPoseSheet) {
            CustomPoseSheet(
                customPoses = customPoses,
                activeCustomPoseId = activeCustomPoseId,
                detectedPointsCount = detectedPosePoints.size,
                onSaveCurrent = { name, desc ->
                    viewModel.saveCurrentPoseAsCustom(name, desc)
                },
                onApplyPose = { pose ->
                    viewModel.applyCustomPose(pose)
                    viewModel.closeCustomPoseSheet()
                },
                onDeletePose = { id ->
                    viewModel.deleteCustomPose(id)
                },
                onClearActive = {
                    viewModel.clearCustomPose()
                },
                onDismiss = { viewModel.closeCustomPoseSheet() }
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

        // ── 对齐达成粒子庆祝动画 ──
        AnimatedVisibility(
            visible = showAlignmentCelebration && !isImmersiveMode,
            enter = fadeIn(tween(200)) + scaleIn(initialScale = 0.5f, animationSpec = tween(400)),
            exit = fadeOut(tween(600)) + scaleOut(targetScale = 1.3f, animationSpec = tween(600))
        ) {
            AlignmentCelebrationOverlay()
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
    // 三层波纹错相扩散，对应国内主流摄影App的扫描效果
    val ripple1 = scanPulse
    val ripple2 = (scanPulse + 0.33f) % 1f
    val ripple3 = (scanPulse + 0.66f) % 1f
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
                // 三层波纹扩散（错相 33%）
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val cx = size.width / 2
                    val cy = size.height / 2
                    val maxR = 110.dp.toPx()
                    val minR = 70.dp.toPx()
                    listOf(ripple1, ripple2, ripple3).forEach { p ->
                        val r = minR + (maxR - minR) * p
                        // 透明度随扩散衰减
                        val a = (1f - p) * 0.6f
                        if (a > 0.01f) {
                            drawCircle(
                                color = Accent.copy(alpha = a),
                                radius = r,
                                center = Offset(cx, cy),
                                style = Stroke(width = 1.5.dp.toPx())
                            )
                        }
                    }
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
                            Spacer(modifier = Modifier.height(Dimens.spacingSm + 2.dp))
                            Text(
                                text = "识别场景中…",
                                color = TextPrimary,
                                fontSize = Dimens.fontLabel,
                                fontWeight = FontWeight.SemiBold,
                                lineHeight = Dimens.lineHeightLabel
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(Dimens.spacingXxl))

            // 提示文字（带渐显动画）
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "将镜头对准拍摄背景",
                    color = TextPrimary.copy(alpha = 0.85f),
                    fontSize = Dimens.fontBody,
                    fontWeight = FontWeight.Medium,
                    lineHeight = Dimens.lineHeightBody
                )
                Spacer(modifier = Modifier.height(Dimens.spacingXs + 2.dp))
                Text(
                    text = "咖啡馆 · 海边 · 森林",
                    color = Accent.copy(alpha = 0.7f),
                    fontSize = Dimens.fontCaption,
                    letterSpacing = 2.sp,
                    lineHeight = Dimens.lineHeightCaption
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
// 对齐达成粒子庆祝动画（国内用户期待的"已对齐"强反馈）
// ═══════════════════════════════════════════════════════════════

@Composable
fun AlignmentCelebrationOverlay() {
    val particleCount = 12
    val particleColors = listOf(Success, Accent, Color(0xFF33D970), Color(0xFF0D9488), SuccessGlow)
    // 粒子扩散动画
    val particleProgress = remember { Animatable(0f) }
    val checkmarkProgress = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        // 先播放粒子扩散
        particleProgress.animateTo(1f, animationSpec = tween(600, easing = FastOutSlowInEasing))
        // 粒子到达后播放对勾 + 文字
        checkmarkProgress.animateTo(1f, animationSpec = spring(DampingRatioMediumBouncy, StiffnessMedium))
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        // 外层暗色遮罩（微弱）
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                color = Color.Black.copy(alpha = 0.15f),
                radius = size.minDimension * 0.4f,
                center = center
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // 粒子环 + 对勾
            Box(
                modifier = Modifier.size(120.dp),
                contentAlignment = Alignment.Center
            ) {
                // 粒子扩散
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    val maxRadius = size.width * 0.65f
                    for (i in 0 until particleCount) {
                        val angle = (2 * Math.PI * i / particleCount).toFloat()
                        val radius = maxRadius * particleProgress.value
                        val px = cx + radius * cos(angle)
                        val py = cy + radius * sin(angle)
                        val particleAlpha = (1f - particleProgress.value) * 0.8f
                        drawCircle(
                            color = particleColors[i % particleColors.size].copy(alpha = particleAlpha),
                            radius = 4.dp.toPx() * (1f - particleProgress.value * 0.5f),
                            center = Offset(px, py)
                        )
                    }
                }

                // 对勾（国产App标志性反馈）
                if (checkmarkProgress.value > 0f) {
                    val checkScale = checkmarkProgress.value
                    Box(
                        modifier = Modifier
                            .size((48 * checkScale).dp)
                            .scale(checkScale)
                            .background(Success, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "✓",
                            color = Color.White,
                            fontSize = Dimens.fontDisplay,
                            fontWeight = FontWeight.Black,
                            lineHeight = Dimens.lineHeightDisplay
                        )
                    }
                }
            }

            // 文字
            if (checkmarkProgress.value > 0.5f) {
                Spacer(modifier = Modifier.height(Dimens.spacingSm))
                Text(
                    text = "姿势到位！",
                    color = Color.White,
                    fontSize = Dimens.fontHeadline,
                    fontWeight = FontWeight.Bold,
                    lineHeight = Dimens.lineHeightHeadline,
                    modifier = Modifier
                        .background(
                            Color.Black.copy(alpha = 0.5f),
                            RoundedCornerShape(Dimens.radiusFull)
                        )
                        .padding(horizontal = Dimens.spacingXl, vertical = Dimens.spacingSm)
                )
            }
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
    timerSeconds: Int = 0,
    captureCount: Int = 0,
    onHelp: () -> Unit,
    onSceneClick: () -> Unit,
    onSettings: () -> Unit = {}
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
                    .background(Surface, RoundedCornerShape(Dimens.radiusLg))
                    .border(Dimens.strokeThin, Border, RoundedCornerShape(Dimens.radiusLg))
                    .padding(horizontal = Dimens.spacingMd, vertical = Dimens.spacingSm),
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
                        modifier = Modifier.size(Dimens.iconSm)
                    )
                }
                Spacer(modifier = Modifier.width(Dimens.spacingSm))
                Column {
                    Text(
                        text = scene.displayName,
                        color = TextSecondary,
                        fontSize = Dimens.fontCaption,
                        fontWeight = FontWeight.Medium,
                        lineHeight = Dimens.lineHeightCaption
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
                            Spacer(modifier = Modifier.width(Dimens.spacingXs))
                            Text(
                                text = "Vlog · 分镜 ${activeVlogClipIndex + 1}/${plan.vlogScript!!.clips.size}",
                                color = Danger,
                                fontSize = Dimens.fontTitle,
                                fontWeight = FontWeight.Black,
                                lineHeight = Dimens.lineHeightTitle
                            )
                        }
                    } else if (plan.sequence.isNotEmpty()) {
                        Text(
                            text = "${currentSequenceIndex + 1}/${plan.sequence.size} · ${plan.sequence[currentSequenceIndex].title}",
                            color = Success,
                            fontSize = Dimens.fontTitle,
                            fontWeight = FontWeight.Bold,
                            lineHeight = Dimens.lineHeightTitle
                        )
                    } else if (plan.multiAngles.isNotEmpty()) {
                        Text(
                            text = "${currentAngleIndex + 1}/${plan.multiAngles.size} · ${plan.multiAngles[currentAngleIndex].title}",
                            color = Danger,
                            fontSize = Dimens.fontTitle,
                            fontWeight = FontWeight.Bold,
                            lineHeight = Dimens.lineHeightTitle
                        )
                    } else {
                        Text(
                            text = plan.poseName,
                            color = TextPrimary,
                            fontSize = Dimens.fontTitle,
                            fontWeight = FontWeight.Bold,
                            lineHeight = Dimens.lineHeightTitle
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // 右侧：倒计时徽章 + 拍照计数 + 分数环 + 设置 + 帮助按钮
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm), verticalAlignment = Alignment.CenterVertically) {
            // 倒计时徽章（timerSeconds > 0 时显示）
            if (timerSeconds > 0) {
                Box(
                    modifier = Modifier
                        .background(Accent.copy(alpha = 0.2f), RoundedCornerShape(Dimens.radiusFull))
                        .border(Dimens.strokeThin, Accent.copy(alpha = 0.6f), RoundedCornerShape(Dimens.radiusFull))
                        .padding(horizontal = Dimens.spacingSm, vertical = Dimens.spacingXs)
                ) {
                    Text(
                        text = "${timerSeconds}s",
                        color = Accent,
                        fontSize = Dimens.fontCaption,
                        fontWeight = FontWeight.Bold,
                        lineHeight = Dimens.lineHeightCaption
                    )
                }
            }
            // 拍照计数徽章（captureCount > 0 时显示，激活 captureCount 死代码）
            if (captureCount > 0) {
                Box(
                    modifier = Modifier
                        .background(Success.copy(alpha = 0.18f), RoundedCornerShape(Dimens.radiusFull))
                        .border(Dimens.strokeThin, Success.copy(alpha = 0.6f), RoundedCornerShape(Dimens.radiusFull))
                        .padding(horizontal = Dimens.spacingSm, vertical = Dimens.spacingXs),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.PhotoCamera,
                            contentDescription = null,
                            tint = Success,
                            modifier = Modifier.size(Dimens.iconXs)
                        )
                        Spacer(modifier = Modifier.width(Dimens.spacingXs))
                        Text(
                            text = "已拍 $captureCount",
                            color = Success,
                            fontSize = Dimens.fontCaption,
                            fontWeight = FontWeight.Bold,
                            lineHeight = Dimens.lineHeightCaption
                        )
                    }
                }
            }
            if (isSceneReady) {
                ScoreRing(score = score, isAligned = isAligned)
            }
            // 设置按钮
            IconButton(
                onClick = onSettings,
                modifier = Modifier
                    .size(Dimens.buttonIcon)
                    .background(Surface, CircleShape)
                    .border(Dimens.strokeThin, Border, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "设置",
                    tint = TextPrimary,
                    modifier = Modifier.size(Dimens.iconSm)
                )
            }
            // 帮助按钮
            IconButton(
                onClick = onHelp,
                modifier = Modifier
                    .size(Dimens.buttonIcon)
                    .background(Surface, CircleShape)
                    .border(Dimens.strokeThin, Border, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.QuestionMark,
                    contentDescription = "帮助",
                    tint = TextPrimary,
                    modifier = Modifier.size(Dimens.iconSm)
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
    // 分数弹性补间：国内用户偏好平滑过渡的分数变化
    val animatedScore by animateFloatAsState(
        targetValue = score,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "scoreValue"
    )
    // 对齐达标瞬间放大反馈
    val alignedScale by animateFloatAsState(
        targetValue = if (isAligned) 1.1f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "alignedScale"
    )

    Box(
        modifier = Modifier.size(Dimens.scoreRingSize).scale(alignedScale),
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
                sweepAngle = (animatedScore / 100f * 360f).coerceIn(0f, 360f),
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
            text = "${animatedScore.toInt()}",
            color = TextPrimary,
            fontSize = Dimens.fontLabel,
            fontWeight = FontWeight.Black,
            lineHeight = Dimens.lineHeightLabel
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
    timerSeconds: Int = 0,
    isImmersiveMode: Boolean = false,
    isBurstMode: Boolean = false,
    isTorchOn: Boolean = false,
    currentFlashMode: Int = 0,
    currentAspectRatioName: String = "4:5",
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
    onToggleBurstMode: () -> Unit,
    onToggleTorch: () -> Unit,
    onCycleFlashMode: () -> Unit = {},
    onCycleAspectRatio: () -> Unit = {},
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
                        Color.Black.copy(alpha = if (isImmersiveMode) 0.35f else 0.55f)
                    )
                )
            )
            .padding(bottom = navBarPadding.calculateBottomPadding() + 16.dp)
    ) {
        // 沉浸模式仅保留快门，其他全部隐藏
        if (!isImmersiveMode) {
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
        } else {
            Spacer(modifier = Modifier.height(20.dp))
        }

        // ── 主控制行（沉浸模式也保留）──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.screenMarginH),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左：相册缩略图（沉浸模式隐藏）
            if (!isImmersiveMode) {
                GalleryThumbnailButton(
                    recentRecords = recentRecords,
                    onClick = onOpenGallery
                )
            } else {
                Spacer(modifier = Modifier.size(Dimens.thumbSize))
            }

            // 中：快门按钮
            ShutterButton(
                isAligned = isAligned,
                isVlogRecording = isVlogRecording,
                isVlogMerging = isVlogMerging,
                breathingScale = breathingScale,
                timerSeconds = timerSeconds,
                onCapture = onCapture,
                onStopVlog = onStopVlog,
                isCompactHeight = isCompactHeight
            )

            // 右：切换摄像头 + 计时器 + 手电筒（沉浸模式隐藏）
            if (!isImmersiveMode) {
                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.spacingMd)) {
                    IconButton(
                        onClick = onSwitchCamera,
                        modifier = Modifier
                            .size(Dimens.buttonAction)
                            .background(Surface, CircleShape)
                            .border(Dimens.strokeThin, Border, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Cameraswitch,
                            contentDescription = "切换摄像头",
                            tint = TextPrimary,
                            modifier = Modifier.size(Dimens.iconMd)
                        )
                    }

                    IconButton(
                        onClick = onToggleTimer,
                        modifier = Modifier
                            .size(Dimens.buttonAction)
                            .background(
                                if (timerSeconds > 0) Accent.copy(alpha = 0.25f) else Surface,
                                CircleShape
                            )
                            .border(
                                width = if (timerSeconds > 0) Dimens.strokeRegular else Dimens.strokeThin,
                                color = if (timerSeconds > 0) Accent.copy(alpha = 0.7f) else Border,
                                shape = CircleShape
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Timer,
                            contentDescription = "计时器",
                            tint = if (timerSeconds > 0) Accent else TextSecondary,
                            modifier = Modifier.size(Dimens.iconMd)
                        )
                    }

                    // 连拍模式按钮
                    IconButton(
                        onClick = onToggleBurstMode,
                        modifier = Modifier
                            .size(Dimens.buttonAction)
                            .background(
                                if (isBurstMode) Accent.copy(alpha = 0.25f) else Surface,
                                CircleShape
                            )
                            .border(
                                width = if (isBurstMode) Dimens.strokeRegular else Dimens.strokeThin,
                                color = if (isBurstMode) Accent.copy(alpha = 0.7f) else Border,
                                shape = CircleShape
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.BurstMode,
                            contentDescription = "连拍",
                            tint = if (isBurstMode) Accent else TextSecondary,
                            modifier = Modifier.size(Dimens.iconMd)
                        )
                    }

                    // 三态闪光灯按钮（激活 cycleFlashMode/setFlashMode 死代码）
                    // 0=关闭, 1=自动, 2=常亮
                    val flashActive = currentFlashMode != 0
                    IconButton(
                        onClick = onCycleFlashMode,
                        modifier = Modifier
                            .size(Dimens.buttonAction)
                            .background(
                                if (flashActive) Accent.copy(alpha = 0.25f) else Surface,
                                CircleShape
                            )
                            .border(
                                width = if (flashActive) Dimens.strokeRegular else Dimens.strokeThin,
                                color = if (flashActive) Accent.copy(alpha = 0.7f) else Border,
                                shape = CircleShape
                            )
                    ) {
                        Icon(
                            imageVector = when (currentFlashMode) {
                                1 -> Icons.Default.FlashAuto
                                2 -> Icons.Default.FlashOn
                                else -> Icons.Default.FlashOff
                            },
                            contentDescription = when (currentFlashMode) {
                                1 -> "自动闪光"
                                2 -> "常亮闪光"
                                else -> "闪光关闭"
                            },
                            tint = if (flashActive) Accent else TextSecondary,
                            modifier = Modifier.size(Dimens.iconMd)
                        )
                    }

                    // 画幅切换按钮（激活 AspectRatio 死代码）
                    IconButton(
                        onClick = onCycleAspectRatio,
                        modifier = Modifier
                            .size(Dimens.buttonAction)
                            .background(Surface, CircleShape)
                            .border(Dimens.strokeThin, Border, CircleShape)
                    ) {
                        Text(
                            text = currentAspectRatioName,
                            color = TextPrimary,
                            fontSize = Dimens.fontCaption,
                            fontWeight = FontWeight.Bold,
                            lineHeight = Dimens.lineHeightCaption
                        )
                    }
                }
            } else {
                Spacer(modifier = Modifier.size(Dimens.thumbSize))
            }
        }
    }
}

// ── 变焦指示器（双指缩放激活，显示当前缩放倍率）──
@Composable
fun ZoomIndicator(
    zoomLevel: Float,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(Dimens.radiusXl))
            .padding(horizontal = Dimens.spacingMd, vertical = Dimens.spacingXs)
    ) {
        Text(
            text = String.format("%.1fx", zoomLevel),
            color = Color.White,
            fontSize = Dimens.fontLabel,
            fontWeight = FontWeight.Medium,
            lineHeight = Dimens.lineHeightLabel
        )
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
        modifier = modifier.padding(horizontal = Dimens.screenMarginH),
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
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
    // 选中弹性放大（国产 App 常见的选中反馈）
    val cardScale by animateFloatAsState(
        targetValue = if (isSelected) 1.04f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "cardScale"
    )

    Column(
        modifier = Modifier
            .scale(cardScale)
            .clip(RoundedCornerShape(Dimens.radiusMd))
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
                width = if (isSelected) Dimens.strokeRegular else Dimens.strokeThin,
                color = if (isSelected) Accent.copy(alpha = 0.75f) else Border,
                shape = RoundedCornerShape(Dimens.radiusMd)
            )
            .clickable { onClick() }
            .padding(horizontal = Dimens.spacingMd, vertical = Dimens.spacingSm),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs + 2.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.spacingXs + 3.dp)) {
            Text(
                text = plan.poseName,
                color = TextPrimary,
                fontSize = Dimens.fontLabel,
                fontWeight = FontWeight.Bold,
                lineHeight = Dimens.lineHeightLabel,
                maxLines = 1
            )
        }

        if (isSelected) {
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.spacingXs + 1.dp)) {
                TagBadge(
                    text = when (plan.composition) {
                        com.poseai.app.model.CompositionRule.CENTER -> "居中"
                        com.poseai.app.model.CompositionRule.RULE_OF_THIRDS -> "三分法"
                        com.poseai.app.model.CompositionRule.DIAGONAL -> "对角线"
                        com.poseai.app.model.CompositionRule.FRAME_WITHIN_FRAME -> "框架"
                        com.poseai.app.model.CompositionRule.GOLDEN_SPIRAL -> "黄金螺旋"
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
                RoundedCornerShape(Dimens.radiusFull)
            )
            .border(
                width = if (active) Dimens.strokeThin else 0.dp,
                color = if (active) Accent.copy(alpha = 0.4f) else Color.Transparent,
                shape = RoundedCornerShape(Dimens.radiusFull)
            )
            .padding(horizontal = Dimens.spacingSm - 1.dp, vertical = Dimens.spacingXs)
    ) {
        Text(
            text = text,
            color = if (active) Accent else TextPrimary.copy(alpha = 0.7f),
            fontSize = Dimens.fontCaption,
            fontWeight = FontWeight.SemiBold,
            lineHeight = Dimens.lineHeightCaption
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
            .size(Dimens.thumbSize)
            .clip(RoundedCornerShape(Dimens.radiusMd))
            .background(Surface)
            .border(Dimens.strokeThin, Border, RoundedCornerShape(Dimens.radiusMd))
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
                modifier = Modifier.size(Dimens.iconLg)
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
    timerSeconds: Int = 0,
    onCapture: () -> Unit,
    onStopVlog: () -> Unit,
    isCompactHeight: Boolean
) {
    val buttonSize = if (isCompactHeight) 82.dp else Dimens.shutterSize
    val haptics = com.poseai.app.util.Haptics.rememberHapticController()
    // 按下缩放反馈（弹性更强，更接近国产App的"果冻"手感）
    var isPressed by remember { mutableStateOf(false) }
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.88f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "pressScale"
    )
    // 对齐时光环呼吸（独立于呼吸缩放，避免互相干扰）
    val ringAlpha = remember { Animatable(0.4f) }
    LaunchedEffect(isAligned) {
        if (isAligned) {
            ringAlpha.animateTo(
                targetValue = 0.9f,
                animationSpec = infiniteRepeatable(
                    animation = tween(900, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                )
            )
        } else {
            ringAlpha.snapTo(0.4f)
        }
    }

    Box(
        modifier = Modifier
            .size(buttonSize)
            .scale(pressScale),
        contentAlignment = Alignment.Center
    ) {
        if (isVlogRecording || isVlogMerging) {
            // 录制中：红色停止按钮
            Box(
                modifier = Modifier
                    .size(Dimens.shutterInner)
                    .background(Danger, CircleShape)
                    .clickable {
                        isPressed = true
                        haptics.perform(com.poseai.app.util.Haptics.Level.CLICK)
                        onStopVlog()
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {
                                haptics.perform(com.poseai.app.util.Haptics.Level.CLICK)
                                onStopVlog()
                            },
                            onPress = {
                                isPressed = true
                                tryAwaitRelease()
                                isPressed = false
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                // 录制状态：方形停止图标外加微弱光晕
                Box(
                    modifier = Modifier
                        .size(Dimens.iconLg)
                        .background(Color.White, RoundedCornerShape(Dimens.radiusSm))
                )
            }
        } else {
            // 对齐时呼吸动效
            if (isAligned) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawCircle(
                        color = SuccessGlow.copy(alpha = ringAlpha.value),
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
                    .size(Dimens.shutterInner)
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
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {
                                // 对齐达标时给"成功"双段反馈；普通拍照给"重"反馈
                                haptics.perform(
                                    if (isAligned) com.poseai.app.util.Haptics.Level.SUCCESS
                                    else com.poseai.app.util.Haptics.Level.HEAVY
                                )
                                onCapture()
                            },
                            onPress = {
                                isPressed = true
                                haptics.perform(com.poseai.app.util.Haptics.Level.TICK)
                                tryAwaitRelease()
                                isPressed = false
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                if (timerSeconds > 0) {
                    // 倒计时模式：显示秒数
                    Text(
                        text = "${timerSeconds}",
                        color = if (isAligned) Color.White else Color.Black.copy(alpha = 0.75f),
                        fontSize = Dimens.fontDisplay,
                        fontWeight = FontWeight.Black,
                        lineHeight = Dimens.lineHeightDisplay
                    )
                } else if (isAligned) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = "拍照",
                        tint = Color.White,
                        modifier = Modifier.size(Dimens.iconLg)
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
            .background(Surface, RoundedCornerShape(Dimens.radiusFull))
            .border(Dimens.strokeThin, Color.Yellow.copy(alpha = 0.4f), RoundedCornerShape(Dimens.radiusFull))
            .padding(horizontal = Dimens.spacingLg, vertical = Dimens.spacingSm)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Lightbulb,
                contentDescription = null,
                tint = Color.Yellow,
                modifier = Modifier.size(Dimens.iconXs)
            )
            Text(
                text = "光线不足，移到明亮处效果更好",
                color = TextPrimary.copy(alpha = 0.9f),
                fontSize = Dimens.fontLabel,
                fontWeight = FontWeight.Medium,
                lineHeight = Dimens.lineHeightLabel
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
    val gridAlpha by animateFloatAsState(
        targetValue = if (composition != null) 1f else 0f,
        animationSpec = tween(Dimens.durationNormal),
        label = "gridAlpha"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val baseAlpha = 0.22f * gridAlpha
        val intersectionAlpha = 0.55f * gridAlpha
        val intersectionRadius = 5.dp.toPx()
        val lineWidth = Dimens.strokeRegular.toPx()

        if (composition == com.poseai.app.model.CompositionRule.RULE_OF_THIRDS) {
            val lineColor = Color.White.copy(alpha = baseAlpha)
            // 四条三分线
            val thirdsX = listOf(w / 3f, 2 * w / 3f)
            val thirdsY = listOf(h / 3f, 2 * h / 3f)
            for (x in thirdsX) {
                drawLine(color = lineColor, start = Offset(x, 0f), end = Offset(x, h), strokeWidth = lineWidth)
            }
            for (y in thirdsY) {
                drawLine(color = lineColor, start = Offset(0f, y), end = Offset(w, y), strokeWidth = lineWidth)
            }
            // 四个交点高亮（三分法精髓）
            val dotColor = Accent.copy(alpha = intersectionAlpha)
            for (x in thirdsX) {
                for (y in thirdsY) {
                    drawCircle(color = dotColor.copy(alpha = 0.15f), radius = intersectionRadius * 3, center = Offset(x, y))
                    drawCircle(color = dotColor, radius = intersectionRadius, center = Offset(x, y))
                }
            }
        } else if (composition == com.poseai.app.model.CompositionRule.CENTER) {
            val lineColor = Color.White.copy(alpha = baseAlpha)
            drawLine(color = lineColor, start = Offset(w / 2f, 0f), end = Offset(w / 2f, h), strokeWidth = lineWidth)
            drawLine(color = lineColor, start = Offset(0f, h / 2f), end = Offset(w, h / 2f), strokeWidth = lineWidth)
            // 中央矩形
            val centerW = w * 0.5f
            val centerH = h * 0.7f
            drawRect(
                color = lineColor.copy(alpha = baseAlpha * 0.7f),
                topLeft = Offset((w - centerW) / 2f, (h - centerH) / 2f),
                size = Size(centerW, centerH),
                style = Stroke(width = lineWidth)
            )
            // 中心点高亮
            drawCircle(color = Accent.copy(alpha = intersectionAlpha), radius = intersectionRadius, center = Offset(w / 2f, h / 2f))
        } else if (composition == com.poseai.app.model.CompositionRule.DIAGONAL) {
            val lineColor = Color.White.copy(alpha = baseAlpha)
            drawLine(color = lineColor, start = Offset(0f, 0f), end = Offset(w, h), strokeWidth = lineWidth)
            drawLine(color = lineColor, start = Offset(w, 0f), end = Offset(0f, h), strokeWidth = lineWidth)
            // 中心交点高亮
            drawCircle(color = Accent.copy(alpha = intersectionAlpha), radius = intersectionRadius, center = Offset(w / 2f, h / 2f))
        } else if (composition == com.poseai.app.model.CompositionRule.FRAME_WITHIN_FRAME) {
            val outerColor = Color.White.copy(alpha = baseAlpha)
            val innerColor = Accent.copy(alpha = baseAlpha * 1.2f)
            drawRect(
                color = outerColor,
                topLeft = Offset(0f, 0f),
                size = Size(w, h),
                style = Stroke(width = lineWidth)
            )
            val innerMargin = minOf(w, h) * 0.15f
            drawRect(
                color = innerColor,
                topLeft = Offset(innerMargin, innerMargin),
                size = Size(w - 2 * innerMargin, h - 2 * innerMargin),
                style = Stroke(width = Dimens.strokeBold.toPx())
            )
        } else if (composition == com.poseai.app.model.CompositionRule.GOLDEN_SPIRAL) {
            drawGoldenSpiral(w, h, gridAlpha)
        }

        // 水平仪（通用：所有构图模式都显示）
        if (gridAlpha > 0.3f) {
            val levelY = h * 0.5f
            val levelWidth = w * 0.25f
            val levelAlpha = 0.3f * gridAlpha
            drawLine(
                color = Accent.copy(alpha = levelAlpha),
                start = Offset((w - levelWidth) / 2f, levelY),
                end = Offset((w + levelWidth) / 2f, levelY),
                strokeWidth = Dimens.strokeRegular.toPx(),
                cap = StrokeCap.Round
            )
            // 水平仪两端的短竖线
            val segmentH = 8.dp.toPx()
            drawLine(
                color = Accent.copy(alpha = levelAlpha * 0.7f),
                start = Offset((w - levelWidth) / 2f, levelY - segmentH),
                end = Offset((w - levelWidth) / 2f, levelY + segmentH),
                strokeWidth = Dimens.strokeThin.toPx()
            )
            drawLine(
                color = Accent.copy(alpha = levelAlpha * 0.7f),
                start = Offset((w + levelWidth) / 2f, levelY - segmentH),
                end = Offset((w + levelWidth) / 2f, levelY + segmentH),
                strokeWidth = Dimens.strokeThin.toPx()
            )
        }
    }
}

/**
 * 绘制黄金螺旋线（Golden Spiral / Fibonacci Spiral）
 * 实现：在黄金分割矩形内递归绘制 1/4 圆弧，形成螺旋曲线
 * 同时绘制黄金分割线作为辅助参考
 */
private fun DrawScope.drawGoldenSpiral(w: Float, h: Float, gridAlpha: Float = 1f) {
    val phi = 1.61803398875f  // 黄金比例
    val spiralColor = Accent.copy(alpha = 0.28f * gridAlpha)
    val guideColor = Color.White.copy(alpha = 0.14f * gridAlpha)

    // 计算适合屏幕的黄金矩形（保持比例）
    // 默认竖屏拍摄，黄金矩形适配竖屏
    val isPortrait = h > w
    val rect: androidx.compose.ui.geometry.Rect
    if (isPortrait) {
        // 竖屏：黄金矩形以高度为基准
        val rectW = h / phi
        val rectH = h
        val left = (w - rectW) / 2f
        val top = 0f
        rect = androidx.compose.ui.geometry.Rect(left, top, left + rectW, top + rectH)
    } else {
        // 横屏：黄金矩形以宽度为基准
        val rectW = w
        val rectH = w / phi
        val left = 0f
        val top = (h - rectH) / 2f
        rect = androidx.compose.ui.geometry.Rect(left, top, left + rectW, top + rectH)
    }

    // 绘制黄金分割辅助线
    drawLine(
        color = guideColor,
        start = Offset(rect.left + rect.width * (1f / phi), rect.top),
        end = Offset(rect.left + rect.width * (1f / phi), rect.bottom),
        strokeWidth = 1.dp.toPx()
    )
    drawLine(
        color = guideColor,
        start = Offset(rect.left, rect.top + rect.height * (1f / phi)),
        end = Offset(rect.right, rect.top + rect.height * (1f / phi)),
        strokeWidth = 1.dp.toPx()
    )

    // 绘制黄金螺旋曲线：递归绘制 1/4 圆弧
    // 螺旋方向：从大矩形开始，逐级缩小，每个 1/4 圆弧都在黄金分割子矩形内
    var currentRect = rect
    var direction = 0  // 0=左上→右下弧, 1=右上→左下弧, 2=右下→左上弧, 3=左下→右上弧
    val maxIterations = 8  // 限制迭代次数避免无限递归

    val path = Path()
    var firstPoint = true

    for (i in 0 until maxIterations) {
        val rw = currentRect.width
        val rh = currentRect.height
        if (rw < 20f || rh < 20f) break

        // 1/4 圆弧所在正方形的边长 = 短边
        val squareSize = minOf(rw, rh)
        // 圆弧中心和起止角度根据方向决定
        val arcRect: androidx.compose.ui.geometry.Rect
        val startAngle: Float
        val sweepAngle: Float = 90f

        when (direction % 4) {
            0 -> {
                // 圆弧在左上角，圆心在左上角对角顶点
                arcRect = androidx.compose.ui.geometry.Rect(
                    currentRect.left, currentRect.top,
                    currentRect.left + squareSize, currentRect.top + squareSize
                )
                startAngle = 0f  // 从右开始顺时针
            }
            1 -> {
                // 圆弧在右上角
                arcRect = androidx.compose.ui.geometry.Rect(
                    currentRect.right - squareSize, currentRect.top,
                    currentRect.right, currentRect.top + squareSize
                )
                startAngle = 90f
            }
            2 -> {
                // 圆弧在右下角
                arcRect = androidx.compose.ui.geometry.Rect(
                    currentRect.right - squareSize, currentRect.bottom - squareSize,
                    currentRect.right, currentRect.bottom
                )
                startAngle = 180f
            }
            else -> {
                // 圆弧在左下角
                arcRect = androidx.compose.ui.geometry.Rect(
                    currentRect.left, currentRect.bottom - squareSize,
                    currentRect.left + squareSize, currentRect.bottom
                )
                startAngle = 270f
            }
        }

        if (firstPoint) {
            // 移动到弧的起点
            val startX = when (direction % 4) {
                0 -> arcRect.right
                1 -> arcRect.right
                2 -> arcRect.left
                else -> arcRect.left
            }
            val startY = when (direction % 4) {
                0 -> arcRect.top
                1 -> arcRect.bottom
                2 -> arcRect.bottom
                else -> arcRect.top
            }
            path.moveTo(startX, startY)
            firstPoint = false
        }

        path.arcTo(
            rect = arcRect,
            startAngleDegrees = startAngle,
            sweepAngleDegrees = sweepAngle,
            forceMoveTo = false
        )

        // 缩小到下一个黄金分割子矩形
        currentRect = when (direction % 4) {
            0 -> androidx.compose.ui.geometry.Rect(
                currentRect.left + squareSize / phi, currentRect.top,
                currentRect.right, currentRect.bottom
            )
            1 -> androidx.compose.ui.geometry.Rect(
                currentRect.left, currentRect.top + squareSize / phi,
                currentRect.right, currentRect.bottom
            )
            2 -> androidx.compose.ui.geometry.Rect(
                currentRect.left, currentRect.top,
                currentRect.right - squareSize / phi, currentRect.bottom
            )
            else -> androidx.compose.ui.geometry.Rect(
                currentRect.left, currentRect.top,
                currentRect.right, currentRect.bottom - squareSize / phi
            )
        }
        direction++
    }

    drawPath(
        path = path,
        color = spiralColor,
        style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round)
    )

    // 绘制黄金矩形外框（淡）
    drawRect(
        color = guideColor,
        topLeft = Offset(rect.left, rect.top),
        size = Size(rect.width, rect.height),
        style = Stroke(width = 1.dp.toPx())
    )
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
    isImmersiveMode: Boolean = false,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp.dp
    val screenHeightDp = configuration.screenHeightDp.dp

    // 是否双人模式：plan.secondaryPosePoints 非空时
    val isDualMode = plan.secondaryPosePoints.isNotEmpty()

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
        // 沉浸模式降低透明度
        val immersiveFactor = if (isImmersiveMode) 0.35f else 1f
        val silAlpha = (if (isAligned) 0.22f else 0.12f) * immersiveFactor
        val strokeAlpha = (if (isAligned) 1f else 0.35f) * immersiveFactor
        val strokeWidth = if (isAligned) 3.dp.toPx() else 1.8.dp.toPx()

        if (isDualMode) {
            // 双人模式：左右各偏移 18% 屏宽渲染两个剪影
            val offset = w * 0.18f
            // 左侧剪影（主姿势）
            val leftMainX = centerX - silW / 2f - offset
            drawSilhouetteShape(
                left = leftMainX,
                top = centerY - silH / 2f,
                width = silW,
                height = silH,
                fillColor = silColor.copy(alpha = silAlpha),
                strokeColor = silColor.copy(alpha = strokeAlpha),
                strokeWidth = strokeWidth,
                isDashed = !isAligned
            )
            // 右侧剪影（备选姿势）
            val leftSecondaryX = centerX - silW / 2f + offset
            drawSilhouetteShape(
                left = leftSecondaryX,
                top = centerY - silH / 2f,
                width = silW,
                height = silH,
                fillColor = silColor.copy(alpha = silAlpha),
                strokeColor = silColor.copy(alpha = strokeAlpha),
                strokeWidth = strokeWidth,
                isDashed = !isAligned
            )

            // P4-6 双人模式主副标注：在剪影上方绘制主/副小色块标记
            // 解决前置摄像头下用户分清左右主副姿势的需求
            val labelColor = silColor.copy(alpha = strokeAlpha)
            val labelSize = 14.dp.toPx()
            val labelOffsetY = 12.dp.toPx()
            val labelW = 16.dp.toPx()
            // 左侧标注色块（主姿势）
            drawRect(
                color = labelColor,
                topLeft = Offset(
                    leftMainX + silW / 2f - labelW / 2f,
                    centerY - silH / 2f - labelOffsetY - labelSize
                ),
                size = Size(labelW, labelSize)
            )
            // 右侧标注色块（备选姿势）
            drawRect(
                color = labelColor.copy(alpha = strokeAlpha * 0.6f),
                topLeft = Offset(
                    leftSecondaryX + silW / 2f - labelW / 2f,
                    centerY - silH / 2f - labelOffsetY - labelSize
                ),
                size = Size(labelW, labelSize)
            )
        } else {
            // 单人模式
            val left = centerX - silW / 2f
            val top = centerY - silH / 2f
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
        }

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
    // 骨架颜色根据评分渐变：低分→白（中性），高分→青墨绿（对齐佳）
    val skeletonColor by animateColorAsState(
        targetValue = when {
            score >= 80f -> Success
            score >= 60f -> Accent
            score >= 40f -> Color(0xFF8EB4B0) // 青墨绿 50% 混合白
            else -> Color.White.copy(alpha = 0.6f)
        },
        animationSpec = tween(Dimens.durationFast),
        label = "skeletonColor"
    )
    val skeletonAlpha by animateFloatAsState(
        targetValue = if (score >= 80f) 0.95f else 0.65f,
        animationSpec = tween(Dimens.durationNormal),
        label = "skeletonAlpha"
    )

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        lines.forEach { (pa, pb) ->
            drawLine(
                color = skeletonColor,
                start = Offset(pa.x * w, pa.y * h),
                end = Offset(pb.x * w, pb.y * h),
                strokeWidth = if (score >= 80f) 5.dp.toPx() else 3.5.dp.toPx(),
                alpha = skeletonAlpha,
                cap = StrokeCap.Round
            )
        }

        points.values.forEach { point ->
            val cx = point.x * w
            val cy = point.y * h
            drawCircle(
                color = skeletonColor,
                radius = if (score >= 80f) 7.dp.toPx() else 5.dp.toPx(),
                center = Offset(cx, cy),
                alpha = skeletonAlpha
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// 以下组件从原文件保留，或微调以匹配新设计
// ═══════════════════════════════════════════════════════════════

@Composable
fun WarningBanner(text: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(color.copy(alpha = 0.2f), RoundedCornerShape(Dimens.radiusSm))
            .padding(horizontal = Dimens.spacingMd, vertical = Dimens.spacingSm),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = color,
            fontSize = Dimens.fontLabel,
            fontWeight = FontWeight.Medium,
            lineHeight = Dimens.lineHeightLabel
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
fun VlogSubtitle(text: String, isRecording: Boolean = false, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .padding(horizontal = Dimens.spacingXxl, vertical = Dimens.spacingSm),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = Dimens.fontHeadline,
            fontWeight = FontWeight.Bold,
            lineHeight = Dimens.lineHeightHeadline,
            modifier = Modifier
                .background(
                    if (isRecording) Danger.copy(alpha = 0.5f) else Color.Black.copy(alpha = 0.6f),
                    RoundedCornerShape(Dimens.radiusMd)
                )
                .padding(horizontal = Dimens.spacingXl, vertical = Dimens.spacingMd)
        )
    }
}

@Composable
fun VlogStatusIndicator(
    isRecording: Boolean,
    isMerging: Boolean,
    currentClip: Int,
    totalClips: Int,
    dotAlpha: Float = 1f,
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
                    .background(Error.copy(alpha = dotAlpha), CircleShape)
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
    PhotoReviewDialog(
        photoPath = photoPath,
        onDismiss = onDismiss,
        onShare = null,
        onDelete = null,
        onRetake = null
    )
}

@Composable
fun PhotoReviewDialog(
    photoPath: String,
    onDismiss: () -> Unit,
    onShare: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    onRetake: (() -> Unit)? = null
) {
    val context = LocalContext.current
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

            // 底部操作栏：分享 / 重拍 / 删除
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = 48.dp, start = 32.dp, end = 32.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // 分享按钮
                ReviewActionButton(
                    icon = Icons.Default.Share,
                    label = "分享",
                    onClick = {
                        if (onShare != null) {
                            onShare.invoke()
                        } else {
                            val file = java.io.File(photoPath)
                            if (file.exists()) {
                                val uri = androidx.core.content.FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    file
                                )
                                val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                    type = "image/jpeg"
                                    putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(android.content.Intent.createChooser(shareIntent, "分享照片"))
                            }
                        }
                    }
                )

                // 重拍按钮
                if (onRetake != null) {
                    ReviewActionButton(
                        icon = Icons.Default.CameraAlt,
                        label = "重拍",
                        onClick = {
                            onRetake.invoke()
                        }
                    )
                }

                // 删除按钮
                if (onDelete != null) {
                    ReviewActionButton(
                        icon = Icons.Default.Delete,
                        label = "删除",
                        onClick = {
                            onDelete.invoke()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ReviewActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .background(Color.White.copy(alpha = 0.15f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            color = Color.White,
            fontSize = 12.sp
        )
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
    val autoRecommendEnabled by viewModel.autoRecommendEnabled.collectAsState()
    val autoRecommendInterval by viewModel.autoRecommendInterval.collectAsState()
    val screenFillLightEnabled by viewModel.screenFillLightEnabled.collectAsState()
    val screenFillLightIntensity by viewModel.screenFillLightIntensity.collectAsState()
    val jpegQuality by viewModel.jpegQuality.collectAsState()
    val outputFormat by viewModel.outputFormat.collectAsState()
    val hdrEnabled by viewModel.hdrEnabled.collectAsState()
    var smileThreshold by remember { mutableStateOf(viewModel.getCurrentSmileThreshold()) }
    val storeManager = com.poseai.app.PoseAIApp.getStoreManager()
    val themeMode by storeManager.themeMode.collectAsState(initial = 0)
    val scope = rememberCoroutineScope()

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(BackgroundDark, RoundedCornerShape(20.dp))
                .padding(20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "设置",
                color = TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))

            // ── 主题模式 ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "主题模式", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Text(
                        text = when (themeMode) {
                            1 -> "强制暗色"
                            2 -> "强制亮色"
                            else -> "跟随系统"
                        },
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }
                Row(
                    modifier = Modifier
                        .background(Surface, RoundedCornerShape(16.dp))
                        .padding(2.dp)
                ) {
                    val modes = listOf(
                        "自动" to 0,
                        "暗色" to 1,
                        "亮色" to 2
                    )
                    modes.forEach { (label, value) ->
                        val isSelected = themeMode == value
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (isSelected) Accent else Color.Transparent)
                                .clickable {
                                    scope.launch { storeManager.setThemeMode(value) }
                                }
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = label,
                                color = if (isSelected) Color.White else TextSecondary,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))

            SettingToggle(
                title = "微笑快门",
                description = "检测到微笑时自动拍照",
                checked = smileEnabled,
                onCheckedChange = { viewModel.toggleSmile(it) }
            )
            // 微笑灵敏度滑块（激活 StoreManager.smileThreshold 持久化）
            if (smileEnabled) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 8.dp, end = 8.dp, bottom = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "微笑灵敏度", color = TextSecondary, fontSize = 13.sp)
                        Text(
                            text = when {
                                smileThreshold <= 0.5f -> "高"
                                smileThreshold >= 0.85f -> "低"
                                else -> "标准"
                            },
                            color = Accent,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Slider(
                        value = smileThreshold,
                        onValueChange = {
                            smileThreshold = it
                            viewModel.setSmileThreshold(it)
                        },
                        valueRange = 0.3f..0.95f,
                        colors = SliderDefaults.colors(
                            thumbColor = Accent,
                            activeTrackColor = Accent
                        )
                    )
                }
            }
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
                title = "水印",
                description = "照片右下角添加 PoseAI 标识",
                checked = watermarkEnabled,
                onCheckedChange = { viewModel.toggleWatermark(it) }
            )
            // 自动推荐开关（激活 StoreManager.autoRecommendEnabled 持久化）
            SettingToggle(
                title = "姿势亲近度推荐",
                description = "根据当前姿势自动推荐方案",
                checked = autoRecommendEnabled,
                onCheckedChange = { viewModel.setAutoRecommendEnabled(it) }
            )
            // 自动抓拍间隔滑块（激活 StoreManager.setAutoRecommendInterval 持久化）
            if (autoRecommendEnabled) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 8.dp, end = 8.dp, bottom = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "推荐检测间隔", color = TextSecondary, fontSize = 13.sp)
                        Text(
                            text = "${autoRecommendInterval}ms",
                            color = Accent,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Slider(
                        value = autoRecommendInterval.toFloat(),
                        onValueChange = { viewModel.setAutoRecommendInterval(it.toInt()) },
                        valueRange = 500f..5000f,
                        steps = 8,
                        colors = SliderDefaults.colors(
                            thumbColor = Accent,
                            activeTrackColor = Accent
                        )
                    )
                }
            }
            // 屏幕补光开关 + 强度滑块（激活 setScreenFillLightIntensity 死代码）
            SettingToggle(
                title = "屏幕补光",
                description = "暗光环境用屏幕补光",
                checked = screenFillLightEnabled,
                onCheckedChange = { viewModel.toggleScreenFillLight() }
            )
            if (screenFillLightEnabled) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 8.dp, end = 8.dp, bottom = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "补光强度", color = TextSecondary, fontSize = 13.sp)
                        Text(
                            text = "${(screenFillLightIntensity * 100).toInt()}%",
                            color = Accent,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Slider(
                        value = screenFillLightIntensity,
                        onValueChange = { viewModel.setScreenFillLightIntensity(it) },
                        valueRange = 0.1f..1f,
                        colors = SliderDefaults.colors(
                            thumbColor = Accent,
                            activeTrackColor = Accent
                        )
                    )
                }
            }

            // ── 画质设置分区 ──
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "画质设置",
                color = TextSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            // HDR 开关
            SettingToggle(
                title = "HDR 高动态范围",
                description = "暗部提亮 + 高光压缩，保留更多细节",
                checked = hdrEnabled,
                onCheckedChange = { viewModel.setHdrEnabled(it) }
            )
            // 输出格式切换：JPEG / WEBP
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "输出格式", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Text(text = "WEBP 体积更小，JPEG 兼容性更好", color = TextSecondary, fontSize = 12.sp)
                }
                Row(
                    modifier = Modifier
                        .background(Surface, RoundedCornerShape(16.dp))
                        .padding(2.dp)
                ) {
                    val formats = listOf("JPEG" to 0, "WEBP" to 1)
                    formats.forEach { (label, value) ->
                        val isSelected = outputFormat == value
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (isSelected) Accent else Color.Transparent)
                                .clickable { viewModel.setOutputFormat(value) }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = label,
                                color = if (isSelected) Color.White else TextSecondary,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
            }
            // JPEG 压缩质量滑块
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 8.dp, bottom = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "压缩质量", color = TextSecondary, fontSize = 13.sp)
                    Text(
                        text = "$jpegQuality",
                        color = Accent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                Slider(
                    value = jpegQuality.toFloat(),
                    onValueChange = { viewModel.setJpegQuality(it.toInt()) },
                    valueRange = 50f..100f,
                    colors = SliderDefaults.colors(
                        thumbColor = Accent,
                        activeTrackColor = Accent
                    )
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "省空间", color = TextSecondary, fontSize = 11.sp)
                    Text(text = "高清", color = TextSecondary, fontSize = 11.sp)
                }
            }
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
                // 自定义姿势入口
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Accent.copy(alpha = 0.12f))
                            .border(1.dp, Accent.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                            .clickable {
                                onDismiss()
                                viewModel.openCustomPoseSheet()
                            }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = Accent,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "自定义姿势",
                                color = Accent,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "保存当前姿势为模板，随时复用",
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = Accent,
                            modifier = Modifier.size(20.dp)
                        )
                    }
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
    filterIntensity: Int,
    beautyEnabled: Boolean,
    smoothingLevel: Int,
    whiteningLevel: Int,
    slimmingLevel: Int,
    currentSticker: StickerEngine.Sticker,
    onFilterSelected: (PhotoFilterEngine.Filter) -> Unit,
    onFilterIntensityChanged: (Int) -> Unit,
    onBeautyEnabledChanged: (Boolean) -> Unit,
    onSmoothingChanged: (Int) -> Unit,
    onWhiteningChanged: (Int) -> Unit,
    onSlimmingChanged: (Int) -> Unit,
    onStickerSelected: (StickerEngine.Sticker) -> Unit,
    onQuickBeauty: () -> Unit,
    onDisableBeauty: () -> Unit,
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("滤镜", "美颜", "贴纸")

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SurfaceDark,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.spacingLg, vertical = Dimens.spacingMd)
        ) {
            // 标题
            Text(
                text = "滤镜美颜",
                color = TextPrimary,
                fontSize = Dimens.fontHeadline,
                fontWeight = FontWeight.Bold,
                lineHeight = Dimens.lineHeightHeadline,
                modifier = Modifier.padding(bottom = Dimens.spacingMd)
            )

            // Tab 切换
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Surface, RoundedCornerShape(Dimens.radiusFull))
                    .padding(Dimens.spacingXs),
                horizontalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
            ) {
                tabs.forEachIndexed { index, title ->
                    val isSelected = index == selectedTab
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(Dimens.radiusFull))
                            .background(
                                if (isSelected) Accent else Color.Transparent
                            )
                            .clickable { selectedTab = index }
                            .padding(vertical = Dimens.spacingSm),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = title,
                            color = if (isSelected) Color.White else TextSecondary,
                            fontSize = Dimens.fontLabel,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            lineHeight = Dimens.lineHeightLabel
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(Dimens.spacingLg))

            // 内容区
            when (selectedTab) {
                0 -> {
                    // ── 滤镜 Tab ──
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingMd),
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

                    // 滤镜强度滑块（非原图时显示）
                    if (currentFilter != PhotoFilterEngine.Filter.ORIGINAL) {
                        Spacer(modifier = Modifier.height(Dimens.spacingLg))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "强度",
                                color = TextSecondary,
                                fontSize = Dimens.fontLabel,
                                fontWeight = FontWeight.Medium,
                                lineHeight = Dimens.lineHeightLabel
                            )
                            Spacer(modifier = Modifier.width(Dimens.spacingMd))
                            Slider(
                                value = filterIntensity.toFloat(),
                                onValueChange = { onFilterIntensityChanged(it.toInt()) },
                                valueRange = 0f..100f,
                                modifier = Modifier.weight(1f),
                                colors = SliderDefaults.colors(
                                    thumbColor = Accent,
                                    activeTrackColor = Accent,
                                    inactiveTrackColor = Border
                                )
                            )
                            Spacer(modifier = Modifier.width(Dimens.spacingSm))
                            Text(
                                text = "$filterIntensity%",
                                color = Accent,
                                fontSize = Dimens.fontLabel,
                                fontWeight = FontWeight.Bold,
                                lineHeight = Dimens.lineHeightLabel,
                                modifier = Modifier.width(40.dp)
                            )
                        }
                    }
                }
                1 -> {
                    // ── 美颜 Tab ──
                    // 总开关 + 一键美颜
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "美颜",
                                color = TextPrimary,
                                fontSize = Dimens.fontBody,
                                fontWeight = FontWeight.Medium,
                                lineHeight = Dimens.lineHeightBody
                            )
                            Spacer(modifier = Modifier.width(Dimens.spacingSm))
                            Switch(
                                checked = beautyEnabled,
                                onCheckedChange = { onBeautyEnabledChanged(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Accent,
                                    checkedTrackColor = Accent.copy(alpha = 0.3f)
                                )
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm)) {
                            // 一键美颜
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(Dimens.radiusFull))
                                    .background(Accent.copy(alpha = 0.15f))
                                    .border(Dimens.strokeThin, Accent.copy(alpha = 0.4f), RoundedCornerShape(Dimens.radiusFull))
                                    .clickable { onQuickBeauty() }
                                    .padding(horizontal = Dimens.spacingMd, vertical = Dimens.spacingSm)
                            ) {
                                Text(
                                    text = "一键美颜",
                                    color = Accent,
                                    fontSize = Dimens.fontCaption,
                                    fontWeight = FontWeight.Bold,
                                    lineHeight = Dimens.lineHeightCaption
                                )
                            }
                            // 关闭
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(Dimens.radiusFull))
                                    .background(Surface)
                                    .border(Dimens.strokeThin, Border, RoundedCornerShape(Dimens.radiusFull))
                                    .clickable { onDisableBeauty() }
                                    .padding(horizontal = Dimens.spacingMd, vertical = Dimens.spacingSm)
                            ) {
                                Text(
                                    text = "关闭",
                                    color = TextSecondary,
                                    fontSize = Dimens.fontCaption,
                                    fontWeight = FontWeight.Medium,
                                    lineHeight = Dimens.lineHeightCaption
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(Dimens.spacingLg))

                    // 三个滑块（仅在美颜开启时可用）
                    val sliderEnabled = beautyEnabled
                    val sliderAlpha = if (sliderEnabled) 1f else 0.4f

                    // 磨皮
                    BeautySliderItem(
                        label = "磨皮",
                        value = smoothingLevel,
                        enabled = sliderEnabled,
                        alpha = sliderAlpha,
                        onValueChange = { onSmoothingChanged(it) }
                    )
                    Spacer(modifier = Modifier.height(Dimens.spacingMd))

                    // 美白
                    BeautySliderItem(
                        label = "美白",
                        value = whiteningLevel,
                        enabled = sliderEnabled,
                        alpha = sliderAlpha,
                        onValueChange = { onWhiteningChanged(it) }
                    )
                    Spacer(modifier = Modifier.height(Dimens.spacingMd))

                    // 瘦脸
                    BeautySliderItem(
                        label = "瘦脸",
                        value = slimmingLevel,
                        enabled = sliderEnabled,
                        alpha = sliderAlpha,
                        onValueChange = { onSlimmingChanged(it) }
                    )
                }
                2 -> {
                    // ── 贴纸 Tab ──
                    Text(
                        text = "选择贴纸装饰",
                        color = TextSecondary,
                        fontSize = Dimens.fontCaption,
                        fontWeight = FontWeight.Medium,
                        lineHeight = Dimens.lineHeightCaption,
                        modifier = Modifier.padding(bottom = Dimens.spacingMd)
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingMd),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(StickerEngine.Sticker.values()) { sticker ->
                            StickerItem(
                                sticker = sticker,
                                isSelected = sticker == currentSticker,
                                onClick = { onStickerSelected(sticker) }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(Dimens.spacingXxl))
        }
    }
}

/** 美颜滑块项 */
@Composable
private fun BeautySliderItem(
    label: String,
    value: Int,
    enabled: Boolean,
    alpha: Float,
    onValueChange: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer(alpha = alpha),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = TextPrimary,
            fontSize = Dimens.fontLabel,
            fontWeight = FontWeight.Medium,
            lineHeight = Dimens.lineHeightLabel,
            modifier = Modifier.width(48.dp)
        )
        Spacer(modifier = Modifier.width(Dimens.spacingSm))
        Slider(
            value = value.toFloat(),
            onValueChange = { if (enabled) onValueChange(it.toInt()) },
            valueRange = 0f..100f,
            enabled = enabled,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(
                thumbColor = Accent,
                activeTrackColor = Accent,
                inactiveTrackColor = Border,
                disabledThumbColor = TextSecondary,
                disabledActiveTrackColor = TextSecondary,
                disabledInactiveTrackColor = Border
            )
        )
        Spacer(modifier = Modifier.width(Dimens.spacingSm))
        Text(
            text = if (enabled) "$value" else "--",
            color = if (enabled) Accent else TextSecondary,
            fontSize = Dimens.fontLabel,
            fontWeight = FontWeight.Bold,
            lineHeight = Dimens.lineHeightLabel,
            modifier = Modifier.width(32.dp)
        )
    }
}

// ═══════════════════════════════════════════════════════════════
// 贴纸项组件
// ═══════════════════════════════════════════════════════════════

/** 贴纸图标映射 */
private val stickerIcons = mapOf(
    StickerEngine.Sticker.NONE to "⊗",
    StickerEngine.Sticker.DATE to "📅",
    StickerEngine.Sticker.WHITE_FRAME to "⬜",
    StickerEngine.Sticker.ROUNDED_FRAME to "🔲",
    StickerEngine.Sticker.GLOW to "✨",
    StickerEngine.Sticker.VIGNETTE to "🌑",
    StickerEngine.Sticker.RETRO_DATE to "🕐",
    StickerEngine.Sticker.FILM_FRAME to "🎞"
)

@Composable
private fun StickerItem(
    sticker: StickerEngine.Sticker,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(Dimens.radiusMd))
            .background(
                if (isSelected) Accent.copy(alpha = 0.15f) else Color.Transparent
            )
            .border(
                width = if (isSelected) Dimens.strokeThick else Dimens.strokeThin,
                color = if (isSelected) Accent else Border,
                shape = RoundedCornerShape(Dimens.radiusMd)
            )
            .clickable { onClick() }
            .padding(Dimens.spacingSm)
    ) {
        Text(
            text = stickerIcons[sticker] ?: "?",
            fontSize = 24.sp
        )
        Spacer(modifier = Modifier.height(Dimens.spacingXs))
        Text(
            text = sticker.displayName,
            color = if (isSelected) Accent else TextSecondary,
            fontSize = Dimens.fontCaption,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            lineHeight = Dimens.lineHeightCaption
        )
    }
}

// ═══════════════════════════════════════════════════════════════
// 分享面板：水印定制 + 话题 + 系统分享
// ═══════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ShareBottomSheet(
    photoPath: String?,
    watermarkStyle: ShareEngine.WatermarkStyle,
    watermarkPosition: ShareEngine.WatermarkPosition,
    username: String,
    location: String,
    topics: List<String>,
    caption: String,
    onWatermarkStyleChanged: (ShareEngine.WatermarkStyle) -> Unit,
    onWatermarkPositionChanged: (ShareEngine.WatermarkPosition) -> Unit,
    onUsernameChanged: (String) -> Unit,
    onLocationChanged: (String) -> Unit,
    onCaptionChanged: (String) -> Unit,
    onAddTopic: (String) -> Unit,
    onRemoveTopic: (String) -> Unit,
    onShare: () -> Unit,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var isSharing by remember { mutableStateOf(false) }
    var topicInput by remember { mutableStateOf("") }
    var previewBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // 加载预览图（缩小，便于在面板中展示）
    LaunchedEffect(photoPath) {
        if (photoPath != null) {
            withContext(kotlinx.coroutines.Dispatchers.IO) {
                val opts = android.graphics.BitmapFactory.Options().apply {
                    inSampleSize = 4
                }
                val bmp = android.graphics.BitmapFactory.decodeFile(photoPath, opts)
                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    previewBitmap = bmp
                }
            }
        }
    }

    // 水印或话题变化时刷新预览
    val watermarkedPreview = remember(
        previewBitmap, watermarkStyle, watermarkPosition, username, location, topics
    ) {
        previewBitmap?.let { src ->
            try {
                val config = ShareEngine.ShareConfig(
                    watermarkStyle = watermarkStyle,
                    watermarkPosition = watermarkPosition,
                    username = username,
                    location = location,
                    sceneName = "",
                    topics = topics,
                    caption = ""
                )
                ShareEngine.applyWatermarkAndTopics(src, config)
            } catch (_: Exception) {
                src
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SurfaceDark,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.spacingLg, vertical = Dimens.spacingMd)
        ) {
            // 标题
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "分享",
                    color = TextPrimary,
                    fontSize = Dimens.fontHeadline,
                    fontWeight = FontWeight.Bold,
                    lineHeight = Dimens.lineHeightHeadline
                )
                Text(
                    text = "定制你的水印和话题",
                    color = TextSecondary,
                    fontSize = Dimens.fontCaption,
                    lineHeight = Dimens.lineHeightCaption
                )
            }

            Spacer(modifier = Modifier.height(Dimens.spacingMd))

            // 预览图
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
                    .clip(RoundedCornerShape(Dimens.radiusMd))
                    .background(Surface)
                    .border(Dimens.strokeThin, Border, RoundedCornerShape(Dimens.radiusMd)),
                contentAlignment = Alignment.Center
            ) {
                val preview = watermarkedPreview
                if (preview != null) {
                    androidx.compose.foundation.Image(
                        bitmap = androidx.compose.ui.graphics.asImageBitmap(preview),
                        contentDescription = "预览",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = androidx.compose.ui.layout.ContentScale.Fit
                    )
                } else {
                    Text(
                        text = "暂无预览",
                        color = TextSecondary,
                        fontSize = Dimens.fontBody,
                        lineHeight = Dimens.lineHeightBody
                    )
                }
            }

            Spacer(modifier = Modifier.height(Dimens.spacingLg))

            // ── 水印风格选择 ──
            Text(
                text = "水印风格",
                color = TextPrimary,
                fontSize = Dimens.fontBody,
                fontWeight = FontWeight.Medium,
                lineHeight = Dimens.lineHeightBody
            )
            Spacer(modifier = Modifier.height(Dimens.spacingSm))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
            ) {
                items(ShareEngine.WatermarkStyle.values()) { style ->
                    ShareStyleChip(
                        text = style.displayName,
                        isSelected = style == watermarkStyle,
                        onClick = { onWatermarkStyleChanged(style) }
                    )
                }
            }

            // 仅在非 NONE、非 MINIMAL 时显示位置选择
            if (watermarkStyle != ShareEngine.WatermarkStyle.NONE &&
                watermarkStyle != ShareEngine.WatermarkStyle.MINIMAL
            ) {
                Spacer(modifier = Modifier.height(Dimens.spacingMd))
                Text(
                    text = "水印位置",
                    color = TextPrimary,
                    fontSize = Dimens.fontBody,
                    fontWeight = FontWeight.Medium,
                    lineHeight = Dimens.lineHeightBody
                )
                Spacer(modifier = Modifier.height(Dimens.spacingSm))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
                ) {
                    items(ShareEngine.WatermarkPosition.values()) { pos ->
                        ShareStyleChip(
                            text = pos.displayName,
                            isSelected = pos == watermarkPosition,
                            onClick = { onWatermarkPositionChanged(pos) }
                        )
                    }
                }
            }

            // 仅在需要用户名/地点时显示输入框
            if (watermarkStyle == ShareEngine.WatermarkStyle.USERNAME_BRAND) {
                Spacer(modifier = Modifier.height(Dimens.spacingMd))
                ShareTextField(
                    label = "用户名",
                    value = username,
                    onValueChange = onUsernameChanged,
                    placeholder = "输入用户名"
                )
            }
            if (watermarkStyle == ShareEngine.WatermarkStyle.DATE_LOCATION) {
                Spacer(modifier = Modifier.height(Dimens.spacingMd))
                ShareTextField(
                    label = "地点",
                    value = location,
                    onValueChange = onLocationChanged,
                    placeholder = "输入地点"
                )
            }

            Spacer(modifier = Modifier.height(Dimens.spacingLg))

            // ── 话题标签 ──
            Text(
                text = "话题",
                color = TextPrimary,
                fontSize = Dimens.fontBody,
                fontWeight = FontWeight.Medium,
                lineHeight = Dimens.lineHeightBody
            )
            Spacer(modifier = Modifier.height(Dimens.spacingSm))

            // 已添加的话题
            if (topics.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Dimens.spacingXs),
                    verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
                ) {
                    topics.forEach { topic ->
                        TopicChip(
                            text = "#$topic",
                            onRemove = { onRemoveTopic(topic) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(Dimens.spacingSm))
            }

            // 话题输入框
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = topicInput,
                    onValueChange = { topicInput = it },
                    placeholder = {
                        Text(
                            "添加话题（回车确认）",
                            color = TextSecondary,
                            fontSize = Dimens.fontBody,
                            lineHeight = Dimens.lineHeightBody
                        )
                    },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        cursorColor = Accent,
                        focusedBorderColor = Accent,
                        unfocusedBorderColor = Border
                    ),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Text,
                        imeAction = androidx.compose.ui.text.input.ImeAction.Done
                    ),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                        onDone = {
                            if (topicInput.isNotBlank()) {
                                onAddTopic(topicInput)
                                topicInput = ""
                            }
                        }
                    )
                )
                Spacer(modifier = Modifier.width(Dimens.spacingSm))
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(Dimens.radiusMd))
                        .background(Accent)
                        .clickable {
                            if (topicInput.isNotBlank()) {
                                onAddTopic(topicInput)
                                topicInput = ""
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "添加",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(Dimens.spacingLg))

            // ── 分享文案 ──
            Text(
                text = "分享文案",
                color = TextPrimary,
                fontSize = Dimens.fontBody,
                fontWeight = FontWeight.Medium,
                lineHeight = Dimens.lineHeightBody
            )
            Spacer(modifier = Modifier.height(Dimens.spacingSm))
            OutlinedTextField(
                value = caption,
                onValueChange = onCaptionChanged,
                placeholder = {
                    Text(
                        "写点什么...",
                        color = TextSecondary,
                        fontSize = Dimens.fontBody,
                        lineHeight = Dimens.lineHeightBody
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 80.dp, max = 140.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    cursorColor = Accent,
                    focusedBorderColor = Accent,
                    unfocusedBorderColor = Border
                )
            )

            Spacer(modifier = Modifier.height(Dimens.spacingLg))

            // ── 分享按钮 ──
            Button(
                onClick = {
                    if (!isSharing) {
                        isSharing = true
                        scope.launch {
                            try {
                                onShare()
                            } finally {
                                isSharing = false
                            }
                        }
                    }
                },
                enabled = !isSharing && photoPath != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Accent,
                    disabledContainerColor = Accent.copy(alpha = 0.4f)
                ),
                shape = RoundedCornerShape(Dimens.radiusFull)
            ) {
                if (isSharing) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(Dimens.spacingSm))
                    Text(
                        text = "分享到...",
                        color = Color.White,
                        fontSize = Dimens.fontBody,
                        fontWeight = FontWeight.Bold,
                        lineHeight = Dimens.lineHeightBody
                    )
                }
            }

            Spacer(modifier = Modifier.height(Dimens.spacingXxl))
        }
    }
}

@Composable
private fun ShareStyleChip(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(Dimens.radiusFull))
            .background(if (isSelected) Accent else Surface)
            .border(
                Dimens.strokeThin,
                if (isSelected) Accent else Border,
                RoundedCornerShape(Dimens.radiusFull)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = Dimens.spacingMd, vertical = Dimens.spacingXs)
    ) {
        Text(
            text = text,
            color = if (isSelected) Color.White else TextSecondary,
            fontSize = Dimens.fontCaption,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            lineHeight = Dimens.lineHeightCaption
        )
    }
}

@Composable
private fun ShareTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            color = TextSecondary,
            fontSize = Dimens.fontCaption,
            lineHeight = Dimens.lineHeightCaption
        )
        Spacer(modifier = Modifier.height(Dimens.spacingXs))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = {
                Text(
                    placeholder,
                    color = TextSecondary,
                    fontSize = Dimens.fontBody,
                    lineHeight = Dimens.lineHeightBody
                )
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                cursorColor = Accent,
                focusedBorderColor = Accent,
                unfocusedBorderColor = Border
            )
        )
    }
}

@Composable
private fun TopicChip(
    text: String,
    onRemove: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(Dimens.radiusFull))
            .background(Accent.copy(alpha = 0.15f))
            .border(Dimens.strokeThin, Accent.copy(alpha = 0.4f), RoundedCornerShape(Dimens.radiusFull))
            .padding(horizontal = Dimens.spacingSm, vertical = Dimens.spacingXs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            color = Accent,
            fontSize = Dimens.fontCaption,
            fontWeight = FontWeight.Bold,
            lineHeight = Dimens.lineHeightCaption
        )
        Spacer(modifier = Modifier.width(Dimens.spacingXs))
        Icon(
            imageVector = Icons.Default.Close,
            contentDescription = "移除",
            tint = Accent,
            modifier = Modifier
                .size(14.dp)
                .clickable(onClick = onRemove)
        )
    }
}

// ═══════════════════════════════════════════════════════════════
// 自定义姿势面板：保存当前姿势 / 选择已保存姿势 / 删除
// ═══════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomPoseSheet(
    customPoses: List<com.poseai.app.store.CustomPose>,
    activeCustomPoseId: String?,
    detectedPointsCount: Int,
    onSaveCurrent: (String, String) -> Boolean,
    onApplyPose: (com.poseai.app.store.CustomPose) -> Unit,
    onDeletePose: (String) -> Unit,
    onClearActive: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var poseName by remember { mutableStateOf("") }
    var poseDesc by remember { mutableStateOf("") }
    var saveResult by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SurfaceDark,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.spacingLg, vertical = Dimens.spacingMd)
        ) {
            // 标题
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "自定义姿势",
                    color = TextPrimary,
                    fontSize = Dimens.fontHeadline,
                    fontWeight = FontWeight.Bold,
                    lineHeight = Dimens.lineHeightHeadline
                )
                Text(
                    text = "保存当前姿势为模板",
                    color = TextSecondary,
                    fontSize = Dimens.fontCaption,
                    lineHeight = Dimens.lineHeightCaption
                )
            }

            Spacer(modifier = Modifier.height(Dimens.spacingLg))

            // ── 保存当前姿势 ──
            Text(
                text = "保存当前姿势",
                color = TextPrimary,
                fontSize = Dimens.fontBody,
                fontWeight = FontWeight.Medium,
                lineHeight = Dimens.lineHeightBody
            )
            Spacer(modifier = Modifier.height(Dimens.spacingXs))
            Text(
                text = if (detectedPointsCount >= 3) {
                    "已检测到 $detectedPointsCount 个关键点，可以保存"
                } else {
                    "请先对准镜头摆好姿势，至少需要 3 个关键点（当前 $detectedPointsCount）"
                },
                color = if (detectedPointsCount >= 3) Success else Warning,
                fontSize = Dimens.fontCaption,
                lineHeight = Dimens.lineHeightCaption
            )

            Spacer(modifier = Modifier.height(Dimens.spacingSm))

            OutlinedTextField(
                value = poseName,
                onValueChange = { poseName = it },
                placeholder = {
                    Text(
                        "姿势名称（如：侧身回眸）",
                        color = TextSecondary,
                        fontSize = Dimens.fontBody,
                        lineHeight = Dimens.lineHeightBody
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    cursorColor = Accent,
                    focusedBorderColor = Accent,
                    unfocusedBorderColor = Border
                )
            )

            Spacer(modifier = Modifier.height(Dimens.spacingSm))

            OutlinedTextField(
                value = poseDesc,
                onValueChange = { poseDesc = it },
                placeholder = {
                    Text(
                        "姿势描述（可选）",
                        color = TextSecondary,
                        fontSize = Dimens.fontBody,
                        lineHeight = Dimens.lineHeightBody
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 60.dp, max = 100.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    cursorColor = Accent,
                    focusedBorderColor = Accent,
                    unfocusedBorderColor = Border
                )
            )

            Spacer(modifier = Modifier.height(Dimens.spacingSm))

            Button(
                onClick = {
                    val ok = onSaveCurrent(poseName, poseDesc)
                    saveResult = if (ok) "保存成功" else "保存失败，请先摆好姿势"
                    if (ok) {
                        poseName = ""
                        poseDesc = ""
                    }
                },
                enabled = detectedPointsCount >= 3 && poseName.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Accent,
                    disabledContainerColor = Accent.copy(alpha = 0.4f)
                ),
                shape = RoundedCornerShape(Dimens.radiusFull)
            ) {
                Icon(
                    imageVector = Icons.Default.Save,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(Dimens.spacingSm))
                Text(
                    "保存当前姿势",
                    color = Color.White,
                    fontSize = Dimens.fontBody,
                    fontWeight = FontWeight.Bold,
                    lineHeight = Dimens.lineHeightBody
                )
            }

            saveResult?.let { msg ->
                Spacer(modifier = Modifier.height(Dimens.spacingXs))
                Text(
                    text = msg,
                    color = if (msg.contains("成功")) Success else Warning,
                    fontSize = Dimens.fontCaption,
                    lineHeight = Dimens.lineHeightCaption
                )
            }

            Spacer(modifier = Modifier.height(Dimens.spacingLg))

            // ── 已保存的姿势列表 ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "我的姿势库 (${customPoses.size})",
                    color = TextPrimary,
                    fontSize = Dimens.fontBody,
                    fontWeight = FontWeight.Medium,
                    lineHeight = Dimens.lineHeightBody
                )
                if (activeCustomPoseId != null) {
                    Text(
                        text = "清除自定义",
                        color = Accent,
                        fontSize = Dimens.fontCaption,
                        fontWeight = FontWeight.Bold,
                        lineHeight = Dimens.lineHeightCaption,
                        modifier = Modifier.clickable { onClearActive() }
                    )
                }
            }

            Spacer(modifier = Modifier.height(Dimens.spacingSm))

            if (customPoses.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "暂无自定义姿势",
                        color = TextSecondary,
                        fontSize = Dimens.fontBody,
                        lineHeight = Dimens.lineHeightBody
                    )
                }
            } else {
                customPoses.forEach { pose ->
                    CustomPoseItem(
                        pose = pose,
                        isActive = pose.id == activeCustomPoseId,
                        onApply = { onApplyPose(pose) },
                        onDelete = { onDeletePose(pose.id) }
                    )
                    Spacer(modifier = Modifier.height(Dimens.spacingSm))
                }
            }

            Spacer(modifier = Modifier.height(Dimens.spacingXxl))
        }
    }
}

@Composable
private fun CustomPoseItem(
    pose: com.poseai.app.store.CustomPose,
    isActive: Boolean,
    onApply: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.radiusMd))
            .background(if (isActive) Accent.copy(alpha = 0.12f) else Surface)
            .border(
                Dimens.strokeThin,
                if (isActive) Accent else Border,
                RoundedCornerShape(Dimens.radiusMd)
            )
            .padding(Dimens.spacingMd),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 姿势缩略图（用 Canvas 绘制骨架）
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(Dimens.radiusSm))
                .background(SurfaceStrong),
            contentAlignment = Alignment.Center
        ) {
            PoseSkeletonPreview(pose.posePoints)
        }

        Spacer(modifier = Modifier.width(Dimens.spacingMd))

        // 名称和描述
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = pose.name,
                color = if (isActive) Accent else TextPrimary,
                fontSize = Dimens.fontBody,
                fontWeight = FontWeight.Bold,
                lineHeight = Dimens.lineHeightBody
            )
            if (pose.description.isNotEmpty()) {
                Text(
                    text = pose.description,
                    color = TextSecondary,
                    fontSize = Dimens.fontCaption,
                    lineHeight = Dimens.lineHeightCaption,
                    maxLines = 1
                )
            }
            Text(
                text = "${pose.posePoints.size} 个关键点",
                color = TextSecondary,
                fontSize = Dimens.fontCaption,
                lineHeight = Dimens.lineHeightCaption
            )
        }

        Spacer(modifier = Modifier.width(Dimens.spacingSm))

        // 应用按钮
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(if (isActive) Accent.copy(alpha = 0.2f) else Surface)
                .clickable(onClick = onApply),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isActive) Icons.Default.Check else Icons.Default.PlayArrow,
                contentDescription = if (isActive) "使用中" else "使用",
                tint = if (isActive) Accent else TextPrimary,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(Dimens.spacingXs))

        // 删除按钮
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Danger.copy(alpha = 0.15f))
                .clickable(onClick = onDelete),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "删除",
                tint = Danger,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/** 用 Canvas 绘制姿势骨架缩略图 */
@Composable
private fun PoseSkeletonPreview(posePoints: Map<String, PointF>) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        // 关节连接关系（简化版）
        val connections = listOf(
            "neck" to "leftShoulder",
            "neck" to "rightShoulder",
            "leftShoulder" to "leftElbow",
            "leftElbow" to "leftWrist",
            "rightShoulder" to "rightElbow",
            "rightElbow" to "rightWrist",
            "neck" to "leftHip",
            "neck" to "rightHip",
            "leftHip" to "leftKnee",
            "leftKnee" to "leftAnkle",
            "rightHip" to "rightKnee",
            "rightKnee" to "rightAnkle"
        )
        // 绘制连线
        connections.forEach { (a, b) ->
            val pa = posePoints[a]
            val pb = posePoints[b]
            if (pa != null && pb != null) {
                drawLine(
                    color = Accent.copy(alpha = 0.7f),
                    start = Offset(pa.x * w, pa.y * h),
                    end = Offset(pb.x * w, pb.y * h),
                    strokeWidth = 2f
                )
            }
        }
        // 绘制关节点
        posePoints.values.forEach { p ->
            drawCircle(
                color = Accent,
                radius = 3f,
                center = Offset(p.x * w, p.y * h)
            )
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
                .clip(RoundedCornerShape(Dimens.radiusMd))
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
                        PhotoFilterEngine.Filter.FILM -> Color(0xFF8D6E63)
                        PhotoFilterEngine.Filter.NOIR -> Color(0xFF212121)
                        PhotoFilterEngine.Filter.LIGHT -> Color(0xFFFFF8E1)
                        PhotoFilterEngine.Filter.NEON -> Color(0xFF00BCD4)
                    }
                )
                .border(
                    width = if (isSelected) Dimens.strokeBold else Dimens.strokeThin,
                    color = if (isSelected) Accent else Color.Transparent,
                    shape = RoundedCornerShape(Dimens.radiusMd)
                )
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = filter.displayName.take(1),
                color = Color.White,
                fontSize = Dimens.fontHeadline,
                fontWeight = FontWeight.Bold,
                lineHeight = Dimens.lineHeightHeadline
            )
        }
        Spacer(modifier = Modifier.height(Dimens.spacingXs + 2.dp))
        Text(
            text = filter.displayName,
            color = if (isSelected) Accent else TextSecondary,
            fontSize = Dimens.fontCaption,
            fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
            lineHeight = Dimens.lineHeightCaption
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
        Spacer(modifier = Modifier.width(Dimens.spacingMd))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = template.name,
                color = TextPrimary,
                fontSize = Dimens.fontTitle,
                fontWeight = FontWeight.Medium,
                lineHeight = Dimens.lineHeightTitle
            )
            Text(
                text = "${template.clips.size} 个分镜 · 约 ${template.clips.sumOf { it.duration.toInt() }} 秒",
                color = TextSecondary,
                fontSize = Dimens.fontLabel,
                lineHeight = Dimens.lineHeightLabel
            )
        }
        Icon(
            imageVector = Icons.Default.PlayArrow,
            contentDescription = "开始",
            tint = Accent,
            modifier = Modifier.size(Dimens.iconLg)
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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.spacingLg)
    ) {
        // 进度小圆点（国内主流：抖音/剪映风格）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.spacingMd, vertical = Dimens.spacingXs),
            horizontalArrangement = Arrangement.Center
        ) {
            for (i in 0 until totalSteps) {
                Box(
                    modifier = Modifier
                        .padding(horizontal = 3.dp)
                        .size(if (i == currentIndex) 8.dp else 6.dp)
                        .background(
                            when {
                                i < currentIndex -> Success.copy(alpha = 0.7f)
                                i == currentIndex -> Accent
                                else -> Color.White.copy(alpha = 0.2f)
                            },
                            CircleShape
                        )
                )
            }
        }

        Spacer(modifier = Modifier.height(Dimens.spacingXs))

        // 步骤卡片
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Surface, RoundedCornerShape(Dimens.radiusMd))
                .border(Dimens.strokeThin, Border, RoundedCornerShape(Dimens.radiusMd))
                .padding(horizontal = Dimens.spacingMd, vertical = Dimens.spacingSm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onPrevious, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Default.ChevronLeft,
                    contentDescription = "上一步",
                    tint = if (currentIndex > 0) TextPrimary else TextSecondary,
                    modifier = Modifier.size(Dimens.iconMd)
                )
            }
            Spacer(modifier = Modifier.width(Dimens.spacingSm))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "第 ${currentIndex + 1} 步 / 共 $totalSteps 步",
                    color = TextSecondary,
                    fontSize = Dimens.fontCaption,
                    lineHeight = Dimens.lineHeightCaption
                )
                Text(
                    text = stepName,
                    color = TextPrimary,
                    fontSize = Dimens.fontBody,
                    fontWeight = FontWeight.Medium,
                    lineHeight = Dimens.lineHeightBody
                )
            }
            // 跳过按钮（最后一页不显示）
            if (currentIndex < totalSteps - 1) {
                TextButton(onClick = onNext) {
                    Text(
                        text = "跳过",
                        color = TextSecondary,
                        fontSize = Dimens.fontCaption,
                        lineHeight = Dimens.lineHeightCaption
                    )
                }
            }
            IconButton(onClick = onNext, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = if (currentIndex >= totalSteps - 1) Icons.Default.Check else Icons.Default.ChevronRight,
                    contentDescription = if (currentIndex >= totalSteps - 1) "完成" else "下一步",
                    tint = if (currentIndex >= totalSteps - 1) Success else TextPrimary,
                    modifier = Modifier.size(Dimens.iconMd)
                )
            }
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
            .padding(horizontal = Dimens.spacingLg)
            .background(Surface, RoundedCornerShape(Dimens.radiusMd))
            .border(Dimens.strokeThin, Border, RoundedCornerShape(Dimens.radiusMd))
            .padding(horizontal = Dimens.spacingMd, vertical = Dimens.spacingSm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 机位图标（俯仰指示）
        Icon(
            imageVector = Icons.Default.RotateRight,
            contentDescription = null,
            tint = Accent,
            modifier = Modifier.size(Dimens.iconMd)
        )
        Spacer(modifier = Modifier.width(Dimens.spacingSm))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "机位 $currentAngleName",
                color = TextPrimary,
                fontSize = Dimens.fontLabel,
                fontWeight = FontWeight.Medium,
                lineHeight = Dimens.lineHeightLabel
            )
            if (angleCount > 1) {
                Text(
                    text = "共 $angleCount 个机位可选",
                    color = TextSecondary,
                    fontSize = Dimens.fontCaption,
                    lineHeight = Dimens.lineHeightCaption
                )
            }
        }
        TextButton(onClick = onNextAngle) {
            Text(
                text = if (angleCount > 1) "换一个" else "确定",
                color = Accent,
                fontSize = Dimens.fontCaption,
                lineHeight = Dimens.lineHeightCaption
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
            .padding(horizontal = Dimens.spacingLg)
            .background(Surface, RoundedCornerShape(Dimens.radiusMd))
            .border(Dimens.strokeThin, Border, RoundedCornerShape(Dimens.radiusMd))
            .padding(horizontal = Dimens.spacingMd, vertical = Dimens.spacingSm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Flip,
            contentDescription = null,
            tint = Accent,
            modifier = Modifier.size(Dimens.iconMd)
        )
        Spacer(modifier = Modifier.width(Dimens.spacingSm))
        Text(
            text = if (isSecondary) "目标姿势 · 备选" else "目标姿势 · 主姿势",
            color = TextPrimary,
            fontSize = Dimens.fontLabel,
            fontWeight = FontWeight.Medium,
            lineHeight = Dimens.lineHeightLabel,
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
            .padding(horizontal = Dimens.spacingLg)
            .clickable { onClick() }
            .background(Accent.copy(alpha = 0.15f), RoundedCornerShape(Dimens.radiusMd))
            .border(Dimens.strokeThin, Border, RoundedCornerShape(Dimens.radiusMd))
            .padding(horizontal = Dimens.spacingMd, vertical = Dimens.spacingSm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.MovieFilter,
            contentDescription = null,
            tint = Accent,
            modifier = Modifier.size(Dimens.iconMd)
        )
        Spacer(modifier = Modifier.width(Dimens.spacingSm))
        Text(
            text = "用当前姿势拍 Vlog",
            color = Accent,
            fontSize = Dimens.fontLabel,
            fontWeight = FontWeight.Medium,
            lineHeight = Dimens.lineHeightLabel,
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector = Icons.Default.PlayArrow,
            contentDescription = null,
            tint = Accent,
            modifier = Modifier.size(Dimens.iconSm)
        )
    }
}

// ═══════════════════════════════════════════════════════════════
// 点击对焦指示器（对焦框动画）
// ═══════════════════════════════════════════════════════════════

@Composable
fun FocusIndicatorOverlay(x: Float, y: Float) {
    val scale = remember { Animatable(1.4f) }
    val alpha = remember { Animatable(1f) }
    LaunchedEffect(x, y) {
        scale.snapTo(1.4f)
        alpha.snapTo(1f)
        scale.animateTo(
            targetValue = 1f,
            animationSpec = tween(250, easing = FastOutSlowInEasing)
        )
        kotlinx.coroutines.delay(800)
        alpha.animateTo(
            targetValue = 0f,
            animationSpec = tween(400, easing = FastOutSlowInEasing)
        )
    }

    val sizePx = with(LocalDensity.current) { 80.dp.toPx() }
    val strokeWidth = with(LocalDensity.current) { 2.dp.toPx() }
    val cornerLen = with(LocalDensity.current) { 14.dp.toPx() }
    val color = Accent.copy(alpha = alpha.value)

    Canvas(modifier = Modifier.fillMaxSize()) {
        val s = scale.value
        val cx = x
        val cy = y
        val halfSize = sizePx / 2f * s

        // 四角 L 形指示线
        val corners = listOf(
            // 左上
            Triple(Offset(cx - halfSize, cy - halfSize + cornerLen), Offset(cx - halfSize, cy - halfSize), Offset(cx - halfSize + cornerLen, cy - halfSize)),
            // 右上
            Triple(Offset(cx + halfSize - cornerLen, cy - halfSize), Offset(cx + halfSize, cy - halfSize), Offset(cx + halfSize, cy - halfSize + cornerLen)),
            // 左下
            Triple(Offset(cx - halfSize, cy + halfSize - cornerLen), Offset(cx - halfSize, cy + halfSize), Offset(cx - halfSize + cornerLen, cy + halfSize)),
            // 右下
            Triple(Offset(cx + halfSize - cornerLen, cy + halfSize), Offset(cx + halfSize, cy + halfSize), Offset(cx + halfSize, cy + halfSize - cornerLen))
        )
        corners.forEach { (a, b, c) ->
            drawLine(color, a, b, strokeWidth, cap = StrokeCap.Round)
            drawLine(color, b, c, strokeWidth, cap = StrokeCap.Round)
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// 倒计时大数字覆盖层
// ═══════════════════════════════════════════════════════════════

@Composable
fun CountdownOverlay(seconds: Int) {
    val haptics = com.poseai.app.util.Haptics.rememberHapticController()
    val scale = remember { Animatable(0.6f) }
    val alpha = remember { Animatable(1f) }
    LaunchedEffect(seconds) {
        // 每秒数字进场：先放大后收缩，伴随触觉反馈
        if (seconds > 0) {
            haptics.perform(com.poseai.app.util.Haptics.Level.CLICK)
        } else {
            // 0 即拍照瞬间：重反馈
            haptics.perform(com.poseai.app.util.Haptics.Level.HEAVY)
        }
        scale.snapTo(0.6f)
        alpha.snapTo(1f)
        scale.animateTo(
            targetValue = 1.3f,
            animationSpec = tween(180, easing = FastOutSlowInEasing)
        )
        scale.animateTo(
            targetValue = 1f,
            animationSpec = tween(280, easing = FastOutSlowInEasing)
        )
        // 数字末尾淡出
        alpha.animateTo(
            targetValue = 0.6f,
            animationSpec = tween(600, easing = FastOutSlowInEasing)
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f)),
        contentAlignment = Alignment.Center
    ) {
        // 数字外的光晕环（国产App常见效果）
        Canvas(modifier = Modifier.size(220.dp)) {
            drawCircle(
                color = Accent.copy(alpha = 0.18f * alpha.value),
                radius = 100.dp.toPx(),
                center = center,
                style = Stroke(width = 4.dp.toPx())
            )
        }
        Text(
            text = "$seconds",
            color = Color.White.copy(alpha = alpha.value),
            fontSize = Dimens.fontCountdown,
            fontWeight = FontWeight.Black,
            lineHeight = Dimens.fontCountdown,
            modifier = Modifier.scale(scale.value)
        )
    }
}

// ═══════════════════════════════════════════════════════════════
// 快门闪光覆盖层
// ═══════════════════════════════════════════════════════════════

@Composable
fun ShutterFlashOverlay() {
    val alpha = remember { Animatable(0.7f) }
    LaunchedEffect(Unit) {
        alpha.animateTo(
            targetValue = 0f,
            animationSpec = tween(180, easing = FastOutSlowInEasing)
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Accent.copy(alpha = alpha.value))
    )
}

/**
 * 连拍结果横幅：连拍完成后短暂展示拍到的缩略图列表
 * 激活 ShootingViewModel.burstPhotos StateFlow 的 UI 消费
 */
@Composable
fun BurstResultBanner(
    photoPaths: List<String>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 4 秒后自动消失
    LaunchedEffect(photoPaths) {
        kotlinx.coroutines.delay(4000)
        onDismiss()
    }

    Row(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(16.dp))
            .border(1.dp, Accent.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = Icons.Default.BurstMode,
            contentDescription = null,
            tint = Accent,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = "连拍 ${photoPaths.size} 张",
            color = Accent,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.width(4.dp))
        photoPaths.take(5).forEach { path ->
            coil.compose.AsyncImage(
                model = path,
                contentDescription = null,
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(6.dp)),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop
            )
        }
        if (photoPaths.size > 5) {
            Text(
                text = "+${photoPaths.size - 5}",
                color = TextSecondary,
                fontSize = Dimens.fontCaption,
                lineHeight = Dimens.lineHeightCaption
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// 距离提示文案
// ═══════════════════════════════════════════════════════════════

@Composable
fun DistanceHintText(
    text: String,
    modifier: Modifier = Modifier
) {
    // 入场/离场淡入淡出
    val hintAlpha by animateFloatAsState(
        targetValue = if (text.isNotEmpty()) 1f else 0f,
        animationSpec = tween(300),
        label = "hintAlpha"
    )

    Box(
        modifier = modifier
            .graphicsLayer(alpha = hintAlpha)
            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(Dimens.radiusXl))
            .border(Dimens.strokeThin, Accent.copy(alpha = 0.3f), RoundedCornerShape(Dimens.radiusXl))
            .padding(horizontal = Dimens.spacingXl, vertical = Dimens.spacingMd)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 方向提示图标
            Text(
                text = when {
                    text.contains("向左") || text.contains("往左") -> "⬅"
                    text.contains("向右") || text.contains("往右") -> "➡"
                    text.contains("站远") || text.contains("离远") || text.contains("后退") -> "⬆"
                    text.contains("靠近") || text.contains("站近") || text.contains("往前") -> "⬇"
                    text.contains("抬高") || text.contains("举手") -> "⬆"
                    text.contains("蹲下") || text.contains("降低") -> "⬇"
                    text.contains("稳定") || text.contains("保持") -> "✦"
                    text.contains("到位") || text.contains("OK") -> "✦"
                    else -> "⊙"
                },
                fontSize = Dimens.fontHeadline,
                lineHeight = Dimens.lineHeightHeadline
            )
            Spacer(modifier = Modifier.width(Dimens.spacingSm))
            Text(
                text = text,
                color = Color.White,
                fontSize = Dimens.fontBody,
                fontWeight = FontWeight.Medium,
                lineHeight = Dimens.lineHeightBody,
                textAlign = TextAlign.Center
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// 姿势引导弹窗（对齐 iOS PoseGuideSheet）
// ═══════════════════════════════════════════════════════════════

@Composable
fun PoseGuideSheet(
    plan: ShootingPlan?,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.7f))
                .clickable(onClick = onDismiss)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(
                        BackgroundDark,
                        RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                    )
                    .clickable(enabled = false) {} // 阻止点击穿透
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 拖拽条
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(4.dp)
                        .background(Color.White.copy(alpha = 0.3f), RoundedCornerShape(2.dp))
                )
                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "拍摄引导",
                    color = TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(16.dp))

                if (plan != null) {
                    // 姿势名称
                    Text(
                        text = plan.poseName,
                        color = Accent,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // 姿势详细描述（激活原 Models.kt 中未展示的 poseDescription 字段）
                    if (plan.poseDescription.isNotBlank()) {
                        Text(
                            text = plan.poseDescription,
                            color = TextPrimary.copy(alpha = 0.9f),
                            fontSize = 14.sp,
                            lineHeight = 22.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .background(Surface, RoundedCornerShape(12.dp))
                                .padding(14.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    // 构图提示
                    Text(
                        text = when (plan.composition) {
                            com.poseai.app.model.CompositionRule.CENTER -> "将人物置于画面中央"
                            com.poseai.app.model.CompositionRule.RULE_OF_THIRDS -> "将人物放在三分线交点处"
                            com.poseai.app.model.CompositionRule.DIAGONAL -> "沿对角线方向摆姿势"
                            com.poseai.app.model.CompositionRule.FRAME_WITHIN_FRAME -> "利用环境框架构图"
                            com.poseai.app.model.CompositionRule.GOLDEN_SPIRAL -> "沿黄金螺旋曲线摆放，主体在螺旋收敛点"
                        },
                        color = TextSecondary,
                        fontSize = 14.sp,
                        lineHeight = 22.sp,
                        textAlign = TextAlign.Center
                    )

                    // 场景提示
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = when {
                            plan.sequence.isNotEmpty() -> "此姿势包含 ${plan.sequence.size} 个分镜步骤，请按顺序完成"
                            plan.multiAngles.isNotEmpty() -> "此姿势支持 ${plan.multiAngles.size} 个角度，切换视角拍出不同感觉"
                            plan.secondaryPosePoints.isNotEmpty() -> "此姿势有备选方案，可切换尝试"
                            plan.vlogScript != null -> "此姿势支持 Vlog 录制，一键生成短视频"
                            else -> "对准剪影轮廓，微调直到评分达到 80 分以上"
                        },
                        color = TextPrimary.copy(alpha = 0.8f),
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .background(Surface, RoundedCornerShape(12.dp))
                            .padding(16.dp)
                    )
                } else {
                    Text(
                        text = "将镜头对准场景，AI 识别后将自动推荐姿势方案",
                        color = TextSecondary,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 关闭按钮
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Accent
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "知道了",
                        color = Color.Black,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// Review Prompt 应用评价引导弹窗（对齐 iOS SKStoreReviewController）
// 触发条件：拍摄次数累计达到阈值；用户手动 dismiss 后不再重复
// 实现：弹出友好的评价邀请对话框，引导用户去应用商店评分
// ═══════════════════════════════════════════════════════════════

@Composable
fun ReviewPromptDialog(
    onDismiss: () -> Unit,
    onRateNow: () -> Unit = {},
    onMaybeLater: () -> Unit = {},
    onNeverAsk: () -> Unit = {}
) {
    val context = LocalContext.current
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Surface, RoundedCornerShape(20.dp))
                .border(1.dp, Border, RoundedCornerShape(20.dp))
                .padding(24.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                // 顶部图标：五角星
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(Accent.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = Accent,
                        modifier = Modifier.size(36.dp)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "喜欢 PoseAI 吗？",
                    color = TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "您的评分能帮助更多人拍出好照片，也能激励我们持续优化体验",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(20.dp))

                // 五星评分预览（视觉示意）
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    repeat(5) { i ->
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = Accent,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // 主操作：立即评价
                Button(
                    onClick = {
                        // 尝试启动应用商店评分流程
                        try {
                            // Google Play 评分 Intent（端侧）
                            val packageName = context.packageName
                            val intent = android.content.Intent(
                                android.content.Intent.ACTION_VIEW,
                                android.net.Uri.parse("market://details?id=$packageName")
                            ).apply {
                                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        } catch (_: Exception) {
                            // 没有 Play Store 时打开网页版
                            try {
                                val packageName = context.packageName
                                val intent = android.content.Intent(
                                    android.content.Intent.ACTION_VIEW,
                                    android.net.Uri.parse("https://play.google.com/store/apps/details?id=$packageName")
                                ).apply {
                                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            } catch (_: Exception) {
                                // 完全无应用商店环境，仅关闭弹窗
                            }
                        }
                        onRateNow()
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Accent),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "立即评价",
                        color = Color.Black,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 次要操作：稍后
                TextButton(
                    onClick = {
                        onMaybeLater()
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "稍后再说",
                        color = TextSecondary,
                        fontSize = 14.sp
                    )
                }

                // 第三操作：不再询问
                TextButton(
                    onClick = {
                        onNeverAsk()
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "不再询问",
                        color = TextSecondary.copy(alpha = 0.7f),
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}