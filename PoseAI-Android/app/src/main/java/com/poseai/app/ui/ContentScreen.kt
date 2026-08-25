package com.poseai.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.camera.view.PreviewView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.poseai.app.design.Brand
import com.poseai.app.model.FrameRatio
import com.poseai.app.model.SceneType
import com.poseai.app.model.ShootingPlan
import com.poseai.app.ui.components.ARFootprintsOverlay
import com.poseai.app.ui.components.AiAdvisorBanner
import com.poseai.app.ui.components.CompositionGuideLines
import com.poseai.app.ui.components.FocusIndicator
import com.poseai.app.ui.components.LowLightGlowOverlay
import com.poseai.app.ui.components.PlanCard
import com.poseai.app.ui.components.RecordingProgressBar
import com.poseai.app.ui.components.ScoreRing
import com.poseai.app.ui.components.SceneScanningOverlay
import com.poseai.app.ui.components.SilhouetteGuideOverlay
import com.poseai.app.ui.components.VlogTextOverlay
import com.poseai.app.ui.components.ZoomLevelIndicator
import com.poseai.app.viewmodel.ShootingViewModel
import kotlinx.coroutines.delay

@Composable
fun ContentScreen(
    vm: ShootingViewModel,
    hasCameraPermission: Boolean,
    onShowHistory: () -> Unit,
    onShowGuide: () -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current

    val scene by vm.scene.collectAsStateWithLifecycle()
    val isSceneReady by vm.isSceneReady.collectAsStateWithLifecycle()
    val currentPlanIndex by vm.currentPlanIndex.collectAsStateWithLifecycle()
    val score by vm.score.collectAsStateWithLifecycle()
    val detectedPoses by vm.detectedPoses.collectAsStateWithLifecycle()
    val isLowLight by vm.isLowLight.collectAsStateWithLifecycle()
    val isImmersive by vm.isImmersiveMode.collectAsStateWithLifecycle()
    val isCapturing by vm.isCapturing.collectAsStateWithLifecycle()
    val showCompositionTip by vm.showCompositionTip.collectAsStateWithLifecycle()
    val showSpaceTip by vm.showSpaceTip.collectAsStateWithLifecycle()
    val aiSuggestion by vm.aiSuggestion.collectAsStateWithLifecycle()
    val timerSeconds by vm.timerSeconds.collectAsStateWithLifecycle()
    val countdown by vm.countdown.collectAsStateWithLifecycle()
    val showShutterFlash by vm.showShutterFlash.collectAsStateWithLifecycle()
    val recordCountdown by vm.recordCountdown.collectAsStateWithLifecycle()
    val isRecordingMode by vm.isRecordingMode.collectAsStateWithLifecycle()
    val devicePitch by vm.devicePitch.collectAsStateWithLifecycle()
    val activeSequenceIndex by vm.activeSequenceIndex.collectAsStateWithLifecycle()
    val activeAngleIndex by vm.activeAngleIndex.collectAsStateWithLifecycle()
    val activeVlogClipIndex by vm.activeVlogClipIndex.collectAsStateWithLifecycle()
    val isVlogRecording by vm.isVlogRecording.collectAsStateWithLifecycle()
    val displayVlogText by vm.displayVlogText.collectAsStateWithLifecycle()
    val burstImages by vm.burstImages.collectAsStateWithLifecycle()
    val capturedShotsCount by vm.capturedShotsCount.collectAsStateWithLifecycle()
    val expectedBurstCount by vm.expectedBurstCount.collectAsStateWithLifecycle()

    val plan = vm.currentPlan
    val isScanning = !isSceneReady && (scene == SceneType.UNKNOWN)

    // 手势和交互状态
    var zoomLevel by remember { mutableStateOf(1f) }
    var focusPoint by remember { mutableStateOf<Offset?>(null) }
    var showFocusIndicator by remember { mutableStateOf(false) }
    var showZoomPanel by remember { mutableStateOf(false) }

    // 自动隐藏对焦指示
    LaunchedEffect(showFocusIndicator) {
        if (showFocusIndicator) {
            delay(1500)
            showFocusIndicator = false
            focusPoint = null
        }
    }

    // 自动隐藏变焦面板
    LaunchedEffect(showZoomPanel) {
        if (showZoomPanel) {
            delay(3000)
            showZoomPanel = false
        }
    }

    // 相机绑定
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    DisposableEffect(hasCameraPermission, previewView) {
        if (hasCameraPermission && previewView != null) {
            val pv = previewView!!
            vm.manager.bindToCamera(lifecycleOwner, pv)
        }
        onDispose { if (!hasCameraPermission) vm.manager.cleanUp() }
    }

    // 切换摄像头后重新绑定
    var lastCameraFacing by remember { mutableStateOf(false) }
    LaunchedEffect(vm.manager.isFrontCamera) {
        if (vm.manager.isFrontCamera != lastCameraFacing) {
            lastCameraFacing = vm.manager.isFrontCamera
            previewView?.let { pv ->
                vm.manager.bindToCamera(lifecycleOwner, pv)
            }
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        val localDensity = LocalDensity.current
        val screenW = with(localDensity) { maxWidth.toPx() }
        val screenH = constraints.maxHeight.toFloat()

        // 1. 相机预览层
        if (hasCameraPermission) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { factoryCtx ->
                    PreviewView(factoryCtx).also { pv ->
                        pv.scaleType = PreviewView.ScaleType.FILL_CENTER
                        previewView = pv
                    }
                }
            )
        } else {
            Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
                Text("需要摄像头权限才能使用", color = Color.White, fontSize = 16.sp)
            }
        }

        // 2. 手势层：双击缩放 + 单击对焦 + 长按变焦面板
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = { offset ->
                            // 双击切换 1x ↔ 2x 缩放
                            val newZoom = if (zoomLevel > 1.5f) 1f else 2f
                            zoomLevel = newZoom
                            vm.setZoom(newZoom)
                        },
                        onTap = { offset ->
                            if (isImmersive) {
                                vm.toggleImmersiveMode()
                            } else {
                                // 单击切换沉浸模式
                                vm.toggleImmersiveMode()
                            }
                        }
                    )
                }
                .pointerInput(Unit) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { offset ->
                            // 长按显示变焦面板
                            showZoomPanel = true
                        },
                        onDragEnd = {
                            showZoomPanel = false
                        }
                    ) { change, dragAmount ->
                        // 拖动调整变焦
                        change.consume()
                        val delta = dragAmount.y / 200f
                        zoomLevel = (zoomLevel - delta).coerceIn(0.5f, 3f)
                        vm.setZoom(zoomLevel)
                    }
                }
        )

        // 2.5. 对焦指示器
        if (showFocusIndicator && focusPoint != null) {
            FocusIndicator(focusPoint!!)
        }

        // 2.6. 变焦水平条（长按显示）
        if (showZoomPanel) {
            ZoomLevelIndicator(
                currentZoom = zoomLevel,
                onZoomChange = { newZoom ->
                    zoomLevel = newZoom
                    vm.setZoom(newZoom)
                }
            )
        }

        // 3. 构图辅助线
        if (isSceneReady && !isImmersive) {
            CompositionGuideLines(plan?.composition)
        }

        // 4. 场景扫描 / 剪影引导
        if (!isSceneReady) {
            SceneScanningOverlay()
        } else if (plan != null) {
            if (plan.secondaryPosePoints != null) {
                val b0 = detectedPoses.getOrNull(0)?.bbox?.let { toComposeRect(it) }
                val b1 = detectedPoses.getOrNull(1)?.bbox?.let { toComposeRect(it) }
                SilhouetteGuideOverlay(vm.isReady, plan, b0, screenW, screenH, forceOffset = -screenW * 0.18f)
                SilhouetteGuideOverlay(vm.isReady, plan, b1, screenW, screenH, forceOffset = screenW * 0.18f)
            } else {
                val b = detectedPoses.firstOrNull()?.bbox?.let { toComposeRect(it) }
                SilhouetteGuideOverlay(vm.isReady, plan, b, screenW, screenH)
            }
        }

        // 5. AR 地面脚印
        if (!isImmersive && isSceneReady && plan?.frameRatio == com.poseai.app.model.FrameRatio.FULL_BODY) {
            ARFootprintsOverlay()
        }

        // 6. 顶部信息栏
        if (!isImmersive) {
            TopBar(
                scene = scene,
                plan = plan,
                isSceneReady = isSceneReady,
                score = score,
                isReady = vm.isReady,
                activeSequenceIndex = activeSequenceIndex,
                activeAngleIndex = activeAngleIndex,
                activeVlogClipIndex = activeVlogClipIndex,
                isVlogRecording = isVlogRecording,
                onGuide = onShowGuide
            )
        }

        // 7. 构图提示浮层
        if (!isImmersive && showCompositionTip && plan != null) {
            CompositionTipOverlay(plan)
        }

        // 8. AI 构图灵感
        if (!isImmersive && aiSuggestion != null) {
            AiAdvisorBanner(aiSuggestion!!)
        }

        // 9. 暗光提示 Banner
        if (!isImmersive && isLowLight && isSceneReady) {
            LowLightBanner()
        }

        // 10. 底部控制区
        BottomPanel(
            vm = vm,
            isSceneReady = isSceneReady && vm.availablePlans.isNotEmpty(),
            isImmersive = isImmersive,
            plans = vm.availablePlans,
            currentPlanIndex = currentPlanIndex,
            timerSeconds = timerSeconds,
            onHistory = onShowHistory
        )

        // 11. 底部提示 (俯仰警告/留白/角度)
        if (!isImmersive) {
            when {
                devicePitch < -0.35f -> PitchWarning()
                showSpaceTip && devicePitch >= -0.35f && !showCompositionTip -> SpaceTip()
                else -> {
                    plan?.multiAngles?.let { multi ->
                        if (activeAngleIndex < multi.size && multi[activeAngleIndex].requiredPitch != null) {
                            AngleGuide(multi[activeAngleIndex].requiredPitch!!, devicePitch)
                        }
                    }
                }
            }
        }

        // 12. Vlog 提词器
        if (!isImmersive && displayVlogText != null) {
            VlogTextOverlay(displayVlogText!!, isVlogRecording, screenH / localDensity.density)
        }

        // 12.5. 录制进度条（Vlog录制时显示）
        if (!isImmersive && isVlogRecording && plan?.vlogScript != null) {
            RecordingProgressBar(
                current = activeVlogClipIndex + 1,
                total = plan.vlogScript.clips.size,
                label = "Vlog 录制中"
            )
        }

        // 13. 屏幕柔边补光带
        if (isLowLight && !isImmersive) {
            LowLightGlowOverlay()
        }

        // 14. 快门闪光
        if (showShutterFlash) {
            ShutterFlashOverlay()
        }

        // 15. 倒计时大数字
        if (countdown > 0) {
            AnimatedCountdown(countdown)
        }

        // 16. 录制倒计时
        if (isRecordingMode && recordCountdown > 0) {
            AnimatedCountdown(recordCountdown, isRecording = true)
        }
    }
}

