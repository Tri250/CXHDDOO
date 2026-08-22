package com.poseai.app.viewmodel

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.poseai.app.ai.AIAdvisor
import com.poseai.app.camera.CameraManager
import com.poseai.app.data.CustomPlanDao
import com.poseai.app.data.CustomPlanEntity
import com.poseai.app.data.ShootingRecordDao
import com.poseai.app.data.ShootingRecordEntity
import com.poseai.app.ml.PoseData
import com.poseai.app.ml.PoseMatcher
import com.poseai.app.model.CompositionRule
import com.poseai.app.model.FrameRatio
import com.poseai.app.model.NormPoint
import com.poseai.app.model.SceneType
import com.poseai.app.model.ShootingPlan
import com.poseai.app.model.VlogTemplate
import com.poseai.app.util.LocationSnapshot
import com.poseai.app.util.LocationUtil
import com.poseai.app.video.DeviceFeedback
import com.poseai.app.video.VideoMerger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * 拍摄 ViewModel——转换自 iOS ShootingViewModel。
 * 统一管理场景/方案/拍摄/倒计时/语音等全部业务状态与逻辑。
 * iOS 内购逻辑在 Android 端一律移除，全功能免费。
 */
class ShootingViewModel(app: Application) : AndroidViewModel(app) {

    val manager = CameraManager(app.applicationContext)
    val feedback = DeviceFeedback(app.applicationContext)

    // 场景与方案
    val scene = MutableStateFlow(SceneType.UNKNOWN)
    val currentPlanIndex = MutableStateFlow(0)
    val isSceneReady = MutableStateFlow(false)

    // 姿势匹配
    val detectedPoses = MutableStateFlow<List<PoseData>>(emptyList())
    val score = MutableStateFlow(0f)
    val isDualMatchedPrimary = MutableStateFlow(true)

    // 拍摄状态
    val showShutterFlash = MutableStateFlow(false)
    val hapticCooldown = MutableStateFlow(false)
    val isCapturing = MutableStateFlow(false)

    // UI 状态
    val showGuide = MutableStateFlow(false)
    val showCompositionTip = MutableStateFlow(false)
    val showSpaceTip = MutableStateFlow(false)
    val isImmersiveMode = MutableStateFlow(false)
    val isLowLight = MutableStateFlow(false)
    val aiSuggestion = MutableStateFlow<String?>(null)

    val burstImages = MutableStateFlow<List<Bitmap>>(emptyList())
    val capturedShotsCount = MutableStateFlow(0)
    val expectedBurstCount = MutableStateFlow(1)
    val isReviewingPhotos = MutableStateFlow(false)
    val showSessionGallery = MutableStateFlow(false)

    // 倒计时自拍
    val timerSeconds = MutableStateFlow(0)
    val countdown = MutableStateFlow(0)

    // Step 10/12/13
    val activeSequenceIndex = MutableStateFlow(0)
    val activeAngleIndex = MutableStateFlow(0)
    val activeVlogClipIndex = MutableStateFlow(0)
    val isVlogRecording = MutableStateFlow(false)
    val displayVlogText = MutableStateFlow<String?>(null)
    val isReviewingVlog = MutableStateFlow(false)
    val exportedVlogUri = MutableStateFlow<String?>(null)

    // 自定义方案
    val customShootingPlans = MutableStateFlow<List<ShootingPlan>>(emptyList())
    val isRecordingMode = MutableStateFlow(false)
    val recordCountdown = MutableStateFlow(0)
    val pointsToSave = MutableStateFlow<Map<String, NormPoint>?>(null)

    // 设备俯仰角
    val devicePitch = MutableStateFlow(0f)

    // Vlog 临时片段
    private val vlogChunks = ArrayList<File>()

    // 内部管理
    private var compositionTipJob: kotlinx.coroutines.Job? = null
    private var scanTimeoutJob: kotlinx.coroutines.Job? = null
    private var lastAutoRecommend = 0L
    private var userOverrideUntil = 0L

    // 稳定触发时间：null = 未稳定
    private var stableStartTime: Long? = null

