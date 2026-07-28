package com.poseai.app.ui

import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mood
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.poseai.app.engine.OotdAnalyzer
import com.poseai.app.model.SceneType
import com.poseai.app.ui.theme.Accent
import com.poseai.app.ui.theme.BackgroundDark
import com.poseai.app.ui.theme.SurfaceDark
import com.poseai.app.ui.theme.SurfaceGlass
import com.poseai.app.ui.theme.TextPrimary
import com.poseai.app.ui.theme.TextSecondary
import com.poseai.app.util.CollageEngine
import com.poseai.app.viewmodel.ShootingViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

private const val TAG = "ExtraScreens"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SceneSelectorBottomSheet(
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
            LazyColumn {
                items(SceneType.values()) { scene ->
                    SceneItem(
                        scene = scene,
                        isSelected = scene == currentScene,
                        onClick = {
                            onSceneSelected(scene)
                            onDismiss()
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun OOTDAnalysisScreen(viewModel: ShootingViewModel) {
    var selectedImagePath by remember { mutableStateOf<String?>(null) }
    var isAnalyzing by remember { mutableStateOf(false) }
    var analysisResult by remember { mutableStateOf<OotdAnalyzer.Result?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // OotdAnalyzer 实例：remember 保证单例，onDispose 时释放资源
    val ootdAnalyzer = remember {
        OotdAnalyzer(context)
    }
    DisposableEffect(ootdAnalyzer) {
        onDispose {
            ootdAnalyzer.close()
        }
    }

    val launcher = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let {
            val path = copyUriToFile(context, it)
            selectedImagePath = path
            analysisResult = null
            errorMessage = null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "OOTD 穿搭分析",
            color = TextPrimary,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(320.dp)
                .background(SurfaceDark, RoundedCornerShape(16.dp))
                .border(2.dp, Accent.copy(alpha = 0.3f), RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (selectedImagePath != null) {
                coil.compose.AsyncImage(
                    model = selectedImagePath,
                    contentDescription = "穿搭照片",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit
                )
                if (isAnalyzing) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = Accent)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("AI 分析中...", color = TextPrimary, fontSize = 14.sp)
                        }
                    }
                }
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Style,
                        contentDescription = "上传照片",
                        tint = Accent,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "上传全身照",
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "AI 将分析搭配并给出建议",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 错误提示
        errorMessage?.let { msg ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFFF6B6B).copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = null,
                        tint = Color(0xFFFF6B6B),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = msg,
                        color = Color(0xFFFF6B6B),
                        fontSize = 13.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        analysisResult?.let { result ->
            OOTDAnalysisCard(result = result)
            Spacer(modifier = Modifier.height(16.dp))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = {
                    launcher.launch(
                        androidx.activity.result.PickVisualMediaRequest(
                            androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly
                        )
                    )
                },
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Accent
                )
            ) {
                Text("选择照片", fontSize = 15.sp)
            }

            Button(
                onClick = {
                    val currentPath = selectedImagePath
                    if (currentPath != null) {
                        isAnalyzing = true
                        errorMessage = null
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                try {
                                    ootdAnalyzer.analyze(currentPath)
                                } catch (e: Exception) {
                                    Log.e(TAG, "OOTD analysis failed", e)
                                    OotdAnalyzer.Result(
                                        overallScore = 0f, colorHarmony = 0f,
                                        proportionScore = 0f, styleMatch = 0f,
                                        suggestions = emptyList(), styleTags = emptyList(),
                                        error = "分析失败：${e.message ?: "未知错误"}"
                                    )
                                }
                            }
                            analysisResult = result
                            if (result.error != null) {
                                errorMessage = result.error
                            }
                            isAnalyzing = false
                        }
                    }
                },
                enabled = selectedImagePath != null && !isAnalyzing,
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Accent)
            ) {
                Text(
                    text = if (isAnalyzing) "分析中" else "开始分析",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.Black
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "支持全身照穿搭分析：场景识别、色彩搭配、比例建议、风格评分",
            color = TextSecondary,
            fontSize = 12.sp,
            textAlign = TextAlign.Center
        )
    }
}

private fun copyUriToFile(context: android.content.Context, uri: android.net.Uri): String? {
    return try {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return null
        val outputFile = java.io.File(context.cacheDir, "ootd_${System.currentTimeMillis()}.jpg")
        inputStream.use { input ->
            java.io.FileOutputStream(outputFile).use { output ->
                input.copyTo(output)
            }
            outputFile.absolutePath
        }
    } catch (e: Exception) {
        Log.w(TAG, "Failed to copy URI to cache", e)
        null
    }
}

@Composable
fun OOTDAnalysisCard(result: OotdAnalyzer.Result) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceDark, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "综合评分",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
                Text(
                    text = "${result.overallScore.toInt()} 分",
                    color = Accent,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .background(Accent.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (result.overallScore >= 80f) "优" else if (result.overallScore >= 60f) "良" else "中",
                    color = Accent,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 场景识别结果
        if (result.detectedScene != SceneType.UNKNOWN) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = Accent,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "识别场景：${result.detectedScene.displayName}",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }
        }

        OOTDScoreBar(label = "色彩和谐", score = result.colorHarmony)
        Spacer(modifier = Modifier.height(8.dp))
        OOTDScoreBar(label = "比例协调", score = result.proportionScore)
        Spacer(modifier = Modifier.height(8.dp))
        OOTDScoreBar(label = "风格契合", score = result.styleMatch)

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "风格标签",
            color = TextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            result.styleTags.forEach { tag ->
                Box(
                    modifier = Modifier
                        .background(Accent.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(text = tag, color = Accent, fontSize = 12.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "搭配建议",
            color = TextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(8.dp))
        result.suggestions.forEachIndexed { index, suggestion ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = "${index + 1}.",
                    color = Accent,
                    fontSize = 13.sp,
                    modifier = Modifier.width(20.dp)
                )
                Text(
                    text = suggestion,
                    color = TextSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun OOTDScoreBar(label: String, score: Float) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = TextSecondary,
            fontSize = 13.sp,
            modifier = Modifier.width(70.dp)
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(6.dp)
                .background(SurfaceGlass, CircleShape)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction = (score / 100f).coerceIn(0f, 1f))
                    .height(6.dp)
                    .background(Accent, CircleShape)
            )
        }
        Text(
            text = "${score.toInt()}",
            color = TextPrimary,
            fontSize = 13.sp,
            modifier = Modifier.width(30.dp),
            textAlign = TextAlign.End
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(
    viewModel: ShootingViewModel,
    onEditPhoto: (Long) -> Unit = {}
) {
    val records by viewModel.getCaptureHistory().collectAsState(initial = emptyList())
    var sceneStats by remember { mutableStateOf<List<com.poseai.app.data.SceneCount>>(emptyList()) }
    var showFavoritesOnly by remember { mutableStateOf(false) }
    var selectedRecord by remember { mutableStateOf<com.poseai.app.data.ShootingRecord?>(null) }

    // ── 拼图模式 ──
    var isCollageMode by remember { mutableStateOf(false) }
    var selectedCollageIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var showCollageLayoutSheet by remember { mutableStateOf(false) }
    var isGeneratingCollage by remember { mutableStateOf(false) }
    var collageResultPath by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // 拍照计数变化时刷新场景统计
    val captureCount by viewModel.captureCount.collectAsState()
    LaunchedEffect(captureCount, records.size) {
        sceneStats = viewModel.getSceneDistribution()
    }

    val displayRecords = if (showFavoritesOnly) records.filter { it.isFavorite } else records

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isCollageMode) {
                // 拼图模式标题栏
                IconButton(onClick = {
                    isCollageMode = false
                    selectedCollageIds = emptySet()
                }) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "退出拼图",
                        tint = TextPrimary
                    )
                }
                Text(
                    text = "已选 ${selectedCollageIds.size} 张",
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                // 确认拼图按钮
                Button(
                    onClick = {
                        if (selectedCollageIds.size in 2..6) {
                            showCollageLayoutSheet = true
                        }
                    },
                    enabled = selectedCollageIds.size in 2..6,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Accent,
                        contentColor = androidx.compose.ui.graphics.Color.Black
                    ),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("拼图", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
            } else {
                Text(
                    text = "我的相册",
                    color = TextPrimary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                // 拼图入口按钮
                IconButton(onClick = {
                    isCollageMode = true
                    selectedCollageIds = emptySet()
                }) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "拼图",
                        tint = Accent,
                        modifier = Modifier.size(22.dp)
                    )
                }
                // 收藏筛选切换
                FilterChip(
                    selected = showFavoritesOnly,
                    onClick = { showFavoritesOnly = !showFavoritesOnly },
                    label = {
                        Text(
                            text = if (showFavoritesOnly) "已收藏" else "收藏",
                            fontSize = 12.sp
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = if (showFavoritesOnly) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Accent.copy(alpha = 0.2f),
                        selectedLabelColor = Accent,
                        selectedLeadingIconColor = Accent
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 场景分布统计卡片（激活 getSceneDistribution 死代码）
        if (sceneStats.isNotEmpty() && !isCollageMode) {
            SceneStatsCard(stats = sceneStats, totalCount = records.size)
            Spacer(modifier = Modifier.height(12.dp))
        }

        if (displayRecords.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = if (showFavoritesOnly) Icons.Default.StarBorder else Icons.Default.Palette,
                        contentDescription = if (showFavoritesOnly) "暂无收藏" else "暂无照片",
                        tint = TextSecondary.copy(alpha = 0.5f),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (showFavoritesOnly) "暂无收藏照片" else "暂无照片",
                        color = TextSecondary,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (showFavoritesOnly) "点击照片右上角的星标添加收藏" else "去拍摄你的第一张 PoseAI 照片吧",
                        color = TextSecondary.copy(alpha = 0.7f),
                        fontSize = 13.sp
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(displayRecords) { record ->
                    if (isCollageMode) {
                        // 拼图多选模式：带复选框
                        val isSelected = record.id in selectedCollageIds
                        Box(
                            modifier = Modifier
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(SurfaceDark)
                                .clickable {
                                    selectedCollageIds = if (isSelected) {
                                        selectedCollageIds - record.id
                                    } else {
                                        if (selectedCollageIds.size < 6) {
                                            selectedCollageIds + record.id
                                        } else {
                                            selectedCollageIds
                                        }
                                    }
                                }
                        ) {
                            if (record.imagePath.isNotEmpty()) {
                                coil.compose.AsyncImage(
                                    model = record.imagePath,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                )
                            }
                            // 选中遮罩
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        if (isSelected) Accent.copy(alpha = 0.4f)
                                        else Color.Black.copy(alpha = 0.15f)
                                    )
                            )
                            // 复选框
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(6.dp)
                                    .size(22.dp)
                                    .background(
                                        if (isSelected) Accent else Color.Black.copy(alpha = 0.5f),
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                } else {
                                    Text(
                                        text = (selectedCollageIds.size + 1).toString()
                                            .takeIf { !isSelected } ?: "",
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    } else {
                        GalleryItem(
                            record = record,
                            onClick = { selectedRecord = record },
                            onToggleFavorite = {
                                viewModel.toggleFavorite(record.id, !record.isFavorite)
                            }
                        )
                    }
                }
            }
        }
    }

    // 详情/收藏/删除弹窗
    selectedRecord?.let { record ->
        PhotoDetailBottomSheet(
            record = record,
            onDismiss = { selectedRecord = null },
            onToggleFavorite = {
                viewModel.toggleFavorite(record.id, !record.isFavorite)
                selectedRecord = null
            },
            onDelete = {
                viewModel.deleteRecordById(record.id)
                selectedRecord = null
            },
            onEdit = {
                selectedRecord = null
                onEditPhoto(record.id)
            }
        )
    }

    // 拼图布局选择底部弹窗
    if (showCollageLayoutSheet) {
        CollageLayoutBottomSheet(
            selectedCount = selectedCollageIds.size,
            onDismiss = { showCollageLayoutSheet = false },
            onLayoutSelected = { layout ->
                showCollageLayoutSheet = false
                isGeneratingCollage = true
                scope.launch {
                    val result = generateCollage(
                        records = displayRecords,
                        selectedIds = selectedCollageIds,
                        layout = layout,
                        outputDir = context.filesDir
                    )
                    collageResultPath = result
                    isGeneratingCollage = false
                    isCollageMode = false
                    selectedCollageIds = emptySet()
                }
            }
        )
    }

    // 拼图生成中遮罩
    if (isGeneratingCollage) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f))
                .clickable(enabled = false) {},
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = Accent)
                Spacer(modifier = Modifier.height(16.dp))
                Text("正在生成拼图...", color = Color.White, fontSize = 16.sp)
            }
        }
    }

    // 拼图结果提示
    collageResultPath?.let { path ->
        LaunchedEffect(path) {
            kotlinx.coroutines.delay(2000)
            collageResultPath = null
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .background(Accent.copy(alpha = 0.9f), RoundedCornerShape(12.dp))
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "拼图已保存到相册",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

/**
 * 拼图布局选择底部弹窗
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollageLayoutBottomSheet(
    selectedCount: Int,
    onDismiss: () -> Unit,
    onLayoutSelected: (CollageEngine.Layout) -> Unit
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
                text = "选择拼图布局",
                color = TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Text(
                text = "已选择 $selectedCount 张照片",
                color = TextSecondary,
                fontSize = 13.sp,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // 只显示可用布局（照片数量 >= 布局要求）
            val availableLayouts = CollageEngine.Layout.values().filter {
                it.count <= selectedCount
            }

            availableLayouts.forEach { layout ->
                val layoutPreview = getLayoutPreviewText(layout)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(SurfaceGlass)
                        .clickable { onLayoutSelected(layout) }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 布局预览图标
                    CollageLayoutPreview(
                        layout = layout,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = layout.displayName,
                            color = TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = layoutPreview,
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "拼图布局",
                        tint = Accent,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * 拼图布局预览小图标
 */
@Composable
fun CollageLayoutPreview(
    layout: CollageEngine.Layout,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Accent.copy(alpha = 0.1f))
            .border(1.dp, Accent.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
    ) {
        when (layout) {
            CollageEngine.Layout.GRID_2 -> {
                // 上下两格
                Row(modifier = Modifier.fillMaxSize().padding(2.dp)) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(1.dp)
                            .background(Accent.copy(alpha = 0.3f), RoundedCornerShape(2.dp))
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(1.dp)
                            .background(Accent.copy(alpha = 0.3f), RoundedCornerShape(2.dp))
                    )
                }
            }
            CollageEngine.Layout.GRID_3 -> {
                // 左大 + 右二小
                Row(modifier = Modifier.fillMaxSize().padding(2.dp)) {
                    Box(
                        modifier = Modifier
                            .weight(0.6f)
                            .fillMaxHeight()
                            .padding(1.dp)
                            .background(Accent.copy(alpha = 0.4f), RoundedCornerShape(2.dp))
                    )
                    Column(modifier = Modifier.weight(0.4f).fillMaxHeight()) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(1.dp)
                                .background(Accent.copy(alpha = 0.3f), RoundedCornerShape(2.dp))
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(1.dp)
                                .background(Accent.copy(alpha = 0.3f), RoundedCornerShape(2.dp))
                        )
                    }
                }
            }
            CollageEngine.Layout.GRID_4 -> {
                // 2x2 四宫格
                Column(modifier = Modifier.fillMaxSize().padding(2.dp)) {
                    Row(modifier = Modifier.weight(1f)) {
                        repeat(2) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .padding(1.dp)
                                    .background(Accent.copy(alpha = 0.3f), RoundedCornerShape(2.dp))
                            )
                        }
                    }
                    Row(modifier = Modifier.weight(1f)) {
                        repeat(2) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .padding(1.dp)
                                    .background(Accent.copy(alpha = 0.3f), RoundedCornerShape(2.dp))
                            )
                        }
                    }
                }
            }
            CollageEngine.Layout.GRID_6 -> {
                // 2x3 六宫格
                Column(modifier = Modifier.fillMaxSize().padding(2.dp)) {
                    Row(modifier = Modifier.weight(1f)) {
                        repeat(3) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .padding(1.dp)
                                    .background(Accent.copy(alpha = 0.3f), RoundedCornerShape(2.dp))
                            )
                        }
                    }
                    Row(modifier = Modifier.weight(1f)) {
                        repeat(3) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .padding(1.dp)
                                    .background(Accent.copy(alpha = 0.3f), RoundedCornerShape(2.dp))
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun getLayoutPreviewText(layout: CollageEngine.Layout): String {
    return when (layout) {
        CollageEngine.Layout.GRID_2 -> "横向/纵向并排，自动选择最佳方向"
        CollageEngine.Layout.GRID_3 -> "1大图+2小图，经典拼图布局"
        CollageEngine.Layout.GRID_4 -> "2×2 四宫格，均匀排列"
        CollageEngine.Layout.GRID_6 -> "2×3 六宫格，适合多图展示"
    }
}

/**
 * 生成拼图：从选中的记录中加载图片，调用 CollageEngine 拼接，保存到相册目录
 */
private suspend fun generateCollage(
    records: List<com.poseai.app.data.ShootingRecord>,
    selectedIds: Set<Long>,
    layout: CollageEngine.Layout,
    outputDir: File
): String? {
    return withContext(Dispatchers.IO) {
        try {
            // 按选中顺序加载 Bitmap
            val selectedRecords = records.filter { it.id in selectedIds }
            val bitmaps = selectedRecords.mapNotNull { record ->
                val path = record.imagePath
                if (path.isNotEmpty()) {
                    try {
                        val options = android.graphics.BitmapFactory.Options().apply {
                            inSampleSize = 2  // 降采样，避免 OOM
                        }
                        android.graphics.BitmapFactory.decodeFile(path, options)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to decode bitmap: $path", e)
                        null
                    }
                } else null
            }

            if (bitmaps.size < layout.count) {
                bitmaps.forEach { it.recycle() }
                return@withContext null
            }

            // 生成拼图
            val collage = CollageEngine.createCollage(bitmaps, layout)
            // 回收原始 Bitmap
            bitmaps.forEach { it.recycle() }

            if (collage == null) return@withContext null

            // 保存到相册目录
            val photosDir = File(outputDir, "photos").apply { mkdirs() }
            val outputFile = File(photosDir, "collage_${UUID.randomUUID()}.jpg")
            FileOutputStream(outputFile).use { out ->
                collage.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, out)
            }
            collage.recycle()

            outputFile.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create collage", e)
            null
        }
    }
}

