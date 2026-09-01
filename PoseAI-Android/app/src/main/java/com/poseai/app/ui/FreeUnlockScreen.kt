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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.poseai.app.design.Brand

@Composable
fun FreeUnlockScreen(onStart: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Brand.SurfaceLow,
                        Brand.Screen
                    )
                )
            )
    ) {
        // 右上角关闭
        Box(
            modifier = Modifier
                .padding(top = 44.dp, end = 20.dp)
                .align(Alignment.TopEnd)
                .size(40.dp)
                .background(Brand.Surface, CircleShape)
                .border(1.dp, Brand.Hairline, CircleShape)
                .clickable { onStart() },
            contentAlignment = Alignment.Center
        ) {
            Text("✕", color = Brand.TextSecondary, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 26.dp)
                .padding(top = 90.dp, bottom = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 皇冠发光光圈
            Box(
                modifier = Modifier
                    .size(110.dp)
                    .background(Brand.Gold.copy(alpha = 0.18f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(78.dp)
                        .background(Brand.Gold.copy(alpha = 0.28f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("👑", fontSize = 38.sp)
                }
            }

            Spacer(Modifier.height(28.dp))

            Text(
                "PoseAI 全部功能 · 永久免费",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(12.dp))

            Text(
                "无需付费，即开即用 · 绝无水印困扰",
                color = Brand.Success,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )

            Spacer(Modifier.height(40.dp))

            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                FeatureItem("🧩", "全场景方案库", "街道、公园、家居等专属姿势推荐全部解锁")
                FeatureItem("📸", "无限连拍·智能抓拍", "不再局限于单张，高速连拍不错过任何瞬间")
                FeatureItem("💧", "无水印纯净保存", "保存与分享均无水印，干干净净")
                FeatureItem("🎬", "Vlog 智能运镜", "多分镜自动拼接，导出电影级成片")
            }

            Spacer(Modifier.weight(1f))

            Spacer(Modifier.height(48.dp))

            // 免费开始
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .background(Brush.horizontalGradient(listOf(Brand.Accent, Brand.AccentSoft)), RoundedCornerShape(28.dp))
                    .clickable { onStart() },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("免费开始", color = Color.Black, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun FeatureItem(icon: String, title: String, desc: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .background(Brand.Gold.copy(alpha = 0.15f), CircleShape)
                    .border(1.dp, Brand.Gold.copy(alpha = 0.35f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(icon, fontSize = 19.sp)
            }
        }
        Spacer(Modifier.width(2.dp))
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(desc, color = Brand.TextSecondary, fontSize = 13.sp)
        }
    }
}