// ─── Helpers ───

private fun toComposeRect(bbox: android.graphics.RectF): Rect =
    Rect(left = bbox.left, top = 1f - bbox.bottom, right = bbox.right, bottom = 1f - bbox.top)

// ─── TopBar ───

@Composable
private fun TopBar(
    scene: SceneType,
    plan: ShootingPlan?,
    isSceneReady: Boolean,
    score: Float,
    isReady: Boolean,
    activeSequenceIndex: Int,
    activeAngleIndex: Int,
    activeVlogClipIndex: Int,
    isVlogRecording: Boolean,
    onGuide: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isSceneReady && plan != null) {
            Row(
                modifier = Modifier
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.White.copy(alpha = 0.08f), Color.White.copy(alpha = 0.02f))
                        ),
                        RoundedCornerShape(Brand.Radius.Lg)
                    )
                    .border(1.dp, Brand.Border, RoundedCornerShape(Brand.Radius.Lg))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(Brand.Surface, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(scene.icon, fontSize = 16.sp, color = Brand.Accent)
                }

                Column {
                    Text(
                        text = scene.displayName,
                        color = Brand.TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                    val subtitle = when {
                        plan.vlogScript != null && activeVlogClipIndex < plan.vlogScript.clips.size -> {
                            val isRecording = isVlogRecording
                            val color = if (isRecording) Brand.Coral else Brand.TextSecondary
                            "分镜 ${activeVlogClipIndex + 1}/${plan.vlogScript.clips.size}"
                        }
                        plan.sequence != null && activeSequenceIndex < plan.sequence.size ->
                            "${activeSequenceIndex + 1}/${plan.sequence.size} ${plan.sequence[activeSequenceIndex].title}"
                        plan.multiAngles != null && activeAngleIndex < plan.multiAngles.size ->
                            "${activeAngleIndex + 1}/${plan.multiAngles.size} ${plan.multiAngles[activeAngleIndex].title}"
                        else -> "${plan.poseEmoji} ${plan.poseName}"
                    }
                    val subColor = when {
                        plan.vlogScript != null -> Brand.Coral
                        plan.sequence != null -> Brand.Success
                        plan.multiAngles != null -> Brand.Coral
                        else -> Brand.TextPrimary
                    }
                    Text(
                        text = subtitle,
                        color = subColor,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(Modifier.weight(1f))

        if (isSceneReady) {
            ScoreRing(score, isReady)
        }

        Spacer(Modifier.size(12.dp))

        Box(
            modifier = Modifier
                .size(44.dp)
                .background(Brand.Surface, CircleShape)
                .border(1.dp, Brand.Border, CircleShape)
                .clickable { onGuide() },
            contentAlignment = Alignment.Center
        ) {
            Text("?", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

// ─── BottomPanel ───

@Composable
private fun BottomPanel(
    vm: ShootingViewModel,
    isSceneReady: Boolean,
    isImmersive: Boolean,
    plans: List<ShootingPlan>,
    currentPlanIndex: Int,
    timerSeconds: Int,
    onHistory: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(Color.Black.copy(alpha = 0f), Color.Black.copy(alpha = 0.7f))
                )
            )
            .navigationBarsPadding()
    ) {
        if (isSceneReady && !isImmersive) {
            PlanPickerSection(
                plans = plans,
                currentIndex = currentPlanIndex,
                onSelect = { vm.selectPlan(it) },
                onStartRecording = { vm.startRecordingCustomPlan() },
                isRecordingMode = vm.isRecordingMode.collectAsStateWithLifecycle().value
            )
        }

        ControlRow(
            timerSeconds = timerSeconds,
            isFrontCamera = vm.manager.isFrontCamera,
            onShutter = { vm.handleShutterTap() },
            onToggleCamera = { vm.manager.switchCamera() },
            onCycleTimer = { vm.cycleTimer() },
            onHistory = onHistory,
            isReady = vm.isReady,
            isCapturing = vm.isCapturing.collectAsStateWithLifecycle().value
        )
    }
}

@Composable
private fun PlanPickerSection(
    plans: List<ShootingPlan>,
    currentIndex: Int,
    onSelect: (Int) -> Unit,
    onStartRecording: () -> Unit,
    isRecordingMode: Boolean
) {
    val listState = rememberLazyListState()

    LaunchedEffect(currentIndex) {
        if (currentIndex in plans.indices) {
            listState.animateScrollToItem(currentIndex)
        }
    }

    LazyRow(
        state = listState,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.padding(top = 10.dp)
    ) {
        item {
            Column(
                modifier = Modifier
                    .background(
                        if (isRecordingMode) Brand.Coral.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.08f),
                        RoundedCornerShape(12.dp)
                    )
                    .border(
                        1.dp,
                        if (isRecordingMode) Brand.Coral.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.3f),
                        RoundedCornerShape(12.dp)
                    )
                    .clickable { onStartRecording() }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = if (isRecordingMode) "●" else "＋",
                    color = if (isRecordingMode) Brand.Coral else Color.White,
                    fontSize = 18.sp
                )
                Text(
                    text = if (isRecordingMode) "捕捉中..." else "录制专属",
                    color = if (isRecordingMode) Brand.Coral else Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        itemsIndexed(plans) { idx, plan ->
            PlanCard(
                plan = plan,
                isSelected = idx == currentIndex,
                onClick = { onSelect(idx) }
            )
        }
    }
}

@Composable
private fun ControlRow(
    timerSeconds: Int,
    isFrontCamera: Boolean,
    onShutter: () -> Unit,
    onToggleCamera: () -> Unit,
    onCycleTimer: () -> Unit,
    onHistory: () -> Unit,
    isReady: Boolean,
    isCapturing: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp, vertical = 22.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left: History
        Box(
            modifier = Modifier
                .weight(1f)
                .size(50.dp)
                .background(Brand.Surface, RoundedCornerShape(12.dp))
                .border(1.dp, Brand.Border, RoundedCornerShape(12.dp))
                .clickable { onHistory() },
            contentAlignment = Alignment.Center
        ) {
            Text("🖼", fontSize = 19.sp)
        }

        // Center: Shutter
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.Center
        ) {
            ShutterButton(
                isReady = isReady,
                isCapturing = isCapturing,
                onClick = onShutter
            )
        }

        // Right: Flip Camera & Timer
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(Brand.Surface, CircleShape)
                    .border(1.dp, Brand.Border, CircleShape)
                    .clickable { onToggleCamera() },
                contentAlignment = Alignment.Center
            ) {
                Text("🔄", fontSize = 18.sp)
            }

            Spacer(Modifier.size(12.dp))

            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        if (timerSeconds > 0) Brand.Accent.copy(alpha = 0.18f) else Brand.Surface,
                        CircleShape
                    )
                    .border(
                        1.dp,
                        if (timerSeconds > 0) Brand.Accent.copy(alpha = 0.6f) else Brand.Border,
                        CircleShape
                    )
                    .clickable { onCycleTimer() },
                contentAlignment = Alignment.Center
            ) {
                if (timerSeconds == 0) {
                    Text("⏱", fontSize = 18.sp, color = Brand.TextSecondary)
                } else {
                    Text(
                        text = "$timerSeconds",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Brand.Accent
                    )
                }
            }
        }
    }
}

