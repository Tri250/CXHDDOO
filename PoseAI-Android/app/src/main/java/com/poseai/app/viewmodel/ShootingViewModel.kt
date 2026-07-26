package com.poseai.app.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.YuvImage
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.view.PreviewView
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.viewModelScope
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.Pose
import com.poseai.app.PoseAIApp
import com.poseai.app.camera.CameraManager
import com.poseai.app.data.ShootingRecord
import com.poseai.app.engine.PoseDetectorEngine
import com.poseai.app.engine.PoseUtils
import com.poseai.app.engine.SceneClassifier
import com.poseai.app.engine.SmileDetector
import com.poseai.app.model.SceneType
import com.poseai.app.model.ShootingPlan
import com.poseai.app.model.VlogClip
import com.poseai.app.model.VlogTemplate
import com.poseai.app.store.StoreManager
import com.poseai.app.util.PhotoFilterEngine
import com.poseai.app.util.VideoMerger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.UUID

class ShootingViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "ShootingVM"
        private const val POSE_FRAME_INTERVAL_NORMAL = 100L
        private const val POSE_FRAME_INTERVAL_HOT = 200L
        private const val SCENE_DETECTION_INTERVAL = 3000L
        private const val SMILE_FRAME_INTERVAL = 150L
        private const val LOW_LIGHT_THRESHOLD = 50
        private const val AUTO_CAPTURE_SCORE_THRESHOLD = 80f
        private const val SCREEN_FILL_LIGHT_BRIGHTNESS = 0.4f
    }

    private val app = application as PoseAIApp
    private val storeManager: StoreManager = app.storeManager

    private var cameraManager: CameraManager? = null
    private var poseDetector: PoseDetectorEngine? = null
    private var sceneClassifier: SceneClassifier? = null
    private var smileDetector: SmileDetector? = null

    private var tts: TextToSpeech? = null
    private var toneGenerator: ToneGenerator? = null

    // ====== 状态流 ======

    private val _currentScene = MutableStateFlow(SceneType.STREET)
    val currentScene: StateFlow<SceneType> = _currentScene.asStateFlow()

    private val _currentPlanIndex = MutableStateFlow(0)
    val currentPlanIndex: StateFlow<Int> = _currentPlanIndex.asStateFlow()

    val currentPlan: ShootingPlan?
        get() = _currentScene.value.plans.getOrNull(_currentPlanIndex.value)

    private val _poseScore = MutableStateFlow(0f)
    val poseScore: StateFlow<Float> = _poseScore.asStateFlow()

    private val _detectedPoseLines = MutableStateFlow<List<Pair<PointF, PointF>>>(emptyList())
    val detectedPoseLines: StateFlow<List<Pair<PointF, PointF>>> = _detectedPoseLines.asStateFlow()

    private val _detectedPosePoints = MutableStateFlow<Map<String, PointF>>(emptyMap())
    val detectedPosePoints: StateFlow<Map<String, PointF>> = _detectedPosePoints.asStateFlow()

    private val _useSecondaryPose = MutableStateFlow(false)
    val useSecondaryPose: StateFlow<Boolean> = _useSecondaryPose.asStateFlow()

    private val _currentSequenceIndex = MutableStateFlow(0)
    val currentSequenceIndex: StateFlow<Int> = _currentSequenceIndex.asStateFlow()

    private val _currentAngleIndex = MutableStateFlow(0)
    val currentAngleIndex: StateFlow<Int> = _currentAngleIndex.asStateFlow()

    private val _isAutoCapturing = MutableStateFlow(false)
    val isAutoCapturing: StateFlow<Boolean> = _isAutoCapturing.asStateFlow()

    private val _lastCapturedPhotoPath = MutableStateFlow<String?>(null)
    val lastCapturedPhotoPath: StateFlow<String?> = _lastCapturedPhotoPath.asStateFlow()

    private val _isReviewingPhoto = MutableStateFlow(false)
    val isReviewingPhoto: StateFlow<Boolean> = _isReviewingPhoto.asStateFlow()

    private val _smileEnabled = MutableStateFlow(false)
    val smileEnabled: StateFlow<Boolean> = _smileEnabled.asStateFlow()

    private val _smileStrength = MutableStateFlow(0f)
    val smileStrength: StateFlow<Float> = _smileStrength.asStateFlow()

    private val _lowLightMode = MutableStateFlow(true)
    val lowLightMode: StateFlow<Boolean> = _lowLightMode.asStateFlow()

    private val _watermarkEnabled = MutableStateFlow(true)
    val watermarkEnabled: StateFlow<Boolean> = _watermarkEnabled.asStateFlow()

    private val _gridEnabled = MutableStateFlow(false)
    val gridEnabled: StateFlow<Boolean> = _gridEnabled.asStateFlow()

    private val _isLowLightWarning = MutableStateFlow(false)
    val isLowLightWarning: StateFlow<Boolean> = _isLowLightWarning.asStateFlow()

    private val _screenFillLightEnabled = MutableStateFlow(false)
    val screenFillLightEnabled: StateFlow<Boolean> = _screenFillLightEnabled.asStateFlow()

    private val _screenFillLightIntensity = MutableStateFlow(SCREEN_FILL_LIGHT_BRIGHTNESS)
    val screenFillLightIntensity: StateFlow<Float> = _screenFillLightIntensity.asStateFlow()

    private val _isHeatWarning = MutableStateFlow(false)
    val isHeatWarning: StateFlow<Boolean> = _isHeatWarning.asStateFlow()

    private val _isBatteryLow = MutableStateFlow(false)
    val isBatteryLow: StateFlow<Boolean> = _isBatteryLow.asStateFlow()

    private val _isVlogRecording = MutableStateFlow(false)
    val isVlogRecording: StateFlow<Boolean> = _isVlogRecording.asStateFlow()

    private val _isVlogMerging = MutableStateFlow(false)
    val isVlogMerging: StateFlow<Boolean> = _isVlogMerging.asStateFlow()

    private val _activeVlogClipIndex = MutableStateFlow(0)
    val activeVlogClipIndex: StateFlow<Int> = _activeVlogClipIndex.asStateFlow()

    private val _displayVlogText = MutableStateFlow("")
    val displayVlogText: StateFlow<String> = _displayVlogText.asStateFlow()

    private val _activeVlogTemplate = MutableStateFlow<VlogTemplate?>(null)
    val activeVlogTemplate: StateFlow<VlogTemplate?> = _activeVlogTemplate.asStateFlow()

    private val _exportedVlogPath = MutableStateFlow<String?>(null)
    val exportedVlogPath: StateFlow<String?> = _exportedVlogPath.asStateFlow()

    private val _isReviewingVlog = MutableStateFlow(false)
    val isReviewingVlog: StateFlow<Boolean> = _isReviewingVlog.asStateFlow()

    private val _isTorchOn = MutableStateFlow(false)
    val isTorchOn: StateFlow<Boolean> = _isTorchOn.asStateFlow()

    private val _captureCount = MutableStateFlow(0)
    val captureCount: StateFlow<Int> = _captureCount.asStateFlow()

    private val _currentFilter = MutableStateFlow(PhotoFilterEngine.Filter.ORIGINAL)
    val currentFilter: StateFlow<PhotoFilterEngine.Filter> = _currentFilter.asStateFlow()

    private val _exposureValue = MutableStateFlow(0)
    val exposureValue: StateFlow<Int> = _exposureValue.asStateFlow()

    private val _zoomLevel = MutableStateFlow(1f)
    val zoomLevel: StateFlow<Float> = _zoomLevel.asStateFlow()

    private val _showSceneSelector = MutableStateFlow(false)
    val showSceneSelector: StateFlow<Boolean> = _showSceneSelector.asStateFlow()

    private val _showFilterSelector = MutableStateFlow(false)
    val showFilterSelector: StateFlow<Boolean> = _showFilterSelector.asStateFlow()

    private val _showVlogTemplateSelector = MutableStateFlow(false)
    val showVlogTemplateSelector: StateFlow<Boolean> = _showVlogTemplateSelector.asStateFlow()

    private val vlogTemplates = listOf(
        VlogTemplate(
            name = "快速 Vlog",
            clips = listOf(
                VlogClip("看镜头微笑", "第1幕 · 开场问候", 3f),
                VlogClip("转个圈展示全身", "第2幕 · 全身展示", 3f),
                VlogClip("挥手告别", "第3幕 · 结尾", 2f)
            )
        ),
        VlogTemplate(
            name = "穿搭分享",
            clips = listOf(
                VlogClip("近距离展示上衣细节", "第1幕 · 上衣", 3f),
                VlogClip("拉远展示全身搭配", "第2幕 · 全身", 4f),
                VlogClip("展示鞋子和包包", "第3幕 · 配饰", 3f),
                VlogClip("比心结束", "第4幕 · 结尾", 2f)
            )
        ),
        VlogTemplate(
            name = "美食探店",
            clips = listOf(
                VlogClip("对着食物比个耶", "第1幕 · 美食登场", 3f),
                VlogClip("做一个开动的手势", "第2幕 · 开动啦", 3f),
                VlogClip("品尝后微笑点头", "第3幕 · 好吃", 3f),
                VlogClip("竖大拇指推荐", "第4幕 · 推荐", 2f)
            )
        ),
        VlogTemplate(
            name = "旅行记录",
            clips = listOf(
                VlogClip("站在景点前挥手", "第1幕 · 到达", 3f),
                VlogClip("展示周围风景", "第2幕 · 风景", 4f),
                VlogClip("开心地比心", "第3幕 · 心情", 2f),
                VlogClip("转身走向远方", "第4幕 · 继续前行", 4f)
            )
        )
    )

    fun getVlogTemplates(): List<VlogTemplate> = vlogTemplates

    private var autoCaptureJob: Job? = null
    private var vlogCaptureJob: Job? = null

    private var lastSceneDetectionTime = 0L
    private var lastPoseFrameTime = 0L
    private var lastSmileFrameTime = 0L

    private var frameWidth = 0
    private var frameHeight = 0

    private val photoOutputDir: File by lazy {
        File(app.filesDir, "photos").apply { mkdirs() }
    }

    private val videoOutputDir: File by lazy {
        File(app.filesDir, "videos").apply { mkdirs() }
    }

    // ====== 初始化 ======

    fun initCamera(lifecycleOwner: LifecycleOwner, previewView: PreviewView) {
        if (cameraManager != null) return
        cameraManager = CameraManager(app)
        poseDetector = PoseDetectorEngine()
        try {
            sceneClassifier = SceneClassifier(app)
        } catch (e: Exception) {
            Log.w(TAG, "Scene classifier not available")
        }
        smileDetector = SmileDetector()

        toneGenerator = try {
            ToneGenerator(AudioManager.STREAM_SYSTEM, 80)
        } catch (e: Exception) {
            null
        }

        tts = TextToSpeech(app) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.CHINA
            }
        }

        // 从 DataStore 加载初始偏好
        viewModelScope.launch {
            _smileEnabled.value = storeManager.smileEnabled.first()
            _watermarkEnabled.value = storeManager.watermarkEnabled.first()
            _gridEnabled.value = storeManager.gridEnabled.first()
            _lowLightMode.value = storeManager.lowLightEnabled.first()
            val sceneStr = storeManager.selectedScene.first()
            _currentScene.value = try {
                SceneType.valueOf(sceneStr)
            } catch (e: IllegalArgumentException) {
                SceneType.STREET
            }
            _currentPlanIndex.value = 0
        }

        // 持续监听偏好变化
        viewModelScope.launch {
            storeManager.smileEnabled.collect { _smileEnabled.value = it }
        }
        viewModelScope.launch {
            storeManager.watermarkEnabled.collect { _watermarkEnabled.value = it }
        }
        viewModelScope.launch {
            storeManager.gridEnabled.collect { _gridEnabled.value = it }
        }
        viewModelScope.launch {
            storeManager.lowLightEnabled.collect { _lowLightMode.value = it }
        }

        cameraManager?.startCamera(
            lifecycleOwner,
            previewView,
            analysisAnalyzer = createAnalyzer()
        )
    }

    // ====== 帧分析器 ======

    /**
     * 关键修复：先从 ImageProxy 提取所有需要的数据，然后再 close
     * 之前的 bug：launch 协程后立即 close，协程读到已关闭的 image
     */
    private fun createAnalyzer(): ImageAnalysis.Analyzer {
        return ImageAnalysis.Analyzer { imageProxy ->
            val now = System.currentTimeMillis()

            // 在当前帧线程上提取数据，避免协程读取已关闭的 imageProxy
            val mediaImage = imageProxy.image
            val rotationDegrees = imageProxy.imageInfo.rotationDegrees
            val width = imageProxy.width
            val height = imageProxy.height

            if (mediaImage != null) {
                // 姿势检测
                val poseInterval = if (_isHeatWarning.value) POSE_FRAME_INTERVAL_HOT else POSE_FRAME_INTERVAL_NORMAL
                if (now - lastPoseFrameTime >= poseInterval) {
                    lastPoseFrameTime = now
                    val plan = currentPlan
                    val detector = poseDetector
                    val isFront = cameraManager?.isFrontCamera?.value ?: false
                    frameWidth = width
                    frameHeight = height

                    if (detector != null) {
                        val inputImage = InputImage.fromMediaImage(mediaImage, rotationDegrees)
                        viewModelScope.launch {
                            try {
                                val pose: Pose = detector.detect(inputImage) ?: return@launch
                                val isValid = PoseUtils.isPoseValid(pose)

                                if (isValid) {
                                    val detectedMap = PoseUtils.poseToNormalizedMap(
                                        pose, width.toFloat(), height.toFloat(), isFront
                                    )
                                    _detectedPosePoints.value = detectedMap

                                    val skeletonLines = PoseUtils.getSkeletonLines(
                                        pose, width.toFloat(), height.toFloat(), isFront
                                    )
                                    _detectedPoseLines.value = skeletonLines
                                } else {
                                    _detectedPosePoints.value = emptyMap()
                                    _detectedPoseLines.value = emptyList()
                                }

                                if (plan != null && isValid) {
                                    val targetPoints = if (_useSecondaryPose.value && plan.secondaryPosePoints.isNotEmpty()) {
                                        plan.secondaryPosePoints
                                    } else {
                                        plan.posePoints
                                    }
                                    val score = PoseUtils.calculateSimilarity(
                                        pose, targetPoints,
                                        width.toFloat(), height.toFloat(), isFront
                                    )
                                    _poseScore.value = score
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Pose detection error", e)
                            }
                        }
                    }
                }

                // 微笑检测
                if (_smileEnabled.value && now - lastSmileFrameTime >= SMILE_FRAME_INTERVAL) {
                    lastSmileFrameTime = now
                    val smileDet = smileDetector
                    if (smileDet != null) {
                        val inputImage = InputImage.fromMediaImage(mediaImage, rotationDegrees)
                        viewModelScope.launch {
                            try {
                                val triggered = smileDet.process(inputImage)
                                _smileStrength.value = smileDet.currentSmileProbability
                                if (triggered && !_isVlogRecording.value && !_isVlogMerging.value) {
                                    takePhoto()
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Smile detection error", e)
                            }
                        }
                    }
                }

                // 场景检测（低频）
                if (now - lastSceneDetectionTime >= SCENE_DETECTION_INTERVAL) {
                    lastSceneDetectionTime = now
                    val classifier = sceneClassifier
                    if (classifier != null) {
                        val bitmap = imageProxyToBitmap(imageProxy)
                        if (bitmap != null) {
                            viewModelScope.launch(Dispatchers.IO) {
                                try {
                                    val scene = classifier.classify(bitmap)
                                    if (scene != SceneType.UNKNOWN && scene != _currentScene.value) {
                                        _currentScene.value = scene
                                        _currentPlanIndex.value = 0
                                        _poseScore.value = 0f
                                        storeManager.setSelectedScene(scene.name)
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "Scene detection error", e)
                                } finally {
                                    bitmap.recycle()
                                }
                            }
                        }
                    }
                }
            }

            // 暗光检测（轻量，不需要 ML Kit）
            checkLowLight(imageProxy)

            // 最后关闭 imageProxy
            imageProxy.close()
        }
    }

    /**
     * 安全的 YUV→Bitmap 转换
     * 处理 rowStride 可能大于 width 的情况
     */
    private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
        return try {
            val planes = imageProxy.planes
            val yBuffer = planes[0].buffer
            val uBuffer = planes[1].buffer
            val vBuffer = planes[2].buffer

            val yRowStride = planes[0].rowStride
            val uvRowStride = planes[1].rowStride
            val uvPixelStride = planes[1].pixelStride

            val width = imageProxy.width
            val height = imageProxy.height

            val nv21 = ByteArray(width * height * 3 / 2)

            // 拷贝 Y 平面
            var offset = 0
            for (row in 0 until height) {
                yBuffer.position(row * yRowStride)
                val length = if (row == height - 1) width else yRowStride.coerceAtMost(width)
                yBuffer.get(nv21, offset, width)
                offset += width
            }

            // 拷贝 VU 平面 (NV21 格式: VUVU...)
            for (row in 0 until height / 2) {
                val vStart = row * uvRowStride
                val uStart = row * uvRowStride
                for (col in 0 until width / 2) {
                    val vIdx = vStart + col * uvPixelStride
                    val uIdx = uStart + col * uvPixelStride
                    if (vIdx < vBuffer.capacity()) {
                        nv21[offset++] = vBuffer.get(vIdx)
                    }
                    if (uIdx < uBuffer.capacity()) {
                        nv21[offset++] = uBuffer.get(uIdx)
                    }
                }
            }

            val yuvImage = YuvImage(nv21, ImageFormat.NV21, width, height, null)
            val out = ByteArrayOutputStream()
            yuvImage.compressToJpeg(Rect(0, 0, width, height), 50, out)
            val imageBytes = out.toByteArray()
            BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
        } catch (e: Exception) {
            Log.e(TAG, "imageProxyToBitmap failed", e)
            null
        }
    }

    private fun checkLowLight(imageProxy: ImageProxy) {
        try {
            val buffer = imageProxy.planes[0].buffer
            val position = buffer.position()
            var sum = 0L
            var count = 0
            val sampleStep = 500
            val maxSamples = 2000

            while (buffer.hasRemaining() && count < maxSamples) {
                sum += (buffer.get().toInt() and 0xFF)
                for (i in 1 until sampleStep) {
                    if (buffer.hasRemaining()) buffer.get() else break
                }
                count++
            }
            buffer.position(position)

            val avg = if (count > 0) sum / count else 128
            val isLow = avg < LOW_LIGHT_THRESHOLD
            _isLowLightWarning.value = isLow

            if (isLow && _lowLightMode.value) {
                cameraManager?.setExposureCompensation(2)
            } else if (!isLow && cameraManager?.exposureIndex?.value != 0) {
                cameraManager?.setExposureCompensation(0)
            }
        } catch (e: Exception) {
            // buffer 读取可能失败，忽略
        }
    }

    // ====== 拍照 ======

    fun takePhoto() {
        val manager = cameraManager ?: return
        if (_isVlogRecording.value || _isVlogMerging.value) return

        playShutterSound()
        vibrateShutter()

        val photoFile = File(photoOutputDir, "${UUID.randomUUID()}.jpg")

        manager.takePhoto(photoFile) { success, path ->
            if (success && path != null) {
                viewModelScope.launch(Dispatchers.IO) {
                    try {
                        processAndSavePhoto(path)
                    } catch (e: Exception) {
                        Log.e(TAG, "Photo processing failed", e)
                    }
                }
            }
        }
    }

    private suspend fun processAndSavePhoto(path: String) {
        var bitmap: Bitmap? = BitmapFactory.decodeFile(path)
        if (bitmap == null) {
            Log.e(TAG, "Failed to decode photo: $path")
            return
        }

        try {
            // 暗光降噪
            if (_lowLightMode.value && _isLowLightWarning.value) {
                val denoised = PhotoFilterEngine.applyLowLightDenoise(bitmap!!)
                if (denoised !== bitmap) {
                    bitmap?.recycle()
                    bitmap = denoised
                }
            }

            // 应用滤镜
            val filter = _currentFilter.value
            if (filter != PhotoFilterEngine.Filter.ORIGINAL && bitmap != null) {
                val filtered = PhotoFilterEngine.applyFilter(bitmap!!, filter)
                if (filtered !== bitmap) {
                    bitmap?.recycle()
                    bitmap = filtered
                }
            }

            // 水印
            if (_watermarkEnabled.value && bitmap != null) {
                val watermarked = PhotoFilterEngine.addWatermark(bitmap!!)
                if (watermarked !== bitmap) {
                    bitmap?.recycle()
                    bitmap = watermarked
                }
            }

            // 智能裁切
            if (bitmap != null) {
                val cropped = PhotoFilterEngine.applySmartCrop(bitmap!!, 4f / 5f)
                // 保存裁切结果
                FileOutputStream(path).use { out ->
                    cropped.compress(Bitmap.CompressFormat.JPEG, 90, out)
                }
                if (cropped !== bitmap) {
                    cropped.recycle()
                }
            }

            // 记录到数据库
            val record = ShootingRecord(
                scene = _currentScene.value.name,
                poseName = currentPlan?.poseName ?: "",
                score = _poseScore.value,
                imagePath = path
            )
            app.database.shootingDao().insert(record)
            _captureCount.value += 1

            // 更新 UI 状态（切到主线程）
            withContext(Dispatchers.Main) {
                _lastCapturedPhotoPath.value = path
                _isReviewingPhoto.value = true
            }
        } finally {
            bitmap?.recycle()
        }
    }

    private fun playShutterSound() {
        viewModelScope.launch {
            try {
                toneGenerator?.startTone(ToneGenerator.TONE_CDMA_PIP, 100)
            } catch (_: Exception) {}
        }
    }

    private fun vibrateShutter() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = app.getSystemService(android.content.Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator.vibrate(
                    VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = app.getSystemService(android.content.Context.VIBRATOR_SERVICE) as Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(30)
                }
            }
        } catch (_: Exception) {}
    }

    // ====== 姿势/场景切换 ======

    fun nextPlan() {
        val plans = _currentScene.value.plans
        if (plans.isEmpty()) return
        _currentPlanIndex.value = (_currentPlanIndex.value + 1) % plans.size
        _poseScore.value = 0f
    }

    fun previousPlan() {
        val plans = _currentScene.value.plans
        if (plans.isEmpty()) return
        _currentPlanIndex.value = if (_currentPlanIndex.value > 0) {
            _currentPlanIndex.value - 1
        } else {
            plans.size - 1
        }
        _poseScore.value = 0f
    }

    fun switchCamera(lifecycleOwner: LifecycleOwner, previewView: PreviewView) {
        cameraManager?.switchCamera(lifecycleOwner, previewView)
        _poseScore.value = 0f
    }

    fun setScene(scene: SceneType) {
        _currentScene.value = scene
        _currentPlanIndex.value = 0
        _poseScore.value = 0f
        viewModelScope.launch {
            storeManager.setSelectedScene(scene.name)
        }
    }

    // ====== 自动抓拍（修复 collect 挂起 bug）======

    fun toggleAutoCapture() {
        if (_isAutoCapturing.value) {
            stopAutoCapture()
        } else {
            startAutoCapture()
        }
    }

    private fun startAutoCapture() {
        if (_isAutoCapturing.value) return
        _isAutoCapturing.value = true

        autoCaptureJob = viewModelScope.launch {
            while (_isAutoCapturing.value) {
                // 获取当前间隔（不使用 collect，用 first 只取一次值）
                val interval = storeManager.autoRecommendInterval.first().toLong()
                    .coerceIn(500L, 5000L)
                delay(interval)

                if (_poseScore.value >= AUTO_CAPTURE_SCORE_THRESHOLD
                    && !_isVlogRecording.value
                    && !_isVlogMerging.value
                ) {
                    takePhoto()
                    delay(interval)
                }
            }
        }
    }

    private fun stopAutoCapture() {
        _isAutoCapturing.value = false
        autoCaptureJob?.cancel()
        autoCaptureJob = null
    }

    // ====== 开关设置 ======

    fun toggleSmile(enabled: Boolean) {
        _smileEnabled.value = enabled
        if (!enabled) {
            _smileStrength.value = 0f
            smileDetector?.reset()
        }
        viewModelScope.launch { storeManager.setSmileEnabled(enabled) }
    }

    fun toggleWatermark(enabled: Boolean) {
        _watermarkEnabled.value = enabled
        viewModelScope.launch { storeManager.setWatermarkEnabled(enabled) }
    }

    fun toggleGrid(enabled: Boolean) {
        _gridEnabled.value = enabled
        viewModelScope.launch { storeManager.setGridEnabled(enabled) }
    }

    fun toggleLowLight(enabled: Boolean) {
        _lowLightMode.value = enabled
        viewModelScope.launch { storeManager.setLowLightEnabled(enabled) }
    }

    fun toggleTorch(): Boolean {
        val result = cameraManager?.toggleTorch() ?: false
        _isTorchOn.value = result
        return result
    }

    // ====== Vlog 导演系统 ======

    fun startVlog(template: VlogTemplate) {
        if (_isVlogRecording.value || _isVlogMerging.value) return
        _activeVlogTemplate.value = template
        _activeVlogClipIndex.value = 0
        cameraManager?.resetVideoChunks()
        executeVlogCapture(template)
    }

    private fun executeVlogCapture(vlog: VlogTemplate) {
        if (_isVlogRecording.value || _isVlogMerging.value) return
        val clips = vlog.clips
        if (clips.isEmpty()) {
            stopVlog()
            return
        }

        val currentIndex = _activeVlogClipIndex.value
        if (currentIndex >= clips.size) {
            mergeVlog(vlog)
            return
        }

        val clip = clips[currentIndex]
        val manager = cameraManager ?: return

        vlogCaptureJob = viewModelScope.launch {
            try {
                // 1. TTS 播报指令
                speak(clip.voiceCommand)
                _displayVlogText.value = clip.overlayText

                // 2. 等待用户反应
                delay(1500)

                // 3. 启动视频录制
                val started = manager.startVideoChunk()
                if (!started) {
                    _displayVlogText.value = "录制启动失败"
                    delay(1000)
                    _displayVlogText.value = ""
                    stopVlog()
                    return@launch
                }
                _isVlogRecording.value = true

                // 4. 录制指定时长
                delay((clip.duration * 1000).toLong())

                // 5. 停止当前分片录制
                manager.stopVideoChunk()
                // 等待 Finalize 回调完成
                delay(500)
                _isVlogRecording.value = false

                // 6. 推进下一幕或合成
                if (currentIndex + 1 < clips.size) {
                    _activeVlogClipIndex.value = currentIndex + 1
                    executeVlogCapture(vlog)
                } else {
                    mergeVlog(vlog)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Vlog 被取消，正常退出
                _isVlogRecording.value = false
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Vlog capture error", e)
                _isVlogRecording.value = false
                _displayVlogText.value = "录制异常: ${e.message}"
                delay(2000)
                stopVlog()
            }
        }
    }

    private fun mergeVlog(vlog: VlogTemplate) {
        _isVlogMerging.value = true
        _displayVlogText.value = "合成中..."

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val chunks = cameraManager?.videoChunks ?: emptyList()
                val bgmFile = vlog.bgmFilename?.let {
                    val f = File(app.filesDir, it)
                    if (f.exists()) f else null
                }

                if (chunks.isNotEmpty()) {
                    val merged = VideoMerger.merge(
                        videoFiles = chunks,
                        bgmFile = bgmFile,
                        outputDir = videoOutputDir
                    )
                    withContext(Dispatchers.Main) {
                        if (merged != null) {
                            _exportedVlogPath.value = merged.absolutePath
                            _isReviewingVlog.value = true
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Vlog merge failed", e)
            } finally {
                _isVlogMerging.value = false
                _displayVlogText.value = ""
            }
        }
    }

    fun stopVlog() {
        vlogCaptureJob?.cancel()
        vlogCaptureJob = null
        _isVlogRecording.value = false
        _isVlogMerging.value = false
        cameraManager?.stopVideoChunk()
        cameraManager?.resetVideoChunks()
        _displayVlogText.value = ""
        _activeVlogTemplate.value = null
        _activeVlogClipIndex.value = 0
    }

    fun closePhotoReview() {
        _isReviewingPhoto.value = false
        _lastCapturedPhotoPath.value = null
    }

    fun closeVlogReview() {
        _isReviewingVlog.value = false
        _exportedVlogPath.value = null
    }

    private fun speak(text: String) {
        try {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "poseai_${System.currentTimeMillis()}")
        } catch (e: Exception) {
            Log.e(TAG, "TTS error", e)
        }
    }

    // ====== 系统 状态 ======

    fun setHeatWarning(value: Boolean) {
        _isHeatWarning.value = value
    }

    fun setBatteryLow(value: Boolean) {
        _isBatteryLow.value = value
    }

    fun getCaptureHistory() = app.database.shootingDao().getAll()

    // ====== 滤镜控制 ======

    fun setFilter(filter: PhotoFilterEngine.Filter) {
        _currentFilter.value = filter
    }

    fun nextFilter() {
        val values = PhotoFilterEngine.Filter.values()
        val idx = values.indexOf(_currentFilter.value)
        _currentFilter.value = values[(idx + 1) % values.size]
    }

    fun previousFilter() {
        val values = PhotoFilterEngine.Filter.values()
        val idx = values.indexOf(_currentFilter.value)
        _currentFilter.value = values[(idx - 1 + values.size) % values.size]
    }

    // ====== 曝光补偿控制 ======

    fun setExposureCompensation(value: Int) {
        cameraManager?.setExposureCompensation(value)
        _exposureValue.value = value
    }

    fun increaseExposure() {
        val max = cameraManager?.getMaxExposure() ?: 10
        val current = _exposureValue.value
        if (current < max) {
            setExposureCompensation(current + 1)
        }
    }

    fun decreaseExposure() {
        val min = cameraManager?.getMinExposure() ?: -10
        val current = _exposureValue.value
        if (current > min) {
            setExposureCompensation(current - 1)
        }
    }

    // ====== 变焦控制 ======

    fun setZoom(ratio: Float) {
        cameraManager?.setZoomRatio(ratio)
        _zoomLevel.value = ratio
    }

    // ====== 辅助姿势 / 连拍 / 多角度 ======

    fun toggleSecondaryPose(): Boolean {
        val newValue = !_useSecondaryPose.value
        _useSecondaryPose.value = newValue
        _poseScore.value = 0f
        return newValue
    }

    fun nextSequenceStep() {
        val plan = currentPlan ?: return
        if (plan.sequence.isEmpty()) return
        _currentSequenceIndex.value = (_currentSequenceIndex.value + 1) % plan.sequence.size
    }

    fun previousSequenceStep() {
        val plan = currentPlan ?: return
        if (plan.sequence.isEmpty()) return
        _currentSequenceIndex.value = if (_currentSequenceIndex.value > 0) {
            _currentSequenceIndex.value - 1
        } else {
            plan.sequence.size - 1
        }
    }

    fun nextAngle() {
        val plan = currentPlan ?: return
        if (plan.multiAngles.isEmpty()) return
        _currentAngleIndex.value = (_currentAngleIndex.value + 1) % plan.multiAngles.size
    }

    fun getCurrentSequenceShot() = currentPlan?.sequence?.getOrNull(_currentSequenceIndex.value)
    fun getCurrentAngle() = currentPlan?.multiAngles?.getOrNull(_currentAngleIndex.value)

    fun startVlogFromPlan() {
        val plan = currentPlan ?: return
        val vlogScript = plan.vlogScript ?: return
        startVlog(vlogScript)
    }

    // ====== 屏幕补光 ======

    fun toggleScreenFillLight(): Boolean {
        val newValue = !_screenFillLightEnabled.value
        _screenFillLightEnabled.value = newValue
        return newValue
    }

    fun setScreenFillLightEnabled(enabled: Boolean) {
        _screenFillLightEnabled.value = enabled
    }

    fun setScreenFillLightIntensity(intensity: Float) {
        _screenFillLightIntensity.value = intensity.coerceIn(0.1f, 1f)
    }

    // ====== 选择器控制 ======

    fun toggleSceneSelector() {
        _showSceneSelector.value = !_showSceneSelector.value
    }

    fun toggleFilterSelector() {
        _showFilterSelector.value = !_showFilterSelector.value
    }

    fun toggleVlogTemplateSelector() {
        _showVlogTemplateSelector.value = !_showVlogTemplateSelector.value
    }

    fun dismissSelectors() {
        _showSceneSelector.value = false
        _showFilterSelector.value = false
        _showVlogTemplateSelector.value = false
    }

    // ====== 清理 ======

    override fun onCleared() {
        super.onCleared()
        stopAutoCapture()
        stopVlog()
        cameraManager?.shutdown()
        poseDetector?.close()
        sceneClassifier?.close()
        smileDetector?.close()
        try {
            toneGenerator?.release()
        } catch (_: Exception) {}
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (_: Exception) {}
    }
}