    private val appContext: android.content.Context get() = getApplication<Application>().applicationContext

    // 数据库引用（懒加载，避免初始化时机问题）
    private val database by lazy {
        (getApplication<Application>() as com.poseai.app.PoseAIApplication).database
    }
    private val recordDao: ShootingRecordDao get() = database.shootingRecordDao()
    private val customDao: CustomPlanDao get() = database.customPlanDao()

    private var customPlansJob: kotlinx.coroutines.Job? = null
    private var pitchJob: kotlinx.coroutines.Job? = null
    private var scanTimeoutJob: kotlinx.coroutines.Job? = null

    // 常量
    val successThreshold: Float = 85f
    val dualSuccessThreshold: Float = 75f

    /** 历史拍板记录（供历史图库/统计页订阅） */
    val historyRecords: kotlinx.coroutines.flow.Flow<List<ShootingRecordEntity>>
        get() = recordDao.observeAll()

    /** 设置应用（全免费，无需解锁逻辑） */
    val isPro = true

    // MARK: - 计算属性
    val availablePlans: List<ShootingPlan>
        get() = customShootingPlans.value + scene.value.plans

    val currentPlan: ShootingPlan?
        get() {
            val plans = availablePlans
            return if (plans.isNotEmpty() && currentPlanIndex.value < plans.size) plans[currentPlanIndex.value] else null
        }

    val isReady: Boolean
        get() = score.value > (if (currentPlan?.secondaryPosePoints != null) dualSuccessThreshold else successThreshold)

    // 全免费：所有功能解锁
    val isPremiumScene: Boolean get() = false

    // MARK: - 初始化
    init {
        feedback.initTts()
        feedback.startPitchTracking()
        startPitchCollection()
        startCollectingCustomPlans()
        bind()
        startScanTimeout()
    }

    private fun startPitchCollection() {
        pitchJob?.cancel()
        pitchJob = viewModelScope.launch {
            while (true) {
                devicePitch.value = feedback.devicePitch
                delay(200)
            }
        }
    }

    private fun startCollectingCustomPlans() {
        customPlansJob?.cancel()
        customPlansJob = viewModelScope.launch {
            customDao.observeAll().collect { entities ->
                customShootingPlans.value = entities.map { it.toShootingPlan() }
            }
        }
    }

    private fun CustomPlanEntity.toShootingPlan(): ShootingPlan {
        val points = try {
            val obj = JSONObject(pointsJson)
            obj.keys().asSequence().associate { key ->
                val p = obj.getJSONObject(key)
                key to NormPoint(p.getDouble("x").toFloat(), p.getDouble("y").toFloat())
            }
        } catch (e: Exception) { emptyMap() }
        return ShootingPlan(
            id = "custom_$id",
            poseName = poseName,
            poseEmoji = if (poseEmoji.isEmpty()) "🧍" else poseEmoji,
            poseDescription = "这是你设计的专属动作",
            composition = CompositionRule.CENTER,
            frameRatio = FrameRatio.FULL_BODY,
            voiceGuide = "摆出你的自定义姿势：加上一点小调整，完美",
            posePoints = points
        )
    }

    // MARK: - 绑定回调
    private fun bind() {
        manager.onPoseUpdate = { poses -> handlePoseUpdate(poses) }

        manager.onSceneChange = { newScene ->
            if (newScene != SceneType.UNKNOWN) {
                scanTimeoutJob?.cancel()
                val isNew = scene.value != newScene
                scene.value = newScene
                isSceneReady.value = true
                if (isNew) {
                    currentPlanIndex.value = 0
                    activeSequenceIndex.value = 0
                    activeAngleIndex.value = 0
                    score.value = 0f
                    stableStartTime = null
                    availablePlans.firstOrNull()?.let { plan ->
                        speak("识别到${newScene.displayName}，推荐${plan.poseName}，${plan.composition.voiceHint}")
                        showTipBriefly()
                    }
                    viewModelScope.launch {
                        delay(1000)
                        manager.takeOOTDSnapshot()
                    }
                }
            }
        }

        manager.onLowLight = { isLow ->
            isLowLight.value = isLow
            manager.isLowLightMode = isLow
        }

        manager.onPhotoCapture = { image -> onPhotoCaptured(image) }

        manager.onOOTDSnapshot = { image ->
            viewModelScope.launch {
                val suggestion = AIAdvisor.analyzeOOTD(image, scene.value)
                aiSuggestion.value = suggestion
                if (activeSequenceIndex.value == 0) speak(suggestion)
                delay(8500)
                aiSuggestion.value = null
            }
        }

        manager.onRecordingSave = { path -> vlogChunks.add(File(path)) }
    }