// ─── ShutterButton ───

@Composable
private fun ShutterButton(
    isReady: Boolean,
    isCapturing: Boolean,
    onClick: () -> Unit
) {
    val breathScale by animateFloatAsState(
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "breathScale"
    )
    val glowAlpha by animateFloatAsState(
        targetValue = 0.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    val readyGlowAlpha by animateFloatAsState(
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "readyGlowAlpha"
    )

    val pressedScale by animateFloatAsState(
        targetValue = if (isCapturing) 0.92f else if (isReady) 1.05f else 1.0f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = 0.55f
        ),
        label = "pressedScale"
    )

    Box(
        modifier = Modifier
            .size(92.dp)
            .graphicsLayer {
                scaleX = pressedScale * if (isReady) breathScale else 1f
                scaleY = pressedScale * if (isReady) breathScale else 1f
            }
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        // Outer glow (when ready)
        if (isReady) {
            Box(
                modifier = Modifier
                    .size(82.dp)
                    .graphicsLayer {
                        scaleX = breathScale * 1.5f
                        scaleY = breathScale * 1.5f
                        alpha = 2.0f - (breathScale * 1.5f)
                    }
                    .background(Brand.SuccessGlow.copy(alpha = readyGlowAlpha), CircleShape)
            )
        }

        // Outer ring
        Box(
            modifier = Modifier
                .size(82.dp)
                .border(
                    width = 2.5.dp,
                    color = if (isReady) Brand.Success.copy(alpha = 0.9f) else Color.White.copy(alpha = 0.55f),
                    shape = CircleShape
                )
        )

        // Inner circle
        Box(
            modifier = Modifier
                .size(68.dp)
                .background(
                    brush = if (isReady) {
                        Brush.linearGradient(
                            listOf(Brand.Success, Color(0xFF33D97A)),
                            start = Offset(0f, 0f),
                            end = Offset(1f, 1f)
                        )
                    } else {
                        Brush.linearGradient(
                            listOf(Color.White.copy(alpha = 0.92f), Color.White.copy(alpha = 0.78f)),
                            start = Offset(0f, 0f),
                            end = Offset(0f, 1f)
                        )
                    },
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isReady) {
                Text("●", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
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

// ─── CompositionTipOverlay ───

@Composable
private fun CompositionTipOverlay(plan: ShootingPlan) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(top = 130.dp)
    ) {
        Row(
            modifier = Modifier
                .background(
                    Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.08f), Color.White.copy(alpha = 0.02f))
                    ),
                    RoundedCornerShape(Brand.Radius.Lg)
                )
                .border(1.dp, Brand.Accent.copy(alpha = 0.35f), RoundedCornerShape(Brand.Radius.Lg))
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .background(Brand.Accent.copy(alpha = 0.2f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(plan.composition.icon, fontSize = 14.sp, color = Brand.Accent)
            }
            Column {
                Text(
                    text = "${plan.composition.displayName} 构图",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = plan.composition.reason,
                    color = Brand.TextSecondary,
                    fontSize = 11.sp,
                    maxLines = 2
                )
            }
        }
    }
}

// ─── LowLightBanner ───

@Composable
private fun LowLightBanner() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(top = 110.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .background(
                    Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.08f), Color.White.copy(alpha = 0.02f))
                    ),
                    RoundedCornerShape(20.dp)
                )
                .border(1.dp, Color(0x66FFEB3B), RoundedCornerShape(20.dp))
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("💡", fontSize = 13.sp)
            Text(
                text = "光线不足，移到明亮处效果更好",
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

// ─── Bottom Tips ───

@Composable
private fun PitchWarning(bottomOffset: Float = 170f) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = bottomOffset.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Row(
            modifier = Modifier
                .background(
                    Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.08f), Color.White.copy(alpha = 0.02f))
                    ),
                    RoundedCornerShape(20.dp)
                )
                .border(1.dp, Brand.Coral.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("⚠️", color = Brand.Coral, fontSize = 14.sp)
            Text(
                text = "请平行或低角度拍摄，显腿更长",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun SpaceTip(bottomOffset: Float = 170f) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = bottomOffset.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Row(
            modifier = Modifier
                .background(
                    Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.08f), Color.White.copy(alpha = 0.02f))
                    ),
                    RoundedCornerShape(20.dp)
                )
                .border(1.dp, Brand.Accent.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("✨", color = Brand.Accent, fontSize = 14.sp)
            Text(
                text = "尝试平移留出一点空白，更有氛围感",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun AngleGuide(reqPitch: Float, devicePitch: Float, bottomOffset: Float = 230f) {
    val isReaching = (reqPitch > 0 && devicePitch >= reqPitch) || (reqPitch < 0 && devicePitch <= reqPitch)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = bottomOffset.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Row(
            modifier = Modifier
                .background(
                    Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.08f), Color.White.copy(alpha = 0.02f))
                    ),
                    RoundedCornerShape(20.dp)
                )
                .border(
                    2.dp,
                    if (isReaching) Brand.Success.copy(alpha = 0.8f) else Brand.Coral.copy(alpha = 0.8f),
                    RoundedCornerShape(20.dp)
                )
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .border(2.dp, Color.White.copy(alpha = 0.3f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 20.dp, height = 3.dp)
                        .background(
                            if (isReaching) Brand.Success else Brand.Coral,
                            RoundedCornerShape(2.dp)
                        )
                )
            }
            Text(
                text = if (isReaching) {
                    "机位正确，保持稳定"
                } else if (reqPitch > 0) {
                    "请摄影师继续下蹲仰拍"
                } else {
                    "请摄影师抬高俯拍"
                },
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// ─── ShutterFlashOverlay ───

@Composable
private fun ShutterFlashOverlay() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFFF0DD).copy(alpha = 0.9f))
    )
}

// ─── AnimatedCountdown ───

@Composable
private fun AnimatedCountdown(seconds: Int, isRecording: Boolean = false) {
    val scaleAnim by animateFloatAsState(
        targetValue = 1.4f,
        animationSpec = keyframes {
            durationMillis = 900
            1.0f at 0
            1.4f at 200
            1.0f at 500
        },
        finishedListener = { },
        label = "countdownPulse_$seconds"
    )

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = "$seconds",
            color = if (isRecording) Brand.Success.copy(alpha = 0.9f) else Color.White.copy(alpha = 0.9f),
            fontSize = 130.sp,
            fontWeight = FontWeight.Light,
            modifier = Modifier.graphicsLayer {
                scaleX = scaleAnim
                scaleY = scaleAnim
            }
        )
    }
}
