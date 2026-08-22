package com.poseai.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.poseai.app.ui.ContentScreen
import com.poseai.app.ui.FreeUnlockScreen
import com.poseai.app.ui.HistoryGalleryScreen
import com.poseai.app.ui.OnboardingScreen
import com.poseai.app.ui.PhotoPreviewScreen
import com.poseai.app.ui.PoseGuideSheet
import com.poseai.app.ui.SaveCustomPlanScreen
import com.poseai.app.ui.StatsScreen
import com.poseai.app.ui.VideoPreviewScreen
import com.poseai.app.util.saveToGallery
import com.poseai.app.viewmodel.ShootingViewModel

/**
 * 应用入口——对应 iOS PoseAIApp。
 * 全功能免费：不再包含任何付费墙/内购。
 * 首次启动：「全功能免费」说明页 -> 首次引导 -> 拍摄主界面。
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PoseAIApp()
        }
    }
}

private enum class Screen { FREE, ONBOARDING, MAIN }

@Composable
fun PoseAIApp() {
    val context = LocalContext.current
    val vm: ShootingViewModel = viewModel()
    val records by vm.historyRecords.collectAsStateWithLifecycle(initialValue = emptyList())

    val prefs = remember { context.getSharedPreferences("poseai_prefs", Context.MODE_PRIVATE) }

    var hasSeenFree by rememberSaveable {
        mutableStateOf(prefs.getBoolean("has_seen_free", false))
    }
    var hasSeenOnboarding by rememberSaveable {
        mutableStateOf(prefs.getBoolean("has_seen_onboarding", false))
    }
    var screen by rememberSaveable {
        mutableStateOf(
            when {
                !hasSeenFree -> Screen.FREE
                !hasSeenOnboarding -> Screen.ONBOARDING
                else -> Screen.MAIN
            }
        )
    }
    var historyOpen by rememberSaveable { mutableStateOf(false) }
    var statsOpen by rememberSaveable { mutableStateOf(false) }

    // 相机 / 录音权限
    var hasCamera by rememberSaveable {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCamera = granted }
    val audioLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    LaunchedEffect(screen) {
        if (screen == Screen.MAIN) {
            if (!hasCamera) cameraLauncher.launch(Manifest.permission.CAMERA)
            audioLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    Box(Modifier.fillMaxSize()) {
        when (screen) {
            Screen.FREE -> FreeUnlockScreen(onStart = {
                hasSeenFree = true
                prefs.edit().putBoolean("has_seen_free", true).apply()
                screen = if (!hasSeenOnboarding) Screen.ONBOARDING else Screen.MAIN
            })

            Screen.ONBOARDING -> OnboardingScreen(onFinish = {
                hasSeenOnboarding = true
                prefs.edit().putBoolean("has_seen_onboarding", true).apply()
                screen = Screen.MAIN
            })

            Screen.MAIN -> when {
                historyOpen -> HistoryGalleryScreen(
                    records = records,
                    onClose = { historyOpen = false },
                    onShowStats = { statsOpen = true; historyOpen = false }
                )
                statsOpen -> StatsScreen(
                    records = records,
                    onBack = { statsOpen = false; historyOpen = true }
                )
                else -> MainContent(
                    vm = vm,
                    hasCamera = hasCamera,
                    onShowHistory = { historyOpen = true },
                    onShowGuide = { vm.showGuide.value = true },
                    onShowStats = { statsOpen = true }
                )
            }
        }
    }
}

@Composable
private fun MainContent(
    vm: ShootingViewModel,
    hasCamera: Boolean,
    onShowHistory: () -> Unit,
    onShowGuide: () -> Unit,
    onShowStats: () -> Unit
) {
    val context = LocalContext.current

    val isReviewingPhotos by vm.isReviewingPhotos.collectAsStateWithLifecycle()
    val isReviewingVlog by vm.isReviewingVlog.collectAsStateWithLifecycle()
    val exportedVlogUri by vm.exportedVlogUri.collectAsStateWithLifecycle()
    val pointsToSave by vm.pointsToSave.collectAsStateWithLifecycle()
    val showGuide by vm.showGuide.collectAsStateWithLifecycle()
    val burstImages by vm.burstImages.collectAsStateWithLifecycle()
    val scene by vm.scene.collectAsStateWithLifecycle()

    when {
        // 拍照连拍后在预览中确认
        isReviewingPhotos -> PhotoPreviewScreen(
            images = burstImages,
            onSave = { bmp ->
                val uri = saveToGallery(context, bmp)
                vm.saveShootingRecord(
                    uri = uri?.toString() ?: "",
                    scoreVal = vm.score.value.toInt(),
                    filterName = null,
                    plan = vm.currentPlan,
                    sceneType = vm.scene.value
                )
                vm.isReviewingPhotos.value = false
            },
            onRetake = { vm.retakePhotos() }
        )

        // Vlog 成片预览
        isReviewingVlog && exportedVlogUri != null -> VideoPreviewScreen(
            videoFile = java.io.File(exportedVlogUri!!),
            onSave = { vm.cleanupVlogTempFiles() },
            onRetake = { vm.cleanupVlogTempFiles() }
        )

        // 保存自定义姿势
        pointsToSave != null -> SaveCustomPlanScreen(
            points = pointsToSave!!,
            onCancel = { vm.pointsToSave.value = null },
            onSave = { name, emoji ->
                val pts = vm.pointsToSave.value
                if (pts != null) vm.saveCustomPlan(name, emoji, pts)
                vm.pointsToSave.value = null
                vm.selectPlan(0)
            }
        )

        // 拍摄指引
        showGuide -> PoseGuideSheet(
            plan = vm.currentPlan,
            scene = scene,
            onClose = { vm.showGuide.value = false }
        )

        else -> ContentScreen(
            vm = vm,
            hasCameraPermission = hasCamera,
            onShowHistory = onShowHistory,
            onShowGuide = onShowGuide,
            onShowStats = onShowStats
        )
    }
}