    private fun handlePoseUpdate(poses: List<PoseData>) {
        if (poses.isNotEmpty()) detectedPoses.value = poses
        val plan = currentPlan ?: return

        // ML Kit 仅支持单人检测；双人方案降级为单人方案
        val isDualPlan = plan.secondaryPosePoints != null
        val activePrimary = activePrimaryPosePoints(plan)
        val secondary = plan.secondaryPosePoints

        val baselineScore: Float
        if (secondary != null && poses.size >= 2) {
            // 双人方案，且检测到两人：使用完整全排列双人匹配
            val (score, primaryAsFirst) = PoseMatcher.calculateDualSimilarity(
                poses[0].points, poses[1].points,
                activePrimary, secondary,
                poses[0].isHalfBody, poses[1].isHalfBody
            )
            isDualMatchedPrimary.value = primaryAsFirst
            baselineScore = score
        } else if (secondary != null) {
            // 降级：双人方案但仅检测到单人，只比较主要姿态并打 0.75x 折扣
            isDualMatchedPrimary.value = true
            baselineScore = if (poses.isNotEmpty()) {
                PoseMatcher.calculateSimilarity(poses[0].points, activePrimary, poses[0].isHalfBody, score.value) * 0.75f
            } else 0f
        } else {
            baselineScore = if (poses.isNotEmpty()) {
                val fp = poses[0]
                PoseMatcher.calculateSimilarity(fp.points, activePrimary, fp.isHalfBody, score.value)
            } else 0f
        }

        // 多机位俯仰约束
        var pitchPenalty = 0f
        val multi = plan.multiAngles
        if (multi != null && activeAngleIndex.value < multi.size) {
            multi[activeAngleIndex.value].requiredPitch?.let { req ->
                val pitch = devicePitch.value
                if ((req > 0 && pitch < req) || (req < 0 && pitch > req)) pitchPenalty = 60f
            }
        }

        val finalBaseline = maxOf(0f, baselineScore - pitchPenalty)
        score.value = score.value * 0.7f + finalBaseline * 0.3f
        val current = score.value
        val activeThreshold = if (isDualPlan) dualSuccessThreshold else successThreshold

        // 稳定触发拍照
        val now = System.currentTimeMillis()
        if (current > activeThreshold) {
            if (stableStartTime == null) {
                stableStartTime = now
                if (!hapticCooldown.value) {
                    hapticCooldown.value = true
                    feedback.impact(DeviceFeedback.RIGID)
                    speak("对齐啦，保持不动！")
                    viewModelScope.launch { delay(3000); hapticCooldown.value = false }
                }
            } else if (now - (stableStartTime ?: now) > 800) {
                stableStartTime = null
                triggerAutoPhoto()
            }
        } else {
            stableStartTime = null
        }

        // P5-3 留白智能提醒
        val bbox = poses.firstOrNull()?.bbox
        if (bbox != null) {
            if (plan.composition != CompositionRule.CENTER && abs(bbox.centerX() - 0.5f) < 0.05f) {
                if (!showSpaceTip.value && (poses.firstOrNull()?.points?.size ?: 0) >= 4) {
                    showSpaceTip.value = true
                }
            } else if (showSpaceTip.value) showSpaceTip.value = false
        } else if (showSpaceTip.value) showSpaceTip.value = false

        // P1-4 姿势亲近度自动推荐
        performAutoRecommend(poses, plan)
    }

