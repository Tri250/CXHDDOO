package com.poseai.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.weight
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
import kotlin.math.max
import kotlin.math.min

/**
 * ContentScreen —— 拍摄主界面深度优化版。
 *
 * 针对国内手机摄影用户体验优化点：
 * - 手势：双击缩放、长按快门录像、单击对焦
 * - 方案选择：吸附滚动、当前方案高亮指示
 * - 快门：呼吸光晕 + 缩放反馈 + 闪光动画
 * - 倒计时：环形进度 + 缩放脉冲
 * - 拍摄进度：连拍数/录像时长实时反馈
 * - 变焦指示：底部变焦水平条
 * - 场景扫描：动画 + 进度步骤
 */
@Composable
fun ContentScreen(
    vm: ShootingViewModel,
    hasCameraPermission: Boolean,
    onShowHistory: () -> Unit,
    onShowGuide: () -> Unit,
    onShowStats: () -> Unit
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

    // 手势状态
    var zoomLevel by remember { mutableStateOf(1f) }
    var focusPoint by remember { mutableStateOf<Offset?>(null) }
    var showFocusIndicator by remember { mutableStateOf(false) }

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

    // 自动隐藏对焦指示
    LaunchedEffect(showFocusIndicator) {
        if (showFocusIndicator) {
            delay(1500)
            showFocusIndicator = false
            focusPoint = null
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        val localDensity = LocalDensity.current
        val screenW = with(localDensity) { maxWidth.toPx() }
        val screenH = constraints.maxHeight.toFloat()

        // 相机预览层
        if (hasCameraPermission) {
            AndroidView(
                modifier = Modifier.matchParentSize(),
                factory = { factoryCtx ->
                    PreviewView(factoryCtx).also { pv ->
                        pv.scaleType = PreviewView.ScaleType.FILL_CENTER
                        previewView = pv
                    }
                }
            )
        } else {
            Box(Modifier.matchParentSize().background(Color.Black), contentAlignment = Alignment.Center) {
                Text("需要摄像头权限才能使用", color = Color.White, fontSize = 16.sp)
            }
        }

        // 暗光屏幕补光
        if (isLowLight) {
            LowLightGlowOverlay()
        }

        // 手势层：双击缩放 + 长按快门 + 单击对焦
        Box(
            Modifier
                .matchParentSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = { offset ->
                            // 双击切换变焦：1x → 2x → 1x
                            val newZoom = if (zoomLevel < 1.5f) 2f else 1f
                            zoomLevel = newZoom
                            vm.setZoom(newZoom)
                            vm.feedback.impact(com.poseai.app.video.DeviceFeedback.LIGHT)
                        },
                        onLongPress = { offset ->
                            // 长按进入录制模式
                            if (isSceneReady) {
                                vm.handleShutterLongPress()
                            }
                        },
                        onTap = { offset ->
                            // 单击对焦
                            focusPoint = offset
                            showFocusIndicator = true
                            val normX = (offset.x / size.width).coerceIn(0f, 1f)
                            val normY = (offset.y / size.height).coerceIn(0f, 1f)
                            vm.focusAt(normX, normY)
                            vm.feedback.impact(com.poseai.app.video.DeviceFeedback.LIGHT)
                        }
                    )
                }
        )

        // 对焦指示框
        if (showFocusIndicator && focusPoint != null) {
            FocusIndicator(point = focusPoint!!)
        }

        // 场景扫描动画
        if (isScanning && !isImmersive) {
            SceneScanningOverlay()
        }

        // AR 脚印覆盖层
        if (isSceneReady && !isImmersive && !isCapturing) {
            ARFootprintsOverlay()
        }

        // 构图辅助线
        if (!isImmersive) {
            CompositionGuideLines(if (isSceneReady) plan?.composition else null)
        }

        // 剪影引导
        if (isSceneReady && plan != null) {
            val w = screenW
            val h = screenH
            if (plan.secondaryPosePoints != null) {
                val b0 = detectedPoses.getOrNull(0)?.bbox?.let { toComposeRect(it) }
                val b1 = detectedPoses.getOrNull(1)?.bbox?.let { toComposeRect(it) }
                SilhouetteGuideOverlay(vm.isReady, plan, b0, w, h, forceOffset = -w * 0.18f)
                SilhouetteGuideOverlay(vm.isReady, plan, b1, w, h, forceOffset = w * 0.18f)
            } else {
                val b = detectedPoses.firstOrNull()?.bbox?.let { toComposeRect(it) }
                SilhouetteGuideOverlay(vm.isReady, plan, b, w, h)
            }
        }

        // 顶部信息栏
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

        // AI 构图灵感
        if (aiSuggestion != null && !isImmersive) {
            AiAdvisorBanner(aiSuggestion!!)
        }

        // 构图提示（位置在 TopBar 下方 8dp，避免双重 statusBarsPadding）
        if (showCompositionTip && plan != null && !isImmersive) {
            CompositionTipOverlay(plan)
        }

        // 暗光提示（位于构图提示下方）
        if (isLowLight && isSceneReady && !isImmersive && !showCompositionTip) {
            LowLightBanner()
        }

        // 底部提示（互斥显示，优先级：俯仰警告 > 留白 > 角度）
        // 放在 BottomPanel 之后渲染，确保在其上方显示
        val showBottomTip = devicePitch < -0.35f || 
            (showSpaceTip && devicePitch >= -0.35f && !showCompositionTip) ||
            (plan?.multiAngles?.let { multi -> 
                val hasAngle = activeAngleIndex < multi.size && multi[activeAngleIndex].requiredPitch != null
                hasAngle && !isImmersive && devicePitch >= -0.35f && !showSpaceTip && !showCompositionTip
            } ?: false)

        // Bottom control area
        BottomPanel(
            vm = vm,
            isSceneReady = isSceneReady && vm.availablePlans.isNotEmpty(),
            isImmersive = isImmersive,
            plans = vm.availablePlans,
            currentPlanIndex = currentPlanIndex,
            timerSeconds = timerSeconds,
            isRecordingMode = isRecordingMode,
            isCapturing = isCapturing,
            zoomLevel = zoomLevel,
            onZoomChange = { newZoom ->
                zoomLevel = newZoom
                vm.setZoom(newZoom)
            },
            onHistory = onShowHistory,
            onStats = onShowStats,
            showBottomSpacing = showBottomTip
        )

        // 底部安全提示条（显示在 BottomPanel 上方或内部）
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

        // Vlog 提词器
        if (displayVlogText != null && !isImmersive) {
            VlogTextOverlay(displayVlogText!!, isVlogRecording, screenH / localDensity.density)
        }

        // 录制进度（Vlog）
        if (isVlogRecording && activeVlogClipIndex < (plan?.vlogScript?.clips?.size ?: 0)) {
            val totalClips = plan?.vlogScript?.clips?.size ?: 1
            val topOffset = screenH / localDensity.density * 0.18f
            RecordingProgressBar(
                current = activeVlogClipIndex + 1,
                total = totalClips,
                label = "Vlog 拍摄中 ${activeVlogClipIndex + 1}/$totalClips",
                topOffsetDp = topOffset
            )
        }

        // 拍摄进度指示（连拍/序列）
        if (isCapturing && expectedBurstCount > 1 && !isVlogRecording) {
            val burstTopOffset = screenH / localDensity.density * 0.19f
            BurstProgressIndicator(
                current = max(capturedShotsCount, burstImages.size),
                total = expectedBurstCount,
                isSequence = plan?.sequence != null,
                sequenceIndex = activeSequenceIndex,
                sequenceTotal = plan?.sequence?.size ?: 1,
                topOffsetDp = burstTopOffset
            )
        }

        // 快门闪光 + 倒计时（在所有内容之上）
        if (showShutterFlash) {
            ShutterFlashOverlay()
        }

        if (countdown > 0) {
            AnimatedCountdown(countdown)
        }

        if (isRecordingMode && recordCountdown > 0) {
            AnimatedCountdown(recordCountdown, isRecording = true)
        }
    }
}