/**
 * 场景分布统计卡片：展示各场景的拍摄次数和占比
 */
@Composable
fun SceneStatsCard(stats: List<com.poseai.app.data.SceneCount>, totalCount: Int) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceDark, RoundedCornerShape(16.dp))
            .border(1.dp, Accent.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.LocationOn,
                contentDescription = "场景分布",
                tint = Accent,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "场景分布",
                color = TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "共 $totalCount 张",
                color = TextSecondary,
                fontSize = 12.sp
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 取前 5 个场景，按 count 降序已由 SQL 保证
        val topStats = stats.take(5)
        val maxCount = topStats.maxOfOrNull { it.count } ?: 1

        topStats.forEach { stat ->
            val sceneDisplayName = try {
                SceneType.valueOf(stat.scene).displayName
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "Unknown scene type: ${stat.scene}", e)
                stat.scene
            }
            val ratio = if (maxCount > 0) stat.count.toFloat() / maxCount else 0f

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = sceneDisplayName,
                        color = TextPrimary,
                        fontSize = 13.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "${stat.count} 张",
                        color = Accent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .background(SurfaceGlass, CircleShape)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction = ratio.coerceIn(0f, 1f))
                            .height(4.dp)
                            .background(Accent, CircleShape)
                    )
                }
            }
        }
    }
}

