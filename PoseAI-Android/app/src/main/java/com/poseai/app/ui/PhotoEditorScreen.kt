package com.poseai.app.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Rect
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.FilterVintage
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.RotateLeft
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect as GeometryRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.poseai.app.ui.theme.Accent
import com.poseai.app.ui.theme.BackgroundDark
import com.poseai.app.ui.theme.Border
import com.poseai.app.ui.theme.BorderActive
import com.poseai.app.ui.theme.Dimens
import com.poseai.app.ui.theme.OverlayBg
import com.poseai.app.ui.theme.Surface
import com.poseai.app.ui.theme.SurfaceDark
import com.poseai.app.ui.theme.SurfaceGlass
import com.poseai.app.ui.theme.TextPrimary
import com.poseai.app.ui.theme.TextSecondary
import com.poseai.app.util.PhotoFilterEngine
import com.poseai.app.viewmodel.ShootingViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

// ═══════════════════════════════════════════════════════════════
// 编辑状态与编辑动作数据模型
// ═══════════════════════════════════════════════════════════════

/**
 * 编辑动作：用于历史记录与撤销
 * 每次原子操作（旋转 / 裁剪 / 应用滤镜）都会封装为一条 EditAction
 */
sealed class EditAction {
    /** 旋转，degrees 仅取 -90 / 90 等 */
    data class Rotate(val degrees: Int) : EditAction()
    /** 裁剪，rect 为基于当前 Bitmap 像素坐标的裁剪区域 */
    data class Crop(val rect: Rect) : EditAction()
    /** 应用滤镜，intensity 取值 0..1 */
    data class ApplyFilter(
        val filter: PhotoFilterEngine.Filter,
        val intensity: Float
    ) : EditAction()
}

/**
 * 编辑状态快照：保存"应用该动作前"的 Bitmap，用于撤销时回滚
 */
private data class EditSnapshot(
    val bitmapBefore: Bitmap,
    val action: EditAction
)

/**
 * 裁剪宽高比预设
 */
enum class CropAspectRatio(val displayName: String, val ratio: Float?) {
    FREE("自由", null),
    RATIO_1_1("1:1", 1f),
    RATIO_4_3("4:3", 4f / 3f),
    RATIO_3_4("3:4", 3f / 4f),
    RATIO_16_9("16:9", 16f / 9f),
    RATIO_9_16("9:16", 9f / 16f)
}

/**
 * 底部工具 Tab
 */
enum class EditorTool(val label: String) {
    CROP("裁剪"),
    ROTATE("旋转"),
    FILTER("滤镜")
}

/**
 * 归一化裁剪区域（坐标均在 [0,1] 区间，相对于当前 Bitmap）
 */
private data class CropRect(
    val left: Float = 0.05f,
    val top: Float = 0.05f,
    val right: Float = 0.95f,
    val bottom: Float = 0.95f
)

// ═══════════════════════════════════════════════════════════════
// Bitmap 处理工具函数
// ═══════════════════════════════════════════════════════════════

/**
 * 从文件路径加载 Bitmap，超过 maxDim 时按 2 的幂次降采样，避免 OOM
 */
private fun loadBitmapFromFile(path: String, maxDim: Int = 2048): Bitmap? {
    return try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sampleSize = 1
        while (bounds.outWidth / sampleSize > maxDim || bounds.outHeight / sampleSize > maxDim) {
            sampleSize *= 2
        }
        val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        BitmapFactory.decodeFile(path, opts)
    } catch (e: Exception) {
        android.util.Log.w("PhotoEditorScreen", "Failed to decode bitmap from path", e)
        null
    }
}

/**
 * 旋转 Bitmap（使用 Matrix 真实旋转像素）
 */
private fun rotateBitmap(source: Bitmap, degrees: Int): Bitmap {
    val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
    return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
}

/**
 * 裁剪 Bitmap（使用 createBitmap 真实裁剪像素区域）
 */
private fun cropBitmap(source: Bitmap, rect: Rect): Bitmap {
    val safeLeft = rect.left.coerceIn(0, source.width - 1)
    val safeTop = rect.top.coerceIn(0, source.height - 1)
    val safeRight = rect.right.coerceIn(safeLeft + 1, source.width)
    val safeBottom = rect.bottom.coerceIn(safeTop + 1, source.height)
    return Bitmap.createBitmap(
        source,
        safeLeft,
        safeTop,
        safeRight - safeLeft,
        safeBottom - safeTop
    )
}

/**
 * 应用滤镜并按强度混合
 * - ORIGINAL 或 intensity <= 0：返回原图副本
 * - intensity >= 1：返回完全滤镜效果
 * - 中间值：使用 PhotoFilterEngine.blendBitmaps 混合
 */
