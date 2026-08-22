# Android 转换协作契约

本文件用于并行子代理转换时保持一致。所有 Kotlin 代码位于 `/workspace/PoseAI-Android/app/src/main/java/com/poseai/app/`，
包名 `com.poseai.app`，Kotlin + Jetpack Compose，全暗色主题，全功能免费（无任何内购）。

## 共享类型（已在 model/Models.kt 与 model/Enums.kt 定义，必须直接使用）
- `data class NormPoint(x: Float, y: Float)` — 归一化二维点，x 向右，y 向上（与 iOS Vision 一致）
- `data class ShootingPlan(id, poseName, poseEmoji, poseDescription, composition, frameRatio, voiceGuide, posePoints, secondaryPosePoints?, sequence?, multiAngles?, vlogScript?)`
- `enum FrameRatio(FULL_BODY/HALF_BODY/PORTRAIT)`，`.heightRatio` `.displayName` `.distanceHint`
- `enum CompositionRule(CENTER/LEFT_THIRD/RIGHT_THIRD/GOLDEN_LEFT/GOLDEN_RIGHT)`，`.offset` `.displayName` `.reason` `.voiceHint`
- `enum SceneType(COFFEE_SHOP/BEACH/FOREST/CITY_STREET/PARK/INDOOR_HOME/NEON_NIGHT/UNKNOWN)`，`.displayName` `.icon`，`.plans` 返回方案
- `enum CropRatio(ORIGINAL/SQUARE/FOUR_THREE/SIXTEEN_NINE/CINEMA)`，`.displayName` `.targetRatio`
- `enum PhotoFilter(ORIGINAL/FILM/BW/LIGHT/NEON)`，`.displayName` `.rawValue`
- `data class ActionFrame(emoji,title,voiceHint,posePoints)`，`data class CameraAngle(title,voiceHint,requiredPitch:Float?,posePoints?)`
- `data class VlogClip(durationSeconds,voiceCommand,overlayText)`，`data class VlogTemplate(bgmFilename?,clips)`
- `object PoseLibrary`：`plansFor(scene): List<ShootingPlan>`、`plan(id)`、`allPlans`
- `object PoseMatcher`（ml 包）：`calculateAngle(p1,center,p2):Float`、`calculateSimilarity(current,preset,isHalfBody):Float`

## 技术映射（iOS → Android）
- SwiftUI → Jetpack Compose；@State/@Published → `StateFlow`（在 ViewModel 中）
- Combine → Kotlin 协程 `viewModelScope.launch`
- AVFoundation 相机 → CameraX（camera-core/camera-camera2/lifecycle/view/video 1.4.1）
- Vision 人体姿态 → ML Kit `com.google.mlkit:pose-detection`（`PoseDetection.getClient`）
- Vision 场景/ImageNet → ML Kit Image Labeling（离线标签），用关键词映射到 SceneType
- SwiftData/CoreData → Room（room-runtime/room-ktx 2.6.1，KSP 编译器）
- CIFilter → android.graphics.ColorMatrix
- AVAssetExportSession/视频合成 → MediaExtractor + MediaMuxer
- UIActivityViewController → android.intent.action.SEND (startActivity)
- AVSpeechSynthesizer → android.speech.tts.TextToSpeech
- SwiftUI Canvas 剪影 → Compose Canvas；`landscapeOrientation` 已统一竖屏
- 相册保存 → MediaStore（仅 Android Q+，minSdk 26）
- GateKeeper（内购 Paywall）→ 全部免费，不实现内购。iOS 的 PaywallView 转换为一页「全功能免费」说明页。

## 状态/命名
- 主状态统一放 `ShootingViewModel`（ui 包），公开 `StateFlow`，管线方法；UI 通过 collectAsStateWithLifecycle 读取。
- 场景切换、姿态数据回调、拍摄结果回传用回调接口注入 VM。
- 严格使用 `compose-bom:2024.10.01`、material3、icons-extended、activity-compose、lifecycle-viewmodel-compose。

## 构建与运行
```
ANDROID_HOME=/opt/android-sdk /root/.local/share/mise/installs/java/17.0.2/bin/java \
 -Dorg.gradle.java.home=/root/.local/share/mise/installs/java/17.0.2/bin/../ \
 -jar $(which) ... # 实际在 /workspace/PoseAI-Android 目录执行 gradle assembleDebug
```
如需编译验证，工作目录为 `/workspace/PoseAI-Android`。