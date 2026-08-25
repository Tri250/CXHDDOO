package com.poseai.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.poseai.app.design.Brand
import kotlinx.coroutines.launch

private data class OnboardingStep(
    val icon: String,
    val iconColor: Color,
    val title: String,
    val subtitle: String,
    val detail: String
)

private val onboardingSteps = listOf(
    OnboardingStep(
        icon = "📷",
        iconColor = Brand.Accent,
        title = "先对准背景",
        subtitle = "打开 App 后，把手机镜头\n对准你想拍照的场景",
        detail = "咖啡馆、海边、森林……\nAI 会自动识别并推荐最佳方案"
    ),
    OnboardingStep(
        icon = "🧍",
        iconColor = Brand.Accent,
        title = "站进人形剪影",
        subtitle = "画面中会出现一个人形轮廓\n调整位置让自己和剪影重合",
        detail = "剪影会根据你的实际身高自动缩放\n右上角分数环越高说明姿势越接近"
    ),
    OnboardingStep(
        icon = "✅",
        iconColor = Brand.Success,
        title = "保持不动，自动拍照",
        subtitle = "匹配度超过 85% 并保持 0.8 秒\nApp 会自动按下快门",
        detail = "也可以点击底部圆形按钮手动拍照\n照片自动保存到你的相册"
    )
)

@Composable
fun OnboardingScreen(
    onStart: () -> Unit,
    onSkip: () -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { onboardingSteps.size })
    val coroutineScope = rememberCoroutineScope()
    var hasAgreedToPrivacy by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // 背景渐变
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF14141F), Color.Black)
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // 跳过按钮
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.End
            ) {
                val skipAlpha by animateFloatAsState(
                    targetValue = if (pagerState.currentPage < onboardingSteps.size - 1) 1f else 0f,
                    animationSpec = tween(300),
                    label = "skipAlpha"
                )
                Text(
                    "跳过",
                    color = Color.White.copy(alpha = 0.45f * skipAlpha),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clickable { onSkip() }
                        .padding(vertical = 16.dp)
                )
            }

            Spacer(Modifier.height(8.dp))

            // 步骤内容
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(380.dp)
            ) { page ->
                StepCard(step = onboardingSteps[page])
            }

            Spacer(Modifier.height(24.dp))

            // 页码指示器
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                onboardingSteps.forEachIndexed { idx, _ ->
                    val isActive = idx == pagerState.currentPage
                    val widthAnim by animateFloatAsState(
                        targetValue = if (isActive) 24f else 8f,
                        animationSpec = spring(dampingRatio = 0.7f),
                        label = "indicatorWidth_$idx"
                    )
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .width(widthAnim.dp)
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                if (isActive) Brand.Accent else Color.White.copy(alpha = 0.2f)
                            )
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // 隐私协议（最后一页）
            if (pagerState.currentPage == onboardingSteps.size - 1) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                if (hasAgreedToPrivacy) Brand.Success else Color.White.copy(alpha = 0.15f)
                            )
                            .border(
                                1.dp,
                                if (hasAgreedToPrivacy) Brand.Success else Color.White.copy(alpha = 0.3f),
                                RoundedCornerShape(4.dp)
                            )
                            .clickable { hasAgreedToPrivacy = !hasAgreedToPrivacy },
                        contentAlignment = Alignment.Center
                    ) {
                        if (hasAgreedToPrivacy) {
                            Text("✓", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "我已阅读并同意",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 12.sp
                    )
                    Text(
                        "《隐私政策》",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                Spacer(Modifier.height(16.dp))
            } else {
                Spacer(Modifier.height(40.dp))
            }

            // 下一步 / 开始按钮
            val isLastPage = pagerState.currentPage == onboardingSteps.size - 1
            val buttonColor = when {
                isLastPage && hasAgreedToPrivacy -> Brand.Success
                isLastPage -> Color.White.copy(alpha = 0.15f)
                else -> Brand.Accent
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .background(buttonColor)
                        .clickable {
                            if (isLastPage) {
                                if (hasAgreedToPrivacy) onStart()
                            } else {
                                val nextPage = pagerState.currentPage + 1
                                if (nextPage < onboardingSteps.size) {
                                    coroutineScope.launch {
                                        pagerState.scrollToPage(nextPage)
                                    }
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            if (isLastPage) "开始拍照" else "下一步",
                            color = Color.Black,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            if (isLastPage) "📷" else "→",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun StepCard(step: OnboardingStep) {
    var appeared by remember { mutableStateOf(false) }

    LaunchedEffect(step) {
        appeared = true
    }

    val scaleAnim by animateFloatAsState(
        targetValue = if (appeared) 1f else 0.75f,
        animationSpec = spring(dampingRatio = 0.78f),
        label = "stepScale"
    )
    val alphaAnim by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = tween(durationMillis = 400),
        label = "stepAlpha"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // 图标光晕
        Box(
            modifier = Modifier
                .size(130.dp)
                .clip(CircleShape)
                .background(step.iconColor.copy(alpha = 0.12f))
                .padding(top = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .background(step.iconColor.copy(alpha = 0.07f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    step.icon,
                    fontSize = 52.sp,
                    fontWeight = FontWeight.Light,
                    color = step.iconColor,
                    modifier = Modifier.graphicsLayer {
                        scaleX = scaleAnim
                        scaleY = scaleAnim
                        alpha = alphaAnim
                    }
                )
            }
        }

        Spacer(Modifier.height(36.dp))

        // 文字
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.graphicsLayer { this.alpha = alphaAnim }
        ) {
            Text(
                step.title,
                color = Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(14.dp))

            Text(
                step.subtitle,
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp
            )

            Spacer(Modifier.height(12.dp))

            Text(
                step.detail,
                color = Color.White.copy(alpha = 0.45f),
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
            )
        }
    }
}
