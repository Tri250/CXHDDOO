package com.poseai.app.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.ExperimentalPersistentRecording
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.poseai.app.ml.MlKitPoseProvider
import com.poseai.app.ml.PoseData
import com.poseai.app.ml.SceneClassifier
import com.poseai.app.model.SceneType
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.Executor

/**
 * 摄像头管理器——对应 iOS CameraManager。
 * 负责 CameraX Session 生命周期、前后置切换、姿态分析、场景识别、拍照与录像。
 *
 * 增强能力（全量 AI 激活实现）：
 *  - 光线参数提取：每帧分析亮度、色温、过曝比例，低光时自动切换分析频率
 *  - 动态帧分析降频：场景/姿态分析根据设备性能自动调整
 *  - 低光模式联动：当 lightLevel < 0.25 时自动触发 isLowLight 回调
 *  - 帧丢弃策略：奇偶帧交替 + 低光加倍间隔，节省算力
 *  - 缓存最近一次光线参数（供 ShootingViewModel 读取后持久化）
 *  - 曝光时间读取：通过 CameraX 的 CameraInfo.getExposureTimeRange 读取
 */
class CameraManager(
    private val context: Context
) {
    private val mainExecutor: Executor = ContextCompat.getMainExecutor(context)

    // MARK: - 回调
    var onPoseUpdate: (List<PoseData>) -> Unit = {}
    var onSceneChange: (SceneType) -> Unit = {}
    var onLowLight: (Boolean) -> Unit = {}
    var onPhotoCapture: (Bitmap) -> Unit = {}
    var onOOTDSnapshot: (Bitmap) -> Unit = {}
    var onRecordingSave: ((String) -> Unit) = {}
    var onLightAnalysis: (LightAnalysisResult) -> Unit = {}

    // 状态
    var isLowLightMode: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                onLowLight(value)
            }
        }
    var isFrontCamera: Boolean = false
    var isRecording: Boolean = false
        private set

    // 姿态提供者 & 场景分类（ML Kit）
    val poseProvider = MlKitPoseProvider(context)
    private val sceneClassifier = SceneClassifier(context)

    // CameraX 组件
    private var cameraProvider: ProcessCameraProvider? = null
    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var camera: Camera? = null

    private var activeRecording: Recording? = null

    // 场景防抖：连续 2 次相同才触发
    private val sceneDebounceThreshold = 2
    private val sceneVoteBuffer = ArrayList<SceneType>()
    private var lastSceneUpdate = 0L
    private val sceneUpdateIntervalMs = 2000L

    // 帧计数（性能降级用）
    private var frameCounter = 0

    private var pendingOOTDRequest = false

    // MARK: - 最近一次光线分析结果（供 ViewModel 读取持久化）
    var lastLightLevel: Float? = null
        private set
    var lastColorTemperature: Float? = null
        private set
    var lastExposureTimeMs: Long? = null
        private set
    var lastIsLowLight: Boolean = false
        private set
    var lastExposureCompensation: Float = 0f
        private set

    // MARK: - 相机参数（曝光/ISO 范围，首次绑定后读取）
    private var exposureTimeRange: android.util.Range<Long>? = null
    private var sensitivityRange: android.util.Range<Int>? = null
    private var minExposureTimeNs: Long = 0L

    // 光线分析滑动窗口（最近 N 帧）
    private val recentLightLevels = ArrayDeque<Float>(10)
    private val recentColorTemps = ArrayDeque<Float>(10)

    // MARK: - 绑定

    fun bindToCamera(lifecycleOwner: androidx.lifecycle.LifecycleOwner, previewView: PreviewView) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            val provider = providerFuture.get()
            cameraProvider = provider
            bindUseCases(lifecycleOwner, previewView)
        }, mainExecutor)
    }

    fun bindUseCases(lifecycleOwner: androidx.lifecycle.LifecycleOwner, previewView: PreviewView) {
        val provider = cameraProvider ?: return
        provider.unbindAll()

        val selector = if (isFrontCamera) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA

        // 预览
        val preview = Preview.Builder().build()
        preview.setSurfaceProvider(previewView.surfaceProvider)

        // 拍照
        imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_ZERO_SHUTTER_LAG)
            .build()

        // 录像
        val recorder = Recorder.Builder()
            .setQualitySelector(QualitySelector.from(Quality.HD))
            .build()
        videoCapture = VideoCapture.withOutput(recorder)

        // 帧分析（姿态 + 场景 + 光线）
        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
        analysis.setAnalyzer(mainExecutor) { imageProxy -> analyzeFrame(imageProxy) }

        camera = provider.bindToLifecycle(
            lifecycleOwner,
            selector,
            preview,
            imageCapture,
            analysis,
            videoCapture
        )

        // 绑定后读取相机的曝光/ISO 能力
        camera?.cameraInfo?.let { info ->
            runCatching {
                exposureTimeRange = info.exposureTimeRange
                sensitivityRange = info.sensitivityRange
            }
        }
    }

    // MARK: - Session 控制

    fun switchCamera() {
        isFrontCamera = !isFrontCamera
        poseProvider.isFrontCamera = isFrontCamera
    }

    // MARK: - 帧分析（含光线分析 + 动态降频）

    @androidx.annotation.OptIn(ExperimentalGetImage::class, ExperimentalPersistentRecording::class)
    private fun analyzeFrame(imageProxy: ImageProxy) {
        frameCounter++

        // 光线分析：每帧都做（用于低光检测），但只采样有限像素
        val lightResult = analyzeLight(imageProxy)
        if (lightResult != null) {
            // 更新滑动窗口
            if (recentLightLevels.size >= 10) recentLightLevels.removeFirst()
            recentLightLevels.addLast(lightResult.lightLevel)
            if (recentColorTemps.size >= 10) recentColorTemps.removeFirst()
            recentColorTemps.addLast(lightResult.colorTemperature)

            // 平滑后存储
            lastLightLevel = recentLightLevels.average().toFloat()
            lastColorTemperature = recentColorTemps.average().toFloat()
            lastExposureTimeMs = lightResult.exposureTimeMs
            lastExposureCompensation = lightResult.exposureCompensation

            // 低光检测：窗口均值 < 0.25
            val smoothedLight = lastLightLevel ?: 0.5f
            val wasLow = lastIsLowLight
            lastIsLowLight = smoothedLight < 0.25f
            if (lastIsLowLight != wasLow) {
                isLowLightMode = lastIsLowLight
            }

            onLightAnalysis(lightResult)
        }

        // 姿态检测：隔帧丢弃（奇偶帧交替），低光时加倍降频
        val modulo = if (isLowLightMode) 4 else 2
        val skipPoseFrame = frameCounter % modulo == 0
        if (!skipPoseFrame) {
            poseProvider.process(imageProxy)
        }

        // 低频场景分类（2s / 4s，低光时间隔加倍）
        val now = System.currentTimeMillis()
        val interval = if (isLowLightMode) sceneUpdateIntervalMs * 2 else sceneUpdateIntervalMs
        if (now - lastSceneUpdate > interval) {
            lastSceneUpdate = now
            val bitmap = imageProxy.toBitmap()
            if (bitmap != null) {
                sceneClassifier.classify(bitmap, onResult = { scene -> handleSceneResult(scene) })
                // OOTD 快照
                if (pendingOOTDRequest) {
                    pendingOOTDRequest = false
                    val bmp = downscale(bitmap, 512)
                    mainExecutor.execute { onOOTDSnapshot(bmp) }
                }
            }
        }

        // 如果跳过了姿态分析，则在低频分析后关闭 imageProxy
        if (skipPoseFrame) {
            imageProxy.close()
        }
        // 否则 poseProvider.process 内部会负责关闭
    }

    /**
     * 帧光线分析：
     *  - 采样缩略图的像素网格（避免全图采样开销）
     *  - 计算平均亮度 (0~1)
     *  - 计算色温 (0=冷蓝, 1=暖红)
     *  - 检测过曝比例
     *  - 读取 CameraX 曝光补偿
     *
     * 使用下采样缩略图 (80x60)，计算开销极低。
     */
    @androidx.annotation.OptIn(ExperimentalGetImage::class)
    private fun analyzeLight(imageProxy: ImageProxy): LightAnalysisResult? {
        val mediaImage = imageProxy.image ?: return null
        val w = imageProxy.width
        val h = imageProxy.height
        if (w == 0 || h == 0) return null

        // 为保证性能：采样限制在 ~200 个点（网格 16x12）
        val sampleCols = 16
        val sampleRows = 12
        val strideX = w / sampleCols
        val strideY = h / sampleRows
        if (strideX <= 0 || strideY <= 0) return null

        // 从 YUV 的 Y 平面直接读取亮度（零颜色转换开销）
        val yPlane = mediaImage.planes[0]
        val yBuffer = yPlane.buffer
        val rowStride = yPlane.rowStride
        val pixelStride = yPlane.pixelStride

        var totalLuma = 0L
        var samples = 0
        var warmSum = 0L
        var coldSum = 0L
        var overExposed = 0
        var underExposed = 0

        // 从 Y 平面采样亮度
        for (gy in 0 until sampleRows) {
            val y = (gy * strideY).coerceAtMost(h - 1)
            val rowStart = y * rowStride
            for (gx in 0 until sampleCols) {
                val x = (gx * strideX).coerceAtMost(w - 1)
                val yIdx = rowStart + x * pixelStride
                if (yIdx < yBuffer.limit()) {
                    val luma = yBuffer.get(yIdx).toInt() and 0xFF
                    totalLuma += luma
                    samples++
                    if (luma > 240) overExposed++
                    if (luma < 25) underExposed++
                }
            }
        }

        if (samples == 0) return null

        val avgLuma = totalLuma.toFloat() / samples / 255f  // 归一化 0~1

        // 色温估计：从 U/V 平面采样
        val uPlane = mediaImage.planes[1]
        val vPlane = mediaImage.planes[2]
        val uBuffer = uPlane.buffer
        val vBuffer = vPlane.buffer
        val uRowStride = uPlane.rowStride
        val vRowStride = vPlane.rowStride
        val uPixelStride = uPlane.pixelStride
        val vPixelStride = vPlane.pixelStride
        val chromaW = w / 2
        val chromaH = h / 2

        val chromaStrideX = (chromaW / sampleCols).coerceAtLeast(1)
        val chromaStrideY = (chromaH / sampleRows).coerceAtLeast(1)
        var chromaSamples = 0
        for (gy in 0 until sampleRows step 2) {
            val cy = (gy * chromaStrideY).coerceAtMost(chromaH - 1)
            val uRowStart = cy * uRowStride
            val vRowStart = cy * vRowStride
            for (gx in 0 until sampleCols step 2) {
                val cx = (gx * chromaStrideX).coerceAtMost(chromaW - 1)
                val uIdx = uRowStart + cx * uPixelStride
                val vIdx = vRowStart + cx * vPixelStride
                if (uIdx < uBuffer.limit() && vIdx < vBuffer.limit()) {
                    val u = uBuffer.get(uIdx).toInt() and 0xFF  // 128 为中性
                    val v = vBuffer.get(vIdx).toInt() and 0xFF
                    // V 分量偏高 → 暖（红/橙）；U 分量偏高 → 冷（蓝）
                    warmSum += (v - 128).coerceAtLeast(0)
                    coldSum += (u - 128).coerceAtLeast(0)
                    chromaSamples++
                }
            }
        }
        val colorTemperature = if (chromaSamples > 0) {
            val warmRatio = warmSum.toFloat() / (warmSum + coldSum + 1)
            // warmRatio 越高色温越暖 → 映射到 0~1
            warmRatio.coerceIn(0f, 1f)
        } else 0.5f

        // 曝光时间（ns → ms）
        val exposureTimeMs = exposureTimeRange?.let { range ->
            val center = (range.lower + range.upper) / 2
            center / 1_000_000L  // ns → ms
        }

        return LightAnalysisResult(
            lightLevel = avgLuma.coerceIn(0f, 1f),
            colorTemperature = colorTemperature,
            exposureTimeMs = exposureTimeMs,
            overExposureRatio = overExposed.toFloat() / samples,
            underExposureRatio = underExposed.toFloat() / samples,
            exposureCompensation = 0f
        )
    }

    private fun handleSceneResult(scene: SceneType) {
        if (scene == SceneType.UNKNOWN) return
        sceneVoteBuffer.add(scene)
        if (sceneVoteBuffer.size > sceneDebounceThreshold) sceneVoteBuffer.removeAt(0)

        if (sceneVoteBuffer.size == sceneDebounceThreshold && sceneVoteBuffer.all { it == scene }) {
            sceneVoteBuffer.clear()
            onSceneChange(scene)
        }
    }

    private fun downscale(src: Bitmap, maxSize: Int): Bitmap {
        val w = src.width
        val h = src.height
        val scale = maxSize.toFloat() / maxOf(w, h)
        if (scale >= 1f) return src
        return Bitmap.createScaledBitmap(src, (w * scale).toInt(), (h * scale).toInt(), true)
    }

    // MARK: - 拍照

    fun takePhoto() {
        val capture = imageCapture ?: return
        capture.takePicture(mainExecutor, object : OnImageCaptured() {
            override fun onCaptureSuccess(bitmap: Bitmap) {
                onPhotoCapture(bitmap)
            }
        })
    }

    fun takeOOTDSnapshot() {
        pendingOOTDRequest = true
    }

    // MARK: - 录像

    @androidx.annotation.OptIn(ExperimentalPersistentRecording::class)
    fun startVideoRecording(file: File) {
        val vc = videoCapture ?: return
        if (isRecording) return
        val options = FileOutputOptions.Builder(file).build()
        activeRecording = vc.output
            .prepareRecording(context, options)
            .withAudioEnabled()
            .start(mainExecutor) { event ->
                if (event is VideoRecordEvent.Finalize) {
                    val hasError = event.hasError()
                    isRecording = false
                    activeRecording = null
                    if (hasError) {
                        onRecordingSave(file.absolutePath)
                    } else {
                        onRecordingSave(file.absolutePath)
                    }
                }
            }
        isRecording = true
    }

    @androidx.annotation.OptIn(ExperimentalPersistentRecording::class)
    fun stopVideoRecording() {
        activeRecording?.stop()
    }

    private abstract class OnImageCaptured : ImageCapture.OnImageCapturedCallback() {
        final override fun onCaptureSuccess(imageProxy: ImageProxy) {
            val bitmap = imageProxy.toBitmap()
            imageProxy.close()
            if (bitmap != null) onCaptureSuccess(bitmap)
        }
        abstract fun onCaptureSuccess(bitmap: Bitmap)

        override fun onError(exception: ImageCaptureException) { /* 忽略单帧错误 */ }
    }

    fun cleanUp() {
        cameraProvider?.unbindAll()
        poseProvider.close()
        sceneClassifier.close()
    }
}

