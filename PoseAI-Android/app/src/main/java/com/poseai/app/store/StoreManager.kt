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
        val KEY_PRO_UNLOCKED = booleanPreferencesKey("pro_unlocked")
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
    }

    val onboardingCompleted: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_ONBOARDING_COMPLETED] ?: false
    }

    val proUnlocked: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_PRO_UNLOCKED] ?: false
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

    suspend fun setProUnlocked(value: Boolean) {
        context.dataStore.edit { it[KEY_PRO_UNLOCKED] = value }
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
}
