package com.poseai.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.poseai.app.design.Brand
import com.poseai.app.model.NormPoint

/** 骨骼连线：from -> to */
private val POSE_CONNECTIONS = listOf(
    "neck" to "leftShoulder",
    "neck" to "rightShoulder",
    "leftShoulder" to "leftElbow",
    "rightShoulder" to "rightElbow",
    "leftElbow" to "leftWrist",
    "rightElbow" to "rightWrist",
    "neck" to "leftHip",
    "neck" to "rightHip",
    "leftHip" to "leftKnee",
    "rightHip" to "rightKnee",
    "leftKnee" to "leftAnkle",
    "rightKnee" to "rightAnkle"
)

/**
 * 保存自定义方案页——复刻 iOS SaveCustomPlanView。
 * 名称 + Emoji 输入，骨骼预览，保存回调返回 name/emoji。
 */
@Composable
fun SaveCustomPlanScreen(
    points: Map<String, NormPoint>,
    onCancel: () -> Unit,
    onSave: (name: String, emoji: String) -> Unit
) {
    var poseName by remember { mutableStateOf("") }
    var emoji by remember { mutableStateOf("✨") }
    val canSave = poseName.isNotBlank()

    Column(modifier = Modifier.fillMaxSize().background(Brand.Screen)) {
        // 顶部栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onCancel) {
                Text("取消", color = Brand.TextSecondary, fontSize = 15.sp)
            }
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text("保存姿势", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            }
            TextButton(onClick = { if (canSave) onSave(poseName, emoji) }) {
                Text(
                    "保存",
                    color = if (canSave) Brand.Accent else Brand.TextMuted.copy(alpha = 0.5f),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // 骨骼预览
        PosePreview(points = points)

        Spacer(Modifier.height(20.dp))

        // 输入区
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = poseName,
                onValueChange = { poseName = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("例如：显高交叉腿", color = Brand.TextMuted, fontSize = 14.sp) },
                label = { Text("名称", color = Brand.TextSecondary) },
                textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 15.sp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Brand.Accent,
                    unfocusedBorderColor = Brand.Hairline,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = Brand.Accent,
                    focusedLabelColor = Brand.Accent,
                    unfocusedLabelColor = Brand.TextSecondary
                )
            )

            OutlinedTextField(
                value = emoji,
                onValueChange = { emoji = it.take(1) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Emoji", color = Brand.TextSecondary) },
                textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 15.sp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Brand.Accent,
                    unfocusedBorderColor = Brand.Hairline,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = Brand.Accent,
                    focusedLabelColor = Brand.Accent,
                    unfocusedLabelColor = Brand.TextSecondary
                )
            )
        }
    }
}

/** 骨骼预览画布：归一化坐标直接映射到画布尺寸（x*width, y*height） */
@Composable
private fun PosePreview(points: Map<String, NormPoint>) {
    Canvas(modifier = Modifier.fillMaxWidth().height(250.dp)) {
        val w = size.width
        val h = size.height

        fun p(name: String): Offset? {
            val pt = points[name] ?: return null
            return Offset(pt.x * w, pt.y * h)
        }

        // 连线
        val lineWidth = 3.dp.toPx()
        for ((a, b) in POSE_CONNECTIONS) {
            val pa = p(a) ?: continue
            val pb = p(b) ?: continue
            drawLine(Brand.Accent, pa, pb, strokeWidth = lineWidth, cap = StrokeCap.Round)
        }

        // 关节点
        val dotRadius = 4.dp.toPx()
        for (pt in points.values) {
            drawCircle(Color.White, radius = dotRadius, center = Offset(pt.x * w, pt.y * h))
        }
    }
}