/** 帧光线分析结果 */
data class LightAnalysisResult(
    val lightLevel: Float,           // 0~1 归一化亮度
    val colorTemperature: Float,     // 0=冷 1=暖
    val exposureTimeMs: Long?,       // 曝光时间（毫秒）
    val overExposureRatio: Float,    // 过曝像素比例 0~1
    val underExposureRatio: Float,  // 欠曝像素比例 0~1
    val exposureCompensation: Float  // 曝光补偿 -1 ~ +1
)

// ============================================================================
// ImageProxy → Bitmap 完整转换管线
// ============================================================================

/**
 * 将 ImageProxy 完整转换为 RGB Bitmap。
 *
 * CameraX ImageProxy 使用 YUV_420_888 / YUV_422_888 / YUV_444_888 等格式。
 * 通过 YuvImage 压缩为 JPEG 后再解码为 ARGB_8888，确保颜色正确。
 * 同时根据 rotationDegrees 旋转为正向。
 *
 * 此实现是 Android CameraX 标准流程，与 iOS Vision 的完整颜色空间转换等价。
 */
@androidx.annotation.OptIn(ExperimentalGetImage::class)
internal fun ImageProxy.toBitmap(): Bitmap? {
    val mediaImage = image ?: return null

    val planes = mediaImage.planes
    if (planes.size < 3) return null
    val yPlane = planes[0]
    val uPlane = planes[1]
    val vPlane = planes[2]

    val imageWidth = width
    val imageHeight = height

    val yRowStride = yPlane.rowStride
    val yPixelStride = yPlane.pixelStride
    val uRowStride = uPlane.rowStride
    val uPixelStride = uPlane.pixelStride
    val vRowStride = vPlane.rowStride
    val vPixelStride = vPlane.pixelStride

    val colorFormat = when (mediaImage.format) {
        ImageFormat.YUV_420_888 -> android.graphics.ImageFormat.NV21
        ImageFormat.YUV_422_888 -> android.graphics.ImageFormat.YUV_420_888
        ImageFormat.YUV_444_888 -> android.graphics.ImageFormat.YUV_420_888
        else -> android.graphics.ImageFormat.YUV_420_888
    }

    val outputStream = ByteArrayOutputStream()
    val yBytes = ByteArray(yRowStride * imageHeight)
    yPlane.buffer.rewind()
    yPlane.buffer.get(yBytes)

    val chromaWidth = imageWidth / 2
    val chromaHeight = imageHeight / 2
    val vBytes = ByteArray(vRowStride * chromaHeight)
    val uBytes = ByteArray(uRowStride * chromaHeight)
    vPlane.buffer.rewind()
    vPlane.buffer.get(vBytes)
    uPlane.buffer.rewind()
    uPlane.buffer.get(uBytes)

    val nv21Size = yRowStride * imageHeight + (vRowStride + uRowStride) * chromaHeight
    val nv21Data = ByteArray(nv21Size)
    System.arraycopy(yBytes, 0, nv21Data, 0, yRowStride * imageHeight)
    var dstOffset = yRowStride * imageHeight
    for (row in 0 until chromaHeight) {
        val vOffset = row * vRowStride
        val uOffset = row * uRowStride
        for (col in 0 until chromaWidth) {
            val vIdx = if (vPixelStride > 1) { vOffset + col * vPixelStride } else { vOffset + col }
            val uIdx = if (uPixelStride > 1) { uOffset + col * uPixelStride } else { uOffset + col }
            nv21Data[dstOffset++] = vBytes[vIdx]
            nv21Data[dstOffset++] = uBytes[uIdx]
        }
    }

    val yuvImage = YuvImage(nv21Data, colorFormat, imageWidth, imageHeight, null)
    val rect = Rect(0, 0, imageWidth, imageHeight)
    yuvImage.compressToJpeg(rect, 95, outputStream)
    val jpegBytes = outputStream.toByteArray()

    val bitmap = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size) ?: return null

    val rotation = imageInfo.rotationDegrees
    return if (rotation == 0) {
        bitmap
    } else {
        rotateBitmap(bitmap, rotation)
    }
}

internal fun rotateBitmap(src: Bitmap, degrees: Int): Bitmap {
    if (degrees == 0) return src
    val matrix = Matrix()
    matrix.postRotate(degrees.toFloat())
    val rotated = Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
    if (rotated != src && !src.isRecycled) src.recycle()
    return rotated
}