// ─── ─── ─── ─── Helpers ─── ─── ─── ───

private fun toComposeRect(bbox: android.graphics.RectF): Rect =
    Rect(left = bbox.left, top = 1f - bbox.bottom, right = bbox.right, bottom = 1f - bbox.top)

// ─── ─── ─── ─── TopBar ─── ─── ─── ───

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
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isSceneReady && plan != null) {
            Row(
                modifier = Modifier
                    .background(Brand.Surface, RoundedCornerShape(14.dp))
                    .border(1.dp, Brand.Border, RoundedCornerShape(14.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(scene.icon, fontSize = 14.sp)
                Column {
                    Text(scene.displayName, color = Brand.TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    val subtitle = when {
                        plan.vlogScript != null && activeVlogClipIndex < plan.vlogScript.clips.size ->
                            "分镜 ${activeVlogClipIndex + 1}/${plan.vlogScript.clips.size}"
                        plan.sequence != null && activeSequenceIndex < plan.sequence.size ->
                            "${activeSequenceIndex + 1}/${plan.sequence.size} ${plan.sequence[activeSequenceIndex].title}"
                        plan.multiAngles != null && activeAngleIndex < plan.multiAngles.size ->
                            "${activeAngleIndex + 1}/${plan.multiAngles.size} ${plan.multiAngles[activeAngleIndex].title}"
                        else -> plan.poseName
                    }
                    Text(
                        subtitle,
                        color = when {
                            plan.sequence != null -> Brand.Success
                            plan.multiAngles != null -> Brand.Coral
                            else -> Color.White
                        },
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        Spacer(Modifier.weight(1f))
        if (isSceneReady) {
            ScoreRing(score, isReady)
        }
        Spacer(Modifier.size(8.dp))
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(Brand.Surface, CircleShape)
                .border(1.dp, Brand.Border, CircleShape)
                .clickable { onGuide() },
            contentAlignment = Alignment.Center
        ) {
            Text("？", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

// ─── ─── ─── ─── BottomPanel ─── ─── ─── ───

@Composable
private fun BottomPanel(
    vm: ShootingViewModel,
    isSceneReady: Boolean,
    isImmersive: Boolean,
    plans: List<ShootingPlan>,
    currentPlanIndex: Int,
    timerSeconds: Int,
    isRecordingMode: Boolean,
    isCapturing: Boolean,
    zoomLevel: Float,
    onZoomChange: (Float) -> Unit,
    onHistory: () -> Unit,
    onStats: () -> Unit,
    showBottomSpacing: Boolean = false
) {
    val listState = rememberLazyListState()

    // 自动滚动到选中方案
    LaunchedEffect(currentPlanIndex) {
        if (currentPlanIndex in plans.indices) {
            listState.animateScrollToItem(currentPlanIndex, scrollToStart = false)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(Color.Black.copy(alpha = 0f), Color.Black.copy(alpha = 0.7f))
                )
            )
            .padding(top = if (showBottomSpacing) 80.dp else 4.dp)
            .navigationBarsPadding()
    ) {
        // 变焦水平条
        if (!isImmersive && isSceneReady) {
            ZoomLevelIndicator(
                currentZoom = zoomLevel,
                onZoomChange = onZoomChange
            )
        }

        // 方案选择横向滑动（带吸附）
        if (isSceneReady && !isImmersive) {
            LazyRow(
                state = listState,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Column(
                        modifier = Modifier
                            .border(1.dp, if (isRecordingMode) Brand.Coral else Brand.Border, RoundedCornerShape(Brand.Radius.Md))
                            .background(Brand.Surface, RoundedCornerShape(Brand.Radius.Md))
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                            .clickable { vm.startRecordingCustomPlan() },
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(if (isRecordingMode) "🔴" else "＋", fontSize = 18.sp,
                            color = if (isRecordingMode) Brand.Coral else Color.White)
                        Text(if (isRecordingMode) "捕捉中" else "录制专属", fontSize = 13.sp,
                            fontWeight = FontWeight.Bold, color = if (isRecordingMode) Brand.Coral else Color.White)
                    }
                }
                itemsIndexed(plans) { idx, plan ->
                    PlanCard(
                        plan = plan,
                        isSelected = idx == currentPlanIndex,
                        onClick = { vm.selectPlan(idx) }
                    )
                }
            }

            // 选中方案指示器
            if (plans.isNotEmpty() && currentPlanIndex in plans.indices) {
                PlanSelectionIndicator(
                    plans = plans,
                    currentIndex = currentPlanIndex
                )
            }
        }

        // 主控制行
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左侧：历史 + 统计
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Start, verticalAlignment = Alignment.CenterVertically) {
                ControlButton(icon = "🖼", onClick = onHistory, size = 48.dp)
                Spacer(Modifier.size(8.dp))
                ControlButton(icon = "📊", onClick = onStats, size = 48.dp)
            }

            // 快门按钮
            EnhancedShutterButton(
                isReady = vm.isReady,
                isCapturing = isCapturing,
                isRecordingMode = isRecordingMode,
                onClick = { vm.handleShutterTap() },
                onLongPress = { vm.handleShutterLongPress() }
            )

            // 右侧：闪光灯 + 切换摄像头 + 倒计时
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                // 闪光灯（暗光时高亮）
                ControlButton(
                    icon = if (vm.isLowLight.value) "⚡" else "⚡",
                    onClick = { vm.toggleFlash() },
                    size = 48.dp,
                    highlighted = vm.isLowLight.value
                )
                Spacer(Modifier.size(8.dp))
                ControlButton(icon = "🔄", onClick = { vm.manager.switchCamera() }, size = 48.dp)
                Spacer(Modifier.size(8.dp))
                TimerButton(timerSeconds = timerSeconds, onClick = { vm.cycleTimer() })
            }
        }
    }
}

@Composable
private fun ControlButton(
    icon: String,
    onClick: () -> Unit,
    size: androidx.compose.ui.unit.Dp = 48.dp,
    highlighted: Boolean = false
) {
    Box(
        modifier = Modifier
            .size(size)
            .background(
                if (highlighted) Brand.Accent.copy(alpha = 0.25f) else Brand.Surface,
                CircleShape
            )
            .border(
                1.dp,
                if (highlighted) Brand.Accent.copy(alpha = 0.6f) else Brand.Border,
                CircleShape
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(icon, fontSize = (size.value * 0.45f).sp)
    }
}

@Composable
private fun TimerButton(timerSeconds: Int, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .background(
                if (timerSeconds > 0) Brand.Accent.copy(alpha = 0.18f) else Brand.Surface,
                CircleShape
            )
            .border(1.dp, if (timerSeconds > 0) Brand.Accent.copy(alpha = 0.6f) else Brand.Border, CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            if (timerSeconds == 0) "⏱" else "${timerSeconds}",
            fontSize = if (timerSeconds == 0) 18.sp else 14.sp,
            fontWeight = FontWeight.Bold,
            color = if (timerSeconds > 0) Brand.Accent else Brand.TextSecondary
        )
    }
}

// ─── ─── ─── ─── 增强快门按钮 ─── ─── ─── ───

@Composable
private fun EnhancedShutterButton(
    isReady: Boolean,
    isCapturing: Boolean,
    isRecordingMode: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "shutterBreath")
    val breathScale by infiniteTransition.animateFloat(
        initialValue = 1.0f, targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "breathScale"
    )
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f, targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )
    val pressedScale by animateFloatAsState(
        targetValue = if (isCapturing) 0.9f else 1.0f,
        animationSpec = spring(response = 0.25f, dampingRatio = 0.55f),
        label = "pressedScale"
    )
    val readyPulse by infiniteTransition.animateFloat(
        initialValue = 1.0f, targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "readyPulse"
    )

    val borderColor = when {
        isReady && !isCapturing -> Brand.Success
        isCapturing -> Brand.Coral
        else -> Color.White.copy(alpha = 0.6f)
    }

    val innerGradient = when {
        isReady -> Brush.radialGradient(
            colors = listOf(Color.White, Brand.Success.copy(alpha = 0.7f)),
            center = Offset(0.3f, 0.3f)
        )
        else -> Brush.radialGradient(
            colors = listOf(Color.White, Color(0xFFE0E0E0)),
            center = Offset(0.3f, 0.3f)
        )
    }

    Box(
        modifier = Modifier
            .size(96.dp)
            .graphicsLayer {
                scaleX = breathScale * pressedScale
                scaleY = breathScale * pressedScale
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onClick() },
                    onLongPress = { onLongPress() }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        // 就绪时脉冲光环
        if (isReady && !isCapturing) {
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .graphicsLayer {
                        scaleX = readyPulse
                        scaleY = readyPulse
                    }
                    .background(
                        Brand.Success.copy(alpha = glowAlpha * 0.5f),
                        CircleShape
                    )
            )
        }

        // 呼吸光晕
        Box(
            modifier = Modifier
                .size(110.dp)
                .background(
                    if (isReady) Brand.Success.copy(alpha = glowAlpha * 0.3f)
                    else Color.White.copy(alpha = glowAlpha * 0.15f),
                    CircleShape
                )
        )

        // 外圈边框
        Box(
            modifier = Modifier
                .size(90.dp)
                .border(3.dp, borderColor, CircleShape)
                .background(innerGradient, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            when {
                isCapturing && isRecordingMode -> {
                    // 录制中 - 脉动红点
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .background(Brand.Coral, RoundedCornerShape(8.dp))
                    )
                }
                isCapturing -> {
                    // 连拍中 - 缩小动画
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .background(Color.Black.copy(alpha = 0.35f), CircleShape)
                    )
                }
                else -> {
                    // 就绪/待拍
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .background(
                                when {
                                    isReady -> Brand.Success.copy(alpha = 0.18f)
                                    else -> Color.Black.copy(alpha = 0.15f)
                                },
                                CircleShape
                            )
                    )
                }
            }
        }
    }
}

