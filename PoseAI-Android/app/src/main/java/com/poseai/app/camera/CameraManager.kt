package com.poseai.app.camera

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.TorchState
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class CameraManager(private val context: Context) {

    companion object {
        private const val TAG = "CameraManager"
    }

    private val _isFrontCamera = MutableStateFlow(false)
    val isFrontCamera: StateFlow<Boolean> = _isFrontCamera.asStateFlow()

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _torchState = MutableStateFlow(TorchState.OFF)
    val torchState: StateFlow<Int> = _torchState.asStateFlow()

    private val _exposureIndex = MutableStateFlow(0)
    val exposureIndex: StateFlow<Int> = _exposureIndex.asStateFlow()

    private val _zoomRatio = MutableStateFlow(1f)
    val zoomRatio: StateFlow<Float> = _zoomRatio.asStateFlow()

    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var preview: Preview? = null
    private var imageCapture: ImageCapture? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var currentAnalyzer: ImageAnalysis.Analyzer? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var activeRecording: Recording? = null
    private var torchObserver: androidx.lifecycle.Observer<Int>? = null

    private val cameraExecutor: ExecutorService = Executors.newFixedThreadPool(4)

    private var currentChunkFile: File? = null
    private val _videoChunks = mutableListOf<File>()
    val videoChunks: List<File> get() = synchronized(_videoChunks) { _videoChunks.toList() }

    private val videoTempDir: File by lazy {
        File(context.cacheDir, "video_chunks").apply { mkdirs() }
    }

    @Volatile
    private var isShutdown = false

    fun startCamera(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        lensFacing: Int = CameraSelector.LENS_FACING_BACK,
        analysisAnalyzer: ImageAnalysis.Analyzer? = null
    ) {
        if (isShutdown) return
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()

                // 先解绑旧的 use cases
                cameraProvider?.unbindAll()

                preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                    .setTargetRotation(previewView.display.rotation)
                    .build()

                val recorder = Recorder.Builder()
                    .setExecutor(cameraExecutor)
                    .build()
                videoCapture = VideoCapture.withOutput(recorder)

                val cameraSelector = CameraSelector.Builder()
                    .requireLensFacing(lensFacing)
                    .build()

                imageAnalysis = if (analysisAnalyzer != null) {
                    currentAnalyzer = analysisAnalyzer
                    ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setTargetRotation(previewView.display.rotation)
                        .build()
                        .also { it.setAnalyzer(cameraExecutor, analysisAnalyzer) }
                } else null

                val useCases = listOfNotNull(preview, imageCapture, videoCapture, imageAnalysis)
                camera = cameraProvider?.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    *useCases.toTypedArray()
                )
                _isFrontCamera.value = lensFacing == CameraSelector.LENS_FACING_FRONT
                observeCameraState()
            } catch (exc: Exception) {
                Log.e(TAG, "Camera start failed", exc)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun observeCameraState() {
        val cam = camera ?: return
        torchObserver?.let { cam.cameraInfo.torchState.removeObserver(it) }
        val observer = androidx.lifecycle.Observer<Int> { state ->
            _torchState.value = state
        }
        torchObserver = observer
        cam.cameraInfo.torchState.observeForever(observer)
        cam.cameraInfo.exposureState.let { exposure ->
            _exposureIndex.value = exposure.exposureCompensationIndex
        }
        cam.cameraInfo.zoomState.value?.let { zoom ->
            _zoomRatio.value = zoom.zoomRatio
        }
    }

    fun switchCamera(lifecycleOwner: LifecycleOwner, previewView: PreviewView) {
        camera?.let { oldCam ->
            torchObserver?.let { oldCam.cameraInfo.torchState.removeObserver(it) }
        }
        torchObserver = null
        val newLens = if (_isFrontCamera.value) {
            CameraSelector.LENS_FACING_BACK
        } else {
            CameraSelector.LENS_FACING_FRONT
        }
        if (!hasCameraLensFacing(newLens)) {
            Log.w(TAG, "设备不存在目标摄像头: $newLens")
            return
        }
        val hasAnalysis = imageAnalysis != null
        startCamera(lifecycleOwner, previewView, newLens, if (hasAnalysis) currentAnalyzer else null)
    }

    private fun hasCameraLensFacing(lensFacing: Int): Boolean {
        return try {
            val cameraProvider = ProcessCameraProvider.getInstance(context).get()
            val selector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
            cameraProvider.hasCamera(selector)
        } catch (e: Exception) {
            false
        }
    }

    fun takePhoto(outputFile: File, onResult: (Boolean, String?) -> Unit) {
        val capture = imageCapture ?: run {
            onResult(false, "Image capture not ready")
            return
        }
        val outputOptions = ImageCapture.OutputFileOptions.Builder(outputFile).build()
        capture.takePicture(
            outputOptions,
            cameraExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    onResult(true, outputFile.absolutePath)
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e(TAG, "Take photo failed: ${exception.message}", exception)
                    onResult(false, exception.message)
                }
            }
        )
    }

    fun startVideoChunk(): Boolean {
        val capture = videoCapture ?: return false
        if (_isRecording.value) return false

        val chunkFile = File(videoTempDir, "${UUID.randomUUID()}.mp4")
        currentChunkFile = chunkFile
        val outputOptions = FileOutputOptions.Builder(chunkFile).build()

        return try {
            val pendingRecording = capture.output
                .prepareRecording(context, outputOptions)
                .withAudioEnabled()

            activeRecording = pendingRecording.start(ContextCompat.getMainExecutor(context)) { event ->
                when (event) {
                    is VideoRecordEvent.Start -> {
                        _isRecording.value = true
                    }
                    is VideoRecordEvent.Finalize -> {
                        _isRecording.value = false
                        activeRecording = null
                        if (!event.hasError()) {
                            currentChunkFile?.let { chunk ->
                                if (chunk.exists() && chunk.length() > 0) {
                                    synchronized(_videoChunks) { _videoChunks.add(chunk) }
                                }
                            }
                        } else {
                            Log.e(TAG, "Recording finalize error: ${event.error}")
                            currentChunkFile?.delete()
                        }
                        currentChunkFile = null
                    }
                }
            }
            true
        } catch (e: SecurityException) {
            Log.e(TAG, "Audio permission denied for video recording", e)
            // 降级：不带音频录制
            try {
                val pendingRecordingNoAudio = capture.output
                    .prepareRecording(context, outputOptions)
                activeRecording = pendingRecordingNoAudio.start(
                    ContextCompat.getMainExecutor(context)
                ) { event ->
                    when (event) {
                        is VideoRecordEvent.Start -> {
                            _isRecording.value = true
                        }
                        is VideoRecordEvent.Finalize -> {
                            _isRecording.value = false
                            activeRecording = null
                            if (!event.hasError()) {
                                currentChunkFile?.let { chunk ->
                                    if (chunk.exists() && chunk.length() > 0) {
                                        synchronized(_videoChunks) { _videoChunks.add(chunk) }
                                    }
                                }
                            } else {
                                currentChunkFile?.delete()
                            }
                            currentChunkFile = null
                        }
                    }
                }
                true
            } catch (e2: Exception) {
                Log.e(TAG, "Video recording fallback also failed", e2)
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "startVideoChunk failed", e)
            false
        }
    }

    fun stopVideoChunk() {
        try {
            activeRecording?.stop()
        } catch (e: Exception) {
            Log.e(TAG, "stopVideoChunk error", e)
        }
        activeRecording = null
    }

    fun resetVideoChunks() {
        synchronized(_videoChunks) { _videoChunks.clear() }
        videoTempDir.listFiles()?.forEach { it.delete() }
    }

    fun setExposureCompensation(index: Int): Boolean {
        val cam = camera ?: return false
        val exposureState = cam.cameraInfo.exposureState
        // 检查是否支持曝光补偿
        if (!exposureState.isExposureCompensationSupported) {
            Log.w(TAG, "设备不支持曝光补偿")
            return false
        }
        val range = exposureState.exposureCompensationRange
        val clampedIndex = index.coerceIn(range.lower, range.upper)
        return try {
            cam.cameraControl.setExposureCompensationIndex(clampedIndex)
            _exposureIndex.value = clampedIndex
            true
        } catch (e: Exception) {
            Log.e(TAG, "Exposure compensation failed", e)
            false
        }
    }

    fun toggleTorch(): Boolean {
        val cam = camera ?: return false
        // 检查是否有闪光灯单元
        if (!cam.cameraInfo.hasFlashUnit()) {
            Log.w(TAG, "设备无闪光灯，无法切换_torch")
            return false
        }
        val newState = _torchState.value != TorchState.ON
        cam.cameraControl.enableTorch(newState)
        return newState
    }

    fun setZoomRatio(ratio: Float) {
        val cam = camera ?: return
        val zoomState = cam.cameraInfo.zoomState.value ?: return
        val minRatio = zoomState.minZoomRatio
        val maxRatio = zoomState.maxZoomRatio
        if (minRatio == 1.0f && maxRatio == 1.0f) {
            Log.w(TAG, "设备不支持变焦")
            return
        }
        val clamped = ratio.coerceIn(minRatio, maxRatio)
        cam.cameraControl.setZoomRatio(clamped)
        _zoomRatio.value = clamped
    }

    fun getMaxExposure(): Int {
        val exposureState = camera?.cameraInfo?.exposureState
        return if (exposureState?.isExposureCompensationSupported == true) {
            exposureState.exposureCompensationRange.upper
        } else 0
    }

    fun getMinExposure(): Int {
        val exposureState = camera?.cameraInfo?.exposureState
        return if (exposureState?.isExposureCompensationSupported == true) {
            exposureState.exposureCompensationRange.lower
        } else 0
    }

    fun getVideoChunkDir(): File = videoTempDir

    fun shutdown() {
        if (isShutdown) return
        isShutdown = true
        try {
            activeRecording?.stop()
            activeRecording = null
        } catch (_: Exception) {}
        // 移除 torch observer 防止内存泄漏
        camera?.let { oldCam ->
            torchObserver?.let { oldCam.cameraInfo.torchState.removeObserver(it) }
        }
        torchObserver = null
        try {
            cameraProvider?.unbindAll()
        } catch (_: Exception) {}
        camera = null
        cameraProvider = null
        preview = null
        imageCapture = null
        imageAnalysis = null
        videoCapture = null
        cameraExecutor.shutdown()
        try {
            if (!cameraExecutor.awaitTermination(3, TimeUnit.SECONDS)) {
                cameraExecutor.shutdownNow()
            }
        } catch (_: InterruptedException) {
            cameraExecutor.shutdownNow()
        }
    }
}
