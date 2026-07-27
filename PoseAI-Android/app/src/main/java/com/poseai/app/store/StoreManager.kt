package com.poseai.app.store

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "poseai_prefs")

class StoreManager(private val context: Context) {

    companion object {
        val KEY_ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val KEY_SELECTED_SCENE = stringPreferencesKey("selected_scene")
        val KEY_CAMERA_LENS = intPreferencesKey("camera_lens")
        val KEY_SMILE_ENABLED = booleanPreferencesKey("smile_enabled")
        val KEY_SMILE_THRESHOLD = floatPreferencesKey("smile_threshold")
        val KEY_LOWLIGHT_ENABLED = booleanPreferencesKey("lowlight_enabled")
        val KEY_WATERMARK_ENABLED = booleanPreferencesKey("watermark_enabled")
        val KEY_GRID_ENABLED = booleanPreferencesKey("grid_enabled")
        val KEY_FLASH_MODE = intPreferencesKey("flash_mode")
        val KEY_AUTO_RECOMMEND_INTERVAL = intPreferencesKey("auto_recommend_interval")
        val KEY_TIMER_SECONDS = intPreferencesKey("timer_seconds")
        val KEY_AUTO_RECOMMEND_ENABLED = booleanPreferencesKey("auto_recommend_enabled")
        // KEY_FLASH_MODE 已在上方声明：0=off, 1=auto, 2=on
        // 画质设置：JPEG 压缩质量 70-100
        val KEY_JPEG_QUALITY = intPreferencesKey("jpeg_quality")
        // 输出格式：0=JPEG, 1=WEBP
        val KEY_OUTPUT_FORMAT = intPreferencesKey("output_format")
        // HDR 开关
        val KEY_HDR_ENABLED = booleanPreferencesKey("hdr_enabled")
        // 主题模式：0=跟随系统, 1=强制暗色, 2=强制亮色
        val KEY_THEME_MODE = intPreferencesKey("theme_mode")
    }

    val onboardingCompleted: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_ONBOARDING_COMPLETED] ?: false
    }

    val selectedScene: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_SELECTED_SCENE] ?: "STREET"
    }

    val cameraLens: Flow<Int> = context.dataStore.data.map { prefs ->
        // 0=后置 (CameraX LENS_FACING_BACK)，1=前置 (LENS_FACING_FRONT)
        // 默认后置，与 CameraManager.startCamera 默认值一致
        prefs[KEY_CAMERA_LENS] ?: 0
    }

    val smileEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_SMILE_ENABLED] ?: false
    }

    val smileThreshold: Flow<Float> = context.dataStore.data.map { prefs ->
        prefs[KEY_SMILE_THRESHOLD] ?: 0.7f
    }

    val lowLightEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_LOWLIGHT_ENABLED] ?: true
    }

    val watermarkEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_WATERMARK_ENABLED] ?: true
    }

    val gridEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_GRID_ENABLED] ?: false
    }

    val autoRecommendInterval: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[KEY_AUTO_RECOMMEND_INTERVAL] ?: 1500
    }

    /** 倒计时秒数：0=关闭，3/5/10 为可选档位 */
    val timerSeconds: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[KEY_TIMER_SECONDS] ?: 0
    }

    suspend fun setOnboardingCompleted(value: Boolean) {
        context.dataStore.edit { it[KEY_ONBOARDING_COMPLETED] = value }
    }

    suspend fun setSelectedScene(value: String) {
        context.dataStore.edit { it[KEY_SELECTED_SCENE] = value }
    }

    suspend fun setCameraLens(value: Int) {
        context.dataStore.edit { it[KEY_CAMERA_LENS] = value }
    }

    suspend fun setSmileEnabled(value: Boolean) {
        context.dataStore.edit { it[KEY_SMILE_ENABLED] = value }
    }

    suspend fun setSmileThreshold(value: Float) {
        context.dataStore.edit { it[KEY_SMILE_THRESHOLD] = value }
    }

    suspend fun setLowLightEnabled(value: Boolean) {
        context.dataStore.edit { it[KEY_LOWLIGHT_ENABLED] = value }
    }

    suspend fun setWatermarkEnabled(value: Boolean) {
        context.dataStore.edit { it[KEY_WATERMARK_ENABLED] = value }
    }

    suspend fun setGridEnabled(value: Boolean) {
        context.dataStore.edit { it[KEY_GRID_ENABLED] = value }
    }

    suspend fun setAutoRecommendInterval(value: Int) {
        context.dataStore.edit { it[KEY_AUTO_RECOMMEND_INTERVAL] = value }
    }

    suspend fun setTimerSeconds(value: Int) {
        context.dataStore.edit { it[KEY_TIMER_SECONDS] = value.coerceIn(0, 10) }
    }

    /** 自动推荐开关（用户可关闭姿势亲近度推荐） */
    val autoRecommendEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_AUTO_RECOMMEND_ENABLED] ?: true
    }

    suspend fun setAutoRecommendEnabled(value: Boolean) {
        context.dataStore.edit { it[KEY_AUTO_RECOMMEND_ENABLED] = value }
    }

    /** 闪光灯模式：0=关闭, 1=自动, 2=常亮 */
    val flashMode: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[KEY_FLASH_MODE] ?: 0
    }

    suspend fun setFlashMode(value: Int) {
        context.dataStore.edit { it[KEY_FLASH_MODE] = value.coerceIn(0, 2) }
    }

    /** JPEG 压缩质量（70-100），默认 90 */
    val jpegQuality: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[KEY_JPEG_QUALITY] ?: 90
    }

    suspend fun setJpegQuality(value: Int) {
        context.dataStore.edit { it[KEY_JPEG_QUALITY] = value.coerceIn(50, 100) }
    }

    /** 输出格式：0=JPEG, 1=WEBP */
    val outputFormat: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[KEY_OUTPUT_FORMAT] ?: 0
    }

    suspend fun setOutputFormat(value: Int) {
        context.dataStore.edit { it[KEY_OUTPUT_FORMAT] = value.coerceIn(0, 1) }
    }

    /** HDR 开关（设备支持时生效） */
    val hdrEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_HDR_ENABLED] ?: false
    }

    suspend fun setHdrEnabled(value: Boolean) {
        context.dataStore.edit { it[KEY_HDR_ENABLED] = value }
    }

    /** 主题模式：0=跟随系统, 1=强制暗色, 2=强制亮色 */
    val themeMode: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[KEY_THEME_MODE] ?: 0
    }

    suspend fun setThemeMode(value: Int) {
        context.dataStore.edit { it[KEY_THEME_MODE] = value.coerceIn(0, 2) }
    }
}