// ─── ─── ─── ─── 拍摄进度指示 ─── ─── ─── ───

@Composable
private fun BurstProgressIndicator(
    current: Int,
    total: Int,
    isSequence: Boolean,
    sequenceIndex: Int,
    sequenceTotal: Int,
    topOffsetDp: Float = 140f
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = topOffsetDp.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .background(Brand.Surface.copy(alpha = 0.85f), RoundedCornerShape(20.dp))
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            if (isSequence) {
                Text("📸", fontSize = 13.sp)
                Text(
                    "连拍 $current/$total",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    " · 分镜 ${sequenceIndex + 1}/$sequenceTotal",
                    color = Brand.TextSecondary,
                    fontSize = 12.sp
                )
            } else {
                repeat(total) { idx ->
                    val isDone = idx < current
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(
                                if (isDone) Brand.Accent else Color.White.copy(alpha = 0.3f),
                                CircleShape
                            )
                    )
                }
                Text(
                    " $current/$total",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun PlanSelectionIndicator(
    plans: List<ShootingPlan>,
    currentIndex: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        plans.forEachIndexed { idx, _ ->
            Box(
                modifier = Modifier
                    .padding(horizontal = 3.dp)
                    .size(if (idx == currentIndex) 6.dp else 4.dp)
                    .background(
                        if (idx == currentIndex) Brand.Accent else Color.White.copy(alpha = 0.25f),
                        CircleShape
                    )
            )
        }
    }
}

// ─── ─── ─── ─── 快门闪光 ─── ─── ─── ───

@Composable
private fun ShutterFlashOverlay() {
    val flashProgress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(durationMillis = 200),
        label = "flashProgress"
    )
    Box(
        Modifier
            .matchParentSize()
            .graphicsLayer {
                alpha = 1f - flashProgress
            }
            .background(
                Brush.radialGradient(
                    colors = listOf(Brand.ShutterWarm.copy(alpha = 0.95f), Brand.ShutterWarm.copy(alpha = 0.3f)),
                    center = Offset(0.5f, 0.5f)
                )
            )
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // 扩散环——随flashProgress从中心扩散到全屏
            val ringRadius = min(size.width, size.height) * 0.15f + flashProgress * min(size.width, size.height) * 0.4f
            drawCircle(
                color = Color.White.copy(alpha = (1f - flashProgress) * 0.5f),
                radius = ringRadius,
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
            )
        }
    }
}