    private fun performAutoRecommend(poses: List<PoseData>, currentPlan: ShootingPlan) {
        val now = System.currentTimeMillis()
        if (now - lastAutoRecommend < 500) return
        lastAutoRecommend = now
        if (now < userOverrideUntil) return

        // 连拍/多机位/Vlog 推进中屏蔽
        if (activeSequenceIndex.value > 0 || activeAngleIndex.value > 0 ||
            activeVlogClipIndex.value > 0 || isVlogRecording.value) return

        val plans = availablePlans
        if (plans.size <= 1) return
        val firstPose = poses.firstOrNull() ?: return
        if (firstPose.points.size < 4) return

        val scores = plans.mapIndexed { idx, p ->
            if (p.secondaryPosePoints != null) idx to 0f
            else idx to PoseMatcher.calculateSimilarity(firstPose.points, p.posePoints, firstPose.isHalfBody)
        }
        val best = scores.maxByOrNull { it.second } ?: return
        val currentIdx = currentPlanIndex.value
        val currentScore = scores.getOrNull(currentIdx)?.second ?: 0f
        if (best.first != currentIdx && best.second > 15f && best.second - currentScore > 8f) {
            currentPlanIndex.value = best.first
            score.value = 0f
            stableStartTime = null
        }
    }

    private fun activePrimaryPosePoints(plan: ShootingPlan): Map<String, NormPoint> {
        val rawPoints: Map<String, NormPoint> = plan.sequence?.let { seq ->
            if (activeSequenceIndex.value < seq.size) seq[activeSequenceIndex.value].posePoints
            else plan.posePoints
        } ?: plan.multiAngles?.let { multi ->
            if (activeAngleIndex.value < multi.size) {
                multi[activeAngleIndex.value].posePoints ?: plan.posePoints
            } else plan.posePoints
        } ?: plan.posePoints

        // 确保关键点映射包含必要的关节名称
        return normalizePosePoints(rawPoints)
    }

    /** 规范化预设姿态点：补齐可能缺失的 neck 等派生关节 */
    private fun normalizePosePoints(points: Map<String, NormPoint>): Map<String, NormPoint> {
        if (points.containsKey("neck")) return points
        val ls = points["leftShoulder"]
        val rs = points["rightShoulder"]
        val nose = points["nose"]
        if (ls != null && rs != null) {
            val mid = NormPoint((ls.x + rs.x) / 2f, (ls.y + rs.y) / 2f)
            val neckPoint = if (nose != null) {
                NormPoint((mid.x + nose.x) / 2f, (mid.y + nose.y) / 2f)
            } else mid
            return points.toMutableMap().apply { put("neck", neckPoint) }
        }
        return points
    }

    private fun onPhotoCaptured(image: Bitmap) {
        currentImage = image
        val current = burstImages.value.toMutableList()
        current.add(image)

        // P5-2 智能裁切双底片
        detectedPoses.value.firstOrNull()?.bbox?.let { bbox ->
            val iw = image.width.toFloat()
            val ih = image.height.toFloat()
            val cropTop = maxOf(0f, bbox.top * ih - ih * 0.10f)
            val cropH = minOf(ih - cropTop, maxOf(bbox.height() * 0.5f * ih, iw * 0.8f))
            val cropW = cropH * 0.8f
            val cx = bbox.centerX() * iw
            val cropX = maxOf(0f, cx - cropW / 2f)
            if (cropW > iw * 0.3f) {
                try {
                    val cropped = Bitmap.createBitmap(image, cropX.toInt(), cropTop.toInt(), cropW.toInt(), cropH.toInt())
                    current.add(cropped)
                } catch (_: Exception) { }
            }
        }
        burstImages.value = current
        capturedShotsCount.value += 1
        if (capturedShotsCount.value >= expectedBurstCount.value) {
            isReviewingPhotos.value = true
        }
    }

    /** 最近一次拍摄的原始底片（保存到相册/分享用） */
    var currentImage: Bitmap? = null
        private set

    // MARK: - 快门
    fun triggerManualPhoto() {
        val plan = currentPlan
        if (plan?.sequence != null && activeSequenceIndex.value != 0) {
            executeSequenceCapture(activeSequenceIndex.value + 1)
            activeSequenceIndex.value = 0
        } else {
            takeBurst(1)
        }
    }

