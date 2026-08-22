package com.poseai.app.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
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
import java.io.File
import java.util.concurrent.Executor

/**
 * 摄像头管理器——对应 iOS CameraManager。
 * 负责 CameraX Session 生命周期、前后置切换、姿态分析、场景识别、拍照与录像。
 */
class CameraManager(
    private val context: Context
) {
    private val mainExecutor: Executor = ContextCompat.getMainExecutor(context)

    // MARK: - 回调（对应 iOS onUpdate / onSceneChange / onLowLight / onPhotoCapture / onOOTDSnapshot 等）
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

    private var activeRecording: androidx.camera.video.Recording? = null

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
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST) // 对应 alwaysDiscardsLateVideoFrames
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
        // 性能降级：隔帧丢弃（暂不依据电量，保持简单）
        frameCounter++
        if (frameCounter % 2 == 0) {
            imageProxy.close()
            return
        }

        // 高频姿态检测
        poseProvider.process(imageProxy)

        // 低频场景分类（2s / 4s）
        val now = System.currentTimeMillis()
        val interval = if (isLowLightMode) sceneUpdateIntervalMs * 2 else sceneUpdateIntervalMs
        if (now - lastSceneUpdate > interval) {
            lastSceneUpdate = now
            val bitmap = imageProxy.toBitmap()
            if (bitmap != null) {
                sceneClassifier.classify(bitmap) { scene -> handleSceneResult(scene) }
            }
            // OOTD 快照
            if (pendingOOTDRequest) {
                pendingOOTDRequest = false
                val bmp = bitmap?.let { downscale(it, 512) }
                if (bmp != null) mainExecutor.execute { onOOTDSnapshot(bmp) }
            }
        }

        // 注意：poseProvider.process 内部会负责 imageProxy.close()
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

    /** 低分辨率 OOTD 快照请求 */
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
                    isRecording = false
                    activeRecording = null
                    if (event.hasError()) {
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

/** 把当前帧 ImageProxy 转为 Bitmap（用于场景分类/快照） */
@androidx.annotation.OptIn(ExperimentalGetImage::class)
private fun ImageProxy.toBitmap(): Bitmap? {
    val mediaImage = image ?: return null
    val plane = mediaImage.planes[0]
    val buffer = plane.buffer
    val pixelStride = plane.pixelStride
    val rowStride = plane.rowStride
    val rowPadding = rowStride - pixelStride * width
    val bmpWidth = width + rowPadding / pixelStride

    val bitmap = Bitmap.createBitmap(bmpWidth, height, Bitmap.Config.ARGB_8888)
    bitmap.copyPixelsFromBuffer(buffer)

    // 裁掉 padding 并旋转到竖屏
    val trimmed = if (bmpWidth != width) {
        Bitmap.createBitmap(bitmap, 0, 0, width, height)
    } else bitmap

    return rotateBitmap(trimmed, imageInfo.rotationDegrees)
}

private fun rotateBitmap(src: Bitmap, degrees: Int): Bitmap {
    if (degrees == 0) return src
    val matrix = Matrix()
    matrix.postRotate(degrees.toFloat())
    val rotated = Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
    if (rotated != src) src.recycle()
    return rotated
}