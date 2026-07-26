package com.poseai.app.ui

import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Exposure
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Mood
import androidx.compose.material.icons.filled.MovieFilter
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.poseai.app.model.SceneType
import com.poseai.app.model.VlogTemplate
import com.poseai.app.ui.theme.Accent
import androidx.compose.ui.graphics.toArgb
import com.poseai.app.ui.theme.BackgroundDark
import com.poseai.app.ui.theme.Error
import com.poseai.app.ui.theme.SurfaceDark
import com.poseai.app.ui.theme.SurfaceGlass
import com.poseai.app.ui.theme.Success
import com.poseai.app.ui.theme.TextPrimary
import com.poseai.app.ui.theme.TextSecondary
import com.poseai.app.ui.theme.Warning
import com.poseai.app.util.PhotoFilterEngine
import com.poseai.app.viewmodel.ShootingViewModel
import kotlinx.coroutines.launch

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

    var showSettings by remember { mutableStateOf(false) }
    var showExposurePanel by remember { mutableStateOf(false) }
    val exposureValue by viewModel.exposureValue.collectAsState()

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    val statusBarPadding = WindowInsets.statusBars.asPaddingValues()
    val navBarPadding = WindowInsets.navigationBars.asPaddingValues()

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

        if (lowLightWarning && lowLightMode) {
            LowLightOverlay()
        }

        if (screenFillLightEnabled) {
            ScreenFillLightOverlay(intensity = screenFillLightIntensity)
        }

        if (smileEnabled) {
            SmileIndicator(strength = smileStrength)
        }

        if (detectedPoseLines.isNotEmpty() || detectedPosePoints.isNotEmpty()) {
            DetectedSkeletonOverlay(
                lines = detectedPoseLines,
                points = detectedPosePoints,
                score = poseScore,
                modifier = Modifier.fillMaxSize()
            )
        }

        currentPlan?.let { plan ->
            val targetPoints = if (useSecondaryPose && plan.secondaryPosePoints.isNotEmpty()) {
                plan.secondaryPosePoints
            } else {
                plan.posePoints
            }
            SilhouetteOverlay(
                posePoints = targetPoints,
                score = poseScore,
                modifier = Modifier.fillMaxSize()
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = statusBarPadding.calculateTopPadding() + 8.dp)
                .padding(horizontal = 16.dp)
        ) {
            TopBar(
                sceneName = currentScene.displayName,
                planName = currentPlan?.poseName ?: "",
                planIndex = (viewModel.currentPlanIndex.collectAsState().value + 1),
                totalPlans = currentScene.plans.size,
                onSettings = { showSettings = true },
                onSceneClick = { viewModel.toggleSceneSelector() }
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

        if (showExposurePanel) {
            ExposurePanel(
                exposureValue = exposureValue,
                minExposure = -10,
                maxExposure = 10,
                onDecrease = { viewModel.decreaseExposure() },
                onIncrease = { viewModel.increaseExposure() },
                onDismiss = { showExposurePanel = false }
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = navBarPadding.calculateBottomPadding() + 16.dp),
            verticalArrangement = Arrangement.Bottom
        ) {
            PoseScoreBar(score = poseScore)

            Spacer(modifier = Modifier.height(16.dp))

            PoseDescription(
                description = currentPlan?.poseDescription ?: "",
                modifier = Modifier.fillMaxWidth()
            )

            currentPlan?.let { plan ->
                if (plan.sequence.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    SequenceIndicator(
                        currentIndex = currentSequenceIndex,
                        totalSteps = plan.sequence.size,
                        stepName = currentSequenceShot?.title ?: "",
                        onPrevious = { viewModel.previousSequenceStep() },
                        onNext = { viewModel.nextSequenceStep() }
                    )
                }
                if (plan.multiAngles.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    AngleIndicator(
                        currentAngleName = currentAngle?.title ?: "",
                        angleCount = plan.multiAngles.size,
                        onNextAngle = { viewModel.nextAngle() }
                    )
                }
                if (plan.secondaryPosePoints.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    SecondaryPoseToggle(
                        isSecondary = useSecondaryPose,
                        onToggle = { viewModel.toggleSecondaryPose() }
                    )
                }
                if (plan.vlogScript != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    PlanVlogButton(
                        onClick = { viewModel.startVlogFromPlan() }
                    )
                }
            }

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
                onFilter = { viewModel.toggleFilterSelector() },
                onExposure = { showExposurePanel = !showExposurePanel },
                onScreenFillLight = { viewModel.toggleScreenFillLight() },
                onStartVlog = { viewModel.toggleVlogTemplateSelector() },
                onStopVlog = { viewModel.stopVlog() },
                onScene = { viewModel.toggleSceneSelector() },
                isVlogRecording = isVlogRecording,
                isVlogMerging = isVlogMerging,
                isAutoCapturing = isAutoCapturing,
                smileEnabled = smileEnabled,
                gridEnabled = gridEnabled,
                screenFillLightEnabled = screenFillLightEnabled,
                currentFilterName = currentFilter.displayName
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

@Composable
fun TopBar(
    sceneName: String,
    planName: String,
    planIndex: Int,
    totalPlans: Int,
    onSettings: () -> Unit,
    onSceneClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceGlass, RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onSceneClick) {
            Icon(
                imageVector = Icons.Default.LocationOn,
                contentDescription = "场景",
                tint = Accent,
                modifier = Modifier.size(22.dp)
            )
        }
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
fun ScreenFillLightOverlay(intensity: Float) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White.copy(alpha = intensity * 0.25f))
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
    onFilter: () -> Unit,
    onExposure: () -> Unit,
    onScreenFillLight: () -> Unit,
    onStartVlog: () -> Unit,
    onStopVlog: () -> Unit,
    onScene: () -> Unit,
    isVlogRecording: Boolean,
    isVlogMerging: Boolean,
    isAutoCapturing: Boolean,
    smileEnabled: Boolean,
    gridEnabled: Boolean,
    screenFillLightEnabled: Boolean,
    currentFilterName: String
) {
    val configuration = LocalConfiguration.current
    val isCompactHeight = configuration.screenHeightDp < 600
    val iconSize = if (isCompactHeight) 22.dp else 26.dp
    val captureButtonSize = if (isCompactHeight) 72.dp else 88.dp
    val navButtonSize = if (isCompactHeight) 48.dp else 56.dp
    val captureIconSize = if (isCompactHeight) 32.dp else 40.dp
    val navIconSize = if (isCompactHeight) 28.dp else 32.dp

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
                    modifier = Modifier.size(iconSize)
                )
            }
            IconButton(onClick = onGrid) {
                Icon(
                    imageVector = Icons.Default.GridOn,
                    contentDescription = "网格",
                    tint = if (gridEnabled) Accent else TextSecondary,
                    modifier = Modifier.size(iconSize)
                )
            }
            IconButton(onClick = onFilter) {
                Icon(
                    imageVector = Icons.Default.Palette,
                    contentDescription = "滤镜",
                    tint = if (currentFilterName != "原图") Accent else TextSecondary,
                    modifier = Modifier.size(iconSize)
                )
            }
            IconButton(onClick = onExposure) {
                Icon(
                    imageVector = Icons.Default.Exposure,
                    contentDescription = "曝光",
                    tint = TextSecondary,
                    modifier = Modifier.size(iconSize)
                )
            }
            IconButton(onClick = onScreenFillLight) {
                Icon(
                    imageVector = Icons.Default.BrightnessHigh,
                    contentDescription = "屏幕补光",
                    tint = if (screenFillLightEnabled) Accent else TextSecondary,
                    modifier = Modifier.size(iconSize)
                )
            }
        }

        Spacer(modifier = Modifier.height(if (isCompactHeight) 6.dp else 10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onScene) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = "场景",
                    tint = TextSecondary,
                    modifier = Modifier.size(iconSize)
                )
            }
            IconButton(onClick = onToggleAuto) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = "自动抓拍",
                    tint = if (isAutoCapturing) Accent else TextSecondary,
                    modifier = Modifier.size(iconSize)
                )
            }
            IconButton(onClick = onSwitch) {
                Icon(
                    imageVector = Icons.Default.Cameraswitch,
                    contentDescription = "切换摄像头",
                    tint = TextPrimary,
                    modifier = Modifier.size(iconSize)
                )
            }
            IconButton(onClick = onStartVlog) {
                Icon(
                    imageVector = Icons.Default.Videocam,
                    contentDescription = "Vlog",
                    tint = if (isVlogRecording || isVlogMerging) Accent else TextSecondary,
                    modifier = Modifier.size(iconSize)
                )
            }
        }

        Spacer(modifier = Modifier.height(if (isCompactHeight) 10.dp else 16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onPrevious,
                modifier = Modifier.size(navButtonSize)
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "上一个姿势",
                    tint = TextPrimary,
                    modifier = Modifier.size(navIconSize)
                )
            }

            if (isVlogRecording || isVlogMerging) {
                IconButton(
                    onClick = onStopVlog,
                    modifier = Modifier
                        .size(captureButtonSize)
                        .background(Error, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = "停止录制",
                        tint = Color.White,
                        modifier = Modifier.size(captureIconSize)
                    )
                }
            } else {
                IconButton(
                    onClick = onCapture,
                    modifier = Modifier
                        .size(captureButtonSize)
                        .background(Accent, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "拍照",
                        tint = Color.White,
                        modifier = Modifier.size(captureIconSize)
                    )
                }
            }

            IconButton(
                onClick = onNext,
                modifier = Modifier.size(navButtonSize)
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "下一个姿势",
                    tint = TextPrimary,
                    modifier = Modifier.size(navIconSize)
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
                alpha = 0.4f
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
                    alpha = 0.3f
                )
            }
        }
    }
}

