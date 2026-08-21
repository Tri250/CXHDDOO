# PoseAI 整体项目 Code Review 报告

**审查范围**：全部 25 个 Swift 源文件，共约 6,822 行代码  
**审查基线**：`e43ca5c..2869b83` (Step 1 → Step 14)  
**审查维度**：架构设计 · 内存安全 · 线程模型 · 逻辑正确性 · 性能 · 可维护性

---

## 🏆 优点总结

| 方面 | 评价 |
|------|------|
| **架构分层** | ViewModel 与 View 完全分离，职责清晰。`CameraManager` → `VisionService` → `ShootingViewModel` → `ContentView` 的数据链路定义明确 |
| **协议抽象** | `SceneClassificationProvider` 使用协议+降级策略（Places365 → MobileNetV2 → Mock），非常健壮 |
| **骨骼匹配算法** | `PoseMatcher` 使用向量夹角法消除距离/体型影响，含 5° 容差和半身剔除，数学上严谨 |
| **防抖设计** | 场景防抖（连续 N 帧一致）、暗光防抖（5s 间隔）、微笑防抖（低频检测窗口）逻辑合理 |
| **EMA 平滑** | VisionService 对骨骼点做 0.6/0.4 滑动平均，有效消抖，且双人模式按 `minX` 排序防止串线 |
| **Vlog 全闭环** | 录制 → 切片存储 → AVMutableComposition 拼接 → BGM 注入 → ExportSession 导出 → PHPhotoLibrary 保存，完整无遗漏 |

---

## 🔴 Critical Issues（必须修复）

### C-1. `triggerAutoPhoto()` 中 Sequence 分支为空操作

```swift
// ShootingViewModel.swift:445
} else if let seq = plan.sequence {
    // ← 空代码块！Sequence 模式被静默跳过到默认拍照
} else if let multi = plan.multiAngles {
```

**问题**：当 Plan 配置了 `sequence`（连拍序列）但没有 `vlogScript` 时，分支匹配到 `else if let seq` 后什么都不做，直接 fall-through 到默认单拍逻辑。所有连拍序列方案彻底失效。

> [!CAUTION]
> 这是一个数据丢失级 Bug——用户以为在执行连续动作拍摄，实际只拍了一张。

**修复**：

```diff
 } else if let seq = plan.sequence {
+    executeSequenceCapture(seqCount: seq.count)
+    return
 } else if let multi = plan.multiAngles {
```

---

### C-2. `VideoPreviewView` 中 NotificationCenter 观察者永不释放

```swift
// VideoPreviewView.swift:27-34
NotificationCenter.default.addObserver(
    forName: .AVPlayerItemDidPlayToEndTime,
    object: p.currentItem,
    queue: .main
) { _ in
    p.seek(to: .zero)
    p.play()
}
```

**问题**：每次 `onAppear` 都注册一个新的 `NotificationCenter` 观察者，但从未调用 `removeObserver`。SwiftUI 的 `onAppear/onDisappear` 可能被多次触发，导致：
- 每次重入叠加一个新监听器
- View 被销毁后旧监听器仍持有 `player` 引用造成内存泄漏

**修复**：存储返回的 `NSObjectProtocol` 令牌，在 `onDisappear` 中移除。

---

### C-3. `evaluateCameraState()` 未包含 `isReviewingVlog`

```swift
// ShootingViewModel.swift:406-412
func evaluateCameraState() {
    if isReviewingPhotos || showPaywall || showSessionGallery || showGuide {
        manager.stop()
    } else {
        manager.start()
    }
}
```

**问题**：进入 Vlog 回看播放时 (`isReviewingVlog == true`)，相机 session 仍在后台全速运行，浪费 CPU/GPU 和电池。ContentView 中也缺少 `.onChange(of: vm.isReviewingVlog)` 的 evaluateCameraState 调用。

**修复**：条件补齐为 `isReviewingPhotos || isReviewingVlog || showPaywall || ...`，并在 `ContentView` 中添加对应 `onChange`。

---

## 🟠 Important Issues（尽快修复）

### I-1. `VideoRecorder` 缺少线程安全防护

`VideoRecorder` 的 `isRecording`、`append(sampleBuffer:)` 和 `stopRecordingChunk` 分别在不同线程被调用：
- `isRecording` 在主线程读取（UI 绑定）
- `append()` 在 `AVCaptureVideoDataOutputSampleBufferDelegate` 的回调队列调用
- `stopRecordingChunk()` 从 ViewModel 的主线程发起

**风险**：竞态条件可能导致 `AVAssetWriter` 在已 `finishWriting` 后仍被 `append`，引发崩溃。

**建议**：引入一个串行的 `DispatchQueue` 来保护所有写入操作，或使用 `@MainActor`。

---

### I-2. `AIAdvisor.analyzeOOTD` 的 `image` 参数被完全忽略

```swift
// AIAdvisor.swift:70
func analyzeOOTD(image: UIImage, currentScene: SceneType) async -> String {
    // image 从未被使用！！
    let mockOOTDS = ["飘逸长裙", "休闲针织衫", ...]
    let currentOOTD = mockOOTDS.randomElement()!
```

**事实**：`CameraManager` 精心构建的 512px 低清截图从未被消费。用户每次拿到的穿搭建议完全是随机的，与实际衣着无关。

**评估**：作为 Mock 降级策略可以理解，但应该在 UI 层或日志中明确标注"模拟模式"，避免给用户造成"AI 真正看懂了衣服"的误导。建议至少将图片写入临时目录用于调试验证抽帧效果。

