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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.poseai.app.design.Brand
import com.poseai.app.model.SceneType
import com.poseai.app.model.ShootingPlan

@Composable
fun PoseGuideSheet(
    plan: ShootingPlan?,
    scene: SceneType,
    onClose: () -> Unit
) {
    Scaffold(
        containerColor = Color.Black,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "拍摄指引",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Box(
                    modifier = Modifier
                        .background(Brand.Surface, RoundedCornerShape(Brand.Radius.Small))
                        .border(1.dp, Brand.Hairline, RoundedCornerShape(Brand.Radius.Small))
                        .clickable { onClose() }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text("完成", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 场景卡片
            GuideCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(scene.icon, fontSize = 26.sp)
                    Column {
                        Text("当前场景", color = Brand.TextMuted, fontSize = 11.sp)
                        Text(scene.displayName, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // 方案卡片
            if (plan != null) {
                GuideCard {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("${plan.poseEmoji} ${plan.poseName}", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Text(plan.poseDescription, color = Brand.TextSecondary, fontSize = 13.sp)
                        }
                        Spacer(Modifier.height(4.dp))
                        InfoRow(
                            emoji = plan.composition.icon,
                            title = "${plan.composition.displayName}构图",
                            detail = plan.composition.reason
                        )
                        InfoRow(
                            emoji = plan.frameRatio.icon,
                            title = "${plan.frameRatio.displayName}拍摄",
                            detail = plan.frameRatio.distanceHint
                        )
                    }
                }
            }

            // 使用说明
            GuideCard {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("使用说明", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    UsageRow("✔", Brand.Success, "绿色边框 + 分数变绿", "姿势对齐！保持不动即自动拍照")
                    UsageRow("🧍", Color.White, "白色虚线", "未对齐，请移动身体贴合剪影")
                    UsageRow("👆", Color(0xFF4A9EFF), "点击底部卡片", "可切换推荐拍摄方案")
                    UsageRow("🔄", Brand.Coral, "左下角图标", "可切换前后置摄像头")
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun GuideCard(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brand.Surface, RoundedCornerShape(14.dp))
            .border(1.dp, Brand.Hairline, RoundedCornerShape(14.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        content()
    }
}

@Composable
private fun InfoRow(emoji: String, title: String, detail: String) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(Brand.Accent.copy(alpha = 0.15f), RoundedCornerShape(9.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(emoji, fontSize = 15.sp)
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(detail, color = Brand.TextSecondary, fontSize = 12.sp, lineHeight = 17.sp)
        }
    }
}

@Composable
private fun UsageRow(emoji: String, color: Color, title: String, detail: String) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(emoji, fontSize = 15.sp)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = color, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(detail, color = Brand.TextSecondary, fontSize = 12.sp, lineHeight = 17.sp)
        }
    }
}