    // 全免费：连拍始终 3 张
    fun triggerAutoPhoto() {
        val plan = currentPlan
        if (plan?.vlogScript != null) { executeVlogCapture(plan.vlogScript); return }
        if (plan?.sequence != null) { executeSequenceCapture(plan.sequence.size); return }
        if (plan?.multiAngles != null) { executeMultiAngleCapture(plan.multiAngles.size); return }
        speak("拍好了！连拍三张")
        takeBurst(3)
        score.value = 0f
    }

    private fun executeMultiAngleCapture(angleCount: Int) {
        if (isCapturing.value) return
        isCapturing.value = true
        if (activeAngleIndex.value == 0) {
            burstImages.value = emptyList()
            capturedShotsCount.value = 0
            expectedBurstCount.value = angleCount
        }
        manager.takePhoto()
        triggerFlash()
        viewModelScope.launch {
            delay(500) // 延长到 500ms，给 CameraX 更多拍照时间
            isCapturing.value = false
            score.value = 0f
            if (activeAngleIndex.value + 1 < angleCount) {
                activeAngleIndex.value += 1
                currentPlan?.multiAngles?.getOrNull(activeAngleIndex.value)?.voiceHint?.let { speak(it) }
            } else {
                speak("真棒！这组机位全都囊括了。")
                activeAngleIndex.value = 0
            }
        }
        // 超时保护：拍照若 3 秒内未触发回调，强制复位
        viewModelScope.launch {
            delay(3000)
            if (isCapturing.value) isCapturing.value = false
        }
    }

    private fun executeSequenceCapture(seqCount: Int) {
        if (isCapturing.value) return
        isCapturing.value = true
        if (activeSequenceIndex.value == 0) {
            burstImages.value = emptyList()
            capturedShotsCount.value = 0
            expectedBurstCount.value = seqCount
        }
        manager.takePhoto()
        triggerFlash()
        viewModelScope.launch {
            delay(500)
            isCapturing.value = false
            score.value = 0f
            if (activeSequenceIndex.value + 1 < seqCount) {
                activeSequenceIndex.value += 1
                currentPlan?.sequence?.getOrNull(activeSequenceIndex.value)?.voiceHint?.let { speak(it) }
            } else {
                speak("真棒！收工结算。")
                activeSequenceIndex.value = 0
            }
        }
        // 超时保护
        viewModelScope.launch {
            delay(3000)
            if (isCapturing.value) isCapturing.value = false
        }
    }

    private fun executeVlogCapture(vlog: VlogTemplate) {
        if (isVlogRecording.value) return
        val clips = vlog.clips
        if (activeVlogClipIndex.value >= clips.size) return
        val clip = clips[activeVlogClipIndex.value]
        if (activeVlogClipIndex.value == 0) {
            vlogChunks.clear()
            burstImages.value = emptyList()
        }
        speak(clip.voiceCommand)
        displayVlogText.value = clip.overlayText
        viewModelScope.launch {
            delay(1500)
            isVlogRecording.value = true
            feedback.impact(DeviceFeedback.HEAVY)
            val chunk = File(appContext.externalCacheDir, "vlog_chunk_${System.currentTimeMillis()}.mp4")
            manager.startVideoRecording(chunk)
            delay((clip.durationSeconds * 1000).toLong())
            manager.stopVideoRecording()
            delay(250)
            isVlogRecording.value = false
            score.value = 0f
            if (activeVlogClipIndex.value + 1 < clips.size) {
                activeVlogClipIndex.value += 1
                executeVlogCapture(vlog)
            } else {
                speak("卡！非常完美，杀青！正在缝合成片……")
                activeVlogClipIndex.value = 0
                displayVlogText.value = "🎞️ 正在合成 Vlog 大片..."
                finalizeVlog()
            }
        }
    }

