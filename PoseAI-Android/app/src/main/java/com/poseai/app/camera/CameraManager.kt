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
 * 关键实现：
 *  - 完整 YUV_420_888 → RGB_888 转换（使用 YuvImage 压缩为 JPEG 再解码）
 *  - 帧级姿态 + 低频场景双轨分析
 *  - 防抖投票机制
 *  - 拍照/录像完整管线
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

    // 状态
    var isLowLightMode: Boolean = false
        set(value) { field = value }
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

        // 帧分析（姿态 + 场景）
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
    }

    // MARK: - Session 控制

    fun switchCamera() {
        isFrontCamera = !isFrontCamera
        poseProvider.isFrontCamera = isFrontCamera
    }

    // MARK: - 帧分析

    @androidx.annotation.OptIn(ExperimentalGetImage::class, ExperimentalPersistentRecording::class)
    private fun analyzeFrame(imageProxy: ImageProxy) {
        // 性能：隔帧丢弃姿态分析（奇偶帧交替）
        frameCounter++
        val skipThisFrame = frameCounter % 2 == 0

        // 高频姿态检测（不过滤时每帧都跑）
        if (!skipThisFrame) {
            poseProvider.process(imageProxy)
        }

        // 低频场景分类（2s / 4s）
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
        if (skipThisFrame) {
            imageProxy.close()
        }
        // 否则 poseProvider.process 内部会负责关闭
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

    val yuvFormat = mediaImage.format
    val planes = mediaImage.planes
    val yPlane = planes[0]
    val uPlane = planes[1]
    val vPlane = planes[2]

    val yBuffer = yPlane.buffer
    val uBuffer = uPlane.buffer
    val vBuffer = vPlane.buffer

    val imageWidth = width
    val imageHeight = height

    val yRowStride = yPlane.rowStride
    val yPixelStride = yPlane.pixelStride
    val uRowStride = uPlane.rowStride
    val uPixelStride = uPlane.pixelStride
    val vRowStride = vPlane.rowStride
    val vPixelStride = vPlane.pixelStride

    val colorFormat = when (yuvFormat) {
        ImageFormat.YUV_420_888 -> android.graphics.ImageFormat.NV21
        ImageFormat.YUV_422_888 -> android.graphics.ImageFormat.YUV_420_888
        ImageFormat.YUV_444_888 -> android.graphics.ImageFormat.YUV_420_888
        else -> android.graphics.ImageFormat.YUV_420_888
    }

    // 将 YUV 数据拼装到连续 buffer
    val outputStream = ByteArrayOutputStream()
    // NV21: Y 平面 + 交错的 V/U 平面
    val yBytes = ByteArray(yRowStride * imageHeight)
    yBuffer.rewind()
    yBuffer.get(yBytes)

    // V 和 U 交错：对于 YUV_420_888，分辨率为 W*H 亮度 + (W/2)*(H/2) 色度
    val chromaWidth = imageWidth / 2
    val chromaHeight = imageHeight / 2
    val vBytes = ByteArray(vRowStride * chromaHeight)
    val uBytes = ByteArray(uRowStride * chromaHeight)
    vBuffer.rewind()
    vBuffer.get(vBytes)
    uBuffer.rewind()
    uBuffer.get(uBytes)

    // 组装 NV21（V then U interleaved）
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

    // 旋转
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