// ─── ─── ─── ─── 倒计时动画 ─── ─── ─── ───

@Composable
private fun AnimatedCountdown(seconds: Int, isRecording: Boolean = false) {
    val currentCount = seconds
    val scaleAnim by animateFloatAsState(
        targetValue = 1.3f,
        animationSpec = keyframes {
            durationMillis = 900
            0f at 0
            1.2f at 200
            1.0f at 500
            0.85f at 800
        },
        finishedListener = { /* 动画完成 */ },
        label = "countdownPulse_$currentCount"
    )

    val progress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(durationMillis = 900),
        label = "countdownProgress_$currentCount"
    )

    Box(Modifier.matchParentSize(), contentAlignment = Alignment.Center) {
        // 环形进度背景
        Canvas(
            modifier = Modifier.size(140.dp)
        ) {
            val strokeWidth = 4.dp.toPx()
            val radius = (size.minDimension - strokeWidth) / 2f
            val center = Offset(size.width / 2, size.height / 2)

            // 背景圆
            drawCircle(
                color = Color.White.copy(alpha = 0.15f),
                radius = radius,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            // 进度弧
            val sweepAngle = 360f * progress
            drawArc(
                brush = Brush.sweepGradient(
                    listOf(
                        if (isRecording) Brand.Coral else Brand.Accent,
                        if (isRecording) Brand.Coral.copy(alpha = 0.5f) else Brand.Accent.copy(alpha = 0.5f)
                    ),
                    center = center
                ),
                startAngle = -90f,
                sweepAngle = sweepAngle,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }

        // 数字
        Text(
            text = "$currentCount",
            color = if (isRecording) Brand.Coral.copy(alpha = 0.95f) else Color.White.copy(alpha = 0.95f),
            fontSize = 88.sp,
            fontWeight = FontWeight.Light,
            modifier = Modifier.graphicsLayer {
                scaleX = scaleAnim
                scaleY = scaleAnim
            }
        )
    }
}

// ─── ─── ─── ─── 构图提示 ─── ─── ─── ───

@Composable
private fun CompositionTipOverlay(plan: ShootingPlan) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(top = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .background(Brand.Surface, RoundedCornerShape(Brand.Radius.Lg))
                .border(1.dp, Brand.Accent.copy(alpha = 0.35f), RoundedCornerShape(Brand.Radius.Lg))
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                Modifier.size(36.dp).background(Brand.Accent.copy(alpha = 0.2f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(plan.composition.icon, fontSize = 16.sp)
            }
            Column {
                Text("${plan.composition.displayName} 构图", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text(plan.composition.reason, color = Brand.TextSecondary, fontSize = 11.sp, maxLines = 2)
            }
        }
    }
}

// ─── ─── ─── ─── 暗光/警告提示 ─── ─── ─── ───

@Composable
private fun LowLightBanner() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(top = 8.dp)
            .background(Brand.Surface, CircleShape)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("💡", fontSize = 13.sp)
        Text(
            " 光线不足，建议开启补光或换个位置",
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * 底部安全提示条——显示在 BottomPanel 上方。
 * 使用 align(Alignment.BottomCenter) 定位到屏幕底部。
 */
@Composable
private fun PitchWarning(bottomOffset: Float = 220f) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .align(Alignment.BottomCenter)
            .navigationBarsPadding()
            .padding(bottom = bottomOffset.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Row(
            modifier = Modifier
                .background(Brand.Surface, RoundedCornerShape(16.dp))
                .border(1.dp, Brand.Coral.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                .padding(horizontal = 18.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            Text("⚠️", color = Brand.Coral, fontSize = 13.sp)
            Text(
                " 建议低角度仰拍，更显腿长",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun SpaceTip(bottomOffset: Float = 220f) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .align(Alignment.BottomCenter)
            .navigationBarsPadding()
            .padding(bottom = bottomOffset.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Row(
            modifier = Modifier
                .background(Brand.Surface, RoundedCornerShape(16.dp))
                .padding(horizontal = 18.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                "稍微平移留出空白，构图更有呼吸感",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun AngleGuide(reqPitch: Float, devicePitch: Float, bottomOffset: Float = 220f) {
    val isReaching = (reqPitch > 0 && devicePitch >= reqPitch) || (reqPitch < 0 && devicePitch <= reqPitch)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .align(Alignment.BottomCenter)
            .navigationBarsPadding()
            .padding(bottom = bottomOffset.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Row(
            modifier = Modifier
                .background(Brand.Surface, RoundedCornerShape(16.dp))
                .border(2.dp, if (isReaching) Brand.Success.copy(alpha = 0.8f) else Brand.Coral.copy(alpha = 0.8f), RoundedCornerShape(16.dp))
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                if (isReaching) "✓ 机位正确，保持不动"
                else if (reqPitch > 0) "请摄影师下蹲仰拍 ${(devicePitch * 100).toInt()}°"
                else "请摄影师抬高俯拍 ${(devicePitch * 100).toInt()}°",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