@Composable
fun DetectedSkeletonOverlay(
    lines: List<Pair<android.graphics.PointF, android.graphics.PointF>>,
    points: Map<String, android.graphics.PointF>,
    score: Float,
    modifier: Modifier = Modifier
) {
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        lines.forEach { (pa, pb) ->
            drawLine(
                color = Color.Green,
                start = androidx.compose.ui.geometry.Offset(pa.x * w, pa.y * h),
                end = androidx.compose.ui.geometry.Offset(pb.x * w, pb.y * h),
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
                center = androidx.compose.ui.geometry.Offset(cx, cy),
                alpha = 0.9f
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

@Composable
fun ExposurePanel(
    exposureValue: Int,
    minExposure: Int,
    maxExposure: Int,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(end = 16.dp, top = 100.dp),
        contentAlignment = Alignment.TopEnd
    ) {
        Column(
            modifier = Modifier
                .background(SurfaceGlass, RoundedCornerShape(16.dp))
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
    templates: List<VlogTemplate>,
    onTemplateSelected: (VlogTemplate) -> Unit,
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
fun VlogTemplateItem(template: VlogTemplate, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp)
            .background(SurfaceGlass, RoundedCornerShape(12.dp))
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
            .background(SurfaceGlass, RoundedCornerShape(12.dp))
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
            .background(SurfaceGlass, RoundedCornerShape(12.dp))
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
            .background(SurfaceGlass, RoundedCornerShape(12.dp))
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
