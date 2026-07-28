package com.poseai.app.ui

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.poseai.app.ui.theme.Accent
import com.poseai.app.ui.theme.BackgroundDark
import com.poseai.app.ui.theme.TextPrimary
import com.poseai.app.ui.theme.TextSecondary

@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    pages: List<OnboardingPage> = defaultOnboardingPages
) {
    var currentPage by remember { mutableStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .padding(24.dp)
    ) {
        Spacer(modifier = Modifier.height(60.dp))

        Text(
            text = "PoseAI",
            color = Accent,
            fontSize = 36.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )

        Text(
            text = "你的私人AI姿态摄影师",
            color = TextSecondary,
            fontSize = 15.sp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.weight(1f))

        Crossfade(targetState = currentPage, label = "onboardingPage") { page ->
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = pages[page].title,
                    color = TextPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = pages[page].description,
                    color = TextSecondary,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            pages.forEachIndexed { index, _ ->
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .size(8.dp)
                        .background(
                            if (index == currentPage) Accent else Color.White.copy(alpha = 0.3f),
                            RoundedCornerShape(4.dp)
                        )
                        .semantics {
                            contentDescription = if (index == currentPage) "第${index + 1}页，当前页" else "第${index + 1}页"
                        }
                )
            }
        }

        Button(
            onClick = {
                if (currentPage < pages.size - 1) {
                    currentPage++
                } else {
                    onComplete()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Accent)
        ) {
            Text(
                text = if (currentPage < pages.size - 1) "下一步" else "开始使用",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )
        }
    }
}

data class OnboardingPage(
    val title: String,
    val description: String
)

val defaultOnboardingPages = listOf(
    OnboardingPage(
        "智能姿态识别",
        "AI 实时识别你的身体姿态，对照标准姿势进行评分和引导"
    ),
    OnboardingPage(
        "场景自适应方案",
        "自动识别当前场景，推荐最适合的拍照姿势和构图"
    ),
    OnboardingPage(
        "一键专业出片",
        "姿势到位自动抓拍，配合智能滤镜和构图，人人都是大片"
    )
)