/**
 * 照片详情底部弹窗：显示大图、收藏、删除按钮
 * 激活 ShootingRecord.isFavorite 字段相关 UI 交互
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoDetailBottomSheet(
    record: com.poseai.app.data.ShootingRecord,
    onDismiss: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit = {}
) {
    val sceneDisplayName = try {
        SceneType.valueOf(record.scene).displayName
    } catch (e: IllegalArgumentException) {
        Log.w(TAG, "Unknown scene type: ${record.scene}", e)
        record.scene
    }

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
            // 大图预览
            if (record.imagePath.isNotEmpty()) {
                coil.compose.AsyncImage(
                    model = record.imagePath,
                    contentDescription = "照片预览",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(360.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(BackgroundDark),
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(BackgroundDark),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "图片文件不存在",
                        color = TextSecondary,
                        fontSize = 14.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 元信息
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "场景：$sceneDisplayName",
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "姿势：${record.poseName.ifEmpty { "未记录" }}",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "评分：${record.score.toInt()} 分",
                        color = Accent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                // 收藏按钮
                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        imageVector = if (record.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = if (record.isFavorite) "取消收藏" else "收藏",
                        tint = if (record.isFavorite) Accent else TextSecondary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 操作按钮：编辑 + 删除
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 编辑按钮（主操作）
                Button(
                    onClick = onEdit,
                    enabled = record.imagePath.isNotEmpty(),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Accent,
                        contentColor = androidx.compose.ui.graphics.Color.Black
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("编辑", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                }
                // 删除按钮（危险操作）
                OutlinedButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = androidx.compose.ui.graphics.Color(0xFFFF6B6B)
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("删除照片", fontSize = 15.sp)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
fun GalleryItem(
    record: com.poseai.app.data.ShootingRecord,
    onClick: () -> Unit = {},
    onToggleFavorite: () -> Unit = {}
) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(SurfaceDark)
    ) {
        if (record.imagePath.isNotEmpty()) {
            coil.compose.AsyncImage(
                model = record.imagePath,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(onClick = onClick),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .background(Color.Black.copy(alpha = 0.5f))
                    .padding(horizontal = 6.dp, vertical = 3.dp)
            ) {
                Text(
                    text = "${record.score.toInt()}",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            // 收藏标识（激活 isFavorite 字段在网格中的可视化）
            if (record.isFavorite) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                        .padding(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "已收藏",
                        tint = Accent,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = record.scene,
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }
        }
    }
}
