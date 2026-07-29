package com.poseai.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.poseai.app.ui.theme.Accent
import com.poseai.app.ui.theme.SurfaceDark
import com.poseai.app.ui.theme.TextPrimary
import com.poseai.app.ui.theme.TextSecondary

private data class ProFeature(val icon: ImageVector, val title: String, val desc: String)

private val proFeatures = listOf(
    ProFeature(Icons.Default.Spa, "高级美颜", "专业级肤质修复、五官精修、立体塑形"),
    ProFeature(Icons.Default.AutoAwesome, "AR 特效", "沉浸式 AR 面部特效与动态贴纸"),
    ProFeature(Icons.Default.SaveAlt, "自定义姿势导出", "导出专属姿势模板，随时复用"),
    ProFeature(Icons.Default.Verified, "无水印", "导出照片与视频不带 PoseAI 水印")
)

/**
 * 付费墙弹窗：展示 Pro 功能权益，提供购买、恢复购买与关闭入口。
 *
 * 使用 Material3 Dialog + Surface 卡片实现，沿用项目暗色主题配色。
 *
 * @param onPurchaseClick 点击「购买 Pro」
 * @param onRestoreClick  点击「恢复购买」
 * @param onDismiss       关闭付费墙
 */
@Composable
fun PaywallView(
    onPurchaseClick: () -> Unit,
    onRestoreClick: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = SurfaceDark,
            tonalElevation = 0.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                // 顶部：标题 + 关闭按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "升级 Pro",
                        color = TextPrimary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "关闭",
                            tint = TextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 价格徽章
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Accent.copy(alpha = 0.15f))
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "一次购买 · 永久解锁",
                        color = Accent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // 功能列表
                proFeatures.forEach { feature ->
                    ProFeatureRow(feature)
                    Spacer(modifier = Modifier.height(12.dp))
                }

                Spacer(modifier = Modifier.height(20.dp))

                // 购买按钮
                Button(
                    onClick = onPurchaseClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Accent,
                        contentColor = Color.Black
                    )
                ) {
                    Text(
                        text = "购买 Pro",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 恢复购买按钮
                OutlinedButton(
                    onClick = onRestoreClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Accent)
                ) {
                    Text(
                        text = "恢复购买",
                        fontSize = 15.sp
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "已购买过？点击「恢复购买」即可恢复 Pro 权益",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun ProFeatureRow(feature: ProFeature) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(Accent.copy(alpha = 0.15f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = feature.icon,
                contentDescription = feature.title,
                tint = Accent,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = feature.title,
                color = TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = feature.desc,
                color = TextSecondary,
                fontSize = 12.sp
            )
        }
    }
}
