# PoseAI 优化升级 — 任务追踪

## S1 — 上架达标（Week 1-2）✅

### Sprint 1.1 — 架构重构
- [x] **T1.1.1** 提取 ShootingViewModel（8h）
  - 30+ @State 变量迁移到 ObservableObject
  - 所有回调闭包改用 [weak self]
  - ContentView 仅保留 UI 布局
- [x] **T1.1.2** 拆分 UI 子视图（6h）
  - PlanCard / TagBadge / ScanCornerLines / CompositionGuideLines 独立组件
  - SilhouetteGuideOverlay + PoseSilhouetteShape 独立组件
  - PoseGuideSheet / GuideInfoRow / GuideRow 独立组件
- [x] **T1.1.3** 提取独立文件（2h）
  - PhotoPreviewView.swift（照片预览 + 水印 + Gallery）
  - PaywallView.swift（内购页面 + ProFeatureRow）

### Sprint 1.2 — 场景识别修复
- [x] **T1.2.1** 补全 7 场景关键词投票（3h）
  - 新增 city_street(45词), park(30词), indoor_home(34词), neon_night(24词) 关键词
  - 优化 coffee_shop 关键词（移除与新场景交叉项）
  - votes 字典从 3→7 场景覆盖

### Sprint 1.3 — 技术债清理
- [x] **T1.3.1** 清理遗留文件和代码（1h）
  - 删除 test_vision.swift
  - 移除废弃 Pose 结构体 + recommendedPose 计算属性
  - 所有 print() 包裹 #if DEBUG
- [x] **T1.3.2** Accessibility 基础支持（2h）
  - 快门按钮 accessibilityLabel
  - 分数环 accessibilityLabel
  - 摄像头切换/倒计时/历史 accessibilityLabel
  - PlanCard accessibilityLabel + isSelected trait

---

## S2 — 体验强化（Week 3-4）

### Sprint 2.1 — StoreKit 2 真实接入
- [x] **T2.1.1** 创建 StoreManager（10h）
  - StoreKit 2 原生 API（Non-Consumable 终身买断）
  - 商品加载 + 购买 + 恢复 + Transaction 后台监听
  - isPro 兼容旧 AppStorage 单向同步
- [x] **T2.1.2** 更新 PaywallView 对接（3h）
  - 动态商品价格 + 真实购买/恢复
  - 5 种 UI 状态（正常/加载/购买中/成功/错误）
  - ShootingViewModel + PhotoPreviewView isPro 迁移
  - PoseAIApp 全局 StoreManager 注入

### Sprint 2.2 — 单元测试基建
- [x] **T2.2.1** 编写测试文件（8h）
  - PoseMatcherTests (7 用例) + ModelTests (8 用例) + SceneDebounceTests (6 用例)
  - ⚠️ 需在 Xcode 中创建 PoseAITests Target

### Sprint 2.3 — 英文本地化
- [x] **T2.3.1** 创建 Localizable.xcstrings（5h）
  - ~65 条中英翻译（UI/Paywall/语音/Accessibility/场景/构图/比例）
  - ⚠️ 需在各 Swift 文件中替换硬编码为 String(localized:) 调用

## S3 — 差异化功能（Week 5-8）

### Sprint 3.1 — AI 调色预设
- [ ] **T3.1.1** CIFilter 调色引擎（10h）

### Sprint 3.2 — 数据持久化
- [ ] **T3.2.1** 拍摄记录持久化（8h）

### Sprint 3.3 — 上架资产与提审
- [ ] **T3.3.1** App Store 资产准备（6h）
- [ ] **T3.3.2** 提审前 Checklist（4h）
