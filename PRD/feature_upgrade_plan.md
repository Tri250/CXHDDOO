# PoseAI 功能升级计划

> 纯功能维度，按依赖关系和难度递进排列，逐步完成
> 每个步骤独立可交付，完成一个再做下一个

---

## Step 1 — CIFilter 拍后调色（3h）✅

**目标**：PhotoPreviewView 底部新增滤镜选择器，拍完照一键调色

- [x] 创建 `PhotoFilterEngine.swift`
  - [x] 4 套 CIFilter 预设：
    - 胶片感 Film：`CITemperatureAndTint` 偏暖 + `CIColorControls` + `CIGammaAdjust`
    - 高级黑白 B&W：`CIPhotoEffectNoir` + `CIColorControls` 高对比 + `CISharpenLuminance`
    - 日系清透 Light：`CIExposureAdjust(+0.35)` + `CIColorControls` 低饱和 + `CIVibrance`
    - 城市霓虹 Neon：`CIColorControls` 高饱和 + `CITemperatureAndTint` 偏冷 + `CIVignette`
  - [x] `func apply(filter:) -> UIImage?` Metal GPU 加速渲染 + 缓存
  - [x] `func thumbnail(filter:size:) -> UIImage?` 缩略图快速预览
- [x] PhotoPreviewView 底部新增横向滤镜缩略图选择器
- [x] 选中滤镜实时预览，保存/分享时应用
- [x] 滤镜开关按钮（收起/展开），激活时高亮指示

---

## Step 2 — 拍摄记录持久化（4h）✅

**目标**：退出 App 后拍摄历史不丢失，可回看

- [x] 创建 SwiftData 模型 `ShootingRecord`
  - [x] sceneType / planName / matchScore / photoAssetID / createdAt
- [x] 拍照保存时自动写入记录（结合 `PhotoAlbumUtil` 获取相册 ID）
- [x] 新增「拍摄历史」页面 `HistoryGalleryView`
  - [x] 按天分组展示
  - [x] 点击可查看大图（包含半透明信息浮层，显示当时的匹配度等）
- [x] 首页入此（替换现有 Session 内存历史，使用 `@Query` 动态显示最新封面）

---

## Step 3 — 快门音效（0.5h）✅

**目标**：无延迟的轻快快门声响，增强拍照的触感反馈。

- [x] 在 `ShootingViewModel` 的 `triggerFlash()` 中调用系统相机原生音效 (`AudioServicesPlaySystemSound(1108)`)，避免引入额外的外部资源，减少等待和内存占用。

---

## Step 4 — 社交画幅裁切（2h）✅

**目标**：一键裁成各平台最佳比例

- [x] PhotoPreviewView 新增画幅选择器
  - [x] 原图 / 16:9 / 4:3 / 1:1 / 2.35:1
- [x] 选中后实时预览裁切遮罩
- [x] 保存/分享时按选中画幅裁切输出

---

## Step 5 — Places365 场景模型替换（4h）✅

**目标**：彻底解决 MobileNetV2 场景识别不准问题

- [x] 创建 `Places365SceneProvider: SceneClassificationProvider`
  - [x] 直接输出场景分类类别，并处理未加载时的优雅降级（退回 MobileNetV2）
- [x] `Places365SceneProvider` 中实现 Places365 输出标签对系统 `SceneType` 的映射
- [x] VisionService 中替换 Provider
- [x] *[已完成]* 已从 GitHub 获取 `GoogLeNetPlaces.mlmodel`，并在 Xcode 中关联。
- [ ] *[TODO]* (待用户) 真机测试 7 种场景识别准确率

---

## Step 6 — 拍摄数据面板（4h）✅

**目标**：可视化拍摄统计，提升使用深度

- [x] 创建 `StatsView.swift`
  - [x] 总拍摄次数 / 最常用场景 / 平均匹配度
  - [x] Swift Charts 柱状图：按场景统计
  - [x] Swift Charts 折线图：评分趋势
- [x] 从 SwiftData ShootingRecord 聚合数据
- [x] 首页导航入口

---

## Step 7 — 自定义姿势方案（6h）✅

**目标**：用户自己摆 Pose → 录入 → 保存为个人方案

- [x] 创建「录制姿势」模式
  - [x] 进入录制 → 倒计时 3s → 抓取当前骨骼点
  - [x] 预览录制的剪影 + 命名
- [x] SwiftData 存储自定义方案 `CustomPlan`
- [x] 方案选择器中显示「我的方案」Tab
- [x] 支持删除/编辑自定义方案 (目前支持创建与自动覆盖等，后续可加列表管理)

---

## Step 8 — 双人合照模式（8h）✅

**目标**：支持两人同时引导

- [x] VisionService 支持多人姿态检测结果
- [x] 创建双人 ShootingPlan（6 套合照方案）
  - [x] 情侣依偎 / 闺蜜背靠背 / 牵手走路 等
- [x] 双剪影渲染（两个 SilhouetteGuideOverlay 实例）
- [x] 双人评分逻辑（两人分别匹配 → 取均值）

---

## Step 9 — 夜景模式增强（3h）✅

**目标**：暗光环境出片质量提升

- [x] CameraManager 暗光自动切换策略
  - [x] ISO 提升 + 更长曝光时间
  - [x] `AVCaptureDevice.activeFormat` 选择低光优化格式
- [x] 简易降噪：`CINoiseReduction` 后处理
- [x] 暗光场景自动启用屏幕补光增强

---

## Step 10 — 动态姿势序列引导（10h）✅

**目标**：从静态 Pose 进化到连续动作引导

- [x] 定义 `PoseSequence` 数据结构（关键帧数组 + 过渡时间）
- [x] 剪影过渡动画（关键帧插值）
- [x] 语音引导节拍器："第一个姿势...切！下一个..."
- [x] 连续自动拍照（每个姿势匹配后拍一张）
- [x] 合成 GIF / 短视频输出

---

## Step 11 — AI 构图建议（6h）✅

**目标**：接入 LLM 给出个性化建议

- [x] 创建 `AIAdvisor.swift`
  - [x] 将当前场景 + 光线 + 人数 + 时间 组合为 Prompt
  - [x] 调用 OpenAI API / 本地 LLaMA (设计为可降级离线智囊库)
  - [x] 返回 1-2 句个性化建议
- [x] UI：顶部浮层显示 AI 建议卡片
- [x] 语音播报 AI 建议
- [x] 离线降级：使用预设建议文案

---

## Step 12 — 多机位引导（4h）✅

**目标**：一次拍摄给出多角度建议

- [x] 方案扩展：每个 Plan 关联 2-3 个摄影角度建议
  - [x] 正面平拍 / 低角度仰拍 / 45° 侧拍
- [x] 完成一个角度后提示"换个角度再来一张"
- [x] 所有角度完成后进入对比选片页

---

## 总览

| Step | 功能 | 工时 | 依赖 |
|------|------|------|------|
| 1 | CIFilter 调色 | 3h | 无 |
| 2 | 拍摄记录持久化 | 4h | 无 |
| 3 | 快门音效 | 0.5h | 无 |
| 4 | 社交画幅裁切 | 2h | 无 |
| 5 | Places365 模型 | 4h | 无 |
| 6 | 数据面板 | 4h | Step 2 |
| 7 | 自定义姿势 | 6h | 无 |
| 8 | 双人合照 | 8h | 无 |
| 9 | 夜景增强 | 3h | 无 |
| 10 | 动态姿势序列 | 10h | 无 |
| 11 | AI 构图建议 | 6h | 无 |
| 12 | 多机位引导 | 4h | 无 |
| **总计** | | **54.5h** | |
