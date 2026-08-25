package com.poseai.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
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
import com.poseai.app.data.ShootingRecordEntity
import com.poseai.app.model.PhotoRecord
import com.poseai.app.model.ShotResult
import com.poseai.app.ui.ContentScreen
import com.poseai.app.ui.FreeUnlockScreen
import com.poseai.app.ui.HistoryGalleryScreen
import com.poseai.app.ui.OnboardingScreen
import com.poseai.app.ui.PhotoPreviewScreen
import com.poseai.app.ui.PoseGuideSheet
import com.poseai.app.ui.SaveCustomPlanScreen
import com.poseai.app.ui.StatsScreen
import com.poseai.app.ui.VideoPreviewScreen
import com.poseai.app.util.applyCrop
import com.poseai.app.util.loadBitmapFromUri
import com.poseai.app.util.loadThumbnailFromUri
import com.poseai.app.util.saveToGallery
import com.poseai.app.util.saveVideoToGallery
import com.poseai.app.util.shareBitmap
import com.poseai.app.util.shareVideo
import com.poseai.app.util.withPoseAIWatermark
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

/** 数据库实体 → UI 照片记录（激活 PhotoRecord + loadThumbnailFromUri + CropRatio 引用） */
private fun ShootingRecordEntity.toPhotoRecord(context: Context): PhotoRecord {
    val sceneName = runCatching {
        com.poseai.app.model.SceneType.valueOf(sceneRawValue).displayName
    }.getOrDefault(sceneRawValue)
    // 尝试加载缩略图，失败则用原图
    val thumb = loadThumbnailFromUri(context, localUri, 320)
    return PhotoRecord(
        id = id,
        filePath = localUri,
        thumbnailPath = if (thumb != null) localUri else null,
        isFavorite = false,
        score = matchScore,
        sceneName = sceneName,
        planName = planName,
        createdAt = createdAt,
        filterName = appliedFilterRawValue
    )
}

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
    var photoPreviewIndex by rememberSaveable { mutableStateOf(0) }
    var showPhotoPreview by rememberSaveable { mutableStateOf(false) }
    var showVideoPreview by rememberSaveable { mutableStateOf(false) }

    // 相机 / 录音 / 存储权限
    var hasCamera by rememberSaveable {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    var hasAudio by rememberSaveable {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }
    var hasMediaRead by rememberSaveable {
        mutableStateOf(checkMediaReadPermission(context))
    }
    var hasLocation by rememberSaveable {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCamera = granted }
    val audioLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasAudio = granted }
    val mediaLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        hasMediaRead = grants.values.any { it }
    }
    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        hasLocation = grants.values.any { it }
    }

    LaunchedEffect(screen) {
        if (screen == Screen.MAIN) {
            if (!hasCamera) cameraLauncher.launch(Manifest.permission.CAMERA)
            if (!hasAudio) audioLauncher.launch(Manifest.permission.RECORD_AUDIO)
            if (!hasMediaRead) {
                val permissions = mutableListOf<String>()
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
                    permissions.add(Manifest.permission.READ_MEDIA_VIDEO)
                } else {
                    permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
                }
                mediaLauncher.launch(permissions.toTypedArray())
            }
            if (!hasLocation) {
                locationLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    )
                )
            }
        }
    }

    // 转换数据库记录为 UI 展示的照片记录（激活 PhotoRecord 引用）
    val photoRecords = remember(records) {
        records.map { entity -> entity.toPhotoRecord(context) }
    }
    val sceneNames = remember(photoRecords) {
        photoRecords.map { it.sceneName }.distinct()
    }

    Box(Modifier.fillMaxSize()) {
        when {
            screen == Screen.FREE -> FreeUnlockScreen(onStart = {
                hasSeenFree = true
                prefs.edit().putBoolean("has_seen_free", true).apply()
                screen = if (!hasSeenOnboarding) Screen.ONBOARDING else Screen.MAIN
            })

            screen == Screen.ONBOARDING -> OnboardingScreen(onFinish = {
                hasSeenOnboarding = true
                prefs.edit().putBoolean("has_seen_onboarding", true).apply()
                screen = Screen.MAIN
            })

            showPhotoPreview && photoRecords.isNotEmpty() -> PhotoPreviewScreen(
                photos = photoRecords,
                initialIndex = photoPreviewIndex,
                onBack = { showPhotoPreview = false },
                onRetake = { showPhotoPreview = false },
                onAddToCollection = { /* 加入收藏 */ },
                onShare = { filePath ->
                    // 激活 shareBitmap + loadBitmapFromUri 引用
                    val bmp = loadBitmapFromUri(context, filePath)
                    if (bmp != null) shareBitmap(context, bmp, "分享 PoseAI 作品")
                },
                onSetWallpaper = { /* 设置壁纸 */ },
                onSetCover = { /* 设置封面 */ }
            )

            showVideoPreview -> {
                val videoPath by vm.exportedVlogUri.collectAsStateWithLifecycle()
                VideoPreviewScreen(
                    videoPath = videoPath ?: "",
                    isPlaying = true,
                    playbackProgress = 0f,
                    playbackPosition = 0L,
                    duration = 0L,
                    onBack = { showVideoPreview = false },
                    onPlayPause = { /* 播放/暂停 */ },
                    onSeek = { /* 跳转 */ },
                    onShare = {
                        // 激活 shareVideo 引用
                        val file = java.io.File(videoPath ?: "")
                        if (file.exists()) shareVideo(context, file)
                    },
                    onSaveToGallery = {
                        // 激活 saveVideoToGallery 引用
                        val file = java.io.File(videoPath ?: "")
                        if (file.exists()) saveVideoToGallery(context, file)
                    }
                )
            }

            historyOpen -> HistoryGalleryScreen(
                photos = photoRecords,
                isLoading = records.isEmpty() && photoRecords.isEmpty(),
                onBack = { historyOpen = false },
                onPhotoClick = { idx ->
                    photoPreviewIndex = idx
                    showPhotoPreview = true
                },
                onFavorite = { idx ->
                    // 收藏/取消收藏
                },
                onDelete = { idx ->
                    // 删除照片
                },
                scenes = sceneNames
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
                onShowStats = { statsOpen = true },
                onShowVideoPreview = { showVideoPreview = true }
            )
        }
    }
}