    private fun finalizeVlog() {
        viewModelScope.launch(Dispatchers.IO) {
            val output = File(appContext.externalCacheDir, "vlog_${System.currentTimeMillis()}.mp4")
            val ok = VideoMerger.merge(vlogChunks, output, null)
            withContext(Dispatchers.Main) {
                displayVlogText.value = null
                if (ok) {
                    exportedVlogUri.value = output.absolutePath
                    isReviewingVlog.value = true
                } else {
                    speak("抱歉，合成过程中出现故障。")
                }
            }
        }
    }

    // MARK: - 连拍
    fun takeBurst(count: Int) {
        if (isCapturing.value) return
        isCapturing.value = true
        expectedBurstCount.value = count
        burstImages.value = emptyList()
        capturedShotsCount.value = 0
        var taken = 0
        fun snap() {
            if (taken >= count) {
                viewModelScope.launch { delay(200); isCapturing.value = false }
                return
            }
            manager.takePhoto()
            triggerFlash()
            taken += 1
            viewModelScope.launch { delay(250); snap() }
        }
        snap()
        // 超时保护
        viewModelScope.launch {
            delay((count * 1500L).coerceAtLeast(3000))
            if (isCapturing.value) isCapturing.value = false
        }
    }

    // MARK: - 倒计时
    fun cycleTimer() {
        val options = arrayOf(0, 3, 5, 10)
        val cur = options.indexOf(timerSeconds.value).takeIf { it >= 0 } ?: 0
        timerSeconds.value = options[(cur + 1) % options.size]
        cancelTimer()
    }

    fun handleShutterTap() {
        if (timerSeconds.value == 0) triggerManualPhoto()
        else if (countdown.value > 0) cancelTimer()
        else startCountdown()
    }

    fun startCountdown() {
        countdown.value = timerSeconds.value
        viewModelScope.launch {
            while (countdown.value > 0) {
                feedback.impact(DeviceFeedback.LIGHT)
                delay(1000)
                countdown.value -= 1
            }
            triggerManualPhoto()
        }
    }

    fun cancelTimer() {
        countdown.value = 0
    }

    fun triggerFlash() {
        feedback.playShutterSound()
        showShutterFlash.value = true
        viewModelScope.launch {
            delay(220)
            showShutterFlash.value = false
        }
    }

    // MARK: - 语音
    fun speak(text: String) = feedback.speak(text)
    fun stopSpeaking() = feedback.stopSpeaking()

    // MARK: - 方案选择
    fun selectPlan(index: Int) {
        currentPlanIndex.value = index
        userOverrideUntil = System.currentTimeMillis() + 8000
        score.value = 0f
        stableStartTime = null
    }

    fun toggleImmersiveMode() {
        isImmersiveMode.value = !isImmersiveMode.value
        feedback.impact(DeviceFeedback.LIGHT)
    }

    // MARK: - 相机生命周期派发（UI 在 onResume/onPause 调用）
    fun resumeCameraIfNeeded() {
        // 相机 bind 由 UI 通过 lifecycle 绑定；这里通知 UI 层面保持运行状态
    }

    // MARK: - 自定义录制流程
    fun startRecordingCustomPlan() {
        if (isRecordingMode.value) return
        isRecordingMode.value = true
        recordCountdown.value = 3
        recordTick()
    }

    private fun recordTick() {
        viewModelScope.launch {
            if (recordCountdown.value > 0) {
                feedback.impact(DeviceFeedback.MEDIUM)
                delay(1000)
                if (!isRecordingMode.value) return@launch
                recordCountdown.value -= 1
                recordTick()
            } else {
                feedback.impact(DeviceFeedback.HEAVY)
                val firstPose = detectedPoses.value.firstOrNull()
                if (firstPose != null && firstPose.points.size >= 5) {
                    pointsToSave.value = firstPose.points
                } else {
                    pointsToSave.value = null
                    speak("未能检测到完整的人体骨架，请站远一点。")
                }
                isRecordingMode.value = false
            }
        }
    }

    fun cancelRecording() {
        isRecordingMode.value = false
        recordCountdown.value = 0
        pointsToSave.value = null
    }

    /** 清理 Vlog 临时分镜文件（预览页保存/重拍后调用，对应 iOS cleanupVlogTempFiles） */
    fun cleanupVlogTempFiles() {
        vlogChunks.forEach { runCatching { it.delete() } }
        vlogChunks.clear()
        isReviewingVlog.value = false
    }

