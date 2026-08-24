package com.poseai.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.poseai.app.ui.components.PrimaryButton
import com.poseai.app.ui.components.SecondaryButton

@Composable
fun OnboardingScreen(
    onStart: () -> Unit,
    onSkip: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Brand.ScreenLow, Brand.Screen, Brand.Surface)
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 80.dp)
            ) {
                // Logo - iOS 风格应用图标
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(Brand.Accent, Brand.AccentSoft)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text("📸", fontSize = 40.sp)
                }

                Spacer(modifier = Modifier.padding(top = 32.dp))

                // 标题 - iOS 风格大标题
                Text(
                    text = "PoseAI",
                    color = Color.White,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Black
                )

                Spacer(modifier = Modifier.padding(top = 8.dp))

                Text(
                    text = "AI 智能拍照助手",
                    color = Brand.TextSecondary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.padding(top = 32.dp))

                // 特性列表
                FeatureRow("🎯", "AI 实时姿态检测")
                FeatureRow("💡", "智能构图建议")
                FeatureRow("📷", "多机位拍摄指导")
                FeatureRow("🎨", "专业滤镜与美化")
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                PrimaryButton(
                    text = "开始使用",
                    onClick = onStart,
                    modifier = Modifier.fillMaxWidth()
                )

                SecondaryButton(
                    text = "跳过",
                    onClick = onSkip,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun FeatureRow(icon: String, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 6.dp)
    ) {
        Text(icon, fontSize = 20.sp)
        Text(
            text = text,
            color = Brand.TextPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 12.dp)
        )
    }
}

private fun Spacer(modifier: Modifier = Modifier) =
    Box(modifier = modifier)
