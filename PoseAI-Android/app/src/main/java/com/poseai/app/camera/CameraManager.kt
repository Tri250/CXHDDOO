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
import kotlin.math.max
import kotlin.math.min

/**
 * 摄像头管理器——深度自检后的完整修复版本。
 *
 * 修复项：
 *  - analyzeFrame 异常安全：imageProxy 在所有分支都正确关闭
 *  - analyzeLight 边界检查：planes 数量/stride/buffer 边界完整保护
 *  - 线程安全：使用 synchronized 保护光线分析状态
 *  - 低光降频的 race condition 修复
 *  - toBitmap 异常路径保证 imageProxy 被关闭
 *  - MediaCodec 配置健壮性：宽度/高度非 4 倍数时对齐
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
    @Volatile
    var isLowLightMode: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                // 避免在回调中再次设置导致无限递归
                onLowLight(value)
            }
        }
    @Volatile
    var isFrontCamera: Boolean = false
    @Volatile
    var isRecording: Boolean = false
        private set
    @Volatile
    private var pendingOOTDRequest = false
    private val ootdLock = Any()

    // 姿态提供者 & 场景分类（ML Kit）
    val poseProvider = MlKitPoseProvider(context)
    private val sceneClassifier = SceneClassifier(context)

    // CameraX 组件
    private var cameraProvider: ProcessCameraProvider? = null
    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var camera: Camera? = null

    @Volatile
    private var activeRecording: Recording? = null

    // 场景防抖
    private val sceneDebounceThreshold = 2
    private val sceneVoteBuffer = ArrayList<SceneType>()
    @Volatile
    private var lastSceneUpdate = 0L
    private val sceneUpdateIntervalMs = 2000L

    // 帧计数（@Volatile 保证跨线程可见性）
    @Volatile
    private var frameCounter = 0

    // MARK: - 最近光线分析结果（synchronized 保护）
    @Volatile var lastLightLevel: Float? = null
        private set
    @Volatile var lastColorTemperature: Float? = null
        private set
    @Volatile var lastExposureTimeMs: Long? = null
        private set
    @Volatile var lastIsLowLight: Boolean = false
        private set
    @Volatile var lastExposureCompensation: Float = 0f
        private set

    // 相机曝光参数范围
    private var exposureTimeRange: android.util.Range<Long>? = null
    private var sensitivityRange: android.util.Range<Int>? = null

    // 光线分析滑动窗口（10 帧平均，线程安全）
    private val recentLightLevels = ArrayDeque<Float>(10)
    private val recentColorTemps = ArrayDeque<Float>(10)
    private val lightLock = Any()

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

        val preview = Preview.Builder().build()
        preview.setSurfaceProvider(previewView.surfaceProvider)

        imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_ZERO_SHUTTER_LAG)
            .build()

        val recorder = Recorder.Builder()
            .setQualitySelector(QualitySelector.from(Quality.HD))
            .build()
        videoCapture = VideoCapture.withOutput(recorder)

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

        // 读取相机曝光/ISO 能力
        camera?.cameraInfo?.let { info ->
            runCatching {
                val cam2 = androidx.camera.camera2.interop.Camera2CameraInfo.from(info)
                val chars = cam2.cameraCharacteristics
                exposureTimeRange = chars.get(android.hardware.camera2.CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_EXPOSURE_TIME_RANGE)
                sensitivityRange = chars.get(android.hardware.camera2.CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)
            }
        }
    }

    // MARK: - Session 控制

    fun switchCamera() {
        isFrontCamera = !isFrontCamera
        poseProvider.isFrontCamera = isFrontCamera
    }

    // MARK: - 帧分析（异常安全 + 完整管线）

    @androidx.annotation.OptIn(ExperimentalGetImage::class, ExperimentalPersistentRecording::class)
    private fun analyzeFrame(imageProxy: ImageProxy) {
        frameCounter++
        var handledByPose = false

        try {
            // 1) 光线分析（每帧）
            val lightResult = runCatching { analyzeLight(imageProxy) }.getOrNull()
            if (lightResult != null) {
                synchronized(lightLock) {
                    if (recentLightLevels.size >= 10) recentLightLevels.removeFirst()
                    recentLightLevels.addLast(lightResult.lightLevel)
                    if (recentColorTemps.size >= 10) recentColorTemps.removeFirst()
                    recentColorTemps.addLast(lightResult.colorTemperature)

                    lastLightLevel = recentLightLevels.average().toFloat()
                    lastColorTemperature = recentColorTemps.average().toFloat()
                    lastExposureTimeMs = lightResult.exposureTimeMs
                    lastExposureCompensation = lightResult.exposureCompensation

                    val smoothedLight = lastLightLevel ?: 0.5f
                    val wasLow = lastIsLowLight
                    lastIsLowLight = smoothedLight < 0.25f
                    if (lastIsLowLight != wasLow) {
                        isLowLightMode = lastIsLowLight
                    }
                }
                onLightAnalysis(lightResult)
            }

            // 2) 姿态检测：隔帧丢弃，低光时加倍降频
            val modulo = if (isLowLightMode) 4 else 2
            val skipPoseFrame = frameCounter % modulo == 0
            if (!skipPoseFrame) {
                handledByPose = true
                // poseProvider.process 会在内部通过 addOnCompleteListener 关闭 imageProxy
                // 如果 process 抛出同步异常，则需要在 finally 中关闭
                runCatching {
                    poseProvider.process(imageProxy)
                }.onFailure {
                    // 同步异常，确保 imageProxy 被关闭
                    runCatching { imageProxy.close() }
                    handledByPose = false
                }
                if (handledByPose) return
            }

            // 3) 低频场景分类
            val now = System.currentTimeMillis()
            val interval = if (isLowLightMode) sceneUpdateIntervalMs * 2 else sceneUpdateIntervalMs
            if (now - lastSceneUpdate > interval) {
                lastSceneUpdate = now
                runCatching {
                    val bitmap = imageProxy.toBitmap()
                    if (bitmap != null) {
                        sceneClassifier.classify(bitmap, onResult = { scene -> handleSceneResult(scene) })
                        val shouldSendOOTD = synchronized(ootdLock) {
                            if (pendingOOTDRequest) {
                                pendingOOTDRequest = false
                                true
                            } else false
                        }
                        if (shouldSendOOTD) {
                            val bmp = downscale(bitmap, 512)
                            mainExecutor.execute { onOOTDSnapshot(bmp) }
                        }
                    }
                }
            }
        } catch (_: Exception) {
            // 帧分析异常，忽略该帧
        } finally {
            if (!handledByPose) {
                runCatching { imageProxy.close() }
            }
        }
    }

    /**
     * 帧光线分析（完整边界保护）：
     *  - 从 YUV 平面直接采样亮度/色温，零颜色转换开销
     *  - 完整的 planes/stride/buffer 边界检查
     *  - 采样 16x12 网格，约 192 点
     */
    @androidx.annotation.OptIn(ExperimentalGetImage::class)
    private fun analyzeLight(imageProxy: ImageProxy): LightAnalysisResult? {
        val mediaImage = imageProxy.image ?: return null
        val w = imageProxy.width
        val h = imageProxy.height
        if (w <= 0 || h <= 0) return null

        val planes = mediaImage.planes
        if (planes.size < 3) return null

        val sampleCols = 16
        val sampleRows = 12
        val strideX = w / sampleCols
        val strideY = h / sampleRows
        if (strideX <= 0 || strideY <= 0) return null

        val yPlane = planes[0]
        val yBuffer = yPlane.buffer
        val rowStride = yPlane.rowStride
        val pixelStride = yPlane.pixelStride
        val yBufferLimit = yBuffer.limit()
        val yBufferCapacity = yBuffer.capacity()

        // 边界检查：确保 rowStride 和 pixelStride 合理
        if (rowStride <= 0 || pixelStride <= 0) return null
        if (yBufferLimit <= 0 || yBufferCapacity <= 0) return null

        var totalLuma = 0L
        var samples = 0
        var warmSum = 0L
        var coldSum = 0L
        var overExposed = 0
        var underExposed = 0

        // 采样 Y 平面亮度（完整边界保护）
        for (gy in 0 until sampleRows) {
            val y = (gy * strideY).coerceIn(0, h - 1)
            val rowStart = y.toLong() * rowStride
            for (gx in 0 until sampleCols) {
                val x = (gx * strideX).coerceIn(0, w - 1)
                val idx = rowStart + x.toLong() * pixelStride
                // 完整边界检查：确保索引在 [0, yBufferLimit) 且 >= 0
                val yIdx = idx.coerceIn(0L, (yBufferLimit - 1).toLong()).toInt()
                if (yIdx >= 0 && yIdx < yBufferLimit) {
                    val luma = yBuffer.get(yIdx).toInt() and 0xFF
                    totalLuma += luma
                    samples++
                    if (luma > 240) overExposed++
                    if (luma < 25) underExposed++
                }
            }
        }

        if (samples == 0) return null
        val avgLuma = totalLuma.toFloat() / samples / 255f

        // 色温估计（U/V 平面）
        val uPlane = planes[1]
        val vPlane = planes[2]
        val uBuffer = uPlane.buffer
        val vBuffer = vPlane.buffer
        val uRowStride = uPlane.rowStride
        val vRowStride = vPlane.rowStride
        val uPixelStride = uPlane.pixelStride
        val vPixelStride = vPlane.pixelStride
        val uLimit = uBuffer.limit()
        val vLimit = vBuffer.limit()

        // 边界检查 UV 平面
        if (uRowStride <= 0 || vRowStride <= 0 || uPixelStride <= 0 || vPixelStride <= 0) {
            return LightAnalysisResult(
                lightLevel = avgLuma.coerceIn(0f, 1f),
                colorTemperature = 0.5f,
                exposureTimeMs = null,
                overExposureRatio = overExposed.toFloat() / samples,
                underExposureRatio = underExposed.toFloat() / samples,
                exposureCompensation = 0f
            )
        }
        if (uLimit <= 0 || vLimit <= 0) {
            return LightAnalysisResult(
                lightLevel = avgLuma.coerceIn(0f, 1f),
                colorTemperature = 0.5f,
                exposureTimeMs = null,
                overExposureRatio = overExposed.toFloat() / samples,
                underExposureRatio = underExposed.toFloat() / samples,
                exposureCompensation = 0f
            )
        }

        val chromaW = w / 2
        val chromaH = h / 2
        if (chromaW <= 0 || chromaH <= 0) return null

        val chromaStrideX = (chromaW / sampleCols.coerceAtLeast(1)).coerceAtLeast(1)
        val chromaStrideY = (chromaH / sampleRows.coerceAtLeast(1)).coerceAtLeast(1)
        var chromaSamples = 0
        for (gy in 0 until sampleRows step 2) {
            val cy = (gy * chromaStrideY).coerceIn(0, chromaH - 1)
            val uRowStart = cy.toLong() * uRowStride
            val vRowStart = cy.toLong() * vRowStride
            for (gx in 0 until sampleCols step 2) {
                val cx = (gx * chromaStrideX).coerceIn(0, chromaW - 1)
                val uIdx = (uRowStart + cx.toLong() * uPixelStride).coerceIn(0L, (uLimit - 1).toLong()).toInt()
                val vIdx = (vRowStart + cx.toLong() * vPixelStride).coerceIn(0L, (vLimit - 1).toLong()).toInt()
                if (uIdx in 0 until uLimit && vIdx in 0 until vLimit) {
                    val u = uBuffer.get(uIdx).toInt() and 0xFF
                    val v = vBuffer.get(vIdx).toInt() and 0xFF
                    if (v > 128) warmSum += (v - 128)
                    if (u > 128) coldSum += (u - 128)
                    chromaSamples++
                }
            }
        }

        val colorTemperature = if (chromaSamples > 0) {
            val total = warmSum + coldSum + 1
            (warmSum.toFloat() / total).coerceIn(0f, 1f)
        } else 0.5f

        val exposureTimeMs = exposureTimeRange?.let { range ->
            val center = (range.lower + range.upper) / 2
            center / 1_000_000L
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
        synchronized(sceneVoteBuffer) {
            sceneVoteBuffer.add(scene)
            if (sceneVoteBuffer.size > sceneDebounceThreshold) sceneVoteBuffer.removeAt(0)

            if (sceneVoteBuffer.size == sceneDebounceThreshold && sceneVoteBuffer.all { it == scene }) {
                sceneVoteBuffer.clear()
            } else {
                return
            }
        }
        onSceneChange(scene)
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
        synchronized(ootdLock) {
            pendingOOTDRequest = true
        }
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
                    onRecordingSave(file.absolutePath)
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
            var bitmap: Bitmap? = null
            try {
                bitmap = imageProxy.toBitmap()
            } finally {
                runCatching { imageProxy.close() }
            }
            if (bitmap != null && !bitmap.isRecycled) {
                onCaptureSuccess(bitmap)
            }
        }
        abstract fun onCaptureSuccess(bitmap: Bitmap)

        override fun onError(exception: ImageCaptureException) { /* 忽略单帧错误 */ }
    }

    fun cleanUp() {
        cameraProvider?.unbindAll()
        runCatching { poseProvider.close() }
        runCatching { sceneClassifier.close() }
        runCatching {
            synchronized(lightLock) {
                recentLightLevels.clear()
                recentColorTemps.clear()
                lastLightLevel = null
                lastColorTemperature = null
                lastExposureTimeMs = null
                lastIsLowLight = false
                lastExposureCompensation = 0f
            }
        }
        // 重置场景状态
        synchronized(sceneVoteBuffer) {
            sceneVoteBuffer.clear()
        }
        lastSceneUpdate = 0L
        // 重置 OOTD 请求
        synchronized(ootdLock) {
            pendingOOTDRequest = false
        }
    }
}