    /** 退出照片预览（重拍） */
    fun retakePhotos() {
        isReviewingPhotos.value = false
        burstImages.value = emptyList()
        capturedShotsCount.value = 0
    }

    // MARK: - 自定义方案保存
    fun saveCustomPlan(name: String, emoji: String, points: Map<String, NormPoint>) {
        viewModelScope.launch(Dispatchers.IO) {
            val obj = JSONObject()
            points.forEach { (k, p) ->
                obj.put(k, JSONObject().put("x", p.x.toDouble()).put("y", p.y.toDouble()))
            }
            customDao.insert(
                CustomPlanEntity(
                    id = UUID.randomUUID().toString(),
                    createdAt = System.currentTimeMillis(),
                    poseName = name,
                    poseEmoji = emoji,
                    pointsJson = obj.toString()
                )
            )
        }
    }

    // MARK: - 拍摄记录

    /**
     * 保存拍摄记录——在原实现基础上增加位置/光线/设备信息的采集。
     * 位置通过 LocationUtil 在 IO 线程异步获取；光线参数从 CameraManager 读取；
     * 设备信息通过 Build.MODEL + 前后置标志组成。
     */
    fun saveShootingRecord(
        uri: String,
        scoreVal: Int,
        filterName: String?,
        plan: ShootingPlan?,
        sceneType: SceneType
    ) {
        val appCtx = appContext
        viewModelScope.launch(Dispatchers.IO) {
            // 1) 位置（非阻塞，无权限时为 null）
            val locationSnapshot: LocationSnapshot = runCatching {
                LocationUtil.captureLocation(appCtx)
            }.getOrDefault(LocationSnapshot(null, null, null, null))

            // 2) 光线参数（从 CameraManager 读取最近一次分析结果）
            val lightLevel = manager.lastLightLevel
            val colorTemperature = manager.lastColorTemperature
            val exposureTimeMs = manager.lastExposureTimeMs
            val isLow = manager.isLowLightMode

            // 3) 设备信息
            val deviceModel = android.os.Build.MODEL
            val lensFacing = if (manager.isFrontCamera) "front" else "back"

            recordDao.insert(
                ShootingRecordEntity(
                    id = UUID.randomUUID().toString(),
                    createdAt = System.currentTimeMillis(),
                    sceneRawValue = sceneType.name,
                    planId = plan?.id ?: "",
                    planName = plan?.poseName ?: "",
                    matchScore = scoreVal,
                    localUri = uri,
                    appliedFilterRawValue = filterName,
                    latitude = locationSnapshot.latitude,
                    longitude = locationSnapshot.longitude,
                    placeName = locationSnapshot.placeName,
                    cityName = locationSnapshot.cityName,
                    lightLevel = lightLevel,
                    colorTemperature = colorTemperature,
                    exposureTimeMs = exposureTimeMs,
                    isLowLight = isLow,
                    deviceModel = deviceModel,
                    lensFacing = lensFacing
                )
            )
        }
    }

    fun startScanTimeout() {
        scanTimeoutJob?.cancel()
        scanTimeoutJob = viewModelScope.launch {
            delay(8000)
            if (!isSceneReady.value) {
                scene.value = SceneType.COFFEE_SHOP
                isSceneReady.value = true
                currentPlanIndex.value = 0
                speak("未能识别背景，展示通用方案，您可以手动切换")
                showTipBriefly()
            }
        }
    }

    fun showTipBriefly() {
        compositionTipJob?.cancel()
        showCompositionTip.value = true
        compositionTipJob = viewModelScope.launch {
            delay(2800)
            showCompositionTip.value = false
        }
    }

    private val appContext: android.content.Context get() = getApplication<Application>().applicationContext

    override fun onCleared() {
        super.onCleared()
        // 取消所有协程任务
        pitchJob?.cancel()
        customPlansJob?.cancel()
        scanTimeoutJob?.cancel()
        // 释放资源
        feedback.release()
        manager.cleanUp()
    }
}