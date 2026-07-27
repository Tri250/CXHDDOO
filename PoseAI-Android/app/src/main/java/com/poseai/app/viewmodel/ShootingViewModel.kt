package com.poseai.app.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.YuvImage
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.View
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
import com.poseai.app.engine.PoseSimilarityModel
import com.poseai.app.engine.PoseUtils
import com.poseai.app.engine.SceneClassifier
import com.poseai.app.engine.SmileDetector
import com.poseai.app.model.SceneType
import com.poseai.app.model.ShootingPlan
import com.poseai.app.model.CompositionRule
import com.poseai.app.model.VlogClip
import com.poseai.app.model.VlogTemplate
import com.poseai.app.store.StoreManager
import com.poseai.app.util.PhotoFilterEngine
import com.poseai.app.util.BeautyEngine
import com.poseai.app.util.ShareEngine
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
        // 关节坐标 EMA 平滑系数：新值权重，越大越灵敏但越抖动
        private const val POSE_EMA_ALPHA = 0.4f
        // 连拍模式：每次连拍张数和间隔
        // P2-1 规范：姿势匹配后自动连拍 3 张
        private const val BURST_COUNT = 3
        private const val BURST_INTERVAL_MS = 400L
        // 留白检测：头部（nose）Y 坐标阈值，大于此值表示头部过低、上方留白过多
        private const val HEADROOM_WARNING_THRESHOLD = 0.35f
        // 人脸 EV 联动：人脸区域平均亮度阈值
        private const val FACE_DARK_THRESHOLD = 80f
        private const val FACE_BRIGHT_THRESHOLD = 180f
        // Review Prompt 触发阈值
        private const val REVIEW_PROMPT_THRESHOLD_1 = 5
        private const val REVIEW_PROMPT_THRESHOLD_2 = 20
    }

    private val app = application as PoseAIApp
    private val storeManager: StoreManager = app.storeManager
    private val customPoseStore = app.customPoseStore

    init {
        // 加载已保存的自定义姿势
        _customPoses.value = customPoseStore.loadAll()
    }

    private var cameraManager: CameraManager? = null
    private var poseDetector: PoseDetectorEngine? = null
    private var sceneClassifier: SceneClassifier? = null
    private var smileDetector: SmileDetector? = null
    /**
     * 姿势相似度模型：激活 AIModelManager 注册的 pose_similarity.tflite
     * - 优先用 TFLite 模型推理（更准确）
     * - 模型不可用时自动降级到 PoseUtils.calculateSimilarity 的欧氏距离方案
     */
    private var poseSimilarityModel: PoseSimilarityModel? = null

    private var tts: TextToSpeech? = null
    private var toneGenerator: ToneGenerator? = null

    // ====== 状态流 ======

    private val _currentScene = MutableStateFlow(SceneType.STREET)
    val currentScene: StateFlow<SceneType> = _currentScene.asStateFlow()

    private val _currentPlanIndex = MutableStateFlow(0)
    val currentPlanIndex: StateFlow<Int> = _currentPlanIndex.asStateFlow()

    val currentPlan: ShootingPlan?
        get() = _customActivePlan ?: _currentScene.value.plans.getOrNull(_currentPlanIndex.value)

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

    /**
     * 闪光灯模式：0=关闭, 1=自动, 2=常亮
     * 持久化保存，启动时恢复（mode=2 时自动打开手电筒）
     */
    private val _currentFlashMode = MutableStateFlow(0)
    val currentFlashMode: StateFlow<Int> = _currentFlashMode.asStateFlow()

    private val _captureCount = MutableStateFlow(0)
    val captureCount: StateFlow<Int> = _captureCount.asStateFlow()

    private val _currentFilter = MutableStateFlow(PhotoFilterEngine.Filter.ORIGINAL)
    val currentFilter: StateFlow<PhotoFilterEngine.Filter> = _currentFilter.asStateFlow()

    /** 滤镜强度 0-100（默认 100 = 完全应用滤镜） */
    private val _filterIntensity = MutableStateFlow(100)
    val filterIntensity: StateFlow<Int> = _filterIntensity.asStateFlow()

    // ── 美颜参数 ──
    /** 磨皮强度 0-100 */
    private val _smoothingLevel = MutableStateFlow(0)
    val smoothingLevel: StateFlow<Int> = _smoothingLevel.asStateFlow()

    /** 美白强度 0-100 */
    private val _whiteningLevel = MutableStateFlow(0)
    val whiteningLevel: StateFlow<Int> = _whiteningLevel.asStateFlow()

    /** 瘦脸强度 0-100 */
    private val _slimmingLevel = MutableStateFlow(0)
    val slimmingLevel: StateFlow<Int> = _slimmingLevel.asStateFlow()

    /** 美颜总开关 */
    private val _beautyEnabled = MutableStateFlow(false)
    val beautyEnabled: StateFlow<Boolean> = _beautyEnabled.asStateFlow()

    /** 当前社交画幅预设（激活 AspectRatio 枚举和 applySmartCropByRatio 死代码） */
    private val _currentAspectRatio = MutableStateFlow(PhotoFilterEngine.AspectRatio.RATIO_4_5)
    val currentAspectRatio: StateFlow<PhotoFilterEngine.AspectRatio> = _currentAspectRatio.asStateFlow()

    private val _exposureValue = MutableStateFlow(0)
    val exposureValue: StateFlow<Int> = _exposureValue.asStateFlow()

    private val _zoomLevel = MutableStateFlow(1f)
    val zoomLevel: StateFlow<Float> = _zoomLevel.asStateFlow()

    // ── 画质设置 ──
    /** JPEG 压缩质量（50-100），默认 90 */
    private val _jpegQuality = MutableStateFlow(90)
    val jpegQuality: StateFlow<Int> = _jpegQuality.asStateFlow()

    /** 输出格式：0=JPEG, 1=WEBP */
    private val _outputFormat = MutableStateFlow(0)
    val outputFormat: StateFlow<Int> = _outputFormat.asStateFlow()

    /** HDR 开关（软件 HDR 色调映射，所有设备可用） */
    private val _hdrEnabled = MutableStateFlow(false)
    val hdrEnabled: StateFlow<Boolean> = _hdrEnabled.asStateFlow()

    private val _showSceneSelector = MutableStateFlow(false)
    val showSceneSelector: StateFlow<Boolean> = _showSceneSelector.asStateFlow()

    private val _showFilterSelector = MutableStateFlow(false)
    val showFilterSelector: StateFlow<Boolean> = _showFilterSelector.asStateFlow()

    private val _showVlogTemplateSelector = MutableStateFlow(false)
    val showVlogTemplateSelector: StateFlow<Boolean> = _showVlogTemplateSelector.asStateFlow()

    // ── 分享配置 ──
    private val _showShareSheet = MutableStateFlow(false)
    val showShareSheet: StateFlow<Boolean> = _showShareSheet.asStateFlow()

    /** 当前分享的照片路径（进入分享面板时设置） */
    private val _sharePhotoPath = MutableStateFlow<String?>(null)
    val sharePhotoPath: StateFlow<String?> = _sharePhotoPath.asStateFlow()

    /** 水印风格 */
    private val _watermarkStyle = MutableStateFlow(ShareEngine.WatermarkStyle.SIGNATURE)
    val watermarkStyle: StateFlow<ShareEngine.WatermarkStyle> = _watermarkStyle.asStateFlow()

    /** 水印位置 */
    private val _watermarkPosition = MutableStateFlow(ShareEngine.WatermarkPosition.BOTTOM_LEFT)
    val watermarkPosition: StateFlow<ShareEngine.WatermarkPosition> = _watermarkPosition.asStateFlow()

    /** 用户名（水印用） */
    private val _shareUsername = MutableStateFlow("")
    val shareUsername: StateFlow<String> = _shareUsername.asStateFlow()

    /** 地点（水印用） */
    private val _shareLocation = MutableStateFlow("")
    val shareLocation: StateFlow<String> = _shareLocation.asStateFlow()

    /** 话题列表 */
    private val _shareTopics = MutableStateFlow<List<String>>(emptyList())
    val shareTopics: StateFlow<List<String>> = _shareTopics.asStateFlow()

    /** 分享文案 */
    private val _shareCaption = MutableStateFlow("")
    val shareCaption: StateFlow<String> = _shareCaption.asStateFlow()

    // ── 自定义姿势 ──
    private val _customPoses = MutableStateFlow<List<com.poseai.app.store.CustomPose>>(emptyList())
    val customPoses: StateFlow<List<com.poseai.app.store.CustomPose>> = _customPoses.asStateFlow()

    private val _showCustomPoseSheet = MutableStateFlow(false)
    val showCustomPoseSheet: StateFlow<Boolean> = _showCustomPoseSheet.asStateFlow()

    /** 是否正在预览自定义姿势（用于切换提示文案） */
    private val _activeCustomPoseId = MutableStateFlow<String?>(null)
    val activeCustomPoseId: StateFlow<String?> = _activeCustomPoseId.asStateFlow()

    // ====== 倒计时 / 闪光 / 沉浸 / 俯拍警告 / Vlog 失败兜底 ======

    /** 倒计时秒数：0=关闭，3/5/10 为可选档位 */
    private val _timerSeconds = MutableStateFlow(0)
    val timerSeconds: StateFlow<Int> = _timerSeconds.asStateFlow()

    /** 当前倒计时剩余秒数（>0 时显示大数字） */
    private val _countdownValue = MutableStateFlow(0)
    val countdownValue: StateFlow<Int> = _countdownValue.asStateFlow()

    /** 快门闪光覆盖（拍照瞬间短暂显示暖白闪光） */
    private val _showShutterFlash = MutableStateFlow(false)
    val showShutterFlash: StateFlow<Boolean> = _showShutterFlash.asStateFlow()

    /** 沉浸模式：单击全屏切换，隐藏顶栏/方案/提示，仅保留剪影和快门 */
    private val _isImmersiveMode = MutableStateFlow(false)
    val isImmersiveMode: StateFlow<Boolean> = _isImmersiveMode.asStateFlow()

    /** 设备俯仰角（弧度），由加速度计+磁力计推算 */
    private val _devicePitch = MutableStateFlow(0f)
    val devicePitch: StateFlow<Float> = _devicePitch.asStateFlow()

    /** 俯拍警告：pitch < -0.35 rad (约 -20°) 视为俯拍 */
    private val _isTopDownWarning = MutableStateFlow(false)
    val isTopDownWarning: StateFlow<Boolean> = _isTopDownWarning.asStateFlow()

    /** Vlog 失败兜底文案（非空时显示提示） */
    private val _vlogErrorMessage = MutableStateFlow<String?>(null)
    val vlogErrorMessage: StateFlow<String?> = _vlogErrorMessage.asStateFlow()

    /** 距离提示文案（未对齐时由 UI 渲染） */
    private val _distanceHint = MutableStateFlow<String?>(null)
    val distanceHint: StateFlow<String?> = _distanceHint.asStateFlow()

    /** 拍照保存失败提示（非空时显示 Snackbar） */
    private val _photoSaveError = MutableStateFlow<String?>(null)
    val photoSaveError: StateFlow<String?> = _photoSaveError.asStateFlow()

    /** 点击对焦视觉反馈：对焦框位置（屏幕像素坐标），null 表示不显示 */
    data class FocusPoint(val x: Float, val y: Float)
    private val _focusIndicator = MutableStateFlow<FocusPoint?>(null)
    val focusIndicator: StateFlow<FocusPoint?> = _focusIndicator.asStateFlow()
    private var focusResetJob: Job? = null

    // ====== EMA 关节平滑缓存 ======
    private var smoothedPosePoints: Map<String, PointF> = emptyMap()

    // ====== 连拍模式 ======
    private val _isBurstMode = MutableStateFlow(false)
    val isBurstMode: StateFlow<Boolean> = _isBurstMode.asStateFlow()
    private var burstJob: Job? = null
    private val _burstPhotos = MutableStateFlow<List<String>>(emptyList())
    val burstPhotos: StateFlow<List<String>> = _burstPhotos.asStateFlow()

    // ====== 留白智能提醒 ======
    private val _isHeadroomWarning = MutableStateFlow(false)
    val isHeadroomWarning: StateFlow<Boolean> = _isHeadroomWarning.asStateFlow()

    // ====== 姿势亲近度自动推荐 ======
    private val _autoRecommendEnabled = MutableStateFlow(true)
    val autoRecommendEnabled: StateFlow<Boolean> = _autoRecommendEnabled.asStateFlow()
    private val _recommendedPlanIndex = MutableStateFlow(-1)
    val recommendedPlanIndex: StateFlow<Int> = _recommendedPlanIndex.asStateFlow()

    /** 自动抓拍/推荐检测间隔（毫秒，500-5000）激活 setAutoRecommendInterval 持久化 */
    private val _autoRecommendInterval = MutableStateFlow(1500)
    val autoRecommendInterval: StateFlow<Int> = _autoRecommendInterval.asStateFlow()

    // ====== Review Prompt ======
    private val _shouldShowReviewPrompt = MutableStateFlow(false)
    val shouldShowReviewPrompt: StateFlow<Boolean> = _shouldShowReviewPrompt.asStateFlow()
    private var reviewPromptShownThreshold = 0

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
    private var countdownJob: Job? = null
    private var flashResetJob: Job? = null
    private var distanceHintResetJob: Job? = null

    private var lastSceneDetectionTime = 0L
    private var lastPoseFrameTime = 0L
    private var lastSmileFrameTime = 0L

    private var frameWidth = 0
    private var frameHeight = 0

    // ====== 陀螺仪 / 俯仰角检测 ======
    private val sensorManager: SensorManager? by lazy {
        app.getSystemService(android.content.Context.SENSOR_SERVICE) as? SensorManager
    }
    private val accelerometer: Sensor? by lazy {
        sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    }
    private val magnetometer: Sensor? by lazy {
        sensorManager?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
    }
    private val gravityValues = FloatArray(3)
    private val geomagneticValues = FloatArray(3)
    private val rotationMatrix = FloatArray(9)
    private val orientationValues = FloatArray(3)
    private var sensorRegistered = false

    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            when (event.sensor.type) {
                Sensor.TYPE_ACCELEROMETER -> {
                    System.arraycopy(event.values, 0, gravityValues, 0, 3)
                    computePitch()
                }
                Sensor.TYPE_MAGNETIC_FIELD -> {
                    System.arraycopy(event.values, 0, geomagneticValues, 0, 3)
                    computePitch()
                }
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    private fun computePitch() {
        if (gravityValues.all { it == 0f } || geomagneticValues.all { it == 0f }) return
        if (SensorManager.getRotationMatrix(rotationMatrix, null, gravityValues, geomagneticValues)) {
            SensorManager.getOrientation(rotationMatrix, orientationValues)
            // orientationValues[1] = pitch，单位弧度；负值表示设备向前倾（俯拍）
            val pitch = orientationValues[1]
            _devicePitch.value = pitch
            _isTopDownWarning.value = pitch < -0.35f
        }
    }

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
        // 激活 pose_similarity 模型：从 AIModelManager 下载目录或 assets 加载
        try {
            poseSimilarityModel = PoseSimilarityModel(app)
            Log.i(TAG, "PoseSimilarityModel loaded=${poseSimilarityModel?.isModelLoaded() == true}")
        } catch (e: Exception) {
            Log.w(TAG, "PoseSimilarityModel init failed, will use heuristic", e)
        }

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
            _timerSeconds.value = storeManager.timerSeconds.first()
            val sceneStr = storeManager.selectedScene.first()
            _currentScene.value = try {
                SceneType.valueOf(sceneStr)
            } catch (e: IllegalArgumentException) {
                SceneType.STREET
            }
            _currentPlanIndex.value = 0
            // 激活 smileThreshold 持久化：从 StoreManager 读取并应用到 SmileDetector
            val savedThreshold = storeManager.smileThreshold.first()
            smileDetector?.triggerThreshold = savedThreshold
            // 激活 autoRecommendEnabled 持久化
            _autoRecommendEnabled.value = storeManager.autoRecommendEnabled.first()
            // 激活 flashMode 持久化：恢复用户上次选择的闪光灯模式
            // 0=关闭, 1=自动, 2=常亮；模式 2 时打开手电筒
            val savedFlashMode = storeManager.flashMode.first()
            _currentFlashMode.value = savedFlashMode
            if (savedFlashMode == 2) {
                // 等相机就绪后打开手电筒（异步，避免阻塞初始化）
                viewModelScope.launch {
                    kotlinx.coroutines.delay(500)
                    val result = cameraManager?.toggleTorch() ?: false
                    _isTorchOn.value = result
                }
            }
            // 激活 autoRecommendInterval 持久化：恢复自动抓拍间隔
            val savedInterval = storeManager.autoRecommendInterval.first()
            _autoRecommendInterval.value = savedInterval
            // 激活画质设置持久化：JPEG 质量 / 输出格式 / HDR
            _jpegQuality.value = storeManager.jpegQuality.first()
            _outputFormat.value = storeManager.outputFormat.first()
            _hdrEnabled.value = storeManager.hdrEnabled.first()
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
        viewModelScope.launch {
            storeManager.timerSeconds.collect { _timerSeconds.value = it }
        }
        // 持续监听 smileThreshold 变化并应用到 SmileDetector
        viewModelScope.launch {
            storeManager.smileThreshold.collect { threshold ->
                smileDetector?.triggerThreshold = threshold
            }
        }
        // 持续监听 autoRecommendEnabled
        viewModelScope.launch {
            storeManager.autoRecommendEnabled.collect { _autoRecommendEnabled.value = it }
        }
        // 持续监听画质设置变化（多进程/其他页面修改时同步）
        viewModelScope.launch {
            storeManager.jpegQuality.collect { _jpegQuality.value = it }
        }
        viewModelScope.launch {
            storeManager.outputFormat.collect { _outputFormat.value = it }
        }
        viewModelScope.launch {
            storeManager.hdrEnabled.collect { _hdrEnabled.value = it }
        }

        // 激活 cameraLens 持久化：根据上次保存的镜头选择启动相机
        // 0=后置 (LENS_FACING_BACK)，1=前置 (LENS_FACING_FRONT)
        viewModelScope.launch {
            val savedLens = storeManager.cameraLens.first()
            val lensFacing = if (savedLens == 1) {
                androidx.camera.core.CameraSelector.LENS_FACING_FRONT
            } else {
                androidx.camera.core.CameraSelector.LENS_FACING_BACK
            }
            cameraManager?.startCamera(
                lifecycleOwner,
                previewView,
                lensFacing = lensFacing,
                analysisAnalyzer = createAnalyzer()
            )
            // 注册陀螺仪监听（用于俯拍警告）
            registerSensorListener()
        }
    }

    /**
     * 注册加速度计 + 磁力计监听，用于推算设备俯仰角
     */
    fun registerSensorListener() {
        if (sensorRegistered) return
        val sm = sensorManager ?: return
        val acc = accelerometer ?: return
        val mag = magnetometer ?: return
        try {
            sm.registerListener(sensorListener, acc, SensorManager.SENSOR_DELAY_UI)
            sm.registerListener(sensorListener, mag, SensorManager.SENSOR_DELAY_UI)
            sensorRegistered = true
        } catch (e: Exception) {
            Log.w(TAG, "Sensor registration failed", e)
        }
    }

    /**
     * 注销传感器监听
     */
    fun unregisterSensorListener() {
        if (!sensorRegistered) return
        try {
            sensorManager?.unregisterListener(sensorListener)
        } catch (_: Exception) {}
        sensorRegistered = false
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
                                    val rawMap = PoseUtils.poseToNormalizedMap(
                                        pose, width.toFloat(), height.toFloat(), isFront
                                    )
                                    // EMA 平滑：对新检测到的关节坐标做指数移动平均，消除抖动
                                    val smoothedMap = if (smoothedPosePoints.isEmpty()) {
                                        rawMap
                                    } else {
                                        rawMap.mapValues { (key, newPoint) ->
                                            val prev = smoothedPosePoints[key]
                                            if (prev != null) {
                                                PointF(
                                                    prev.x * (1 - POSE_EMA_ALPHA) + newPoint.x * POSE_EMA_ALPHA,
                                                    prev.y * (1 - POSE_EMA_ALPHA) + newPoint.y * POSE_EMA_ALPHA
                                                )
                                            } else {
                                                newPoint
                                            }
                                        }
                                    }
                                    smoothedPosePoints = smoothedMap
                                    _detectedPosePoints.value = smoothedMap

                                    val skeletonLines = PoseUtils.getSkeletonLines(
                                        pose, width.toFloat(), height.toFloat(), isFront
                                    )
                                    _detectedPoseLines.value = skeletonLines

                                    // 留白智能提醒：检测头部上方是否有足够留白
                                    val nosePoint = smoothedMap["nose"]
                                    if (nosePoint != null) {
                                        // nose.y 过大表示头部在画面偏下方，上方留白过多
                                        _isHeadroomWarning.value = nosePoint.y > HEADROOM_WARNING_THRESHOLD
                                    } else {
                                        _isHeadroomWarning.value = false
                                    }
                                } else {
                                    smoothedPosePoints = emptyMap()
                                    _detectedPosePoints.value = emptyMap()
                                    _detectedPoseLines.value = emptyList()
                                    _isHeadroomWarning.value = false
                                }

                                if (plan != null && isValid) {
                                    val targetPoints = if (_useSecondaryPose.value && plan.secondaryPosePoints.isNotEmpty()) {
                                        plan.secondaryPosePoints
                                    } else {
                                        plan.posePoints
                                    }
                                    // 优先使用 PoseSimilarityModel（激活 pose_similarity 模型）
                                    // 失败或模型未加载时降级到 PoseUtils.calculateSimilarity
                                    val simModel = poseSimilarityModel
                                    val score = if (simModel != null) {
                                        simModel.computeSimilarity(smoothedPosePoints, targetPoints)
                                    } else {
                                        PoseUtils.calculateSimilarity(
                                            pose, targetPoints,
                                            width.toFloat(), height.toFloat(), isFront
                                        )
                                    }
                                    _poseScore.value = score

                                    // 姿势亲近度自动推荐：计算所有方案的相似度，推荐最高分方案
                                    if (_autoRecommendEnabled.value) {
                                        val plans = _currentScene.value.plans
                                        if (plans.size > 1) {
                                            var bestIdx = _currentPlanIndex.value
                                            var bestScore = score
                                            plans.forEachIndexed { idx, p ->
                                                if (idx != _currentPlanIndex.value) {
                                                    val tp = p.posePoints
                                                    val s = if (simModel != null) {
                                                        simModel.computeSimilarity(smoothedPosePoints, tp)
                                                    } else {
                                                        PoseUtils.calculateSimilarity(
                                                            pose, tp,
                                                            width.toFloat(), height.toFloat(), isFront
                                                        )
                                                    }
                                                    if (s > bestScore + 10f) {
                                                        bestScore = s
                                                        bestIdx = idx
                                                    }
                                                }
                                            }
                                            _recommendedPlanIndex.value = bestIdx
                                        }
                                    }
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

            // 人脸 EV 联动：基于检测到的 nose 位置采样人脸区域亮度
            val nosePoint = smoothedPosePoints["nose"]
            if (nosePoint != null) {
                val faceBrightness = sampleFaceRegionBrightness(buffer, imageProxy.width, imageProxy.height, nosePoint.x, nosePoint.y)
                buffer.position(position)
                if (faceBrightness != null) {
                    adjustExposureForFace(faceBrightness)
                }
            }
        } catch (e: Exception) {
            // buffer 读取可能失败，忽略
        }
    }

    /**
     * 采样人脸区域的 Y 通道平均亮度
     * @param buffer YUV Y 平面 buffer
     * @param width 帧宽度
     * @param height 帧高度
     * @param noseX 归一化 nose X 坐标 [0,1]
     * @param noseY 归一化 nose Y 坐标 [0,1]
     * @return 人脸区域平均亮度 (0-255)，或 null 表示采样失败
     */
    private fun sampleFaceRegionBrightness(buffer: java.nio.ByteBuffer, width: Int, height: Int, noseX: Float, noseY: Float): Float? {
        return try {
            // 人脸区域：以 nose 为中心，上下左右各扩展一定范围
            val faceRadiusX = (width * 0.12f).toInt()
            val faceRadiusY = (height * 0.08f).toInt()
            val centerX = (noseX * width).toInt().coerceIn(faceRadiusX, width - faceRadiusX)
            val centerY = (noseY * height).toInt().coerceIn(faceRadiusY, height - faceRadiusY)

            var sum = 0L
            var count = 0
            val rowStride = width
            // 采样 5x5 网格
            for (dy in -faceRadiusY..faceRadiusY step (faceRadiusY / 2).coerceAtLeast(1)) {
                for (dx in -faceRadiusX..faceRadiusX step (faceRadiusX / 2).coerceAtLeast(1)) {
                    val px = centerX + dx
                    val py = centerY + dy
                    if (px in 0 until width && py in 0 until height) {
                        val idx = py * rowStride + px
                        if (idx < buffer.capacity()) {
                            sum += (buffer.get(idx).toInt() and 0xFF)
                            count++
                        }
                    }
                }
            }
            if (count > 0) sum.toFloat() / count else null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 基于人脸亮度自动调整 EV 曝光补偿
     * - 人脸过暗（< FACE_DARK_THRESHOLD）：EV+1~2
     * - 人脸过亮（> FACE_BRIGHT_THRESHOLD）：EV-1
     * - 正常：EV 归零（如果当前非暗光模式）
     */
    private fun adjustExposureForFace(faceBrightness: Float) {
        // 如果暗光模式已介入，不覆盖其 EV 设置
        if (_isLowLightWarning.value && _lowLightMode.value) return

        val currentEV = cameraManager?.exposureIndex?.value ?: 0
        when {
            faceBrightness < FACE_DARK_THRESHOLD -> {
                if (currentEV < 2) {
                    cameraManager?.setExposureCompensation((currentEV + 1).coerceAtMost(2))
                }
            }
            faceBrightness > FACE_BRIGHT_THRESHOLD -> {
                if (currentEV > -1) {
                    cameraManager?.setExposureCompensation((currentEV - 1).coerceAtLeast(-1))
                }
            }
            else -> {
                // 人脸亮度正常，如果 EV 不为 0 且没有手动设置，归零
                if (currentEV != 0 && !_isLowLightWarning.value) {
                    cameraManager?.setExposureCompensation(0)
                }
            }
        }
    }

    // ====== 拍照 ======

    /**
     * 拍照入口：若 timerSeconds > 0 则启动倒计时，倒计时结束触发实际拍照；
     * 否则直接拍照。倒计时进行中再次点击会取消并重排。
     */
    fun takePhoto() {
        if (_isVlogRecording.value || _isVlogMerging.value) return
        // 连拍模式：直接执行连拍
        if (_isBurstMode.value) {
            executeBurstCapture()
            return
        }
        if (_countdownValue.value > 0) {
            // 倒计时进行中：取消并立即触发
            cancelCountdown()
            executeCapture()
            return
        }
        val seconds = _timerSeconds.value
        if (seconds > 0) {
            startCountdown(seconds)
        } else {
            executeCapture()
        }
    }

    /**
     * 启动倒计时：每秒更新 countdownValue，到 0 时触发拍照
     */
    private fun startCountdown(seconds: Int) {
        countdownJob?.cancel()
        _countdownValue.value = seconds
        countdownJob = viewModelScope.launch {
            try {
                for (i in seconds downTo 1) {
                    _countdownValue.value = i
                    // 倒计时短促提示音
                    playCountdownTick()
                    kotlinx.coroutines.delay(1000)
                }
                _countdownValue.value = 0
                executeCapture()
            } catch (e: kotlinx.coroutines.CancellationException) {
                _countdownValue.value = 0
                throw e
            }
        }
    }

    /**
     * 取消倒计时
     */
    fun cancelCountdown() {
        countdownJob?.cancel()
        countdownJob = null
        _countdownValue.value = 0
    }

    /**
     * 实际执行拍照：闪光 + 声音 + 震动 + 保存
     */
    private fun executeCapture() {
        val manager = cameraManager ?: return
        if (_isVlogRecording.value || _isVlogMerging.value) return

        // 快门闪光反馈
        triggerShutterFlash()
        playShutterSound()
        vibrateShutter()

        val photoFile = File(photoOutputDir, "${UUID.randomUUID()}.jpg")

        manager.takePhoto(photoFile) { success, path ->
            if (success && path != null) {
                viewModelScope.launch(Dispatchers.IO) {
                    try {
                        processAndSavePhoto(path)
                        // 保存成功：成功触觉反馈
                        vibrateSuccess()
                        // Review Prompt 计数
                        checkReviewPromptTrigger()
                    } catch (e: Exception) {
                        Log.e(TAG, "Photo processing failed", e)
                        _photoSaveError.value = "照片保存失败：${e.message ?: "未知错误"}"
                    }
                }
            } else {
                _photoSaveError.value = "拍照失败：${path ?: "相机未就绪"}"
            }
        }
    }

    // ====== 连拍模式 ======

    /**
     * 切换连拍模式开关
     */
    fun toggleBurstMode(): Boolean {
        _isBurstMode.value = !_isBurstMode.value
        if (!_isBurstMode.value) {
            stopBurst()
        }
        return _isBurstMode.value
    }

    /**
     * 执行连拍：快速连续拍摄 BURST_COUNT 张照片
     */
    fun executeBurstCapture() {
        if (_isVlogRecording.value || _isVlogMerging.value) return
        if (burstJob?.isActive == true) return

        _burstPhotos.value = emptyList()
        burstJob = viewModelScope.launch {
            val capturedPaths = mutableListOf<String>()
            for (i in 0 until BURST_COUNT) {
                val manager = cameraManager ?: break
                val photoFile = File(photoOutputDir, "burst_${UUID.randomUUID()}.jpg")

                // 连拍只拍照不弹预览，避免打断节奏
                val success = kotlinx.coroutines.suspendCancellableCoroutine<Boolean> { cont ->
                    manager.takePhoto(photoFile) { ok, path ->
                        if (cont.isActive) cont.resumeWith(Result.success(ok))
                    }
                }

                if (success) {
                    val originalPath = photoFile.absolutePath
                    capturedPaths.add(originalPath)
                    // 后台处理保存：使用返回的最终路径替换原始路径（WEBP 时会变更）
                    viewModelScope.launch(Dispatchers.IO) {
                        try {
                            val finalPath = processAndSavePhoto(originalPath)
                            if (finalPath != null && finalPath != originalPath) {
                                // WEBP 格式转换：更新 capturedPaths 中的路径
                                val idx = capturedPaths.indexOf(originalPath)
                                if (idx >= 0) capturedPaths[idx] = finalPath
                                _burstPhotos.value = capturedPaths.toList()
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Burst photo processing failed", e)
                        }
                    }
                }

                if (i < BURST_COUNT - 1) {
                    delay(BURST_INTERVAL_MS)
                }
            }

            // 连拍完成反馈
            vibrateSuccess()
            _burstPhotos.value = capturedPaths
        }
    }

    /**
     * 停止连拍
     */
    fun stopBurst() {
        burstJob?.cancel()
        burstJob = null
    }

    /**
     * 清空连拍结果列表（UI 关闭连拍横幅时调用）
     */
    fun clearBurstPhotos() {
        _burstPhotos.value = emptyList()
    }

    /**
     * Review Prompt 触发检查：在特定拍照次数时提示评价
     */
    private fun checkReviewPromptTrigger() {
        val count = _captureCount.value
        if (count >= REVIEW_PROMPT_THRESHOLD_2 && reviewPromptShownThreshold < REVIEW_PROMPT_THRESHOLD_2) {
            _shouldShowReviewPrompt.value = true
            reviewPromptShownThreshold = REVIEW_PROMPT_THRESHOLD_2
        } else if (count >= REVIEW_PROMPT_THRESHOLD_1 && reviewPromptShownThreshold < REVIEW_PROMPT_THRESHOLD_1) {
            _shouldShowReviewPrompt.value = true
            reviewPromptShownThreshold = REVIEW_PROMPT_THRESHOLD_1
        }
    }

    /** 用户已看到 Review Prompt，不再重复提示 */
    fun dismissReviewPrompt() {
        _shouldShowReviewPrompt.value = false
    }

    /** 切换自动推荐开关 */
    fun setAutoRecommendEnabled(enabled: Boolean) {
        _autoRecommendEnabled.value = enabled
        if (!enabled) {
            _recommendedPlanIndex.value = -1
        }
        viewModelScope.launch { storeManager.setAutoRecommendEnabled(enabled) }
    }

    /**
     * 设置自动抓拍/推荐检测间隔（激活 StoreManager.setAutoRecommendInterval 死代码）
     * @param intervalMs 毫秒数，会被夹到 500..5000
     */
    fun setAutoRecommendInterval(intervalMs: Int) {
        val clamped = intervalMs.coerceIn(500, 5000)
        _autoRecommendInterval.value = clamped
        viewModelScope.launch { storeManager.setAutoRecommendInterval(clamped) }
    }

    /**
     * 设置微笑触发阈值（激活 StoreManager.smileThreshold 持久化）
     * @param threshold 0.3-0.95，越大越不灵敏
     */
    fun setSmileThreshold(threshold: Float) {
        smileDetector?.triggerThreshold = threshold
        viewModelScope.launch { storeManager.setSmileThreshold(threshold) }
    }

    /**
     * 获取当前微笑阈值（供设置页 UI 显示）
     */
    fun getCurrentSmileThreshold(): Float = smileDetector?.triggerThreshold ?: 0.7f

    /**
     * 设置闪光灯模式（激活 StoreManager.flashMode 持久化）
     * @param mode 0=关闭, 1=自动, 2=常亮
     */
    fun setFlashMode(mode: Int) {
        _currentFlashMode.value = mode
        viewModelScope.launch { storeManager.setFlashMode(mode) }
        // mode=2 常亮 → 打开手电筒；其他 → 关闭
        when (mode) {
            2 -> {
                if (!_isTorchOn.value) toggleTorch()
            }
            else -> {
                if (_isTorchOn.value) toggleTorch()
            }
        }
    }

    /**
     * 循环切换闪光灯模式：0 关闭 → 1 自动 → 2 常亮 → 0 关闭
     * 激活 setFlashMode 在 UI 中的使用，与 iOS FlashMode 枚举对齐
     */
    fun cycleFlashMode() {
        val next = (_currentFlashMode.value + 1) % 3
        setFlashMode(next)
    }

    /**
     * 接受推荐方案：切换到推荐的 plan
     */
    fun acceptRecommendedPlan() {
        val rec = _recommendedPlanIndex.value
        if (rec >= 0 && rec != _currentPlanIndex.value) {
            selectPlan(rec)
            _recommendedPlanIndex.value = -1
        }
    }

    /**
     * 触发快门闪光：短暂显示暖白全屏覆盖（约 180ms）
     */
    private fun triggerShutterFlash() {
        _showShutterFlash.value = true
        flashResetJob?.cancel()
        flashResetJob = viewModelScope.launch {
            kotlinx.coroutines.delay(180)
            _showShutterFlash.value = false
        }
    }

    /**
     * 拍照成功反馈：双段震动（等效 iOS UINotificationFeedbackGenerator.success）
     */
    private fun vibrateSuccess() {
        try {
            val pattern = longArrayOf(0, 40, 60, 80)
            val amplitudes = intArrayOf(0, 180, 0, 220)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    (app.getSystemService(android.content.Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
                } else {
                    @Suppress("DEPRECATION")
                    app.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? Vibrator
                }
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, amplitudes, -1))
            } else {
                @Suppress("DEPRECATION")
                val vibrator = app.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? Vibrator
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, -1)
            }
        } catch (_: Exception) {}
    }

    private fun playCountdownTick() {
        viewModelScope.launch {
            try {
                toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 80)
            } catch (_: Exception) {}
        }
    }

    /** 清除拍照错误提示 */
    fun clearPhotoSaveError() {
        _photoSaveError.value = null
    }

    private suspend fun processAndSavePhoto(path: String): String? {
        var bitmap: Bitmap? = BitmapFactory.decodeFile(path)
        if (bitmap == null) {
            Log.e(TAG, "Failed to decode photo: $path")
            return null
        }

        // 最终保存路径（WEBP 格式时需要更换扩展名）
        var savedPath = path

        try {
            // 暗光降噪
            if (_lowLightMode.value && _isLowLightWarning.value) {
                val denoised = PhotoFilterEngine.applyLowLightDenoise(bitmap!!)
                if (denoised !== bitmap) {
                    bitmap?.recycle()
                    bitmap = denoised
                }
            }

            // HDR 色调映射（画质设置：暗部提亮 + 高光压缩）
            if (_hdrEnabled.value && bitmap != null) {
                val hdr = PhotoFilterEngine.applyHdrToneMapping(bitmap!!)
                if (hdr !== bitmap) {
                    bitmap?.recycle()
                    bitmap = hdr
                }
            }

            // 美颜处理（磨皮 → 美白 → 瘦脸）
            if (_beautyEnabled.value && bitmap != null) {
                val smoothed = if (_smoothingLevel.value > 0) {
                    BeautyEngine.applySmoothing(bitmap!!, _smoothingLevel.value)
                } else bitmap!!
                if (smoothed !== bitmap) {
                    bitmap?.recycle()
                    bitmap = smoothed
                }

                val whitened = if (_whiteningLevel.value > 0) {
                    BeautyEngine.applyWhitening(bitmap!!, _whiteningLevel.value)
                } else bitmap!!
                if (whitened !== bitmap) {
                    bitmap?.recycle()
                    bitmap = whitened
                }

                // 瘦脸需要人脸关键点，暂用默认估算（脸中心 = 图片中上区域）
                if (_slimmingLevel.value > 0 && bitmap != null) {
                    val w = bitmap!!.width
                    val h = bitmap!!.height
                    val landmarks = BeautyEngine.FaceLandmarks(
                        leftCheek = android.graphics.PointF(w * 0.35f, h * 0.4f),
                        rightCheek = android.graphics.PointF(w * 0.65f, h * 0.4f),
                        faceWidth = w * 0.5f,
                        faceCenter = android.graphics.PointF(w * 0.5f, h * 0.35f)
                    )
                    val slimmed = BeautyEngine.applyFaceSlimming(bitmap!!, _slimmingLevel.value, landmarks)
                    if (slimmed !== bitmap) {
                        bitmap?.recycle()
                        bitmap = slimmed
                    }
                }
            }

            // 应用滤镜（含强度调节）
            val filter = _currentFilter.value
            if (filter != PhotoFilterEngine.Filter.ORIGINAL && bitmap != null) {
                val filtered = PhotoFilterEngine.applyFilter(bitmap!!, filter)
                if (filtered !== bitmap) {
                    // 滤镜强度混合：intensity=100 → 完全滤镜，intensity=0 → 完全原图
                    val intensity = _filterIntensity.value / 100f
                    if (intensity < 1f) {
                        val blended = PhotoFilterEngine.blendBitmaps(bitmap!!, filtered, 1f - intensity)
                        filtered.recycle()
                        bitmap?.recycle()
                        bitmap = blended
                    } else {
                        bitmap?.recycle()
                        bitmap = filtered
                    }
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

            // 智能裁切（使用当前画幅预设）+ 按画质设置保存
            if (bitmap != null) {
                val cropped = PhotoFilterEngine.applySmartCropByRatio(bitmap!!, _currentAspectRatio.value)
                // 画质设置：JPEG 质量 + 输出格式（JPEG/WEBP）
                val quality = _jpegQuality.value.coerceIn(50, 100)
                val useWebp = _outputFormat.value == 1
                // WEBP 格式时更换文件扩展名，避免 .jpg 文件写入 WEBP 数据导致解码失败
                if (useWebp && path.endsWith(".jpg", ignoreCase = true)) {
                    val webpFile = File(path.substringBeforeLast('.') + ".webp")
                    savedPath = webpFile.absolutePath
                    FileOutputStream(webpFile).use { out ->
                        cropped.compress(Bitmap.CompressFormat.WEBP, quality, out)
                    }
                    // 删除原始 jpg 文件（已写入 webp 副本）
                    try {
                        File(path).takeIf { it.exists() && it.absolutePath != savedPath }?.delete()
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to delete original jpg after webp save", e)
                    }
                } else {
                    FileOutputStream(path).use { out ->
                        cropped.compress(Bitmap.CompressFormat.JPEG, quality, out)
                    }
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
                imagePath = savedPath
            )
            app.database.shootingDao().insert(record)
            _captureCount.value += 1

            // 更新 UI 状态（切到主线程）
            withContext(Dispatchers.Main) {
                _lastCapturedPhotoPath.value = savedPath
                _isReviewingPhoto.value = true
            }
            return savedPath
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
        _currentSequenceIndex.value = 0
        _currentAngleIndex.value = 0
        _useSecondaryPose.value = false
        cancelCountdown()
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
        _currentSequenceIndex.value = 0
        _currentAngleIndex.value = 0
        _useSecondaryPose.value = false
        cancelCountdown()
    }

    fun selectPlan(index: Int) {
        val plans = _currentScene.value.plans
        if (index in plans.indices) {
            _currentPlanIndex.value = index
            _poseScore.value = 0f
            _currentSequenceIndex.value = 0
            _currentAngleIndex.value = 0
            _useSecondaryPose.value = false
            // 切换方案时取消进行中的倒计时
            cancelCountdown()
        }
    }

    fun switchCamera(lifecycleOwner: LifecycleOwner, previewView: PreviewView) {
        cameraManager?.switchCamera(lifecycleOwner, previewView)
        _poseScore.value = 0f
        // 切换摄像头时取消倒计时，避免拍到切换前的画面
        cancelCountdown()
        // 激活 cameraLens 持久化：记录用户选择的镜头（0=后置，1=前置）
        viewModelScope.launch {
            val currentLens = storeManager.cameraLens.first()
            storeManager.setCameraLens(if (currentLens == 0) 1 else 0)
        }
    }

    fun setScene(scene: SceneType) {
        _currentScene.value = scene
        _currentPlanIndex.value = 0
        _poseScore.value = 0f
        _currentSequenceIndex.value = 0
        _currentAngleIndex.value = 0
        _useSecondaryPose.value = false
        // 切换场景时清除自定义姿势，恢复内置方案
        _customActivePlan = null
        _activeCustomPoseId.value = null
        // 切换场景时取消进行中的倒计时，避免拍到旧场景
        cancelCountdown()
        // 关闭所有选择器
        dismissSelectors()
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
                // 直接读取缓存的 StateFlow 值，避免每次循环查 DataStore
                val interval = _autoRecommendInterval.value.toLong()
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

    // ====== 倒计时控制 ======

    /**
     * 循环切换倒计时档位：0 → 3 → 5 → 10 → 0
     */
    fun cycleTimer() {
        val next = when (_timerSeconds.value) {
            0 -> 3
            3 -> 5
            5 -> 10
            else -> 0
        }
        setTimerSeconds(next)
    }

    fun setTimerSeconds(seconds: Int) {
        _timerSeconds.value = seconds
        // 如果关闭倒计时，取消进行中的倒计时
        if (seconds == 0) cancelCountdown()
        viewModelScope.launch {
            storeManager.setTimerSeconds(seconds)
        }
    }

    // ====== 沉浸模式 ======

    fun toggleImmersiveMode() {
        _isImmersiveMode.value = !_isImmersiveMode.value
    }

    fun setImmersiveMode(enabled: Boolean) {
        _isImmersiveMode.value = enabled
    }

    // ====== 点击对焦 ======

    /**
     * 点击对焦：提交对焦请求并显示对焦框视觉反馈
     * @param x 屏幕坐标 X（像素）
     * @param y 屏幕坐标 Y（像素）
     * @param previewView 当前预览视图
     * @return true 表示已成功提交对焦请求
     */
    fun tapToFocus(x: Float, y: Float, previewView: PreviewView): Boolean {
        val success = cameraManager?.tapToFocus(x, y, previewView) ?: false
        if (success) {
            // 显示对焦框反馈，1.5s 后自动消失
            _focusIndicator.value = FocusPoint(x, y)
            focusResetJob?.cancel()
            focusResetJob = viewModelScope.launch {
                kotlinx.coroutines.delay(1500)
                _focusIndicator.value = null
            }
        }
        return success
    }

    /** 清除对焦指示器 */
    fun clearFocusIndicator() {
        _focusIndicator.value = null
    }

    // ====== 距离提示 ======

    /**
     * 根据当前评分更新距离提示文案
     * - 评分 < 50：明显的距离/姿势提示
     * - 评分 50-80：轻微调整提示
     * - 评分 >= 80：清除提示
     */
    fun updateDistanceHint(score: Float, plan: ShootingPlan?) {
        if (score >= 80f) {
            _distanceHint.value = null
            return
        }
        if (plan == null) {
            _distanceHint.value = null
            return
        }
        val hint = when {
            score < 20f -> "站远一点，让全身进入画面"
            score < 35f -> "往左挪一步，对准剪影轮廓"
            score < 50f -> "再靠近一点，手抬高一些"
            score < 65f -> "快了快了，微调一下就好"
            score < 75f -> "稳住别动，就差一点点"
            else -> "好！保持这个姿势"
        }
        _distanceHint.value = hint
    }

    // ====== App 前后台生命周期 ======

    /**
     * App 进入前台：重新注册传感器、相机已由 LifecycleOwner 自动恢复
     */
    fun onAppForeground() {
        registerSensorListener()
    }

    /**
     * App 进入后台：取消倒计时、停止 TTS、注销传感器
     * 相机由 LifecycleOwner 自动停止
     */
    fun onAppBackground() {
        cancelCountdown()
        try {
            tts?.stop()
        } catch (_: Exception) {}
        unregisterSensorListener()
    }

    /** 清除 Vlog 错误提示 */
    fun clearVlogError() {
        _vlogErrorMessage.value = null
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

                if (chunks.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        _vlogErrorMessage.value = "Vlog 分镜加载失败：未捕获到任何视频片段"
                        _displayVlogText.value = ""
                    }
                } else {
                    // 构建字幕条目：根据每段视频的开始时间和时长生成
                    val subtitles = mutableListOf<VideoMerger.SubtitleEntry>()
                    var currentTimeUs = 0L
                    vlog.clips.forEachIndexed { idx, clip ->
                        val durationUs = (clip.duration * 1_000_000).toLong()
                        subtitles.add(VideoMerger.SubtitleEntry(
                            startTimeUs = currentTimeUs,
                            endTimeUs = currentTimeUs + durationUs,
                            text = clip.overlayText
                        ))
                        currentTimeUs += durationUs
                        // 加上转场间隙
                        if (idx < vlog.clips.size - 1) {
                            currentTimeUs += 200_000L
                        }
                    }

                    val merged = VideoMerger.merge(
                        videoFiles = chunks,
                        bgmFile = bgmFile,
                        outputDir = videoOutputDir,
                        subtitles = subtitles
                    )
                    withContext(Dispatchers.Main) {
                        if (merged != null) {
                            _exportedVlogPath.value = merged.absolutePath
                            _isReviewingVlog.value = true
                        } else {
                            _vlogErrorMessage.value = "Vlog 合成失败，请重试"
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Vlog merge failed", e)
                withContext(Dispatchers.Main) {
                    _vlogErrorMessage.value = "Vlog 合成异常：${e.message ?: "未知错误"}"
                }
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

    /**
     * 创建社交分享 Intent：通过系统分享面板分享照片
     * @param photoPath 照片文件路径
     * @return 配置好的 Intent，或 null 表示文件不存在
     */
    fun createShareIntent(photoPath: String): android.content.Intent? {
        val file = File(photoPath)
        if (!file.exists()) return null
        val uri = androidx.core.content.FileProvider.getUriForFile(
            app,
            "${app.packageName}.fileprovider",
            file
        )
        return android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /**
     * 删除照片文件并从数据库移除记录
     * @param photoPath 照片文件路径
     */
    fun deletePhoto(photoPath: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val file = File(photoPath)
                if (file.exists()) file.delete()
                app.database.shootingDao().deleteByPath(photoPath)
            } catch (e: Exception) {
                Log.e(TAG, "Delete photo failed", e)
            }
        }
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

    /**
     * 场景分布统计：返回各场景的拍摄次数（激活 ShootingDao.getSceneDistribution 死代码）
     * 用于在统计页/相册页展示拍摄场景分布
     */
    suspend fun getSceneDistribution(): List<com.poseai.app.data.SceneCount> {
        return try {
            app.database.shootingDao().getSceneDistribution()
        } catch (e: Exception) {
            Log.e(TAG, "getSceneDistribution failed", e)
            emptyList()
        }
    }

    /**
     * 获取全部拍摄记录（一次性）：用于统计分析和收藏列表
     */
    suspend fun getAllRecordsOnce(): List<ShootingRecord> {
        return try {
            app.database.shootingDao().getAllRecordsOnce()
        } catch (e: Exception) {
            Log.e(TAG, "getAllRecordsOnce failed", e)
            emptyList()
        }
    }

    /**
     * 切换照片收藏状态（激活 ShootingRecord.isFavorite 死代码）
     * @param id 记录 ID
     * @param favorite 是否收藏
     */
    fun toggleFavorite(id: Long, favorite: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val record = app.database.shootingDao().getById(id) ?: return@launch
                app.database.shootingDao().update(record.copy(isFavorite = favorite))
            } catch (e: Exception) {
                Log.e(TAG, "toggleFavorite failed", e)
            }
        }
    }

    /**
     * 删除记录（按 ID）
     */
    fun deleteRecordById(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val record = app.database.shootingDao().getById(id)
                if (record != null) {
                    val file = File(record.imagePath)
                    if (file.exists()) file.delete()
                    app.database.shootingDao().deleteById(id.toInt())
                }
            } catch (e: Exception) {
                Log.e(TAG, "deleteRecordById failed", e)
            }
        }
    }

    /**
     * 替换照片文件：更新数据库记录的 imagePath 触发相册 Flow 刷新
     * 由照片编辑器在保存编辑结果后调用，新文件路径与旧路径不同可避免 Coil 缓存命中旧图
     *
     * @param recordId 拍摄记录 ID
     * @param newImagePath 编辑后新照片文件路径
     */
    fun replacePhotoFile(recordId: Long, newImagePath: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val record = app.database.shootingDao().getById(recordId) ?: return@launch
                val oldPath = record.imagePath
                // 更新数据库记录的 imagePath，触发 Flow 发射，相册自动刷新
                app.database.shootingDao().update(record.copy(imagePath = newImagePath))
                // 数据库更新成功后再删除旧文件（避免竞态导致记录指向已删文件）
                if (oldPath != newImagePath) {
                    val oldFile = File(oldPath)
                    if (oldFile.exists()) oldFile.delete()
                }
            } catch (e: Exception) {
                Log.e(TAG, "replacePhotoFile failed", e)
            }
        }
    }

    // ====== 滤镜控制 ======

    fun setFilter(filter: PhotoFilterEngine.Filter) {
        _currentFilter.value = filter
    }

    /** 设置社交画幅预设（激活 AspectRatio 死代码） */
    fun setAspectRatio(ratio: PhotoFilterEngine.AspectRatio) {
        _currentAspectRatio.value = ratio
    }

    /** 循环切换画幅预设 */
    fun cycleAspectRatio() {
        val values = PhotoFilterEngine.AspectRatio.values()
        val idx = values.indexOf(_currentAspectRatio.value)
        _currentAspectRatio.value = values[(idx + 1) % values.size]
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

    // ====== 滤镜强度控制 ======

    fun setFilterIntensity(intensity: Int) {
        _filterIntensity.value = intensity.coerceIn(0, 100)
    }

    // ====== 美颜控制 ======

    fun setBeautyEnabled(enabled: Boolean) {
        _beautyEnabled.value = enabled
    }

    fun setSmoothingLevel(level: Int) {
        _smoothingLevel.value = level.coerceIn(0, 100)
    }

    fun setWhiteningLevel(level: Int) {
        _whiteningLevel.value = level.coerceIn(0, 100)
    }

    fun setSlimmingLevel(level: Int) {
        _slimmingLevel.value = level.coerceIn(0, 100)
    }

    /** 一键美颜：设置预设强度 */
    fun applyQuickBeauty() {
        _beautyEnabled.value = true
        _smoothingLevel.value = 40
        _whiteningLevel.value = 30
        _slimmingLevel.value = 20
    }

    /** 关闭美颜 */
    fun disableBeauty() {
        _beautyEnabled.value = false
        _smoothingLevel.value = 0
        _whiteningLevel.value = 0
        _slimmingLevel.value = 0
    }

    // ====== 画质控制 ======

    /** 设置 JPEG 压缩质量（50-100） */
    fun setJpegQuality(value: Int) {
        val clamped = value.coerceIn(50, 100)
        _jpegQuality.value = clamped
        viewModelScope.launch { storeManager.setJpegQuality(clamped) }
    }

    /** 设置输出格式：0=JPEG, 1=WEBP */
    fun setOutputFormat(value: Int) {
        val clamped = value.coerceIn(0, 1)
        _outputFormat.value = clamped
        viewModelScope.launch { storeManager.setOutputFormat(clamped) }
    }

    /** 设置 HDR 开关 */
    fun setHdrEnabled(value: Boolean) {
        _hdrEnabled.value = value
        viewModelScope.launch { storeManager.setHdrEnabled(value) }
    }

    // ====== 分享控制 ======

    /** 打开分享面板 */
    fun openShareSheet(photoPath: String?) {
        _sharePhotoPath.value = photoPath
        // 用当前场景名作为水印副文本默认值
        val sceneName = _currentScene.value.displayName
        _watermarkStyle.value = if (_watermarkEnabled.value) {
            ShareEngine.WatermarkStyle.SIGNATURE
        } else {
            ShareEngine.WatermarkStyle.NONE
        }
        // 注：sceneName 通过 buildShareConfig 时取自 currentScene
        _showShareSheet.value = true
    }

    fun closeShareSheet() {
        _showShareSheet.value = false
    }

    fun setWatermarkStyle(style: ShareEngine.WatermarkStyle) {
        _watermarkStyle.value = style
    }

    fun setWatermarkPosition(position: ShareEngine.WatermarkPosition) {
        _watermarkPosition.value = position
    }

    fun setShareUsername(name: String) {
        _shareUsername.value = name
    }

    fun setShareLocation(location: String) {
        _shareLocation.value = location
    }

    fun setShareCaption(caption: String) {
        _shareCaption.value = caption
    }

    /** 添加话题（自动去重、去空、限长 20） */
    fun addTopic(topic: String) {
        val cleaned = topic.trim().replace("#", "").replace(" ", "")
        if (cleaned.isEmpty()) return
        val current = _shareTopics.value.toMutableList()
        if (cleaned !in current) {
            current.add(cleaned)
            if (current.size > 8) current.removeAt(0) // 最多 8 个话题
            _shareTopics.value = current
        }
    }

    /** 移除话题 */
    fun removeTopic(topic: String) {
        _shareTopics.value = _shareTopics.value.filterNot { it == topic }
    }

    /** 清空话题 */
    fun clearTopics() {
        _shareTopics.value = emptyList()
    }

    /** 构建当前分享配置 */
    fun buildShareConfig(): ShareEngine.ShareConfig {
        return ShareEngine.ShareConfig(
            watermarkStyle = _watermarkStyle.value,
            watermarkPosition = _watermarkPosition.value,
            username = _shareUsername.value,
            location = _shareLocation.value,
            sceneName = _currentScene.value.displayName,
            topics = _shareTopics.value,
            caption = _shareCaption.value
        )
    }

    /**
     * 执行系统分享（在 IO 线程准备图片，主线程启动分享面板）
     * @return true 表示成功启动分享面板
     */
    suspend fun executeShare(context: android.content.Context): Boolean {
        val photoPath = _sharePhotoPath.value ?: return false
        val config = buildShareConfig()
        return withContext(Dispatchers.IO) {
            ShareEngine.shareToSystem(context, photoPath, config)
        }
    }

    // ====== 自定义姿势控制 ======

    /** 打开自定义姿势面板 */
    fun openCustomPoseSheet() {
        _customPoses.value = customPoseStore.loadAll()
        _showCustomPoseSheet.value = true
    }

    fun closeCustomPoseSheet() {
        _showCustomPoseSheet.value = false
    }

    /**
     * 保存当前检测到的姿势为自定义模板
     *
     * 从 _detectedPosePoints 提取归一化坐标，存入 CustomPoseStore。
     * 要求至少检测到 3 个关键点才能保存。
     *
     * @param name 姿势名称
     * @param description 姿势描述
     * @return true 表示保存成功
     */
    fun saveCurrentPoseAsCustom(name: String, description: String): Boolean {
        val points = _detectedPosePoints.value
        if (points.size < 3) return false
        val cleanedName = name.trim().ifEmpty { "我的姿势" }
        val pose = com.poseai.app.store.CustomPose(
            name = cleanedName,
            description = description.trim(),
            posePoints = points.toMap()
        )
        val ok = customPoseStore.save(pose)
        if (ok) {
            _customPoses.value = customPoseStore.loadAll()
        }
        return ok
    }

    /** 删除自定义姿势 */
    fun deleteCustomPose(id: String): Boolean {
        val ok = customPoseStore.delete(id)
        if (ok) {
            _customPoses.value = customPoseStore.loadAll()
            if (_activeCustomPoseId.value == id) {
                _activeCustomPoseId.value = null
            }
        }
        return ok
    }

    /**
     * 应用自定义姿势为当前目标
     *
     * 将 CustomPose 转为临时 ShootingPlan，替换当前场景的方案列表头部，
     * 这样姿势匹配、剪影绘制、相似度计算等链路无需改动即可工作。
     */
    fun applyCustomPose(pose: com.poseai.app.store.CustomPose) {
        val plan = ShootingPlan(
            poseName = pose.name,
            poseDescription = pose.description.ifEmpty { "自定义姿势" },
            posePoints = pose.posePoints,
            composition = CompositionRule.CENTER
        )
        // 将自定义姿势作为唯一方案注入当前场景的运行时副本
        _customActivePlan = plan
        _currentPlanIndex.value = 0
        _poseScore.value = 0f
        _currentSequenceIndex.value = 0
        _currentAngleIndex.value = 0
        _useSecondaryPose.value = false
        _activeCustomPoseId.value = pose.id
        cancelCountdown()
    }

    /** 当前激活的自定义方案（非 null 时覆盖 currentPlan） */
    @Volatile
    private var _customActivePlan: ShootingPlan? = null

    /** 清除自定义姿势，恢复内置场景方案 */
    fun clearCustomPose() {
        _customActivePlan = null
        _activeCustomPoseId.value = null
        _poseScore.value = 0f
        _currentPlanIndex.value = 0
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
        cancelCountdown()
        stopBurst()
        flashResetJob?.cancel()
        focusResetJob?.cancel()
        distanceHintResetJob?.cancel()
        unregisterSensorListener()
        cameraManager?.shutdown()
        poseDetector?.close()
        sceneClassifier?.close()
        smileDetector?.close()
        poseSimilarityModel?.close()
        try {
            toneGenerator?.release()
        } catch (_: Exception) {}
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (_: Exception) {}
    }
}