private fun applyFilterWithIntensity(
    source: Bitmap,
    filter: PhotoFilterEngine.Filter,
    intensity: Float
): Bitmap {
    if (filter == PhotoFilterEngine.Filter.ORIGINAL || intensity <= 0f) {
        return source.copy(source.config ?: Bitmap.Config.ARGB_8888, true)
    }
    val filtered = PhotoFilterEngine.applyFilter(source, filter)
    if (intensity >= 1f) return filtered
    val blended = PhotoFilterEngine.blendBitmaps(source, filtered, 1f - intensity)
    if (filtered !== source && filtered !== blended) {
        filtered.recycle()
    }
    return blended
}

/**
 * 保存 Bitmap 到文件（JPEG 95% 质量）
 */
private fun saveBitmapToFile(bitmap: Bitmap, path: String) {
    FileOutputStream(path).use { out ->
        bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
    }
}

/**
 * 将原图降采样到最大边 maxDim px，用于生成滤镜缩略图，避免内存峰值
 */
private fun downscaleForThumbnail(source: Bitmap, maxDim: Int = 120): Bitmap {
    val w = source.width
    val h = source.height
    val max = maxOf(w, h)
    if (max <= maxDim) return source.copy(source.config ?: Bitmap.Config.ARGB_8888, true)
    val scale = maxDim.toFloat() / max
    return Bitmap.createScaledBitmap(source, (w * scale).toInt(), (h * scale).toInt(), true)
}

// ═══════════════════════════════════════════════════════════════
// 主 Composable
// ═══════════════════════════════════════════════════════════════

