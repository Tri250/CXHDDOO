package com.poseai.app.util

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * 统一触觉反馈工具，对齐国内主流摄影 App（轻颜/B612/一甜）的反馈强度档位
 *
 * 档位定义：
 * - TICK    极轻量确认（10ms, 5）— 用于滑块、状态切换
 * - CLICK   轻量点击（20ms, 80）— 用于按钮按下
 * - HEAVY   中等反馈（30ms, 180）— 用于快门、连拍抓拍
 * - SUCCESS 双段反馈 — 用于姿势对齐达标、自动抓拍成功
 * - WARN    重反馈（50ms, 255）— 用于警告/俯拍提示
 */
object Haptics {

    enum class Level { TICK, CLICK, HEAVY, SUCCESS, WARN }

    @Composable
    fun rememberHapticController(): HapticController {
        val context = LocalContext.current
        return remember { HapticController(context) }
    }
}

class HapticController(private val context: Context) {

    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vm?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    fun perform(level: Haptics.Level) {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                when (level) {
                    Haptics.Level.TICK ->
                        v.vibrate(VibrationEffect.createOneShot(10, 5))
                    Haptics.Level.CLICK ->
                        v.vibrate(VibrationEffect.createOneShot(20, 80))
                    Haptics.Level.HEAVY ->
                        v.vibrate(VibrationEffect.createOneShot(30, 180))
                    Haptics.Level.WARN ->
                        v.vibrate(VibrationEffect.createOneShot(50, 255))
                    Haptics.Level.SUCCESS -> {
                        // 双段反馈：短-长，模拟"咔嚓"两段手感
                        v.vibrate(VibrationEffect.createWaveform(
                            longArrayOf(0, 18, 40, 35),
                            intArrayOf(0, 120, 0, 220),
                            -1
                        ))
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(when (level) {
                    Haptics.Level.TICK -> 10
                    Haptics.Level.CLICK -> 20
                    Haptics.Level.HEAVY -> 30
                    Haptics.Level.WARN -> 50
                    Haptics.Level.SUCCESS -> 50
                })
            }
        } catch (_: Exception) {
            // 部分厂商ROM有自定义Vibrator实现，吞掉异常避免崩溃
        }
    }
}
