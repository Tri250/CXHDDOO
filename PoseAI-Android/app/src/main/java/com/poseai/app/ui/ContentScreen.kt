package com.poseai.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.outlined.QuestionMark
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.poseai.app.design.Brand
import com.poseai.app.model.SceneType
import com.poseai.app.model.ShootingPlan
import com.poseai.app.ui.components.CompositionGuideLines
import com.poseai.app.ui.components.PlanCard
import com.poseai.app.ui.components.ScoreRing
import com.poseai.app.ui.components.SilhouetteGuideOverlay
import com.poseai.app.viewmodel.ShootingViewModel

@Composable
fun ContentScreen(
    vm: ShootingViewModel,
    hasCameraPermission: Boolean,
    onShowHistory: () -> Unit,
    onShowGuide: () -> Unit,
    onShowStats: () -> Unit
) {
    val ctx = LocalContext.current
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

    val plan = vm.currentPlan

    // 相机绑定
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    DisposableEffect(hasCameraPermission, previewView) {
        if (hasCameraPermission && previewView != null) {
            val pv = previewView!!
            vm.manager.bindToCamera(lifecycleOwner, pv)
        }
        onDispose { if (!hasCameraPermission) vm.manager.cleanUp() }
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
                Text("需要摄像头权限", color = Color.White, fontSize = 16.sp)
            }
        }

        // 点击进入/退出沉浸模式
        Box(
            Modifier.matchParentSize()
                .clickable { vm.toggleImmersiveMode() }
        )

        if (!isImmersive) {
            CompositionGuideLines(if (isSceneReady) plan?.composition else null)
        }

        // 剪影
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

        // 构图提示
        if (showCompositionTip && plan != null && !isImmersive) {
            CompositionTipOverlay(plan)
        }

        // AI 构图灵感
        if (aiSuggestion != null && !isImmersive) {
            AiBanner(aiSuggestion!!)
        }

        // 暗光提示
        if (isLowLight && isSceneReady && !isImmersive) {
            LowLightBanner()
        }

        // 俯拍警告
        if (devicePitch < -0.35f && !isImmersive) {
            PitchWarning()
        }

        // 留白提醒
        if (showSpaceTip && devicePitch >= -0.35f && !showCompositionTip && !isImmersive) {
            SpaceTip()
        }

        // 多机位角度指示
        plan?.multiAngles?.let { multi ->
            if (activeAngleIndex < multi.size && multi[activeAngleIndex].requiredPitch != null && !isImmersive) {
                AngleGuide(multi[activeAngleIndex].requiredPitch!!, devicePitch)
            }
        }

        // Vlog 提词器
        if (displayVlogText != null && !isImmersive) {
            VlogTextOverlay(displayVlogText!!, isVlogRecording)
        }

        // 底部控制区
        BottomPanel(
            vm = vm,
            isSceneReady = isSceneReady && vm.availablePlans.isNotEmpty(),
            isImmersive = isImmersive,
            plans = vm.availablePlans,
            currentPlanIndex = currentPlanIndex,
            timerSeconds = timerSeconds,
            isRecordingMode = isRecordingMode,
            isCapturing = isCapturing,
            onHistory = onShowHistory
        )

        // 快门闪光
        if (showShutterFlash) {
            Box(Modifier.matchParentSize().background(Brand.ShutterWarm.copy(alpha = 0.9f)))
        }

        // 倒计时大数字
        if (countdown > 0) {
            Box(Modifier.matchParentSize(), contentAlignment = Alignment.Center) {
                Text("$countdown", color = Color.White.copy(alpha = 0.9f), fontSize = 130.sp, fontWeight = FontWeight.Light)
            }
        }
        // 录制倒计时
        if (isRecordingMode && recordCountdown > 0) {
            Box(Modifier.matchParentSize(), contentAlignment = Alignment.Center) {
                Text("$recordCountdown", color = Brand.Success, fontSize = 130.sp, fontWeight = FontWeight.Light)
            }
        }
    }
}