/**
 * 照片编辑器主页面
 *
 * 完整编辑链路：
 * 1. 从 imagePath 加载原图
 * 2. 用户可执行：裁剪 / 旋转 / 滤镜
 * 3. 每个操作维护历史快照，支持 Undo
 * 4. 保存：旋转 → 裁剪 → 滤镜 顺序产出最终 Bitmap，写入新文件并更新数据库记录
 * 5. 返回相册，相册通过 Room Flow 自动刷新
 *
 * @param recordId 拍摄记录 ID，用于保存后更新数据库
 * @param imagePath 原图文件路径
 * @param viewModel 拍摄 ViewModel，用于持久化
 * @param onBack 取消编辑返回
 * @param onSaved 保存完成返回
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoEditorScreen(
    recordId: Long,
    imagePath: String,
    viewModel: ShootingViewModel,
    onBack: () -> Unit,
    onSaved: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var isSaving by remember { mutableStateOf(false) }

    // ── 加载原图 ──
    var originalBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var loadFailed by remember { mutableStateOf(false) }

    LaunchedEffect(imagePath) {
        withContext(Dispatchers.IO) {
            val bmp = loadBitmapFromFile(imagePath)
            withContext(Dispatchers.Main) {
                if (bmp != null) originalBitmap = bmp else loadFailed = true
            }
        }
    }

    // ── 编辑状态 ──
    // currentBitmap 始终代表"已提交"的编辑结果（旋转+裁剪已生效）
    // 滤镜为实时预览，未提交到 currentBitmap，仅用于显示
    var currentBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var selectedFilter by remember { mutableStateOf(PhotoFilterEngine.Filter.ORIGINAL) }
    var filterIntensity by remember { mutableStateOf(1f) }
    val editHistory = remember { mutableStateListOf<EditSnapshot>() }

    // 当原图加载完成时，初始化 currentBitmap
    LaunchedEffect(originalBitmap) {
        if (originalBitmap != null && currentBitmap == null) {
            currentBitmap = originalBitmap!!.copy(
                originalBitmap!!.config ?: Bitmap.Config.ARGB_8888,
                true
            )
        }
    }

    // ── 当前工具 Tab ──
    var currentTool by remember { mutableStateOf(EditorTool.CROP) }

    // ── 裁剪状态（提升到此处，供预览 Overlay 与底部面板共享） ──
    var cropRect by remember { mutableStateOf(CropRect()) }
    var selectedRatio by remember { mutableStateOf(CropAspectRatio.FREE) }

    // 切换比例时调整裁剪框到该比例（保持中心点）
    LaunchedEffect(selectedRatio, currentBitmap) {
        val ratio = selectedRatio.ratio ?: return@LaunchedEffect
        val cw = cropRect.right - cropRect.left
        val ch = cropRect.bottom - cropRect.top
        if (cw <= 0f || ch <= 0f) return@LaunchedEffect
        val cx = (cropRect.left + cropRect.right) / 2f
        val cy = (cropRect.top + cropRect.bottom) / 2f
        val curRatio = cw / ch
        var nl = cropRect.left
        var nt = cropRect.top
        var nr = cropRect.right
        var nb = cropRect.bottom
        if (ratio > curRatio) {
            // 以宽度为基准，缩小高度
            val newH = cw / ratio
            nt = (cy - newH / 2f).coerceIn(0f, 1f)
            nb = (nt + newH).coerceIn(0f, 1f)
            if (nb - nt < newH) nt = (nb - newH).coerceIn(0f, 1f)
        } else {
            val newW = ch * ratio
            nl = (cx - newW / 2f).coerceIn(0f, 1f)
            nr = (nl + newW).coerceIn(0f, 1f)
            if (nr - nl < newW) nl = (nr - newW).coerceIn(0f, 1f)
        }
        cropRect = CropRect(nl, nt, nr, nb)
    }

    // ── 滤镜缩略图缓存 ──
    var filterThumbnails by remember { mutableStateOf<Map<PhotoFilterEngine.Filter, Bitmap>>(emptyMap()) }
    LaunchedEffect(originalBitmap) {
        if (originalBitmap != null) {
            withContext(Dispatchers.IO) {
                // 用一张小图批量生成 12 种滤镜缩略图，避免在主线程重复计算
                val thumbSource = downscaleForThumbnail(originalBitmap!!)
                val result = linkedMapOf<PhotoFilterEngine.Filter, Bitmap>()
                PhotoFilterEngine.Filter.values().forEach { f ->
                    result[f] = PhotoFilterEngine.applyFilter(thumbSource, f)
                }
                withContext(Dispatchers.Main) {
                    filterThumbnails = result
                }
            }
        }
    }

    // ── 实时预览 Bitmap（currentBitmap + 滤镜） ──
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(currentBitmap, selectedFilter, filterIntensity) {
        val src = currentBitmap ?: return@LaunchedEffect
        val oldPreview = previewBitmap
        previewBitmap = withContext(Dispatchers.Default) {
            applyFilterWithIntensity(src, selectedFilter, filterIntensity)
        }
        // 回收上一帧预览 Bitmap，避免内存泄漏
        oldPreview?.recycle()
    }

    // ── 撤销操作 ──
    fun undo() {
        if (editHistory.isEmpty()) return
        val last = editHistory.removeAt(editHistory.lastIndex)
        currentBitmap = last.bitmapBefore
    }

    // ── 应用旋转 ──
    fun applyRotation(degrees: Int) {
        val src = currentBitmap ?: return
        val snapshot = EditSnapshot(
            bitmapBefore = src,
            action = EditAction.Rotate(degrees)
        )
        currentBitmap = rotateBitmap(src, degrees)
        editHistory.add(snapshot)
        // 旋转后重置裁剪框
        cropRect = CropRect()
    }

    // ── 应用裁剪 ──
    fun applyCrop() {
        val src = currentBitmap ?: return
        val left = (cropRect.left * src.width).toInt().coerceIn(0, src.width - 1)
        val top = (cropRect.top * src.height).toInt().coerceIn(0, src.height - 1)
        val right = (cropRect.right * src.width).toInt().coerceIn(left + 1, src.width)
        val bottom = (cropRect.bottom * src.height).toInt().coerceIn(top + 1, src.height)
        if (right - left < 10 || bottom - top < 10) return
        val snapshot = EditSnapshot(
            bitmapBefore = src,
            action = EditAction.Crop(Rect(left, top, right, bottom))
        )
        currentBitmap = cropBitmap(src, Rect(left, top, right, bottom))
        editHistory.add(snapshot)
        // 裁剪后重置裁剪框
        cropRect = CropRect()
    }

    // ── 保存 ──
    fun save() {
        val finalBitmap = previewBitmap ?: currentBitmap ?: return
        if (isSaving) return
        isSaving = true
        scope.launch(Dispatchers.IO) {
            try {
                // 输出到原目录下的新文件，删除旧文件并更新数据库记录
                // 这样相册通过 Room Flow 自动刷新，且 Coil 缓存不会命中旧路径
                // 注：旧文件由 viewModel.replacePhotoFile 在数据库更新成功后删除，
                // 避免竞态导致记录指向已删文件
                val outputDir = File(context.filesDir, "photos").apply { mkdirs() }
                val newFile = File(outputDir, "edited_${System.currentTimeMillis()}.jpg")
                saveBitmapToFile(finalBitmap, newFile.absolutePath)

                // 更新数据库记录的 imagePath，触发相册 Flow 刷新
                // replacePhotoFile 内部会在 DB 更新成功后安全删除旧文件
                viewModel.replacePhotoFile(recordId, newFile.absolutePath)

                withContext(Dispatchers.Main) {
                    snackbarHostState.showSnackbar("已保存", duration = SnackbarDuration.Short)
                    isSaving = false
                    onSaved()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    snackbarHostState.showSnackbar("保存失败：${e.message}", duration = SnackbarDuration.Short)
                    isSaving = false
                }
            }
        }
    }

    // ── 加载中 / 失败占位 ──
    if (loadFailed) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(BackgroundDark),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.Image,
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(Dimens.spacingMd))
                Text("图片加载失败", color = TextSecondary, fontSize = Dimens.fontBody)
                Spacer(modifier = Modifier.height(Dimens.spacingLg))
                TextButton(onClick = onBack) { Text("返回", color = Accent) }
            }
        }
        return
    }

    if (originalBitmap == null || currentBitmap == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(BackgroundDark),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = Accent)
        }
        return
    }

    Scaffold(
        containerColor = BackgroundDark,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            EditorTopBar(
                canUndo = editHistory.isNotEmpty(),
                isSaving = isSaving,
                onBack = onBack,
                onUndo = ::undo,
                onSave = ::save
            )
        },
        bottomBar = {
            EditorBottomBar(
                currentTool = currentTool,
                onToolChanged = { currentTool = it }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(BackgroundDark)
        ) {
            // ── 图片预览区（占据剩余空间） ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(BackgroundDark)
            ) {
                EditorPreview(
                    bitmap = previewBitmap ?: currentBitmap!!,
                    showCropOverlay = currentTool == EditorTool.CROP,
                    cropRect = cropRect,
                    selectedRatio = selectedRatio,
                    onCropChange = { cropRect = it }
                )
            }

            // ── 当前工具面板（固定高度，不滚出屏幕） ──
            when (currentTool) {
                EditorTool.CROP -> CropPanel(
                    cropRect = cropRect,
                    selectedRatio = selectedRatio,
                    onRatioSelected = { selectedRatio = it },
                    onCropApplied = ::applyCrop
                )
                EditorTool.ROTATE -> RotatePanel(
                    onRotateLeft = { applyRotation(-90) },
                    onRotateRight = { applyRotation(90) }
                )
                EditorTool.FILTER -> FilterPanel(
                    thumbnails = filterThumbnails,
                    selectedFilter = selectedFilter,
                    intensity = filterIntensity,
                    onFilterSelected = { selectedFilter = it },
                    onIntensityChanged = { filterIntensity = it }
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// 顶部栏
// ═══════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorTopBar(
    canUndo: Boolean,
    isSaving: Boolean,
    onBack: () -> Unit,
    onUndo: () -> Unit,
    onSave: () -> Unit
) {
    TopAppBar(
        title = {
            Text(
                text = "编辑",
                color = TextPrimary,
                fontSize = Dimens.fontTitle,
                fontWeight = FontWeight.SemiBold
            )
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "取消编辑",
                    tint = TextPrimary,
                    modifier = Modifier.size(Dimens.iconLg)
                )
            }
        },
        actions = {
            // 撤销按钮
            IconButton(onClick = onUndo, enabled = canUndo) {
                Icon(
                    imageVector = Icons.Default.Undo,
                    contentDescription = "撤销",
                    tint = if (canUndo) TextPrimary else TextSecondary.copy(alpha = 0.4f),
                    modifier = Modifier.size(Dimens.iconLg)
                )
            }
            // 保存按钮
            TextButton(
                onClick = onSave,
                enabled = !isSaving,
                contentPadding = PaddingValues(horizontal = Dimens.spacingMd, vertical = Dimens.spacingXs)
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(Dimens.iconMd),
                        strokeWidth = 2.dp,
                        color = Accent
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Save,
                        contentDescription = "保存",
                        tint = Accent,
                        modifier = Modifier.size(Dimens.iconMd)
                    )
                    Spacer(modifier = Modifier.width(Dimens.spacingXs))
                    Text(
                        text = "保存",
                        color = Accent,
                        fontSize = Dimens.fontBody,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = BackgroundDark,
            titleContentColor = TextPrimary,
            navigationIconContentColor = TextPrimary,
            actionIconContentColor = TextPrimary
        )
    )
}

// ═══════════════════════════════════════════════════════════════
// 底部工具栏 Tab
// ═══════════════════════════════════════════════════════════════

@Composable
private fun EditorBottomBar(
    currentTool: EditorTool,
    onToolChanged: (EditorTool) -> Unit
) {
    Surface(color = SurfaceDark, tonalElevation = 0.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(vertical = Dimens.spacingSm),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            EditorTool.values().forEach { tool ->
                val selected = currentTool == tool
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clickable { onToolChanged(tool) }
                        .padding(horizontal = Dimens.spacingLg, vertical = Dimens.spacingXs)
                ) {
                    Icon(
                        imageVector = when (tool) {
                            EditorTool.CROP -> Icons.Default.Crop
                            EditorTool.ROTATE -> Icons.Default.RotateRight
                            EditorTool.FILTER -> Icons.Default.FilterVintage
                        },
                        contentDescription = tool.label,
                        tint = if (selected) Accent else TextSecondary,
                        modifier = Modifier.size(Dimens.iconMd)
                    )
                    Spacer(modifier = Modifier.height(Dimens.spacingXs))
                    Text(
                        text = tool.label,
                        color = if (selected) Accent else TextSecondary,
                        fontSize = Dimens.fontCaption,
                        fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// 图片预览区（含双指缩放 + 裁剪 Overlay）
// ═══════════════════════════════════════════════════════════════

@Composable
private fun EditorPreview(
    bitmap: Bitmap,
    showCropOverlay: Boolean,
    cropRect: CropRect,
    selectedRatio: CropAspectRatio,
    onCropChange: (CropRect) -> Unit
) {
    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .pointerInput(showCropOverlay) {
                // 双指缩放 + 单指拖动（裁剪模式下禁用缩放，避免与裁剪手势冲突）
                if (!showCropOverlay) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        val newScale = (scale * zoom).coerceIn(1f, 5f)
                        scale = newScale
                        if (newScale > 1f) {
                            offsetX += pan.x
                            offsetY += pan.y
                        } else {
                            offsetX = 0f
                            offsetY = 0f
                        }
                    }
                }
            }
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            val density = LocalDensity.current
            val containerW = constraints.maxWidth.toFloat()
            val containerH = constraints.maxHeight.toFloat()

            // 按图片宽高比 + 容器尺寸计算图片实际显示区域（ContentScale.Fit）
            val bmpW = bitmap.width.toFloat()
            val bmpH = bitmap.height.toFloat()
            val imgRatio = if (bmpH > 0f) bmpW / bmpH else 1f
            val containerRatio = if (containerH > 0f) containerW / containerH else 1f

            val drawW: Float
            val drawH: Float
            if (imgRatio > containerRatio) {
                drawW = containerW
                drawH = if (imgRatio > 0f) containerW / imgRatio else containerH
            } else {
                drawH = containerH
                drawW = if (imgRatio > 0f) containerH * imgRatio else containerW
            }
            val drawLeft = (containerW - drawW) / 2f
            val drawTop = (containerH - drawH) / 2f

            // 显示区域（在容器坐标系中）
            val drawRect = GeometryRect(
                left = drawLeft,
                top = drawTop,
                right = drawLeft + drawW,
                bottom = drawTop + drawH
            )

            // AnimatedContent 用于旋转/裁剪切换时的渐变过渡
            AnimatedContent(
                targetState = bitmap,
                transitionSpec = {
                    fadeIn(tween(Dimens.durationNormal)) togetherWith
                        fadeOut(tween(Dimens.durationNormal))
                },
                label = "preview"
            ) { bmp ->
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "预览图",
                    modifier = Modifier
                        .size(
                            width = with(density) { drawW.toDp() },
                            height = with(density) { drawH.toDp() }
                        )
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = if (scale > 1f) offsetX else 0f,
                            translationY = if (scale > 1f) offsetY else 0f
                        ),
                    contentScale = ContentScale.Fit
                )
            }

            // 裁剪框 overlay
            if (showCropOverlay && drawW > 0f && drawH > 0f) {
                CropOverlay(
                    containerWidth = containerW,
                    containerHeight = containerH,
                    drawRect = drawRect,
                    cropRect = cropRect,
                    selectedRatio = selectedRatio,
                    onCropChange = onCropChange
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// 裁剪面板（宽高比选择 + 确认按钮）
// ═══════════════════════════════════════════════════════════════

@Composable
private fun CropPanel(
    cropRect: CropRect,
    selectedRatio: CropAspectRatio,
    onRatioSelected: (CropAspectRatio) -> Unit,
    onCropApplied: () -> Unit
) {
    Surface(color = SurfaceDark) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.spacingLg, vertical = Dimens.spacingSm),
            horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 宽高比选择条
            LazyRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
            ) {
                items(CropAspectRatio.values()) { ratio ->
                    RatioChip(
                        text = ratio.displayName,
                        selected = selectedRatio == ratio,
                        onClick = { onRatioSelected(ratio) }
                    )
                }
            }
            // 确认裁剪按钮
            Button(
                onClick = onCropApplied,
                colors = ButtonDefaults.buttonColors(containerColor = Accent),
                shape = RoundedCornerShape(Dimens.radiusFull),
                contentPadding = PaddingValues(horizontal = Dimens.spacingLg, vertical = Dimens.spacingXs)
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = Color.Black,
                    modifier = Modifier.size(Dimens.iconSm)
                )
                Spacer(modifier = Modifier.width(Dimens.spacingXs))
                Text(
                    "裁剪",
                    color = Color.Black,
                    fontSize = Dimens.fontBody,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun RatioChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .background(
                color = if (selected) Accent.copy(alpha = 0.2f) else Surface,
                shape = RoundedCornerShape(Dimens.radiusFull)
            )
            .border(
                width = Dimens.strokeThin,
                color = if (selected) Accent else Border,
                shape = RoundedCornerShape(Dimens.radiusFull)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = Dimens.spacingMd, vertical = Dimens.spacingXs)
    ) {
        Text(
            text = text,
            color = if (selected) Accent else TextSecondary,
            fontSize = Dimens.fontLabel,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal
        )
    }
}

// ═══════════════════════════════════════════════════════════════
// 裁剪框 Overlay（绘制遮罩 + 三分线 + 可拖动四角）
// ═══════════════════════════════════════════════════════════════

@Composable
private fun CropOverlay(
    containerWidth: Float,
    containerHeight: Float,
    drawRect: GeometryRect,
    cropRect: CropRect,
    selectedRatio: CropAspectRatio,
    onCropChange: (CropRect) -> Unit
) {
    val density = LocalDensity.current
    // 把归一化 [0,1] 映射到显示图片区域内的像素坐标
    val imgLeft = drawRect.left
    val imgTop = drawRect.top
    val imgW = drawRect.width
    val imgH = drawRect.height

    val cropX1 = imgLeft + cropRect.left * imgW
    val cropY1 = imgTop + cropRect.top * imgH
    val cropX2 = imgLeft + cropRect.right * imgW
    val cropY2 = imgTop + cropRect.bottom * imgH

    val handleSizePx = with(density) { 28.dp.toPx() }
    val handleSizeDp = with(density) { 28.dp }

    Canvas(modifier = Modifier.fillMaxSize()) {
        // 半透明遮罩：四块（上/下/左/右）
        val overlayColor = OverlayBg
        drawRect(overlayColor, Offset(0f, 0f), Size(containerWidth, cropY1))
        drawRect(overlayColor, Offset(0f, cropY2), Size(containerWidth, containerHeight - cropY2))
        drawRect(overlayColor, Offset(0f, cropY1), Size(cropX1, cropY2 - cropY1))
        drawRect(overlayColor, Offset(cropX2, cropY1), Size(containerWidth - cropX2, cropY2 - cropY1))

        // 裁剪框边框
        drawRect(
            color = BorderActive,
            topLeft = Offset(cropX1, cropY1),
            size = Size(cropX2 - cropX1, cropY2 - cropY1),
            style = Stroke(width = with(density) { Dimens.strokeRegular.toPx() })
        )

        // 三分线辅助
        val gridColor = Color.White.copy(alpha = 0.35f)
        val thirdW = (cropX2 - cropX1) / 3f
        val thirdH = (cropY2 - cropY1) / 3f
        for (i in 1..2) {
            drawLine(
                color = gridColor,
                start = Offset(cropX1 + thirdW * i, cropY1),
                end = Offset(cropX1 + thirdW * i, cropY2),
                strokeWidth = 1f
            )
            drawLine(
                color = gridColor,
                start = Offset(cropX1, cropY1 + thirdH * i),
                end = Offset(cropX2, cropY1 + thirdH * i),
                strokeWidth = 1f
            )
        }

        // 四角 L 形把手
        val cornerColor = Accent
        val cornerLen = with(density) { 18.dp.toPx() }
        val cornerStroke = with(density) { Dimens.strokeThick.toPx() }
        // 左上
        drawLine(cornerColor, Offset(cropX1, cropY1), Offset(cropX1 + cornerLen, cropY1), cornerStroke)
        drawLine(cornerColor, Offset(cropX1, cropY1), Offset(cropX1, cropY1 + cornerLen), cornerStroke)
        // 右上
        drawLine(cornerColor, Offset(cropX2, cropY1), Offset(cropX2 - cornerLen, cropY1), cornerStroke)
        drawLine(cornerColor, Offset(cropX2, cropY1), Offset(cropX2, cropY1 + cornerLen), cornerStroke)
        // 左下
        drawLine(cornerColor, Offset(cropX1, cropY2), Offset(cropX1 + cornerLen, cropY2), cornerStroke)
        drawLine(cornerColor, Offset(cropX1, cropY2), Offset(cropX1, cropY2 - cornerLen), cornerStroke)
        // 右下
        drawLine(cornerColor, Offset(cropX2, cropY2), Offset(cropX2 - cornerLen, cropY2), cornerStroke)
        drawLine(cornerColor, Offset(cropX2, cropY2), Offset(cropX2, cropY2 - cornerLen), cornerStroke)
    }

    // 四角拖动手势
    // drag.x / drag.y 为屏幕像素位移，需除以图片显示宽高 imgW / imgH 换算成归一化位移
    val ratio = selectedRatio.ratio

    // 左上角
    CropHandle(
        x = cropX1, y = cropY1,
        handleSizeDp = handleSizeDp, handleSizePx = handleSizePx,
        onDrag = { dxPx, dyPx ->
            val dx = if (imgW > 0f) dxPx / imgW else 0f
            val dy = if (imgH > 0f) dyPx / imgH else 0f
            var nl = (cropRect.left + dx).coerceIn(0f, cropRect.right - 0.05f)
            var nt = (cropRect.top + dy).coerceIn(0f, cropRect.bottom - 0.05f)
            val nr = cropRect.right
            val nb = cropRect.bottom
            if (ratio != null) {
                val curW = (cropRect.right - nl) * imgW
                val curH = (cropRect.bottom - nt) * imgH
                if (curH > 0f && curW / curH > ratio) {
                    val newW = (cropRect.bottom - nt) * imgH * ratio
                    nl = (cropRect.right - newW / imgW).coerceIn(0f, cropRect.right - 0.05f)
                } else if (curW > 0f) {
                    val newH = (cropRect.right - nl) * imgW / ratio
                    nt = (cropRect.bottom - newH / imgH).coerceIn(0f, cropRect.bottom - 0.05f)
                }
            }
            onCropChange(CropRect(nl, nt, nr, nb))
        }
    )
    // 右上角
    CropHandle(
        x = cropX2, y = cropY1,
        handleSizeDp = handleSizeDp, handleSizePx = handleSizePx,
        onDrag = { dxPx, dyPx ->
            val dx = if (imgW > 0f) dxPx / imgW else 0f
            val dy = if (imgH > 0f) dyPx / imgH else 0f
            val nl = cropRect.left
            var nt = (cropRect.top + dy).coerceIn(0f, cropRect.bottom - 0.05f)
            var nr = (cropRect.right + dx).coerceIn(cropRect.left + 0.05f, 1f)
            val nb = cropRect.bottom
            if (ratio != null) {
                val curW = (nr - cropRect.left) * imgW
                val curH = (cropRect.bottom - nt) * imgH
                if (curH > 0f && curW / curH > ratio) {
                    val newW = (cropRect.bottom - nt) * imgH * ratio
                    nr = (cropRect.left + newW / imgW).coerceIn(cropRect.left + 0.05f, 1f)
                } else if (curW > 0f) {
                    val newH = (nr - cropRect.left) * imgW / ratio
                    nt = (cropRect.bottom - newH / imgH).coerceIn(0f, cropRect.bottom - 0.05f)
                }
            }
            onCropChange(CropRect(nl, nt, nr, nb))
        }
    )
    // 左下角
    CropHandle(
        x = cropX1, y = cropY2,
        handleSizeDp = handleSizeDp, handleSizePx = handleSizePx,
        onDrag = { dxPx, dyPx ->
            val dx = if (imgW > 0f) dxPx / imgW else 0f
            val dy = if (imgH > 0f) dyPx / imgH else 0f
            var nl = (cropRect.left + dx).coerceIn(0f, cropRect.right - 0.05f)
            val nt = cropRect.top
            val nr = cropRect.right
            var nb = (cropRect.bottom + dy).coerceIn(cropRect.top + 0.05f, 1f)
            if (ratio != null) {
                val curW = (cropRect.right - nl) * imgW
                val curH = (nb - cropRect.top) * imgH
                if (curH > 0f && curW / curH > ratio) {
                    val newW = (nb - cropRect.top) * imgH * ratio
                    nl = (cropRect.right - newW / imgW).coerceIn(0f, cropRect.right - 0.05f)
                } else if (curW > 0f) {
                    val newH = (cropRect.right - nl) * imgW / ratio
                    nb = (cropRect.top + newH / imgH).coerceIn(cropRect.top + 0.05f, 1f)
                }
            }
            onCropChange(CropRect(nl, nt, nr, nb))
        }
    )
    // 右下角
    CropHandle(
        x = cropX2, y = cropY2,
        handleSizeDp = handleSizeDp, handleSizePx = handleSizePx,
        onDrag = { dxPx, dyPx ->
            val dx = if (imgW > 0f) dxPx / imgW else 0f
            val dy = if (imgH > 0f) dyPx / imgH else 0f
            val nl = cropRect.left
            val nt = cropRect.top
            var nr = (cropRect.right + dx).coerceIn(cropRect.left + 0.05f, 1f)
            var nb = (cropRect.bottom + dy).coerceIn(cropRect.top + 0.05f, 1f)
            if (ratio != null) {
                val curW = (nr - cropRect.left) * imgW
                val curH = (nb - cropRect.top) * imgH
                if (curH > 0f && curW / curH > ratio) {
                    val newW = (nb - cropRect.top) * imgH * ratio
                    nr = (cropRect.left + newW / imgW).coerceIn(cropRect.left + 0.05f, 1f)
                } else if (curW > 0f) {
                    val newH = (nr - cropRect.left) * imgW / ratio
                    nb = (cropRect.top + newH / imgH).coerceIn(cropRect.top + 0.05f, 1f)
                }
            }
            onCropChange(CropRect(nl, nt, nr, nb))
        }
    )
}

/**
 * 单个裁剪角拖动手势区域
 *
 * 使用 rememberUpdatedState 包装 onDrag 回调，确保 pointerInput(Unit) 内部
 * 始终使用最新的回调（该回调闭包捕获了最新的 cropRect / imgW / imgH 等值）
 */
