package com.poseai.app.ui

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.poseai.app.util.AdvancedBeautyEngine
import com.poseai.app.util.MakeupEngine
import com.poseai.app.util.SkinRepairEngine
import com.poseai.app.util.ArFaceEffectEngine
import com.poseai.app.viewmodel.ShootingViewModel
import kotlinx.coroutines.launch

/**
 * 美颜特效综合面板
 *
 * 包含四个标签页：高级美颜 / 美妆 / 皮肤修复 / AR特效
 * 从拍摄页面的美颜按钮进入。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BeautyEffectPanel(
    viewModel: ShootingViewModel,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableStateOf(0) }

    val tabs = remember { listOf("高级美颜", "美妆", "皮肤修复", "AR特效") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .padding(horizontal = 16.dp)
        ) {
            // 标题行
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "美颜特效",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = {
                    when (selectedTab) {
                        0 -> viewModel.resetAdvancedBeauty()
                        1 -> viewModel.resetMakeup()
                        2 -> viewModel.resetSkinRepair()
                        3 -> viewModel.clearArEffects()
                    }
                }) {
                    Text("重置", color = MaterialTheme.colorScheme.primary)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 标签页
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                edgePadding = 0.dp,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title, fontSize = 14.sp, fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 内容区
            Crossfade(targetState = selectedTab, label = "beautyTab") { tab ->
                when (tab) {
                    0 -> AdvancedBeautyTab(viewModel)
                    1 -> MakeupTab(viewModel)
                    2 -> SkinRepairTab(viewModel)
                    3 -> ArEffectTab(viewModel)
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// 高级美颜标签页
// ═══════════════════════════════════════════════════════════════

@Composable
private fun AdvancedBeautyTab(viewModel: ShootingViewModel) {
    val params by viewModel.advancedBeautyParams.collectAsState()

    val items = remember {
        listOf(
            "enlargeEyes" to "大眼",
            "slimNose" to "瘦鼻",
            "shrinkChin" to "缩下巴",
            "enlargeForehead" to "额头",
            "slimCheekbone" to "颧骨",
            "slimJawline" to "下颌线",
            "slimFace" to "整体瘦脸",
            "brightenEyes" to "亮眼",
            "whitenTeeth" to "白牙"
        )
    }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        items(items) { (field, label) ->
            val value = when (field) {
                "enlargeEyes" -> params.enlargeEyes
                "slimNose" -> params.slimNose
                "shrinkChin" -> params.shrinkChin
                "enlargeForehead" -> params.enlargeForehead
                "slimCheekbone" -> params.slimCheekbone
                "slimJawline" -> params.slimJawline
                "slimFace" -> params.slimFace
                "brightenEyes" -> params.brightenEyes
                "whitenTeeth" -> params.whitenTeeth
                else -> 0
            }
            BeautySliderItem(
                label = label,
                value = value,
                onValueChange = { viewModel.setAdvancedBeautyParam(field, it) }
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// 美妆标签页
// ═══════════════════════════════════════════════════════════════

@Composable
private fun MakeupTab(viewModel: ShootingViewModel) {
    val params by viewModel.makeupParams.collectAsState()

    val itemDefs = remember {
        listOf(
            "lipstickIntensity" to "口红",
            "blushIntensity" to "腮红",
            "eyebrowIntensity" to "眉毛",
            "eyeshadowIntensity" to "眼影",
            "eyelinerIntensity" to "眼线",
            "eyelashIntensity" to "睫毛"
        )
    }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        items(itemDefs) { (field, label) ->
            val value = when (field) {
                "lipstickIntensity" -> params.lipstickIntensity
                "blushIntensity" -> params.blushIntensity
                "eyebrowIntensity" -> params.eyebrowIntensity
                "eyeshadowIntensity" -> params.eyeshadowIntensity
                "eyelinerIntensity" -> params.eyelinerIntensity
                "eyelashIntensity" -> params.eyelashIntensity
                else -> 0
            }
            BeautySliderItem(
                label = label,
                value = value,
                onValueChange = { viewModel.setMakeupParam(field, it) }
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// 皮肤修复标签页
// ═══════════════════════════════════════════════════════════════

@Composable
private fun SkinRepairTab(viewModel: ShootingViewModel) {
    val params by viewModel.skinRepairParams.collectAsState()

    val items = remember {
        listOf(
            "removeAcne" to "祛痘",
            "removeSpots" to "祛斑",
            "removeDarkCircles" to "祛黑眼圈",
            "brightenSkinTone" to "均匀肤色"
        )
    }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        items(items) { (field, label) ->
            val value = when (field) {
                "removeAcne" -> params.removeAcne
                "removeSpots" -> params.removeSpots
                "removeDarkCircles" -> params.removeDarkCircles
                "brightenSkinTone" -> params.brightenSkinTone
                else -> 0
            }
            BeautySliderItem(
                label = label,
                value = value,
                onValueChange = { viewModel.setSkinRepairParam(field, it) }
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// AR特效标签页
// ═══════════════════════════════════════════════════════════════

@Composable
private fun ArEffectTab(viewModel: ShootingViewModel) {
    val activeEffects by viewModel.activeArEffects.collectAsState()

    val categories = remember { listOf("动物", "头饰", "装饰", "动态", "滤镜") }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        categories.forEach { category ->
            val effects = ArFaceEffectEngine.ArEffect.values().filter { it.category == category }
            if (effects.isNotEmpty()) {
                item {
                    Text(
                        text = category,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                item {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(effects) { effect ->
                            val isActive = effect in activeEffects
                            Box(
                                modifier = Modifier
                                    .size(width = 72.dp, height = 80.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                        else MaterialTheme.colorScheme.surfaceVariant
                                    )
                                    .clickable { viewModel.toggleArEffect(effect) }
                                    .padding(4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (isActive) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isActive) {
                                            Text("✓", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                                                modifier = Modifier.semantics { contentDescription = "已激活" })
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = effect.displayName,
                                        fontSize = 10.sp,
                                        color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// 通用组件
// ═══════════════════════════════════════════════════════════════

@Composable
private fun BeautySliderItem(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = if (value > 0) "$value" else "关",
                fontSize = 13.sp,
                color = if (value > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (value > 0 || true) {
            Slider(
                value = value.toFloat(),
                onValueChange = { onValueChange(it.toInt()) },
                valueRange = 0f..100f,
                steps = 20,
                colors = SliderDefaults.colors(
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }
    }
}
