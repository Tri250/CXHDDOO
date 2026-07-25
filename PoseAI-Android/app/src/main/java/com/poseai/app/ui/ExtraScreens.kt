package com.poseai.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mood
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Star
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
import com.poseai.app.model.SceneType
import com.poseai.app.ui.theme.Accent
import com.poseai.app.ui.theme.BackgroundDark
import com.poseai.app.ui.theme.SurfaceDark
import com.poseai.app.ui.theme.TextPrimary
import com.poseai.app.ui.theme.TextSecondary
import com.poseai.app.viewmodel.ShootingViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect

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
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = Accent,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
fun OOTDAnalysisScreen(viewModel: ShootingViewModel) {
    var selectedImagePath by remember { mutableStateOf<String?>(null) }
    var isAnalyzing by remember { mutableStateOf(false) }
    var analysisResult by remember { mutableStateOf<OOTDAnalysisResult?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val launcher = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let {
            val path = copyUriToFile(context, it)
            selectedImagePath = path
            analysisResult = null
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
                    contentDescription = null,
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
                        contentDescription = null,
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

        if (analysisResult != null) {
            OOTDAnalysisCard(result = analysisResult!!)
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
                    if (selectedImagePath != null) {
                        isAnalyzing = true
                        scope.launch {
                            kotlinx.coroutines.delay(1500)
                            analysisResult = generateOOTDAnalysis(selectedImagePath!!)
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
            text = "支持全身照穿搭分析：色彩搭配、比例建议、风格评分",
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
    } catch (e: Exception) {
        null
    }
}

private fun generateOOTDAnalysis(imagePath: String): OOTDAnalysisResult {
    val bitmap = android.graphics.BitmapFactory.decodeFile(imagePath) ?: return OOTDAnalysisResult(
        overallScore = 0f,
        colorHarmony = 0f,
        proportionScore = 0f,
        styleMatch = 0f,
        suggestions = emptyList(),
        styleTags = emptyList()
    )
    val width = bitmap.width
    val height = bitmap.height
    val avgR = mutableListOf<Int>()
    val avgG = mutableListOf<Int>()
    val avgB = mutableListOf<Int>()
    val sampleSize = 50
    for (y in 0 until height step sampleSize) {
        for (x in 0 until width step sampleSize) {
            val pixel = bitmap.getPixel(x, y)
            avgR.add(android.graphics.Color.red(pixel))
            avgG.add(android.graphics.Color.green(pixel))
            avgB.add(android.graphics.Color.blue(pixel))
        }
    }
    val meanR = avgR.average().toFloat()
    val meanG = avgG.average().toFloat()
    val meanB = avgB.average().toFloat()
    val brightness = (0.299 * meanR + 0.587 * meanG + 0.114 * meanB).toFloat()
    val maxColor = maxOf(meanR, meanG, meanB)
    val minColor = minOf(meanR, meanG, meanB)
    val saturation = if (maxColor > 0f) (maxColor - minColor) / maxColor else 0f
    val colorHarmony = (1f - kotlin.math.abs(saturation - 0.4f) * 2f).coerceIn(0f, 1f) * 100f
    val ratio = width.toFloat() / height.toFloat()
    val proportionScore = if (ratio in 0.4f..0.6f) 85f else 70f
    val styleMatch = if (brightness > 120f) 80f else 75f
    val overallScore = (colorHarmony * 0.4f + proportionScore * 0.3f + styleMatch * 0.3f).coerceIn(0f, 100f)
    val suggestions = mutableListOf<String>()
    if (saturation < 0.2f) {
        suggestions.add("整体色彩偏淡，建议添加亮色配饰提升亮点")
    }
    if (brightness > 180f) {
        suggestions.add("整体偏亮色调，适合春夏清爽风格")
    }
    if (brightness < 80f) {
        suggestions.add("整体偏暗色调，建议搭配亮色系单品增加层次")
    }
    if (ratio > 0.6f) {
        suggestions.add("照片比例偏宽，建议竖拍更好展示全身搭配")
    }
    if (suggestions.isEmpty()) {
        suggestions.add("整体搭配和谐，继续保持！")
    }
    val styleTags = mutableListOf<String>()
    if (brightness > 150f && saturation > 0.3f) {
        styleTags.add("清新")
        styleTags.add("活力")
    }
    if (brightness < 100f) {
        styleTags.add("沉稳")
        styleTags.add("高级感")
    }
    if (saturation < 0.25f) {
        styleTags.add("极简")
        styleTags.add("简约")
    }
    if (styleTags.isEmpty()) {
        styleTags.add("日常")
        styleTags.add("休闲")
    }
    return OOTDAnalysisResult(
        overallScore = overallScore,
        colorHarmony = colorHarmony,
        proportionScore = proportionScore,
        styleMatch = styleMatch,
        suggestions = suggestions,
        styleTags = styleTags
    )
}

data class OOTDAnalysisResult(
    val overallScore: Float,
    val colorHarmony: Float,
    val proportionScore: Float,
    val styleMatch: Float,
    val suggestions: List<String>,
    val styleTags: List<String>
)

@Composable
fun OOTDAnalysisCard(result: OOTDAnalysisResult) {
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

@Composable
fun GalleryScreen(viewModel: ShootingViewModel) {
    val records by viewModel.getCaptureHistory().collectAsState(initial = emptyList())

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .padding(16.dp)
    ) {
        Text(
            text = "我的相册",
            color = TextPrimary,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        if (records.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "暂无照片",
                        color = TextSecondary,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "去拍摄你的第一张 PoseAI 照片吧",
                        color = TextSecondary.copy(alpha = 0.7f),
                        fontSize = 13.sp
                    )
                }
            }
        } else {
            androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(3),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(records) { record ->
                    GalleryItem(record = record)
                }
            }
        }
    }
}

@Composable
fun GalleryItem(record: com.poseai.app.data.ShootingRecord) {
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
                modifier = Modifier.fillMaxSize(),
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

@Composable
fun ProScreen(
    isProUnlocked: Boolean,
    onPurchase: (String) -> Unit,
    onRestore: () -> Unit
) {
    var selectedPlan by remember { mutableStateOf("monthly") }
    val context = LocalContext.current
    val storeManager = com.poseai.app.PoseAIApp.getStoreManager()
    var proUnlocked by remember { mutableStateOf(isProUnlocked) }

    LaunchedEffect(Unit) {
        storeManager.proUnlocked.collect { proUnlocked = it }
    }

    val plans = listOf(
        ProPlan("monthly", "月度会员", "¥38", "/月", "按月订阅，随时取消"),
        ProPlan("yearly", "年度会员", "¥298", "/年", "立省 ¥158，更划算", true),
        ProPlan("lifetime", "永久会员", "¥698", "", "一次购买，终身使用")
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "PoseAI Pro",
            color = Accent,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "解锁全部高级功能",
            color = TextSecondary,
            fontSize = 15.sp
        )

        Spacer(modifier = Modifier.height(20.dp))

        ProFeatureGrid()

        Spacer(modifier = Modifier.height(20.dp))

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            plans.forEach { plan ->
                ProPlanCard(
                    plan = plan,
                    isSelected = selectedPlan == plan.id,
                    onClick = { selectedPlan = plan.id }
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        if (proUnlocked) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Accent.copy(alpha = 0.15f), RoundedCornerShape(16.dp))
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = Accent,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "已解锁 Pro 全部功能",
                        color = Accent,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        } else {
            Button(
                onClick = {
                    onPurchase(selectedPlan)
                    android.widget.Toast.makeText(context, "正在启动支付流程...", android.widget.Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Accent)
            ) {
                Text(
                    text = "立即订阅 ${plans.find { it.id == selectedPlan }?.price ?: ""}",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            TextButton(onClick = {
                onRestore()
                android.widget.Toast.makeText(context, "正在恢复购买...", android.widget.Toast.LENGTH_SHORT).show()
            }) {
                Text("恢复购买", color = TextSecondary, fontSize = 14.sp)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "订阅将自动续费，可在设置中取消",
            color = TextSecondary.copy(alpha = 0.6f),
            fontSize = 11.sp,
            textAlign = TextAlign.Center
        )
    }
}

data class ProPlan(
    val id: String,
    val name: String,
    val price: String,
    val period: String,
    val description: String,
    val isPopular: Boolean = false
)

@Composable
fun ProFeatureGrid() {
    val features = listOf(
        "全部姿势方案" to Icons.Default.Star,
        "场景识别" to Icons.Default.LocationOn,
        "专业滤镜" to Icons.Default.Palette,
        "微笑快门" to Icons.Default.Mood,
        "智能裁切" to Icons.Default.Style,
        "无水印" to Icons.Default.Check,
        "Vlog 导演" to Icons.Default.Videocam,
        "暗光优化" to Icons.Default.Lightbulb
    )

    androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
        columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(4),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        items(features) { (name, icon) ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(vertical = 4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(Accent.copy(alpha = 0.12f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = Accent,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = name,
                    color = TextPrimary,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun ProPlanCard(
    plan: ProPlan,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isSelected) Accent.copy(alpha = 0.12f) else SurfaceDark,
                RoundedCornerShape(14.dp)
            )
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) Accent else SurfaceGlass,
                shape = RoundedCornerShape(14.dp)
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .background(
                    if (isSelected) Accent else Color.Transparent,
                    CircleShape
                )
                .border(
                    width = 2.dp,
                    color = if (isSelected) Accent else TextSecondary.copy(alpha = 0.5f),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = Color.Black,
                    modifier = Modifier.size(14.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = plan.name,
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
                if (plan.isPopular) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .background(Accent, RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "推荐",
                            color = Color.Black,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            Text(
                text = plan.description,
                color = TextSecondary,
                fontSize = 12.sp
            )
        }

        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = plan.price,
                color = Accent,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = plan.period,
                color = TextSecondary,
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 2.dp)
            )
        }
    }
}