@Composable
private fun CropHandle(
    x: Float,
    y: Float,
    handleSizeDp: androidx.compose.ui.unit.Dp,
    handleSizePx: Float,
    onDrag: (dxNormalized: Float, dyNormalized: Float) -> Unit
) {
    val currentOnDrag by rememberUpdatedState(onDrag)
    Box(
        modifier = Modifier
            .offset { IntOffset((x - handleSizePx / 2).toInt(), (y - handleSizePx / 2).toInt()) }
            .size(handleSizeDp)
            .pointerInput(Unit) {
                detectDragGestures { change, drag ->
                    change.consume()
                    // drag.x / drag.y 为像素位移；调用方在 onDrag 中按显示图片宽高换算
                    currentOnDrag(drag.x, drag.y)
                }
            }
    )
}

// ═══════════════════════════════════════════════════════════════
// 旋转面板
// ═══════════════════════════════════════════════════════════════

@Composable
private fun RotatePanel(
    onRotateLeft: () -> Unit,
    onRotateRight: () -> Unit
) {
    Surface(color = SurfaceDark) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.spacingLg, vertical = Dimens.spacingMd),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            RotateButton(
                icon = Icons.Default.RotateLeft,
                label = "左转 90°",
                onClick = onRotateLeft,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(Dimens.spacingLg))
            RotateButton(
                icon = Icons.Default.RotateRight,
                label = "右转 90°",
                onClick = onRotateRight,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun RotateButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(Dimens.radiusMd))
            .background(Surface)
            .border(Dimens.strokeThin, Border, RoundedCornerShape(Dimens.radiusMd))
            .clickable(onClick = onClick)
            .padding(vertical = Dimens.spacingMd),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = Accent,
            modifier = Modifier.size(Dimens.iconLg)
        )
        Spacer(modifier = Modifier.height(Dimens.spacingXs))
        Text(
            text = label,
            color = TextPrimary,
            fontSize = Dimens.fontLabel,
            fontWeight = FontWeight.Medium
        )
    }
}

