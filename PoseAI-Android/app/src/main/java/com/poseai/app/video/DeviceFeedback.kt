package com.poseai.app.video

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import java.util.Locale
import kotlin.math.atan2

/**
 * 设备反馈工具——对应 iOS 的 AVSpeechSynthesizer / UIImpactFeedbackGenerator / AudioServicesPlaySystemSound / CoreMotion。
 * 提供：语音播报、触觉反馈、快门音、设备俯仰角。
 */
class DeviceFeedback(private val context: Context) {

    // MARK: - TTS（中断式语音，对应 iOS synthesizer）
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    var isSpeaking: Boolean = false
        private set

    fun initTts() {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.setLanguage(Locale.CHINA)
                tts?.setSpeechRate(0.6f)
                ttsReady = true
            }
        }
    }

    fun speak(text: String) {
        val engine = tts ?: return
        if (!ttsReady) return
        // 中断式：新指令立即替换当前播报
        if (isSpeaking) engine.stop()
        isSpeaking = true
        engine.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
            override fun onStart(p0: String?) {}
            override fun onDone(p0: String?) { isSpeaking = false }
            override fun onError(p0: String?) { isSpeaking = false }
        })
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "poseai_$text")
    }

    fun stopSpeaking() {
        tts?.stop()
        isSpeaking = false
    }

    // MARK: - 触觉反馈
    private val vibrator: Vibrator? by lazy {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    fun impact(style: Int) {
        val v = vibrator ?: return
        val effect = when (style) {
            LIGHT -> VibrationEffect.createOneShot(18, VibrationEffect.DEFAULT_AMPLITUDE)
            MEDIUM -> VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE)
            RIGID -> VibrationEffect.createOneShot(24, 200)
            HEAVY -> VibrationEffect.createOneShot(45, 255)
            else -> VibrationEffect.createOneShot(20, VibrationEffect.DEFAULT_AMPLITUDE)
        }
        v.vibrate(effect)
    }

    // MARK: - 快门音（对应 AudioServicesPlaySystemSound(1108)，用短促提示音近似）
    private val toneGen: android.media.ToneGenerator by lazy {
        android.media.ToneGenerator(android.media.AudioManager.STREAM_MUSIC, 90)
    }

    fun playShutterSound() {
        runCatching { toneGen.startTone(android.media.ToneGenerator.TONE_PROP_BEEP2, 80) }
    }

    // MARK: - 设备俯仰角（对应 CoreMotion devicePitch）
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    var devicePitch: Float = 0f
        private set

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return
            val ax = event.values[0]
            val ay = event.values[1]
            val az = event.values[2]
            // 手机竖屏时：绕设备 X 轴俯仰（正值=顶部前倾，负值=顶部后仰）近似 iOS pitch
            devicePitch = atan2(-ay.toDouble(), Math.sqrt((ax * ax + az * az).toDouble())).toFloat()
        }
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    fun startPitchTracking() {
        accelerometer?.let {
            sensorManager.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    fun stopPitchTracking() {
        sensorManager.unregisterListener(listener)
    }

    companion object {
        const val LIGHT = 0
        const val MEDIUM = 1
        const val RIGID = 2
        const val HEAVY = 3
    }
}