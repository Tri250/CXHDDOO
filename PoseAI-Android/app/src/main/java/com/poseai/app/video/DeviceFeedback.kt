package com.poseai.app.video

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.Locale
import kotlin.math.atan2

/**
 * 设备反馈工具——对应 iOS 的 AVSpeechSynthesizer / UIImpactFeedbackGenerator /
 * AudioServicesPlaySystemSound / CoreMotion。
 * 提供：语音播报、触觉反馈、快门音、设备俯仰角。
 *
 * 关键实现：
 *  - TTS 完整生命周期管理（init/speak/stop/shutdown）
 *  - SoundPool 播放真实快门音（PCM 生成 wav 片段）
 *  - 多级触觉反馈（Light/Medium/Rigid/Heavy）
 *  - 加速度计实时俯仰角
 */
class DeviceFeedback(private val context: Context) {

    // MARK: - TTS

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var ttsInitialized = false
    var isSpeaking: Boolean = false
        private set

    fun initTts() {
        if (ttsInitialized) return
        ttsInitialized = true
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(Locale.CHINA)
                if (result == TextToSpeech.LANG_AVAILABLE || result == TextToSpeech.LANG_COUNTRY_AVAILABLE) {
                    ttsReady = true
                    tts?.setSpeechRate(0.92f)
                    tts?.setPitch(1.0f)
                } else {
                    // 中文不可用时降级到默认语言
                    tts?.setLanguage(Locale.getDefault())
                    ttsReady = true
                }
            }
        }
    }

    fun speak(text: String) {
        if (!ttsReady) return
        val engine = tts ?: return
        // 中断式：新指令立即替换当前播报
        engine.stop()
        isSpeaking = true
        val utteranceId = "poseai_${System.currentTimeMillis()}"
        engine.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
            override fun onStart(p0: String?) {}
            override fun onDone(p0: String?) { isSpeaking = false }
            override fun onError(p0: String?) { isSpeaking = false }
        })
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    fun stopSpeaking() {
        tts?.stop()
        isSpeaking = false
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        ttsReady = false
        ttsInitialized = false
    }

    // MARK: - 触觉反馈

    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    fun impact(style: Int) {
        val v = vibrator ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            when (style) {
                LIGHT -> v.vibrate(VibrationEffect.createOneShot(15, VibrationEffect.DEFAULT_AMPLITUDE))
                MEDIUM -> v.vibrate(VibrationEffect.createOneShot(25, VibrationEffect.DEFAULT_AMPLITUDE))
                RIGID -> v.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE))
                HEAVY -> v.vibrate(VibrationEffect.createOneShot(40, 200))
                else -> v.vibrate(VibrationEffect.createOneShot(20, VibrationEffect.DEFAULT_AMPLITUDE))
            }
        } else {
            @Suppress("DEPRECATION")
            when (style) {
                LIGHT -> v.vibrate(VibrationEffect.createOneShot(15, VibrationEffect.DEFAULT_AMPLITUDE))
                MEDIUM -> v.vibrate(VibrationEffect.createOneShot(25, VibrationEffect.DEFAULT_AMPLITUDE))
                RIGID -> v.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE))
                HEAVY -> v.vibrate(VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE))
                else -> v.vibrate(VibrationEffect.createOneShot(20, VibrationEffect.DEFAULT_AMPLITUDE))
            }
        }
    }

    // MARK: - 快门音

    private var soundPool: SoundPool? = null
    private var shutterSoundId: Int = -1
    private var soundPoolReady = false

    private fun ensureSoundPool() {
        if (soundPool != null) return
        val sp = SoundPool.Builder()
            .setMaxStreams(1)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .build()
        soundPool = sp
        sp.setOnLoadCompleteListener { sp2, soundId, loadStatus ->
            if (loadStatus == 0) soundPoolReady = true
        }
        // 生成快门音并写入临时文件后加载
        val pcmBytes = generateShutterPcm()
        val tempFile = java.io.File(context.cacheDir, "shutter_sound_${System.currentTimeMillis()}.wav")
        java.io.FileOutputStream(tempFile).use { it.write(pcmBytes) }
        shutterSoundId = sp.load(tempFile.absolutePath, 0)
    }

    /** 生成模拟相机快门声的 PCM 数据：短促 click + 轻微 echo */
    private fun generateShutterPcm(): ByteArray {
        val sampleRate = 44100
        val durationMs = 120
        val numSamples = sampleRate * durationMs / 1000
        val bytesPerSample = 2 // 16-bit PCM
        val frameSize = bytesPerSample // mono
        val totalBytes = numSamples * frameSize + 44 // WAV header

        val wav = ByteArrayOutputStream(totalBytes)

        // WAV Header (44 bytes)
        val totalDataLen = numSamples * frameSize
        wav.write("RIFF".toByteArray())
        wav.write(intToLittleEndian(totalDataLen + 36))
        wav.write("WAVE".toByteArray())
        wav.write("fmt ".toByteArray())
        wav.write(intToLittleEndian(16)) // PCM chunk size
        wav.write(shortToLittleEndian(1)) // PCM format
        wav.write(shortToLittleEndian(1)) // mono
        wav.write(intToLittleEndian(sampleRate))
        wav.write(intToLittleEndian(sampleRate * frameSize)) // byte rate
        wav.write(shortToLittleEndian(frameSize)) // block align
        wav.write(shortToLittleEndian(16)) // bits per sample
        wav.write("data".toByteArray())
        wav.write(intToLittleEndian(totalDataLen))

        // 生成快门声：快速频率扫描 + 衰减
        val audioData = ByteArray(numSamples * frameSize)
        for (i in 0 until numSamples) {
            val t = i.toFloat() / sampleRate
            // 主 click：高频衰减正弦
            val clickEnv = kotlin.math.exp(-t * 80) // 快速衰减
            val clickFreq = 2500f - t * 8000f // 频率快速下降
            val click = kotlin.math.sin(2 * Math.PI.toFloat() * clickFreq * t) * clickEnv

            // 次级 echo：更低频、更长衰减
            val echoEnv = kotlin.math.exp(-t * 30) * 0.3f
            val echo = kotlin.math.sin(2 * Math.PI.toFloat() * 800f * t) * echoEnv

            // 白噪声 burst
            val noise = if (t < 0.015f) {
                (Math.random().toFloat() * 2 - 1) * kotlin.math.exp(-t * 200) * 0.5f
            } else 0f

            val sample = (click + echo + noise).coerceIn(-1f, 1f) * Short.MAX_VALUE
            val idx = i * frameSize
            audioData[idx] = (sample.toInt() and 0xFF).toByte()
            audioData[idx + 1] = ((sample.toInt() ushr 8) and 0xFF).toByte()
        }
        wav.write(audioData)
        return wav.toByteArray()
    }

    private fun intToLittleEndian(v: Int): ByteArray {
        return byteArrayOf(
            (v and 0xFF).toByte(),
            ((v ushr 8) and 0xFF).toByte(),
            ((v ushr 16) and 0xFF).toByte(),
            ((v ushr 24) and 0xFF).toByte()
        )
    }

    private fun shortToLittleEndian(v: Int): ByteArray {
        return byteArrayOf(
            (v and 0xFF).toByte(),
            ((v ushr 8) and 0xFF).toByte()
        )
    }

    fun playShutterSound() {
        ensureSoundPool()
        val sp = soundPool ?: return
        if (!soundPoolReady || shutterSoundId < 0) {
            // 降级：用 ToneGenerator 发一个短音
            runCatching {
                val tg = ToneGenerator(AudioManager.STREAM_MUSIC, 80)
                tg.startTone(ToneGenerator.TONE_PROP_BEEP2, 60)
            }
            return
        }
        sp.play(shutterSoundId, 0.7f, 0.7f, 1, 0, 1.0f)
    }

    // MARK: - 设备俯仰角

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
            // 使用标准公式计算 pitch（绕 X 轴旋转角度）
            // 正 = 屏幕顶部向下倾斜
            devicePitch = (atan2(-ay.toDouble(), Math.sqrt((ax * ax + az * az).toDouble())) * 180.0 / Math.PI).toFloat()
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