---

### I-3. `VideoMerger.merge` 使用同步 `tracks(withMediaType:)` API

```swift
// VideoMerger.swift:29-30
let asset = AVAsset(url: url)
guard let assetTrack = asset.tracks(withMediaType: .video).first else { continue }
```

**问题**：`AVAsset.tracks(withMediaType:)` 在 iOS 16+ 已被标记为 **deprecated**，这是一个同步阻塞调用，在主线程执行会造成 UI 卡顿。苹果官方推荐使用 `async` 版本：`asset.loadTracks(withMediaType:)`。

---

### I-4. Vlog 执行链嵌套回调深度过大

```swift
// ShootingViewModel.swift:512-565 (executeVlogCapture)
DispatchQueue.main.asyncAfter(...) {          // 层 1
    DispatchQueue.main.asyncAfter(...) {      // 层 2
        self.manager.videoRecorder.stopRecordingChunk { // 层 3 (异步回调)
            VideoMerger.merge(...) { finalURL in         // 层 4 (异步回调)
                DispatchQueue.main.async {                // 层 5
```

**问题**：5 层嵌套回调（经典"回调地狱"），难以阅读和调试。如果中途出现边界条件（如用户退出），多个 `DispatchWorkItem` 无法被 cancel。

**建议**：考虑将此流程重构为 `async/await` 链，或至少提取为独立方法减少嵌套层级。

---

### I-5. Info.plist 缺少 `NSPhotoLibraryUsageDescription`（读取权限）

当前只配置了 `NSPhotoLibraryAddUsageDescription`（写入权限）。`HistoryGalleryView` 通过 `PHAsset` 读取相册图片时需要读取权限（`NSPhotoLibraryUsageDescription`），否则在某些 iOS 版本上可能被系统拒绝。

---

### I-6. `VideoRecorder.reset()` 不清理 `assetWriter` 状态

```swift
func reset() {
    recordedChunks.forEach { try? FileManager.default.removeItem(at: $0) }
    recordedChunks.removeAll()
    isRecording = false
    // ← assetWriter、videoInput、currentChunkURL 未被置空
}
```

如果在录制中途调用 `reset()`（如场景切换），`assetWriter` 仍处于 writing 状态，下次 `startRecordingChunk` 时不会报错但行为不可预测。

---

## 🟡 Minor Issues（后续改进）

### M-1. `ContentView.swift` 达到 1,554 行
建议将 `SilhouetteGuideOverlay`、`PoseGuideSheet`、`PoseSilhouetteShape` 等独立子视图提取为单独文件。

### M-2. `ShootingViewModel.swift` 达到 796 行
`bind()` 方法超过 300 行，建议按功能域拆分为扩展（如 `+VlogCapture`、`+MultiAngle`、`+OOTD`）。

### M-3. `Design` 常量放在 `ContentView.swift` 顶部
应提取为独立的 `DesignSystem.swift`，其他视图文件也在引用这些常量。

### M-4. 魔法数值散布
- `PoseMatcher`: `confidence > 0.3`、容差 `5.0°`、惩罚基准 `90°`
- `VisionService`: `ratioToFace > 0.40`、`mouthAspectRatio > 3.0`
- `ShootingViewModel`: `smoothed > activeThreshold`、`best.1 > 15`、`best.1 - currentScore > 8`

建议统一集中到 `Config` 命名空间中管理。

### M-5. `VideoMerger` 临时文件未做生命周期管理
合成完毕后，`recordedChunks` 中的切片和最终的 `_final.mp4` 都位于 `tmp/`，依赖系统清理。建议在用户保存或放弃后主动清除。

### M-6. `speak()` 不支持队列
当前实现 `guard !synthesizer.isSpeaking else { return }` 会静默丢弃后续语音。在快速换场景时，重要提示可能被吞掉。建议改为队列或中断式。

### M-7. `generateAdvice()` 在 OOTD 模式下成为死代码
Step 14 将 `onSceneChange` 中原来调用 `generateAdvice` 的逻辑替换为了 `takeOOTDSnapshot`，但 `generateAdvice()` 方法本身仍然保留着。如果不再需要，应标注 `@available(*, deprecated)`。

### M-8. 测试覆盖不足
`PoseMatcherTests` 和 `SceneDebounceTests` 覆盖了核心算法，但新增的 `VideoRecorder`、`VideoMerger`、`AIAdvisor.analyzeOOTD` 均无测试。

---

## 📊 总评

| 维度 | 评分 | 说明 |
|------|------|------|
| **架构设计** | ⭐⭐⭐⭐ | 分层清晰，协议抽象优秀 |
| **功能完整性** | ⭐⭐⭐⭐ | V2 闭环完整，从录到存全链路打通 |
| **代码质量** | ⭐⭐⭐ | 少量空分支 Bug 和回调地狱需整改 |
| **内存安全** | ⭐⭐⭐ | NotificationCenter 泄漏和线程竞态需修 |
| **可维护性** | ⭐⭐⭐ | 两个巨型文件需要拆分 |
| **测试覆盖** | ⭐⭐ | 新模块缺少测试 |

## 🎯 建议修复优先级

1. **立即修复**: C-1 (Sequence 空分支)、C-2 (NotificationCenter 泄漏)、C-3 (evaluateCameraState 缺口)
2. **本周修复**: I-1 (线程安全)、I-5 (Info.plist 权限)、I-6 (reset 不完整)
3. **迭代优化**: I-3 (deprecated API)、I-4 (回调地狱)、M-1~M-8
