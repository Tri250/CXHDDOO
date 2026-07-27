package com.poseai.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.poseai.app.PoseAIApp

private val DarkColorScheme = darkColorScheme(
    primary = Accent,
    secondary = Accent,
    background = BackgroundDark,
    surface = SurfaceDark,
    onPrimary = BackgroundDark,
    onSecondary = BackgroundDark,
    onBackground = TextPrimary,
    onSurface = TextPrimary
)

private val LightColorScheme = lightColorScheme(
    primary = Accent,
    secondary = Accent,
    background = BackgroundLight,
    surface = SurfaceLight,
    onPrimary = BackgroundLight,
    onSecondary = BackgroundLight,
    onBackground = TextPrimaryLight,
    onSurface = TextPrimaryLight
)

/**
 * 亮色/暗色主题入口
 * 用户在设置中切换后通过 StoreManager 持久化，默认跟随系统
 */
@Composable
fun PoseAITheme(content: @Composable () -> Unit) {
    val storeManager = PoseAIApp.getStoreManager()
    val themeMode by storeManager.themeMode.collectAsState(initial = 0)
    val useDarkTheme = when (themeMode) {
        1 -> true   // 强制暗色
        2 -> false  // 强制亮色
        else -> isSystemInDarkTheme()  // 0 = 跟随系统
    }
    MaterialTheme(
        colorScheme = if (useDarkTheme) DarkColorScheme else LightColorScheme,
        typography = PoseAITypography,
        content = content
    )
}