private fun toComposeRect(bbox: android.graphics.RectF): Rect =
    // bbox 为归一化坐标（x 右，y 上），转换为 Compose 的 y 向下表示
    Rect(
        left = bbox.left,
        top = 1f - bbox.bottom,
        right = bbox.right,
        bottom = 1f - bbox.top
    )

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
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isSceneReady && plan != null) {
            Row(
                modifier = Modifier
                    .background(Brand.Surface, RoundedCornerShape(16.dp))
                    .border(1.dp, Brand.Hairline, RoundedCornerShape(16.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(scene.icon, fontSize = 15.sp)
                Column {
                    Text(scene.displayName, color = Brand.TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    val subtitle = when {
                        plan.vlogScript != null && activeVlogClipIndex < plan.vlogScript.clips.size ->
                            "Vlog [分镜 ${activeVlogClipIndex + 1}/${plan.vlogScript.clips.size}]"
                        plan.sequence != null && activeSequenceIndex < plan.sequence.size ->
                            "[${activeSequenceIndex + 1}/${plan.sequence.size}] ${plan.sequence[activeSequenceIndex].emoji} ${plan.sequence[activeSequenceIndex].title}"
                        plan.multiAngles != null && activeAngleIndex < plan.multiAngles.size ->
                            "[${activeAngleIndex + 1}/${plan.multiAngles.size}] 📷 ${plan.multiAngles[activeAngleIndex].title}"
                        else -> "${plan.poseEmoji} ${plan.poseName}"
                    }
                    Text(
                        subtitle,
                        color = if (plan.sequence != null) Brand.Success else if (plan.multiAngles != null) Brand.Coral else Color.White,
                        fontSize = 15.sp, fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        Spacer(Modifier.weight(1f))
        if (isSceneReady) {
            ScoreRing(score, isReady)
        }
        Spacer(Modifier.size(10.dp))
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(Brand.Surface, CircleShape)
                .border(1.dp, Brand.Hairline, CircleShape)
                .clickable { onGuide() },
            contentAlignment = Alignment.Center
        ) {
            Text("？", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

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
    onHistory: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.4f))
            .padding(top = 10.dp, bottom = 30.dp)
    ) {
        if (isSceneReady && !isImmersive) {
            LazyRow(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Column(
                        modifier = Modifier
                            .border(1.dp, if (isRecordingMode) Brand.Coral else Brand.Hairline, RoundedCornerShape(12.dp))
                            .background(Brand.Surface, RoundedCornerShape(12.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                            .clickable { vm.startRecordingCustomPlan() },
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(if (isRecordingMode) "🔴" else "＋", fontSize = 18.sp,
                            color = if (isRecordingMode) Brand.Coral else Color.White)
                        Text(if (isRecordingMode) "捕捉中..." else "录制专属", fontSize = 13.sp,
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
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .background(Brand.Surface, RoundedCornerShape(12.dp))
                        .border(1.dp, Brand.Hairline, RoundedCornerShape(12.dp))
                        .clickable { onHistory() },
                    contentAlignment = Alignment.Center
                ) {
                    Text("🖼️", fontSize = 20.sp)
                }
            }

            // 快门
            ShutterButton(isReady = vm.isReady, isCapturing = isCapturing, onClick = { vm.handleShutterTap() })

            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                // 切换摄像头
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(Brand.Surface, CircleShape)
                        .border(1.dp, Brand.Hairline, CircleShape)
                        .clickable { vm.manager.switchCamera() },
                    contentAlignment = Alignment.Center
                ) {
                    Text("🔄", fontSize = 18.sp)
                }
                Spacer(Modifier.size(12.dp))
                // 倒计时
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(if (timerSeconds > 0) Brand.Accent.copy(alpha = 0.18f) else Brand.Surface, CircleShape)
                        .border(1.dp, if (timerSeconds > 0) Brand.Accent.copy(alpha = 0.6f) else Brand.Hairline, CircleShape)
                        .clickable { vm.cycleTimer() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(if (timerSeconds == 0) "⏱" else "${timerSeconds}s",
                        fontSize = 14.sp, fontWeight = FontWeight.Bold,
                        color = if (timerSeconds > 0) Brand.Accent else Brand.TextSecondary)
                }
            }
        }
    }
}

@Composable
private fun ShutterButton(isReady: Boolean, isCapturing: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(82.dp)
            .border(2.5f.dp, if (isReady) Brand.Success else Color.White.copy(alpha = 0.55f), CircleShape)
            .background(
                if (isReady) Brand.Success else Color.White.copy(alpha = 0.9f),
                CircleShape
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (!isReady) {
            Box(Modifier.size(26.dp).background(Color.Black.copy(alpha = 0.15f), CircleShape))
        }
    }
}

@Composable
private fun CompositionTipOverlay(plan: ShootingPlan) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 130.dp)) {
        Row(
            modifier = Modifier
                .background(Brand.Surface.copy(alpha = 0.9f), RoundedCornerShape(16.dp))
                .border(1.dp, Brand.Accent.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(Modifier.size(34.dp).background(Brand.Accent.copy(alpha = 0.2f), CircleShape), contentAlignment = Alignment.Center) {
                Text(plan.composition.displayName.take(2), color = Brand.Accent, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
            Column {
                Text("${plan.composition.displayName} 构图", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text(plan.composition.reason, color = Brand.TextSecondary, fontSize = 11.sp, maxLines = 2)
            }
        }
    }
}

@Composable
private fun AiBanner(text: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 120.dp)) {
        Row(
            modifier = Modifier
                .background(Brand.Surface.copy(alpha = 0.9f), RoundedCornerShape(20.dp))
                .border(1.5f.dp, Brand.AccentSoft.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            Text("✨", fontSize = 20.sp)
            Column(Modifier.padding(start = 12.dp)) {
                Text("AI 构图灵感", color = Brand.AccentSoft, fontSize = 13.sp, fontWeight = FontWeight.Black)
                Text(text, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun LowLightBanner() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 110.dp).padding(horizontal = 24.dp)
            .background(Brand.Surface.copy(alpha = 0.9f), CircleShape).padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("💡", fontSize = 13.sp)
        Text(" 光线不足，移到明亮处效果更好", color = Color.White.copy(alpha = 0.9f), fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun PitchWarning() {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Bottom) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .background(Brand.Surface.copy(alpha = 0.9f), CircleShape)
                .padding(horizontal = 18.dp, vertical = 12.dp)
                .padding(bottom = 150.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            Text("⚠️", color = Brand.Coral, fontSize = 14.sp)
            Text(" 请平行或低角度拍摄，显腿更长", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun SpaceTip() {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Bottom) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .background(Brand.Surface.copy(alpha = 0.9f), CircleShape)
                .padding(horizontal = 18.dp, vertical = 12.dp)
                .padding(bottom = 150.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            Text("尝试平移留出一点空白，更有氛围感", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun AngleGuide(reqPitch: Float, devicePitch: Float) {
    val isReaching = (reqPitch > 0 && devicePitch >= reqPitch) || (reqPitch < 0 && devicePitch <= reqPitch)
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Bottom) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .background(Brand.Surface.copy(alpha = 0.9f), CircleShape)
                .border(2.dp, if (isReaching) Brand.Success.copy(alpha = 0.8f) else Brand.Coral.copy(alpha = 0.8f), CircleShape)
                .padding(horizontal = 20.dp, vertical = 14.dp)
                .padding(bottom = 210.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                if (isReaching) "机位正确，保持稳定" else if (reqPitch > 0) "请摄影师继续下蹲仰拍" else "请摄影师抬高俯拍",
                color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun VlogTextOverlay(text: String, isRecording: Boolean) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Bottom, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text,
            color = Color.White,
            fontSize = 26.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier
                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .padding(bottom = 260.dp)
        )
    }
}