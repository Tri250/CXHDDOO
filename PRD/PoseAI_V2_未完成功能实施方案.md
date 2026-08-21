# PoseAI V2 未完成功能实施方案

> 基于 PoseAI_V2_PRD.md 逐项代码审查后生成

## 一、完成度总览

| Sprint | 模块 | 完成度 | 状态 |
|--------|------|--------|------|
| V2-Sprint-1 | CIFilter 电影级调色引擎 | 85% | 🟡 部分完成 |
| V2-Sprint-2 | SwiftData 本地持久化 | 90% | 🟡 部分完成 |
| V2-Sprint-3 | GPT-Vision 多模态接入 | 10% | 🔴 未完成 |

## 二、未完成功能清单

### Sprint-1 遗留（0.6 天）

1. **VideoPreviewView 滤镜集成**
   - PRD 要求 `VideoPreviewView` 也可切换滤镜
   - 当前该页面没有任何滤镜代码
   - 方案：使用 `AVMutableVideoComposition` + `CIFilter` 在导出时应用滤镜

2. **滤镜切换震动反馈**
   - PRD 要求"切换带有微小震动反馈"
   - 方案：在 `PhotoPreviewView.applyFilter()` 中加入 `UIImpactFeedbackGenerator`

### Sprint-2 遗留（0.4 天）

3. **按月份流式浏览**
   - PRD 要求"支持按时间段（月份/日期）流式浏览"
   - 当前仅按日分组，缺月份维度切换
   - 方案：`HistoryGalleryView` 顶部添加月份选择器

4. **位置信息预留字段**
   - PRD 要求 `ShootingRecord` 预留位置信息
   - 方案：添加 `latitude: Double?` 和 `longitude: Double?` 字段

5. **StatsView 语法 Bug**
   - 第 65 行 `guard !records.isEmpty else else` 重复 else

### Sprint-3 遗留（2.5 天）

6. **GPT-Vision 真实 API 接入**
   - 当前 `AIAdvisor` 使用纯本地 mock 随机词
   - 方案：构建 base64 图片 + 场景上下文 prompt 发送至 Vision API

7. **光线参数 + GPS 上下文提取**
   - PRD 要求传递相机硬件返回的光线参数 + GPS 定位
   - 方案：`CameraManager` 提取 ISO/曝光时长，新建 `LocationService` 管理定位

8. **10 秒超时降级逻辑**
   - PRD 要求网络请求超时后降级为离线模式
   - 方案：`withThrowingTaskGroup` 实现竞速超时，失败回落原有 mock 引擎

## 三、待确认事项

1. Sprint-3 所使用的大模型 API 选型（OpenAI / Claude / 通义千问 / 其他）
2. API Key 管理方式（客户端直连 vs 后端中转）
3. 如暂无 API 条件，是否先仅执行 Sprint-1 + Sprint-2 遗留

## 四、文件变更清单

| 操作 | 文件 | 所属批次 |
|------|------|----------|
| MODIFY | PhotoPreviewView.swift | 批次1 |
| MODIFY | VideoPreviewView.swift | 批次1 |
| MODIFY | PhotoFilterEngine.swift | 批次1 |
| MODIFY | ShootingRecord.swift | 批次2 |
| MODIFY | HistoryGalleryView.swift | 批次2 |
| MODIFY | StatsView.swift | 批次2 |
| NEW | LocationService.swift | 批次3 |
| MODIFY | Info.plist | 批次3 |
| MODIFY | CameraManager.swift | 批次3 |
| MODIFY | AIAdvisor.swift | 批次3 |
| MODIFY | ShootingViewModel.swift | 批次3 |
| MODIFY | ContentView.swift | 批次3 |
| MODIFY | PoseAIApp.swift | 批次3 |
