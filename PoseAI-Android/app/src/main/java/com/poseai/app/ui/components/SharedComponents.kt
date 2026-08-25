package com.poseai.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.poseai.app.design.Brand

// ─── iOS 风格顶部栏 ───

/**
 * iOS 风格的 Navigation Bar — 对应 UINavigationBar。
 * 统一的顶部栏组件，支持左侧按钮、标题、右侧按钮。
 */
@Composable
fun AppTopBar(
    title: String? = null,
    leftIcon: String = "‹",
    leftText: String? = null,
    rightText: String? = null,
    onLeft: () -> Unit = {},
    onRight: () -> Unit = {},
    showRight: Boolean = true,
    translucent: Boolean = false
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (translucent) Color.Transparent
                else Brand.Surface.copy(alpha = 0.95f)
            )
            .statusBarsPadding()
            .height(56.dp)
    ) {
        // 左侧按钮
        Box(
            modifier = Modifier
                .padding(start = 12.dp)
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Transparent)
                .clickable { onLeft() },
            contentAlignment = Alignment.Center
        ) {
            if (leftText != null) {
                Text(leftText, color = Brand.Accent, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            } else {
                Text(leftIcon, color = Brand.Accent, fontSize = 24.sp, fontWeight = FontWeight.Normal)
            }
        }

        // 居中标题
        if (title != null) {
            Text(
                text = title,
                color = Brand.TextPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        // 右侧按钮
        if (showRight && rightText != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 12.dp)
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Transparent)
                    .clickable { onRight() },
                contentAlignment = Alignment.Center
            ) {
                Text(rightText, color = Brand.Accent, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

// ─── iOS 风格按钮 ───

/** iOS Primary Button — 对应 UIButton .systemBlue */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: List<Color> = listOf(Brand.Accent, Brand.AccentSoft)
) {
    Box(
        modifier = modifier
            .height(54.dp)
            .clip(RoundedCornerShape(Brand.Radius.Lg))
            .then(
                if (enabled) {
                    Modifier.background(Brush.horizontalGradient(colors))
                } else {
                    Modifier.background(Brand.SurfaceHigh)
                }
            )
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (enabled) Color.White else Brand.TextMuted,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/** iOS Secondary Button — 对应 UIButton */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Box(
        modifier = modifier
            .height(54.dp)
            .clip(RoundedCornerShape(Brand.Radius.Lg))
            .background(Brand.SurfaceHigh)
            .border(1.dp, Brand.Hairline, RoundedCornerShape(Brand.Radius.Lg))
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (enabled) Brand.Accent else Brand.TextMuted,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

// ─── iOS 风格关闭按钮 ───

/** iOS Style Close Button — 对应 UIBarButtonItem close */
@Composable
fun CloseButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 40.dp
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Brand.Surface.copy(alpha = 0.85f))
            .border(1.dp, Brand.Hairline, CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text("✕", color = Brand.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}

// ─── iOS 风格 Loading 指示器 ───

/** iOS Style Loading Spinner — 对应 UIActivityIndicatorView */
@Composable
fun LoadingIndicator(
    modifier: Modifier = Modifier,
    color: Color = Brand.Accent
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        androidx.compose.material3.CircularProgressIndicator(
            modifier = Modifier.size(32.dp),
            color = color,
            strokeWidth = 3.dp
        )
    }
}

// ─── iOS 风格空状态 ───

/** iOS Style Empty State — 对应空状态视图 */
@Composable
fun EmptyState(
    emoji: String,
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(emoji, fontSize = 48.sp)
            Text(
                text = title,
                color = Brand.TextPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 16.dp)
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = Brand.TextSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}