// ═══════════════════════════════════════════════════════════════
// 滤镜面板
// ═══════════════════════════════════════════════════════════════

@Composable
private fun FilterPanel(
    thumbnails: Map<PhotoFilterEngine.Filter, Bitmap>,
    selectedFilter: PhotoFilterEngine.Filter,
    intensity: Float,
    onFilterSelected: (PhotoFilterEngine.Filter) -> Unit,
    onIntensityChanged: (Float) -> Unit
) {
    Surface(color = SurfaceDark) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = Dimens.spacingSm)
        ) {
            // 滤镜缩略图横向列表（与 PhotoFilterEngine.Filter 一致，共 12 种）
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
                contentPadding = PaddingValues(horizontal = Dimens.spacingLg)
            ) {
                items(PhotoFilterEngine.Filter.values()) { filter ->
                    val thumb = thumbnails[filter]
                    FilterThumbnailItem(
                        filter = filter,
                        thumbnail = thumb,
                        selected = selectedFilter == filter,
                        onClick = { onFilterSelected(filter) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(Dimens.spacingSm))

            // 滤镜强度滑块（仅当非原图时可用）
            val enabled = selectedFilter != PhotoFilterEngine.Filter.ORIGINAL
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimens.spacingLg),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "强度",
                    color = if (enabled) TextSecondary else TextSecondary.copy(alpha = 0.4f),
                    fontSize = Dimens.fontLabel,
                    modifier = Modifier.width(40.dp)
                )
                Slider(
                    value = intensity,
                    onValueChange = onIntensityChanged,
                    enabled = enabled,
                    valueRange = 0f..1f,
                    colors = SliderDefaults.colors(
                        thumbColor = Accent,
                        activeTrackColor = Accent,
                        inactiveTrackColor = SurfaceGlass,
                        disabledThumbColor = TextSecondary.copy(alpha = 0.4f),
                        disabledActiveTrackColor = TextSecondary.copy(alpha = 0.4f),
                        disabledInactiveTrackColor = Surface
                    ),
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${(intensity * 100).toInt()}%",
                    color = if (enabled) Accent else TextSecondary.copy(alpha = 0.4f),
                    fontSize = Dimens.fontLabel,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(48.dp)
                )
            }
        }
    }
}

@Composable
private fun FilterThumbnailItem(
    filter: PhotoFilterEngine.Filter,
    thumbnail: Bitmap?,
    selected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(64.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(Dimens.radiusSm))
                .background(Surface)
                .border(
                    width = if (selected) Dimens.strokeThick else Dimens.strokeThin,
                    color = if (selected) Accent else Border,
                    shape = RoundedCornerShape(Dimens.radiusSm)
                )
        ) {
            if (thumbnail != null) {
                Image(
                    bitmap = thumbnail.asImageBitmap(),
                    contentDescription = filter.displayName,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Image,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(Dimens.iconMd)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(Dimens.spacingXs))
        Text(
            text = filter.displayName,
            color = if (selected) Accent else TextSecondary,
            fontSize = Dimens.fontCaption,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            maxLines = 1,
            textAlign = TextAlign.Center
        )
    }
}
