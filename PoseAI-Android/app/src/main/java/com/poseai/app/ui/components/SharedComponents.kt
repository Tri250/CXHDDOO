package com.poseai.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.poseai.app.design.Brand
import com.poseai.app.design.Type

// ─── 顶部栏 ───

/**
 * 统一顶部栏：极简返回 + 居中标题 + 可选右侧操作。
 * 实心中性灰底，去玻璃拟态。
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
                else Brand.Surface
            )
            .statusBarsPadding()
            .height(56.dp)
    ) {
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
                Text(
                    leftText,
                    style = Type.headline,
                    color = Brand.TextPrimary,
                )
            } else {
                Text(
                    leftIcon,
                    style = Type.title,
                    color = Brand.TextPrimary,
                )
            }
        }

        if (title != null) {
            Text(
                text = title,
                style = Type.title,
                color = Brand.TextPrimary,
                modifier = Modifier.align(Alignment.Center)
            )
        }

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
                Text(rightText, style = Type.headline, color = Brand.TextPrimary)
            }
        }
    }
}

// ─── 按钮 ───

/** 主按钮：中性白底 + 黑字（Doka 单色主 CTA） */
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
            style = Type.headline,
            color = if (enabled) Color.Black else Brand.TextMuted,
        )
    }
}

/** 次级按钮：中性灰底 + 主文本 */
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
            style = Type.headline,
            color = if (enabled) Brand.TextPrimary else Brand.TextMuted,
        )
    }
}

// ─── 关闭按钮 ───

/** 圆形关闭按钮 */
@Composable
fun CloseButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Brand.SurfaceStrong)
            .border(1.dp, Brand.Hairline, CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text("✕", color = Brand.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}

// ─── 加载指示器 ───

@Composable
fun LoadingIndicator(
    modifier: Modifier = Modifier,
    color: Color = Brand.TextSecondary
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            modifier = Modifier.size(32.dp),
            color = color,
            strokeWidth = 3.dp
        )
    }
}

// ─── 空状态 ───

@Composable
fun EmptyState(
    emoji: String,
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(emoji, fontSize = 44.sp)
            Text(
                text = title,
                style = Type.title,
                color = Brand.TextPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 16.dp)
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = Type.bodySecondary,
                    color = Brand.TextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}