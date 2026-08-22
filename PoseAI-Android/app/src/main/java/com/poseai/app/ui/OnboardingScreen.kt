package com.poseai.app.ui

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.poseai.app.design.Brand
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    val pagerState = rememberPagerState(pageCount = { 3 })
    val scope = rememberCoroutineScope()
    var privacyAgreed by remember { mutableStateOf(false) }
    val isLast = pagerState.currentPage == 2

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF141420), Color.Black)))
    ) {
        // 右上角跳过
        Box(
            modifier = Modifier
                .padding(top = 40.dp, end = 20.dp)
                .align(Alignment.TopEnd)
                .clickable(enabled = true) { onFinish() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                "跳过",
                color = Color.White.copy(alpha = if (isLast) 0f else 0.7f),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }

        // 页面内容
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize().padding(bottom = 190.dp),
        ) { page ->
            OnboardingStep(
                emoji = when (page) {
                    0 -> "📷"
                    1 -> "🧍"
                    else -> "📸"
                },
                emojiColor = if (page == 2) Brand.Success else Brand.Gold,
                title = when (page) {
                    0 -> "先对准背景"
                    1 -> "站进人形剪影"
                    else -> "保持不动，自动拍照"
                },
                subtitle = when (page) {
                    0 -> "打开 App 后，把手机镜头\n对准你想拍照的场景"
                    1 -> "画面中会出现一个人形轮廓\n调整位置让自己和剪影重合"
                    else -> "匹配度超过 85% 并保持 0.8 秒\nApp 会自动按下快门"
                },
                detail = when (page) {
                    0 -> "咖啡馆、海边、森林……\nAI 会自动识别并推荐最佳方案"
                    1 -> "剪影会根据你的实际身高自动缩放\n右上角分数环越高说明姿势越接近"
                    else -> "也可以点击底部圆形按钮手动拍照\n照片自动保存到你的相册"
                }
            )
        }

        // 底部
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 28.dp)
                .padding(bottom = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 指示点
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(3) { index ->
                    val active = index == pagerState.currentPage
                    Box(
                        modifier = Modifier
                            .height(8.dp)
                            .width(if (active) 24.dp else 8.dp)
                            .background(
                                if (active) Brand.Gold else Color.White.copy(alpha = 0.3f),
                                RoundedCornerShape(4.dp)
                            )
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // 隐私勾选（仅最后一页）
            if (isLast) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { privacyAgreed = !privacyAgreed },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .border(1.5.dp, if (privacyAgreed) Brand.Success else Color.White.copy(alpha = 0.5f), CircleShape)
                            .background(if (privacyAgreed) Brand.Success else Color.Transparent, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (privacyAgreed) {
                            Text("✓", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "我已阅读并同意《隐私政策》",
                        color = Brand.TextSecondary,
                        fontSize = 13.sp
                    )
                }
                Spacer(Modifier.height(18.dp))
            }

            // 按钮
            Button(
                onClick = {
                    if (isLast) {
                        onFinish()
                    } else {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    }
                },
                enabled = if (isLast) privacyAgreed else true,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isLast && privacyAgreed) Brand.Success else Brand.Gold,
                    contentColor = Color.Black,
                    disabledContainerColor = Brand.SurfaceHigh,
                    disabledContentColor = Brand.TextMuted
                )
            ) {
                Text(
                    if (isLast) "开始拍照" else "下一步",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun OnboardingStep(
    emoji: String,
    emojiColor: Color,
    title: String,
    subtitle: String,
    detail: String
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // 图标光圈
        Box(
            modifier = Modifier
                .size(150.dp)
                .background(emojiColor.copy(alpha = 0.15f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(112.dp)
                    .background(emojiColor.copy(alpha = 0.22f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(emoji, fontSize = 52.sp)
            }
        }

        Spacer(Modifier.height(40.dp))

        Text(
            title,
            color = Color.White,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(16.dp))

        Text(
            subtitle,
            color = Brand.TextPrimary,
            fontSize = 16.sp,
            lineHeight = 24.sp,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(12.dp))

        Text(
            detail,
            color = Brand.TextMuted,
            fontSize = 13.sp,
            lineHeight = 20.sp,
            textAlign = TextAlign.Center
        )
    }
}