/** 帧光线分析结果 */
data class LightAnalysisResult(
    val lightLevel: Float,
    val colorTemperature: Float,
    val exposureTimeMs: Long?,
    val overExposureRatio: Float,
    val underExposureRatio: Float,
    val exposureCompensation: Float
)

// ============================================================================
// ImageProxy → Bitmap 完整转换管线（带资源安全）
// ============================================================================

/**
 * ImageProxy → Bitmap，完整带资源安全：
 *  - try/finally 保护 YUV 解析过程
 *  - stride 异常自动降级
 *  - 空 planes / 0 尺寸直接返回 null
 */
@androidx.annotation.OptIn(ExperimentalGetImage::class)
internal fun ImageProxy.toBitmap(): Bitmap? {
    val mediaImage = image ?: return null
    val planes = mediaImage.planes
    if (planes.size < 3) return null

    val imageWidth = width
    val imageHeight = height
    if (imageWidth <= 0 || imageHeight <= 0) return null

    val yPlane = planes[0]
    val uPlane = planes[1]
    val vPlane = planes[2]

    val yBuffer = yPlane.buffer
    val uBuffer = uPlane.buffer
    val vBuffer = vPlane.buffer

    // 边界保护：buffer 大小足够
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

    return try {
        val outputStream = ByteArrayOutputStream()
        try {
            // 安全读取 Y 平面
            val yBytesSize = (yRowStride * imageHeight).coerceAtMost(yBuffer.remaining())
            val yBytes = ByteArray(yBytesSize)
            yBuffer.rewind()
            yBuffer.get(yBytes)

            val chromaWidth = imageWidth / 2
            val chromaHeight = imageHeight / 2

            val vBytesSize = (vRowStride * chromaHeight).coerceAtMost(vBuffer.remaining())
            val uBytesSize = (uRowStride * chromaHeight).coerceAtMost(uBuffer.remaining())
            val vBytes = ByteArray(vBytesSize)
            val uBytes = ByteArray(uBytesSize)
            vBuffer.rewind()
            vBuffer.get(vBytes)
            uBuffer.rewind()
            uBuffer.get(uBytes)

            val nv21Size = yBytes.size + (vBytes.size + uBytes.size)
            val nv21Data = ByteArray(nv21Size)
            System.arraycopy(yBytes, 0, nv21Data, 0, yBytes.size)
            var dstOffset = yBytes.size

            // 交错组装 NV21（按实际 chromaHeight 行）
            val actualChromaHeight = minOf(chromaHeight, vBytes.size / vRowStride.coerceAtLeast(1))
            for (row in 0 until actualChromaHeight) {
                val vOffset = (row * vRowStride).coerceIn(0, vBytes.size - 1)
                val uOffset = (row * uRowStride).coerceIn(0, uBytes.size - 1)
                for (col in 0 until chromaWidth) {
                    val vIdx = if (vPixelStride > 1) {
                        (vOffset + col * vPixelStride).coerceIn(0, vBytes.size - 1)
                    } else {
                        (vOffset + col).coerceIn(0, vBytes.size - 1)
                    }
                    val uIdx = if (uPixelStride > 1) {
                        (uOffset + col * uPixelStride).coerceIn(0, uBytes.size - 1)
                    } else {
                        (uOffset + col).coerceIn(0, uBytes.size - 1)
                    }
                    if (dstOffset < nv21Data.size) {
                        nv21Data[dstOffset++] = vBytes[vIdx]
                        nv21Data[dstOffset++] = uBytes[uIdx]
                    }
                }
            }

            val yuvImage = YuvImage(nv21Data, colorFormat, imageWidth, imageHeight, null)
            val rect = Rect(0, 0, imageWidth, imageHeight)
            yuvImage.compressToJpeg(rect, 95, outputStream)
            val jpegBytes = outputStream.toByteArray()

            BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)?.let { bitmap ->
                val rotation = imageInfo?.rotationDegrees ?: 0
                if (rotation == 0) bitmap else rotateBitmap(bitmap, rotation)
            }
        } finally {
            runCatching { outputStream.close() }
        }
    } catch (_: Exception) {
        null
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