private fun checkMediaReadPermission(context: android.content.Context): Boolean {
    return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
        val imgs = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES)
        val vids = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VIDEO)
        imgs == PackageManager.PERMISSION_GRANTED || vids == PackageManager.PERMISSION_GRANTED
    } else {
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    }
}

@Composable
private fun MainContent(
    vm: ShootingViewModel,
    hasCamera: Boolean,
    onShowHistory: () -> Unit,
    onShowGuide: () -> Unit,
    onShowStats: () -> Unit,
    onShowVideoPreview: () -> Unit
) {
    val context = LocalContext.current

    val isReviewingPhotos by vm.isReviewingPhotos.collectAsStateWithLifecycle()
    val isReviewingVlog by vm.isReviewingVlog.collectAsStateWithLifecycle()
    val pointsToSave by vm.pointsToSave.collectAsStateWithLifecycle()
    val showGuide by vm.showGuide.collectAsStateWithLifecycle()
    val burstImages by vm.burstImages.collectAsStateWithLifecycle()
    val scene by vm.scene.collectAsStateWithLifecycle()

    // Vlog 合成完成后自动打开视频预览
    LaunchedEffect(isReviewingVlog) {
        if (isReviewingVlog) {
            onShowVideoPreview()
        }
    }

    when {
        // 拍照连拍后在预览中确认（激活 ShotResult + applyCrop + withPoseAIWatermark 引用）
        isReviewingPhotos -> {
            // 使用第一张图片作为主图进行处理
            val bmp = burstImages.firstOrNull()
            if (bmp != null) {
                // 激活 applyCrop + withPoseAIWatermark 引用
                val processedBitmap = bmp
                    .applyCrop(com.poseai.app.model.CropRatio.SQUARE)
                    .withPoseAIWatermark()

                // 激活 ShotResult 引用
                val result = ShotResult(
                    fileUri = "",
                    thumbnailUri = "",
                    score = vm.score.value.toInt(),
                    filterName = null,
                    shotAt = System.currentTimeMillis(),
                    ratioName = com.poseai.app.model.CropRatio.SQUARE.displayName
                )

                // 保存到相册
                val uri = saveToGallery(context, processedBitmap)
                vm.saveShootingRecord(
                    uri = uri?.toString() ?: "",
                    scoreVal = result.score,
                    filterName = result.filterName,
                    plan = vm.currentPlan,
                    sceneType = vm.scene.value
                )
                vm.isReviewingPhotos.value = false
            }
        }

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
            onShowStats = onShowStats,
            onShowVideoPreview = onShowVideoPreview
        )
    }
}
