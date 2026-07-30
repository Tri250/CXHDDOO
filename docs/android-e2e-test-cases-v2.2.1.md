# PoseAI Android v2.2.1 — 操作链路完整（E2E）测试用例与验收标准

> **版本**: v2.2.1 (versionCode=12)
> **覆盖率目标**: 100% 功能模块的端到端操作链路
> **测试类型**: 端到端（E2E）操作链路测试 — 覆盖完整用户旅程、状态流转、跨模块衔接与异常恢复
> **测试环境**: Android 8.0~15 (API 26~35)，真机 + 模拟器
> **依据**: 代码库 53 个 Kotlin 源文件逐链路梳理，10 大主链路 + 跨链路组合 + 异常恢复链路
> **区别**: 本文档聚焦"操作链路完整性"，与 `android-functional-test-cases-v2.2.1.md`（单点功能验证）互补

---

## 目录

| 链路 | 用例数 | 覆盖链路 |
|------|--------|----------|
| [E1. 应用启动与导航链路](#e1-应用启动与导航链路) | 10 | 冷启动/引导/AI激活/权限/导航/热启动/付费墙入口 |
| [E2. 拍照完整链路](#e2-拍照完整链路) | 14 | 预览/姿势检测/构图/倒计时/连拍/微笑/保存/相册闭环 |
| [E3. Vlog 录制完整链路](#e3-vlog-录制完整链路) | 10 | 模板选择/分镜录制/合成/BGM/字幕/导出/分享闭环 |
| [E4. 照片编辑完整链路](#e4-照片编辑完整链路) | 12 | 选图/加载/裁剪/旋转/滤镜/撤销/保存/DB刷新闭环 |
| [E5. OOTD 穿搭分析完整链路](#e5-ootd-穿搭分析完整链路) | 8 | 选图/复制/解码/分析/评分/建议/资源释放闭环 |
| [E6. 内购完整链路](#e6-内购完整链路) | 10 | 触发/连接/购买/确认/解锁/恢复/重连/已拥有闭环 |
| [E7. 自定义姿势完整链路](#e7-自定义姿势完整链路) | 8 | 打开/保存/去重/应用/覆盖/删除/清空/场景切换闭环 |
| [E8. 设置与主题切换链路](#e8-设置与主题切换链路) | 10 | 主题/网格/微笑/水印/倒计时/画质/HDR/补光/持久化闭环 |
| [E9. 分享完整链路](#e9-分享完整链路) | 10 | 打开/预览/水印/话题/FileProvider/系统面板/关闭闭环 |
| [E10. 异常恢复链路](#e10-异常恢复链路) | 14 | 权限/相机/存储/AI模型/合成/购买/生命周期/电池温度 |
| [E11. 跨链路组合场景](#e11-跨链路组合场景) | 10 | 拍照→编辑→分享/录制→编辑/Vlog→相册/设置→拍摄闭环 |
| **合计** | **116** | **11 大链路 100% 覆盖** |

---

## 测试约定

### 优先级定义
| 级别 | 含义 | 通过标准 |
|------|------|---------|
| P0 | 阻断级 — 核心链路不可走通 | 100% 必须通过 |
| P1 | 严重 — 主要链路异常 | 100% 必须通过 |
| P2 | 一般 — 次要链路或降级路径 | ≥95% 通过 |
| P3 | 轻微 — 体验/边界路径 | ≥90% 通过 |

### 用例结构
每条 E2E 用例包含：
- **链路图**：步骤序列的精简流转图
- **状态断言点**：链路中需验证的关键 StateFlow/DataStore/DB 状态
- **验收标准**：链路走通的可观测判据（UI/文件/日志/DB）

### 前置条件
- 测试设备已安装 PoseAI v2.2.1 APK
- 设备已连接网络（内购/AI 模型/OOTD 在线分析需要）
- 设备摄像头可用，存储空间 ≥ 500MB
- 测试账号：Google Play 测试账号（用于内购测试）

---

## E1. 应用启动与导航链路

### TC-E1-01: 首次冷启动完整链路（引导→AI激活→拍摄）
| 项 | 内容 |
|----|------|
| **用例ID** | TC-E1-01 |
| **优先级** | P0 |
| **链路图** | 启动器图标 → PoseAIApp.onCreate → MainActivity → onboardingCompleted=false → OnboardingScreen(3页) → 点击"开始使用" → ai_activation → 激活/跳过 → shooting |
| **前置条件** | 首次安装或清除数据 |
| **操作步骤** | 1. 安装 APK 2. 点击启动器图标 3. 翻阅引导页 3 页 4. 点击"开始使用" 5. 进入 AI 激活页 6. 点击"跳过"或等待激活 |
| **状态断言点** | ① `onboardingCompleted=true` 写入 DataStore ② `aiModelManager.isActivated` 状态更新 ③ 起始 destination 切换为 shooting ④ `hasCameraPermission` 判断分支 |
| **预期结果** | 引导页 Crossfade 动画完成，跳转 AI 激活页，最终进入拍摄页 |
| **验收标准** | 全局单例（Database/StoreManager/AIModelManager/CustomPoseStore/BillingManager）就绪，logcat 无初始化异常，拍摄页预览显示 |

### TC-E1-02: 非首次冷启动直达拍摄页
| 项 | 内容 |
|----|------|
| **用例ID** | TC-E1-02 |
| **优先级** | P0 |
| **链路图** | 启动器图标 → PoseAIApp.onCreate → MainActivity → onboardingCompleted=true → 直达 shooting |
| **前置条件** | 已完成引导 |
| **操作步骤** | 1. 冷启动应用 |
| **状态断言点** | ① 起点destination=shooting ② `billingManager.startConnection()` 被调用 ③ `restorePurchases()` 连接成功后触发 |
| **预期结果** | 跳过引导页和 AI 激活页，直接进入拍摄页 |
| **验收标准** | 拍摄页预览正常，BillingClient 自动连接并恢复购买状态 |

### TC-E1-03: 相机权限缺失阻断链路
| 项 | 内容 |
|----|------|
| **用例ID** | TC-E1-03 |
| **优先级** | P0 |
| **链路图** | 启动 → hasCameraPermission=false → PermissionRequestScreen → 拒绝 → 阻断 → 允许 → 恢复导航 |
| **前置条件** | 相机权限未授予 |
| **操作步骤** | 1. 启动应用 2. 权限弹窗点击"拒绝" 3. 观察权限请求页 4. 点击跳转设置 5. 设置中授权相机 6. 返回应用 |
| **状态断言点** | ① `hasCameraPermission=false` 显示 PermissionRequestScreen ② `onResume` 调用 `updatePermissionStates()` ③ 权限变更后自动启动预览 |
| **预期结果** | 拒绝时显示权限请求页，设置授权返回后预览自动启动 |
| **验收标准** | 权限请求页显示"去设置"按钮，返回后 `updatePermissionStates` 刷新，预览恢复无崩溃 |

### TC-E1-04: 底部导航 4 项切换链路
| 项 | 内容 |
|----|------|
| **用例ID** | TC-E1-04 |
| **优先级** | P1 |
| **链路图** | shooting → gallery → ootd → pro(触发付费墙) → 关闭 → shooting |
| **前置条件** | 主界面 |
| **操作步骤** | 1. 点击"相册" 2. 点击"穿搭" 3. 点击"Pro" 4. 关闭付费墙 5. 返回拍摄 |
| **状态断言点** | ① `showBottomBar` 仅在 gallery/ootd 为 true ② shooting 页隐藏底部栏 ③ pro 项触发 `onShowPaywall()` 而非导航 |
| **预期结果** | 相册/穿搭正常切换，Pro 触发付费墙弹窗，拍摄页全屏沉浸 |
| **验收标准** | 导路由 NavController 管控，Pro 入口不导航，付费墙 Dialog 可关闭 |

### TC-E1-05: 热启动恢复链路
| 项 | 内容 |
|----|------|
| **用例ID** | TC-E1-05 |
| **优先级** | P1 |
| **链路图** | 拍摄中 → Home 键 → 后台(onPause/onAppBackground) → 最近任务 → 前台(onResume/onAppForeground) → 恢复预览 |
| **前置条件** | 拍摄页预览中 |
| **操作步骤** | 1. 按 Home 键退后台 2. 等待 5 秒 3. 从最近任务恢复 |
| **状态断言点** | ① `onPause` 调用 `onAppBackground()`（取消倒计时/停TTS/注销传感器）② `onResume` 调用 `onAppForeground()`（重注册传感器）③ 相机预览恢复 |
| **预期结果** | 后台暂停，前台恢复无黑屏无崩溃 |
| **验收标准** | CameraX 生命周期绑定正确，传感器重新注册，无内存泄漏 |

### TC-E1-06: 电池/温度监测链路
| 项 | 内容 |
|----|------|
| **用例ID** | TC-E1-06 |
| **优先级** | P2 |
| **链路图** | onStart → 注册 batteryReceiver → 低电量/过热广播 → ViewModel.setBatteryLow/setHeatWarning → UI 警告 → onStop → 注销 |
| **前置条件** | 应用前台运行 |
| **操作步骤** | 1. 模拟电量低于 20% 2. 模拟温度高于 45°C 3. 观察警告 4. 退后台 |
| **状态断言点** | ① `isBatteryLow=true` ② `isHeatWarning=true` ③ 姿势检测间隔提升到 200ms（`POSE_FRAME_INTERVAL_HOT`）④ onStop 注销广播 |
| **预期结果** | 低电量和过热警告显示，过热时降帧保护 |
| **验收标准** | StateFlow 正确更新，UI 显示警告，过热降帧生效 |

### TC-E1-07: 配置变化自处理链路
| 项 | 内容 |
|----|------|
| **用例ID** | TC-E1-07 |
| **优先级** | P2 |
| **链路图** | 旋转屏幕/切换主题 → Manifest 自处理 → Activity 不重建 → 实时响应 |
| **前置条件** | 应用运行中 |
| **操作步骤** | 1. 旋转屏幕 2. 切换系统暗色模式 |
| **状态断言点** | ① Manifest 声明 `orientation|screenSize|uiMode` 自处理 ② Activity 不重建 ③ 主题实时切换 |
| **预期结果** | 旋转不重建 Activity，主题切换实时生效 |
| **验收标准** | 相机预览不中断，主题立即响应 |

### TC-E1-08: edge-to-edge 适配链路
| 项 | 内容 |
|----|------|
| **用例ID** | TC-E1-08 |
| **优先级** | P2 |
| **链路图** | Android 15+ 启动 → enableEdgeToEdge → 状态栏透明 → 内容延伸 |
| **前置条件** | Android 15+ 设备 |
| **操作步骤** | 1. 启动应用 2. 观察状态栏/导航栏 |
| **状态断言点** | ① `enableEdgeToEdge` 生效 ② 状态栏透明 ③ 图标可见 |
| **预期结果** | 内容延伸到系统栏 |
| **验收标准** | 沉浸式显示，状态栏图标不遮挡 |

### TC-E1-09: 引导页完成持久化闭环
| 项 | 内容 |
|----|------|
| **用例ID** | TC-E1-09 |
| **优先级** | P1 |
| **链路图** | 引导第3页 → 点击"开始使用" → DataStore写入 → 杀进程 → 重启 → 跳过引导 |
| **前置条件** | 引导页第3页 |
| **操作步骤** | 1. 点击"开始使用" 2. 强杀应用 3. 重新冷启动 |
| **状态断言点** | ① `onboardingCompleted=true` 持久化 ② 重启后起点destination=shooting |
| **预期结果** | 重启后跳过引导页 |
| **验收标准** | DataStore 持久化生效，重启直达拍摄页 |

### TC-E1-10: 全局异常处理链路
| 项 | 内容 |
|----|------|
| **用例ID** | TC-E1-10 |
| **优先级** | P2 |
| **链路图** | 运行中触发未捕获异常 → setupUncaughtExceptionHandler → 记录日志 → 转交默认处理器 → 进程终止 |
| **前置条件** | 应用运行中 |
| **操作步骤** | 1. 模拟 TFLite/ML Kit 内部异常 |
| **状态断言点** | ① 异常被捕获 ② 日志含线程名/异常/设备型号/版本 ③ 转交默认处理器 |
| **预期结果** | 崩溃日志完整记录，进程正常终止 |
| **验收标准** | logcat 含设备信息，不阻止默认终止行为 |

---

## E2. 拍照完整链路

### TC-E2-01: 标准拍照完整链路（预览→拍照→保存→相册）
| 项 | 内容 |
|----|------|
| **用例ID** | TC-E2-01 |
| **优先级** | P0 |
| **链路图** | 进入shooting → initCamera → 预览启动 → 帧分析器(姿势/微笑/场景/暗光) → 点击快门 → executeCapture → 快门闪光+音+震 → CameraManager.takePhoto → processAndSavePhoto(降噪/HDR/美颜/滤镜/水印/裁切/贴纸) → Room写入 → MediaStore入库 → 预览照片 → 相册可见 |
| **前置条件** | 相机权限已授予 |
| **操作步骤** | 1. 进入拍摄页 2. 等待预览稳定 3. 点击快门按钮 4. 观察快门动画 5. 等待保存完成 6. 切换到相册 |
| **状态断言点** | ① `cameraLens=0` 后置预览 ② `_showShutterFlash=true`(180ms) ③ `_captureCount++` ④ `ShootingRecord` 写入 Room ⑤ `MediaStoreHelper.addImageToGallery` 成功 ⑥ `_isReviewingPhoto=true` ⑦ 相册 Flow 刷新 |
| **预期结果** | 拍照完成后照片出现在相册，预览页显示刚拍照片 |
| **验收标准** | 照片文件存在于 Pictures/PoseAI，Room 记录含 imagePath/poseScore/timestamp，相册列表立即刷新 |

### TC-E2-02: 倒计时拍照链路
| 项 | 内容 |
|----|------|
| **用例ID** | TC-E2-02 |
| **优先级** | P1 |
| **链路图** | 设置timerSeconds=5 → 点击快门 → startCountdown(5) → 倒计时显示5/4/3/2/1 → 到0 → executeCapture → 保存 |
| **前置条件** | 预览中 |
| **操作步骤** | 1. 设置倒计时 5 秒 2. 点击快门 3. 观察倒计时 4. 等待拍照 |
| **状态断言点** | ① `_timerSeconds=5` ② `_countdownValue` 递减 5→0 ③ 到 0 触发 `executeCapture()` ④ 倒计时中再点快门取消并立即拍照 |
| **预期结果** | 倒计时完成后自动拍照 |
| **验收标准** | 倒计时数字显示，到 0 触发拍照，倒计时中点击可取消 |

### TC-E2-03: 连拍模式链路
| 项 | 内容 |
|----|------|
| **用例ID** | TC-E2-03 |
| **优先级** | P1 |
| **链路图** | 开启连拍 → 点击快门 → executeBurstCapture(3张,间隔400ms) → 逐张保存 → 相册3条记录 |
| **前置条件** | 预览中 |
| **操作步骤** | 1. 开启连拍模式 2. 点击快门 3. 观察 3 次快门 4. 查看相册 |
| **状态断言点** | ① `_isBurstMode=true` ② 3 次 `executeCapture` 调用 ③ `_captureCount` +3 ④ 相册 3 条新记录 |
| **预期结果** | 连拍 3 张照片保存到相册 |
| **验收标准** | 3 张照片时间戳递增，间隔约 400ms，相册可见 3 条 |

### TC-E2-04: 微笑快门自动拍照链路
| 项 | 内容 |
|----|------|
| **用例ID** | TC-E2-04 |
| **优先级** | P1 |
| **链路图** | 开启微笑快门 → 帧分析器检测微笑 → 笑容值>阈值 → 自动拍照 |
| **前置条件** | 预览中，微笑快门已开启 |
| **操作步骤** | 1. 开启微笑快门 2. 调整灵敏度 3. 面对镜头微笑 4. 等待自动拍照 |
| **状态断言点** | ① `smileEnabled=true` ② `smileDetector.triggerThreshold` 生效 ③ 笑容值超阈值触发 `executeCapture` |
| **预期结果** | 微笑达到阈值后自动拍照 |
| **验收标准** | 阈值可调（0.3-0.95），自动拍照无延迟过大 |

### TC-E2-05: 姿势检测与评分链路
| 项 | 内容 |
|----|------|
| **用例ID** | TC-E2-05 |
| **优先级** | P1 |
| **链路图** | 预览 → 帧分析器 → PoseDetectorEngine检测 → EMA平滑 → 与当前方案模板比对 → PoseSimilarityModel评分 → _poseScore更新 → UI分数环显示 |
| **前置条件** | 智能导拍模式，选择姿势模板 |
| **操作步骤** | 1. 进入智能导拍 2. 选择姿势模板 3. 摆姿势 4. 观察分数环 |
| **状态断言点** | ① `PoseDetectorEngine` 检测到骨骼点 ② EMA 平滑后坐标稳定 ③ `PoseSimilarityModel` 返回相似度 ④ `_poseScore` 更新 ⑤ 分数环 UI 同步 |
| **预期结果** | 姿势越接近模板分数越高 |
| **验收标准** | 分数实时更新，无大幅跳变，分数环颜色随分数变化 |

### TC-E2-06: 姿势相似度模型降级链路
| 项 | 内容 |
|----|------|
| **用例ID** | TC-E2-06 |
| **优先级** | P2 |
| **链路图** | PoseSimilarityModel加载失败 → 降级到 PoseUtils.calculateSimilarity(欧氏距离) → 评分继续 |
| **前置条件** | TFLite 模型文件损坏 |
| **操作步骤** | 1. 破坏模型文件 2. 启动应用 3. 进入智能导拍 4. 观察评分 |
| **状态断言点** | ① `PoseSimilarityModel` 不可用 ② 降级到 `PoseUtils.calculateSimilarity` ③ 评分仍可计算 |
| **预期结果** | 模型失败时降级到欧氏距离，评分功能不中断 |
| **验收标准** | logcat 记录降级，评分正常显示，无崩溃 |

### TC-E2-07: 构图引导链路
| 项 | 内容 |
|----|------|
| **用例ID** | TC-E2-07 |
| **优先级** | P2 |
| **链路图** | 智能导拍 → 网格线/剪影叠加 → 分数环 → 距离检测 → 留白检测 → 人脸EV联动 → 智能裁切建议 |
| **前置条件** | 智能导拍模式 |
| **操作步骤** | 1. 开启网格线 2. 选择姿势 3. 观察构图引导元素 |
| **状态断言点** | ① 网格/剪影/分数环叠加显示 ② `_isHeadroomWarning`/距离警告 ③ 人脸 EV 联动 |
| **预期结果** | 构图引导元素正确叠加 |
| **验收标准** | 引导不遮挡关键信息，警告实时响应 |

### TC-E2-08: 自动抓拍链路
| 项 | 内容 |
|----|------|
| **用例ID** | TC-E2-08 |
| **优先级** | P2 |
| **链路图** | 姿势评分≥85 → 稳定800ms → 自动抓拍 → 保存 |
| **前置条件** | 智能导拍，自动抓拍开启 |
| **操作步骤** | 1. 进入智能导拍 2. 摆出高匹配姿势 3. 保持稳定 4. 等待自动拍照 |
| **状态断言点** | ① `_poseScore>=AUTO_CAPTURE_SCORE_THRESHOLD(85)` ② 稳定 `AUTO_CAPTURE_STABILITY_MS(800ms)` ③ `_isAutoCapturing=true` ④ 触发 `executeCapture` |
| **预期结果** | 高分稳定后自动拍照 |
| **验收标准** | 阈值 85，稳定 800ms，自动拍照无延迟 |

### TC-E2-09: 拍照失败恢复链路
| 项 | 内容 |
|----|------|
| **用例ID** | TC-E2-09 |
| **优先级** | P1 |
| **链路图** | 点击快门 → CameraManager.takePhoto失败 → onResult(false) → _photoSaveError设置 → UI提示 → 恢复可用 |
| **前置条件** | 相机被占用或存储异常 |
| **操作步骤** | 1. 模拟相机异常 2. 点击快门 3. 观察错误提示 4. 恢复后重试 |
| **状态断言点** | ① `ImageCaptureException` 回调 ② `_photoSaveError="拍照失败：..."` ③ UI 显示错误 ④ 恢复后可继续拍照 |
| **预期结果** | 拍照失败显示错误，不崩溃，恢复后可用 |
| **验收标准** | 错误提示清晰，状态可恢复 |

### TC-E2-10: 拍照与视频互斥保护链路
| 项 | 内容 |
|----|------|
| **用例ID** | TC-E2-10 |
| **优先级** | P1 |
| **链路图** | Vlog录制中 → 点击快门 → takePhoto检测_isVlogRecording → 直接return → 不中断录制 |
| **前置条件** | Vlog 录制中 |
| **操作步骤** | 1. 开始 Vlog 录制 2. 点击拍照快门 3. 观察无反应 |
| **状态断言点** | ① `_isVlogRecording=true` 或 `_isVlogMerging=true` ② `takePhoto()` 直接 return |
| **预期结果** | 录制中拍照无效，不中断录制 |
| **验收标准** | 互斥保护生效，录制不中断 |

### TC-E2-11: WEBP 格式保存链路
| 项 | 内容 |
|----|------|
| **用例ID** | TC-E2-11 |
| **优先级** | P2 |
| **链路图** | 设置outputFormat=WEBP → 拍照 → processAndSavePhoto → 保存.jpg → 转WEBP → 删除.jpg → 更新savedPath |
| **前置条件** | 输出格式设为 WEBP |
| **操作步骤** | 1. 设置输出格式 WEBP 2. 拍照 3. 检查文件 |
| **状态断言点** | ① `outputFormat=1` ② 生成 .webp 文件 ③ 原 .jpg 删除 ④ `savedPath` 更新为 .webp |
| **预期结果** | 照片保存为 WEBP 格式 |
| **验收标准** | 文件扩展名 .webp，原 jpg 已删除，DB 记录路径正确 |

### TC-E2-12: 评星提示触发链路
| 项 | 内容 |
|----|------|
| **用例ID** | TC-E2-12 |
| **优先级** | P3 |
| **链路图** | 拍照累计5次/20次 → _shouldShowReviewPrompt=true → 评星弹窗 → 用户操作 |
| **前置条件** | 拍照计数接近阈值 |
| **操作步骤** | 1. 拍照至累计 5 次 2. 观察评星弹窗 3. 继续拍照至 20 次 4. 观察弹窗 |
| **状态断言点** | ① `_captureCount=5` 或 `20` ② `_shouldShowReviewPrompt=true` ③ 弹窗显示 |
| **预期结果** | 达到阈值时弹出评星提示 |
| **验收标准** | 5 次和 20 次各触发一次，不频繁打扰 |

### TC-E2-13: 暗光检测与屏幕补光链路
| 项 | 内容 |
|----|------|
| **用例ID** | TC-E2-13 |
| **优先级** | P2 |
| **链路图** | 帧分析器检测亮度 → 低于阈值 → _isLowLightWarning=true → UI警告 → 屏幕补光(白屏+强度) → 拍照 |
| **前置条件** | 暗光环境 |
| **操作步骤** | 1. 在暗光环境预览 2. 观察暗光警告 3. 开启屏幕补光 4. 拍照 |
| **状态断言点** | ① `_isLowLightWarning=true` ② `screenFillLightEnabled=true` ③ 补光强度生效 |
| **预期结果** | 暗光警告显示，屏幕补光提亮 |
| **验收标准** | 首次触发 `vibrateWarn()`，补光可调强度 |

### TC-E2-14: 拍照前后置切换链路
| 项 | 内容 |
|----|------|
| **用例ID** | TC-E2-14 |
| **优先级** | P1 |
| **链路图** | 后置预览 → 点击切换 → cameraLens=1 → 前置预览 → 状态持久化 → 重启保持前置 |
| **前置条件** | 后置预览中 |
| **操作步骤** | 1. 点击切换镜头 2. 观察前置预览 3. 切回后置 4. 重启应用 |
| **状态断言点** | ① `cameraLens` 切换 0↔1 ② DataStore 持久化 ③ 重启恢复上次镜头 ④ 前置无闪光灯时禁用 |
| **预期结果** | 前后切换流畅，状态持久化 |
| **验收标准** | 切换耗时 ≤1.5s，重启保持，前置闪光灯禁用 |

---

## E3. Vlog 录制完整链路

### TC-E3-01: Vlog 完整录制链路（模板→分镜→合成→导出）
| 项 | 内容 |
|----|------|
| **用例ID** | TC-E3-01 |
| **优先级** | P0 |
| **链路图** | setShootingMode(2) → 模板选择器 → 选模板(快速Vlog) → startVlog → executeVlogCapture循环分镜 → TTS播报+overlayText → startVideoChunk → 录制duration秒 → stopVideoChunk → 全部分镜完成 → mergeVlog → VideoMerger.merge(BGM+字幕) → _exportedVlogPath → MediaStore入库 → 预览Vlog |
| **前置条件** | 相机+录音权限已授予 |
| **操作步骤** | 1. 切换到 Vlog 模式 2. 选择"快速 Vlog"模板 3. 等待分镜录制完成 4. 等待合成 5. 预览导出的 Vlog |
| **状态断言点** | ① `_shootingMode=2` ② `_activeVlogTemplate` 设置 ③ `_isVlogRecording=true` ④ `_displayVlogText` 更新 ⑤ `_isVlogMerging=true` ⑥ `_exportedVlogPath` 非空 ⑦ `MediaStoreHelper.addVideoToGallery` 成功 |
| **预期结果** | Vlog 合成完成，视频出现在相册 |
| **验收标准** | 视频文件存在，含 BGM 和字幕，时长≈分镜总和，相册可见 |

### TC-E3-02: Vlog 分镜 TTS 播报链路
| 项 | 内容 |
|----|------|
| **用例ID** | TC-E3-02 |
| **优先级** | P1 |
| **链路图** | 分镜开始 → TTS speak(clip.voiceCommand) → overlayText显示 → delay1500ms → 开始录制 |
| **前置条件** | Vlog 录制中 |
| **操作步骤** | 1. 开始 Vlog 录制 2. 观察每幕 TTS 播报 3. 观察 overlay 文字 |
| **状态断言点** | ① TTS 播报 voiceCommand ② `_displayVlogText=clip.overlayText` ③ delay 1500ms 后录制 |
| **预期结果** | 每幕有语音播报和文字提示 |
| **验收标准** | TTS 播报清晰，文字同步显示 |

### TC-E3-03: Vlog 录制前台服务链路
| 项 | 内容 |
|----|------|
| **用例ID** | TC-E3-03 |
| **优先级** | P1 |
| **链路图** | 开始录制 → startRecordingForegroundService(ACTION_START) → startForeground(CAMERA类型) → 通知显示 → 录制结束 → ACTION_STOP → stopForeground+stopSelf |
| **前置条件** | Android 14+ 设备 |
| **操作步骤** | 1. 开始 Vlog 录制 2. 观察通知栏 3. 切后台 4. 录制结束 5. 观察通知消失 |
| **状态断言点** | ① `RecordingForegroundService` 启动 ② `FOREGROUND_SERVICE_TYPE_CAMERA` 声明 ③ 通知显示 ④ 结束后通知消失 |
| **预期结果** | 录制时前台服务通知显示，后台不中断 |
| **验收标准** | 无 `MissingForegroundServiceTypeException`，后台录制持续 |

### TC-E3-04: Vlog 合成失败恢复链路
| 项 | 内容 |
|----|------|
| **用例ID** | TC-E3-04 |
| **优先级** | P1 |
| **链路图** | mergeVlog → VideoMerger.merge返回null → _vlogErrorMessage="Vlog合成失败,请重试" → UI提示 → 用户可重试 |
| **前置条件** | 模拟合成失败（如 ffmpeg 异常） |
| **操作步骤** | 1. 开始 Vlog 录制 2. 模拟合成失败 3. 观察错误提示 4. 重新尝试 |
| **状态断言点** | ① `VideoMerger.merge` 返回 null ② `_isVlogMerging=false` ③ `_vlogErrorMessage` 设置 ④ UI 显示错误 |
| **预期结果** | 合成失败显示错误，可重试 |
| **验收标准** | 错误信息清晰，状态可恢复 |

### TC-E3-05: Vlog 分镜为空保护链路
| 项 | 内容 |
|----|------|
| **用例ID** | TC-E3-05 |
| **优先级** | P2 |
| **链路图** | 录制完成 → videoChunks为空 → _vlogErrorMessage="Vlog分镜加载失败:未捕获到任何视频片段" → 停止 |
| **前置条件** | 模拟分镜录制全部失败 |
| **操作步骤** | 1. 模拟录制失败 2. 观察 chunks 为空 3. 观察错误提示 |
| **状态断言点** | ① `cameraManager.videoChunks` 为空 ② `_vlogErrorMessage` 设置 |
| **预期结果** | 空分镜时提示错误，不崩溃 |
| **验收标准** | 错误提示明确，无 NPE |

### TC-E3-06: Vlog 录制取消链路
| 项 | 内容 |
|----|------|
| **用例ID** | TC-E3-06 |
| **优先级** | P2 |
| **链路图** | 录制中 → 用户取消 → 协程取消 → _isVlogRecording=false → 重抛CancellationException → 清理 |
| **前置条件** | Vlog 录制中 |
| **操作步骤** | 1. 开始 Vlog 录制 2. 中途取消 3. 观察状态 |
| **状态断言点** | ① 协程取消 ② `_isVlogRecording=false` ③ `CancellationException` 重抛 ④ 资源清理 |
| **预期结果** | 取消后状态正确清理 |
| **验收标准** | 无残留录制状态，可重新开始 |

### TC-E3-07: 录音权限拒绝降级链路
| 项 | 内容 |
|----|------|
| **用例ID** | TC-E3-07 |
| **优先级** | P1 |
| **链路图** | 录音权限拒绝 → CameraManager.startVideoChunk捕获SecurityException → 回退无音频录制 → Vlog无声音但视频正常 |
| **前置条件** | 录音权限拒绝 |
| **操作步骤** | 1. 拒绝录音权限 2. 开始 Vlog 录制 3. 观察视频 |
| **状态断言点** | ① `SecurityException` 捕获 ② 回退无音频 ③ 视频仍生成 |
| **预期结果** | 无音频但视频正常合成 |
| **验收标准** | Toast 提示无音频，视频文件存在 |

### TC-E3-08: Vlog BGM 加载链路
| 项 | 内容 |
|----|------|
| **用例ID** | TC-E3-08 |
| **优先级** | P2 |
| **链路图** | mergeVlog → 加载vlog.bgmFilename → VideoMerger.merge(视频+BGM+字幕) → 输出含BGM |
| **前置条件** | Vlog 录制完成 |
| **操作步骤** | 1. 录制 Vlog 2. 检查输出视频是否含 BGM |
| **状态断言点** | ① BGM 文件加载成功 ② 合成视频含音轨 |
| **预期结果** | 输出视频含背景音乐 |
| **验收标准** | BGM 与视频同步，音量正常 |

### TC-E3-09: Vlog 字幕生成链路
| 项 | 内容 |
|----|------|
| **用例ID** | TC-E3-09 |
| **优先级** | P2 |
| **链路图** | 分镜overlayText → 构建字幕条目(开始/结束时间,段间+200ms转场) → VideoMerger.mergeWithSubtitles → 生成MP4+SRT |
| **前置条件** | Vlog 录制完成 |
| **操作步骤** | 1. 录制 Vlog 2. 检查字幕文件 3. 播放视频观察字幕 |
| **状态断言点** | ① 字幕条目构建 ② SRT 文件生成 ③ 视频含字幕轨 |
| **预期结果** | 字幕与分镜对应，段间有转场 |
| **验收标准** | SRT 时间戳正确，字幕内容匹配 overlayText |

### TC-E3-10: Vlog 预览与分享闭环链路
| 项 | 内容 |
|----|------|
| **用例ID** | TC-E3-10 |
| **优先级** | P1 |
| **链路图** | 合成完成 → _isReviewingVlog=true → 预览Vlog → 触发分享 → 系统分享面板 → 关闭预览 |
| **前置条件** | Vlog 合成完成 |
| **操作步骤** | 1. 合成完成 2. 预览 Vlog 3. 点击分享 4. 选择分享目标 5. 返回 |
| **状态断言点** | ① `_isReviewingVlog=true` ② `_exportedVlogPath` 用于分享 ③ 分享 Intent 发起 |
| **预期结果** | 可预览并分享 Vlog |
| **验收标准** | 预览播放正常，分享面板弹出，返回无残留 |

---

## E4. 照片编辑完整链路

### TC-E4-01: 照片编辑完整链路（选图→编辑→保存→DB刷新）
| 项 | 内容 |
|----|------|
| **用例ID** | TC-E4-01 |
| **优先级** | P0 |
| **链路图** | 相册 → 点击照片 → PhotoDetailBottomSheet → 点"编辑" → navigate(photo_editor/{recordId}) → 异步查DB imagePath → PhotoEditorScreen → loadBitmapFromFile(降采样2048) → 编辑(裁剪/旋转/滤镜) → save() → saveBitmapToFile(edited_${ts}.jpg, JPEG95%) → viewModel.replacePhotoFile(recordId,newPath) → DB更新 → Flow刷新相册 → 删旧文件 → MediaStore入库 → popBackStack |
| **前置条件** | 相册有照片 |
| **操作步骤** | 1. 进入相册 2. 点击照片 3. 点击"编辑" 4. 应用滤镜 5. 点击保存 6. 返回相册 |
| **状态断言点** | ① recordId 查询成功 ② `originalBitmap` 加载 ③ `currentBitmap` 编辑后 ④ `edited_${timestamp}.jpg` 生成 ⑤ `ShootingDao.update` 更新 imagePath ⑥ 相册 Flow 刷新 ⑦ 旧文件删除 |
| **预期结果** | 编辑后照片保存，相册显示新图 |
| **验收标准** | DB 记录 imagePath 更新，相册显示编辑后图片，旧文件已删 |

### TC-E4-02: 裁剪完整链路
| 项 | 内容 |
|----|------|
| **用例ID** | TC-E4-02 |
| **优先级** | P1 |
| **链路图** | 选CROP工具 → 选比例(1:1) → 拖动CropHandle → applyCrop() → 后台cropBitmap → 历史快照 → currentBitmap更新 |
| **前置条件** | 编辑器中 |
| **操作步骤** | 1. 选择裁剪工具 2. 选择 1:1 比例 3. 拖动把手 4. 应用裁剪 |
| **状态断言点** | ① `selectedRatio=1:1` ② `cropRect` 更新 ③ `applyCrop` 调用 ④ `EditSnapshot` 入栈 ⑤ `currentBitmap` 更新 |
| **预期结果** | 裁剪后图片按比例 |
| **验收标准** | 裁剪区域准确，历史可撤销 |

### TC-E4-03: 旋转完整链路
| 项 | 内容 |
|----|------|
| **用例ID** | TC-E4-03 |
| **优先级** | P1 |
| **链路图** | 选ROTATE工具 → 点击-90/+90 → applyRotation() → 后台rotateBitmap → 历史快照 → currentBitmap更新 |
| **前置条件** | 编辑器中 |
| **操作步骤** | 1. 选择旋转工具 2. 点击逆时针 3. 点击顺时针 4. 观察旋转 |
| **状态断言点** | ① `applyRotation(-90/+90)` 调用 ② `EditSnapshot` 入栈 ③ `currentBitmap` 旋转 |
| **预期结果** | 图片按 90° 旋转 |
| **验收标准** | 旋转流畅，历史可撤销 |

### TC-E4-04: 滤镜实时预览链路
| 项 | 内容 |
|----|------|
| **用例ID** | TC-E4-04 |
| **优先级** | P1 |
| **链路图** | 选FILTER工具 → 选滤镜 → 强度滑块 → previewBitmap实时更新(applyFilterWithIntensity+blendBitmaps) → 确认 |
| **前置条件** | 编辑器中 |
| **操作步骤** | 1. 选择滤镜工具 2. 选择滤镜 3. 调整强度 4. 观察实时预览 |
| **状态断言点** | ① `selectedFilter` 设置 ② `filterIntensity` 0-100 ③ `previewBitmap` 实时更新 ④ 滤镜缩略图缓存 |
| **预期结果** | 滤镜实时预览，强度可调 |
| **验收标准** | 预览无延迟，强度混合正确 |

### TC-E4-05: 撤销链路
| 项 | 内容 |
|----|------|
| **用例ID** | TC-E4-05 |
| **优先级** | P1 |
| **链路图** | 多次编辑 → 点撤销 → undo() → 弹出EditSnapshot → 恢复bitmapBefore → currentBitmap更新 |
| **前置条件** | 有编辑历史 |
| **操作步骤** | 1. 旋转 2. 裁剪 3. 点撤销 4. 观察恢复到裁剪前 5. 再撤销 6. 恢复到原图 |
| **状态断言点** | ① `editHistory` 非空 ② `undo()` 弹出栈顶 ③ `currentBitmap` 恢复 `bitmapBefore` |
| **预期结果** | 逐步撤销恢复历史状态 |
| **验收标准** | 撤销链正确，状态准确恢复 |

### TC-E4-06: 原图加载失败链路
| 项 | 内容 |
|----|------|
| **用例ID** | TC-E4-06 |
| **优先级** | P1 |
| **链路图** → navigate(photo_editor) → 查DB → imagePath无效 → loadBitmapFromFile失败 → 显示"图片加载失败" → 返回 |
| **前置条件** | 图片文件已删除 |
| **操作步骤** | 1. 删除图片文件 2. 相册点击编辑 3. 观察错误提示 4. 返回 |
| **状态断言点** | ① `loadBitmapFromFile` 返回 null ② `loadFailed=true` ③ 显示占位+返回按钮 |
| **预期结果** | 加载失败显示提示，可返回 |
| **验收标准** | 不崩溃，错误提示清晰 |

### TC-E4-07: recordId 不存在链路
| 项 | 内容 |
|----|------|
| **用例ID** | TC-E4-07 |
| **优先级** | P2 |
| **链路图** | navigate(photo_editor/无效id) → 查DB返回null → 显示"照片记录不存在" → 返回 |
| **前置条件** | recordId 无效 |
| **操作步骤** | 1. 使用无效 recordId 进入编辑器 2. 观察提示 |
| **状态断言点** | ① DB 查询返回 null ② 显示"照片记录不存在" |
| **预期结果** | 显示记录不存在提示 |
| **验收标准** | 不崩溃，可返回 |

### TC-E4-08: 保存失败恢复链路
| 项 | 内容 |
|----|------|
| **用例ID** | TC-E4-08 |
| **优先级** | P1 |
| **链路图** | save() → saveBitmapToFile异常 → Snackbar"保存失败" → isSaving复位 → 可重试 |
| **前置条件** | 存储空间不足 |
| **操作步骤** | 1. 编辑照片 2. 模拟存储不足 3. 点击保存 4. 观察错误 5. 恢复后重试 |
| **状态断言点** | ① 保存异常捕获 ② Snackbar 显示错误 ③ `isSaving=false` ④ 可重试 |
| **预期结果** | 保存失败提示，可重试 |
| **验收标准** | 状态正确复位，不卡死 |

### TC-E4-09: 替换文件原子性链路
| 项 | 内容 |
|----|------|
| **用例ID** | TC-E4-09 |
| **优先级** | P1 |
| **链路图** | replacePhotoFile → DB更新imagePath成功 → 才删旧文件 → 避免竞态 |
| **前置条件** | 编辑保存 |
| **操作步骤** | 1. 编辑保存 2. 检查 DB 更新与旧文件删除顺序 |
| **状态断言点** | ① `ShootingDao.update` 先成功 ② 旧文件后删除 ③ 无竞态导致记录指向已删文件 |
| **预期结果** | DB 更新后才删旧文件 |
| **验收标准** | 无竞态，记录始终指向有效文件 |

### TC-E4-10: OOM 防护链路
| 项 | 内容 |
|----|------|
| **用例ID** | TC-E4-10 |
| **优先级** | P2 |
| **链路图** | 加载大图 → loadBitmapFromFile(maxDim=2048降采样) → 滤镜缩略图(120px) → 避免OOM |
| **前置条件** | 大图照片 |
| **操作步骤** | 1. 拍摄高分辨率照片 2. 进入编辑器 3. 应用多个操作 |
| **状态断言点** | ① `loadBitmapFromFile` 降采样 ② 缩略图 120px ③ 无 OOM |
| **预期结果** | 大图编辑不崩溃 |
| **验收标准** | `largeHeap=true`，降采样生效 |

### TC-E4-11: 多比例裁剪链路
| 项 | 内容 |
|----|------|
| **用例ID** | TC-E4-11 |
| **优先级** | P2 |
| **链路图** | 选比例FREE/1:1/4:3/3:4/16:9/9:16 → 裁剪框约束 → applyCrop |
| **前置条件** | 编辑器裁剪工具 |
| **操作步骤** | 1. 依次选择各比例 2. 观察裁剪框 3. 应用裁剪 |
| **状态断言点** | ① 各比例裁剪框正确 ② `cropRect` 按比例约束 |
| **预期结果** | 各比例裁剪正确 |
| **验收标准** | 比例准确，无变形 |

### TC-E4-12: 编辑后相册即时刷新链路
| 项 | 内容 |
|----|------|
| **用例ID** | TC-E4-12 |
| **优先级** | P1 |
| **链路图** | 保存成功 → DB更新 → Room Flow推送 → 相册列表重组 → 显示新图 |
| **前置条件** | 编辑保存完成 |
| **操作步骤** | 1. 编辑保存 2. 返回相册 3. 观察列表 |
| **状态断言点** | ① `ShootingDao.update` 触发 Flow ② 相册列表重组 ③ 显示编辑后图片 |
| **预期结果** | 相册立即显示编辑后图片 |
| **验收标准** | Room Flow 响应及时，UI 刷新无延迟 |

---

## E5. OOTD 穿搭分析完整链路

### TC-E5-01: OOTD 分析完整链路（选图→分析→评分→建议）
| 项 | 内容 |
|----|------|
| **用例ID** | TC-E5-01 |
| **优先级** | P0 |
| **链路图** | 底部导航"穿搭" → OOTDAnalysisScreen → 点"选择照片" → PickVisualMedia(ImageOnly) → 返回URI → copyUriToFile(cacheDir/ootd_${ts}.jpg) → selectedImagePath → 点"开始分析" → isAnalyzing=true → OotdAnalyzer.analyze → decodeSampledBitmap(512) → SceneClassifier场景分类 → 像素色彩统计(上/中/下三段) → 计算色彩和谐度/比例/风格 → 加权评分(40%+30%+30%) → 生成建议+标签 → UI显示评分卡 |
| **前置条件** | 相册有穿搭照片 |
| **操作步骤** | 1. 进入穿搭页 2. 选择照片 3. 点击开始分析 4. 等待结果 5. 查看评分和建议 |
| **状态断言点** | ① `selectedImagePath` 设置 ② `isAnalyzing=true` ③ `OotdAnalyzer.analyze` 返回 Result ④ `overallScore` 0-100 ⑤ 评级(优/良/中) ⑥ 建议列表非空 |
| **预期结果** | 显示综合评分、三项分项、场景、风格标签、建议 |
| **验收标准** | 评分合理，建议与场景匹配，资源释放无泄漏 |

### TC-E5-02: 图片选择与复制链路
| 项 | 内容 |
|----|------|
| **用例ID** | TC-E5-02 |
| **优先级** | P1 |
| **链路图** | 点"选择照片" → PickVisualMedia → URI → copyUriToFile → cacheDir/ootd_${ts}.jpg → selectedImagePath更新 |
| **前置条件** | 穿搭页 |
| **操作步骤** | 1. 点击选择照片 2. 系统选择器选图 3. 观察预览 |
| **状态断言点** | ① PickVisualMedia 返回 URI ② `copyUriToFile` 复制成功 ③ `selectedImagePath` 更新 |
| **预期结果** | 选中图片预览显示 |
| **验收标准** | 复制到 cacheDir，预览可见 |

### TC-E5-03: 图片解码失败链路
| 项 | 内容 |
|----|------|
| **用例ID** | TC-E5-03 |
| **优先级** | P1 |
| **链路图** | analyze → decodeSampledBitmap失败 → Result(error="图片解码失败") → UI显示错误 |
| **前置条件** | 损坏图片 |
| **操作步骤** | 1. 选择损坏图片 2. 点击分析 3. 观察错误 |
| **状态断言点** | ① `decodeSampledBitmap` 返回 null ② `Result.error="图片解码失败，请选择其他图片"` |
| **预期结果** | 显示解码失败提示 |
| **验收标准** | 不崩溃，提示清晰 |

### TC-E5-04: 像素采样为空链路
| 项 | 内容 |
|----|----|
| **用例ID** | TC-E5-04 |
| **优先级** | P2 |
| **链路图** | decodeSampledBitmap成功 → 像素采样为0 → Result(error="无法读取图片像素数据") |
| **前置条件** | 特殊格式图片 |
| **操作步骤** | 1. 选择特殊图片 2. 分析 3. 观察错误 |
| **状态断言点** | ① 像素采样返回 0 ② `Result.error="无法读取图片像素数据"` |
| **预期结果** | 显示像素读取错误 |
| **验收标准** | 不崩溃，错误明确 |

### TC-E5-05: SceneClassifier 降级链路
| 项 | 内容 |
|----|------|
| **用例ID** | TC-E5-05 |
| **优先级** | P2 |
| **链路图** | SceneClassifier.classify异常 → 降级SceneType.UNKNOWN → 继续分析 |
| **前置条件** | TFLite 模型不可用 |
| **操作步骤** | 1. 破坏场景模型 2. 分析图片 3. 观察结果 |
| **状态断言点** | ① SceneClassifier 异常捕获 ② 降级 `UNKNOWN` ③ 分析继续 |
| **预期结果** | 场景未知但分析完成 |
| **验收标准** | 降级不中断分析，评分正常 |

### TC-E5-06: 分析异常捕获链路
| 项 | 内容 |
|----|------|
| **用例ID** | TC-E5-06 |
| **优先级** | P2 |
| **链路图** | analyze过程抛异常 → UI捕获 → Result(error="分析失败：${e.message}") |
| **前置条件** | 模拟分析异常 |
| **操作步骤** | 1. 模拟异常 2. 分析 3. 观察错误 |
| **状态断言点** | ① 异常捕获 ② `Result.error="分析失败：..."` |
| **预期结果** | 显示分析失败提示 |
| **验收标准** | 不崩溃，错误含异常信息 |

### TC-E5-07: URI 复制失败链路
| 项 | 内容 |
|----|------|
| **用例ID** | TC-E5-07 |
| **优先级** | P2 |
| **链路图** | copyUriToFile失败 → 返回null → selectedImagePath不更新 → 用户可重选 |
| **前置条件** | URI 无权限 |
| **操作步骤** | 1. 选择图片 2. 模拟复制失败 3. 观察无预览 4. 重选 |
| **状态断言点** | ① `copyUriToFile` 返回 null ② `selectedImagePath` 未更新 |
| **预期结果** | 复制失败可重选 |
| **验收标准** | 不崩溃，可重新选择 |

### TC-E5-08: 资源释放链路
| 项 | 内容 |
|----|------|
| **用例ID** | TC-E5-08 |
| **优先级** | P2 |
| **链路图** | 分析完成 → bitmap.recycle() → 离开页面 → DisposableEffect → ootdAnalyzer.close() → 资源释放 |
| **前置条件** | 分析完成 |
| **操作步骤** | 1. 完成分析 2. 离开穿搭页 3. 检查资源释放 |
| **状态断言点** | ① `bitmap.recycle()` 调用 ② `DisposableEffect.onDispose` 触发 ③ `ootdAnalyzer.close()` 调用 |
| **预期结果** | 离开页面资源释放 |
| **验收标准** | 无内存泄漏，TFLite 资源关闭 |

---

## E6. 内购完整链路

### TC-E6-01: 内购完整链路（触发→购买→解锁→关闭付费墙）
| 项 | 内容 |
|----|------|
| **用例ID** | TC-E6-01 |
| **优先级** | P0 |
| **链路图** | 底部导航"Pro" → onShowPaywall() → showPaywall=true → PaywallView显示 → 点"购买Pro" → billingManager.launchBillingFlow → queryProductDetails(PRO_PRODUCT_ID) → BillingFlowParams → billingClient.launchBillingFlow → onPurchasesUpdated(OK) → handlePurchases → 过滤Pro商品 → PURCHASED → updateProState(true) + acknowledgePurchase → _isProUnlocked=true → StoreManager.setProUnlocked(true)持久化 → LaunchedEffect(isProUnlocked) → 关闭付费墙 + Toast"Pro已解锁" |
| **前置条件** | Google Play 测试账号，未解锁 Pro |
| **操作步骤** | 1. 点击底部"Pro" 2. 观察付费墙 3. 点击"购买 Pro" 4. 完成 Play 购买 5. 观察付费墙关闭 |
| **状态断言点** | ① `showPaywall=true` ② `launchBillingFlow` 调用 ③ `onPurchasesUpdated` 回调 OK ④ `handlePurchases` 命中 Pro 商品 ⑤ `_isProUnlocked=true` ⑥ `StoreManager.setProUnlocked(true)` ⑦ `acknowledgePurchase` 调用 ⑧ 付费墙关闭 + Toast |
| **预期结果** | 购买成功，Pro 解锁，付费墙自动关闭 |
| **验收标准** | `isProUnlocked=true`，购买已 acknowledge，付费墙关闭，Toast 提示 |

### TC-E6-02: BillingClient 连接链路
| 项 | 内容 |
|----|------|
| **用例ID** | TC-E6-02 |
| **优先级** | P1 |
| **链路图** | PoseAIApp.onCreate → billingManager.startConnection() → onBillingSetupFinished(OK) → isConnectionEstablished=true → restorePurchases() → 从DataStore恢复_isProUnlocked |
| **前置条件** | 应用启动 |
| **操作步骤** | 1. 冷启动应用 2. 观察 Billing 连接 |
| **状态断言点** | ① `startConnection` 调用 ② `onBillingSetupFinished` OK ③ `isConnectionEstablished=true` ④ `restorePurchases` 触发 ⑤ DataStore 恢复状态 |
| **预期结果** | 启动自动连接并恢复购买 |
| **验收标准** | 连接成功，Pro 状态恢复 |

### TC-E6-03: 购买取消链路
| 项 | 内容 |
|----|------|
| **用例ID** | TC-E6-03 |
| **优先级** | P1 |
| **链路图** | launchBillingFlow → 用户取消 → onPurchasesUpdated(USER_CANCELED) → 仅日志 → 付费墙保持 |
| **前置条件** | 付费墙显示 |
| **操作步骤** | 1. 点击购买 2. Play 弹窗点击取消 3. 观察付费墙 |
| **状态断言点** | ① `onPurchasesUpdated` 回调 USER_CANCELED ② 仅日志记录 ③ `_isProUnlocked` 不变 ④ 付费墙保持 |
| **预期结果** | 取消后付费墙保持，可再次购买 |
| **验收标准** | 不崩溃，状态不变 |

### TC-E6-04: 已拥有商品恢复链路
| 项 | 内容 |
|----|------|
| **用例ID** | TC-E6-04 |
| **优先级** | P1 |
| **链路图** | launchBillingFlow → onPurchasesUpdated(ITEM_ALREADY_OWNED) → 触发restorePurchases() → 恢复Pro状态 |
| **前置条件** | 已购买过 Pro |
| **操作步骤** | 1. 已购买账号点击购买 2. 观察恢复 |
| **状态断言点** | ① `ITEM_ALREADY_OWNED` 回调 ② `restorePurchases` 触发 ③ Pro 状态恢复 |
| **预期结果** | 已拥有时自动恢复 |
| **验收标准** | 不重复购买，自动恢复 Pro |

### TC-E6-05: 恢复购买链路
| 项 | 内容 |
|----|------|
| **用例ID** | TC-E6-05 |
| **优先级** | P0 |
| **链路图** | 点"恢复购买" → restorePurchases(onRestore) → queryPurchasesAsync(INAPP) → handlePurchases(fromRestore=true) → 命中Pro → updateProState(true) → onRestore(true) → Toast"已恢复Pro权益" |
| **前置条件** | 已购买过 Pro，重装应用 |
| **操作步骤** | 1. 重装应用 2. 点击底部 Pro 3. 点击"恢复购买" 4. 观察 Toast |
| **状态断言点** | ① `restorePurchases` 调用 ② `queryPurchasesAsync` 返回 ③ `handlePurchases(fromRestore=true)` ④ `updateProState(true)` ⑤ `onRestore(true)` ⑥ Toast 显示 |
| **预期结果** | 恢复购买成功，Pro 解锁 |
| **验收标准** | Toast"已恢复 Pro 权益"，`isProUnlocked=true` |

### TC-E6-06: 恢复购买未找到链路
| 项 | 内容 |
|----|------|
| **用例ID** | TC-E6-06 |
| **优先级** | P2 |
| **链路图** | 点"恢复购买" → queryPurchasesAsync → 无Pro购买 → onRestore(false) → Toast"未找到可恢复的购买记录" |
| **前置条件** | 未购买过 |
| **操作步骤** | 1. 点击恢复购买 2. 观察 Toast |
| **状态断言点** | ① `handlePurchases` 未命中 Pro ② `onRestore(false)` ③ Toast 提示 |
| **预期结果** | 提示未找到购买记录 |
| **验收标准** | Toast 明确，状态不变 |

### TC-E6-07: 连接断开重连链路
| 项 | 内容 |
|----|------|
| **用例ID** | TC-E6-07 |
| **优先级** | P2 |
| **链路图** | onBillingServiceDisconnected → retryReconnect() → delay3s → startConnection → 最多5次 → 放弃 |
| **前置条件** | 网络不稳定 |
| **操作步骤** | 1. 模拟网络断开 2. 观察重连 3. 恢复网络 |
| **状态断言点** | ① `onBillingServiceDisconnected` 触发 ② `retryReconnect` 调用 ③ `retryCount` 递增 ④ 最多 5 次 |
| **预期结果** | 断开后自动重连 |
| **验收标准** | 延迟 3s，最多 5 次，日志记录 |

### TC-E6-08: 未连接发起购买链路
| 项 | 内容 |
|----|------|
| **用例ID** | TC-E6-08 |
| **优先级** | P2 |
| **链路图** | launchBillingFlow → isConnectionEstablished=false → startConnection() → 返回(不直接发起) → 用户需再点 |
| **前置条件** | Billing 未连接 |
| **操作步骤** | 1. 未连接时点购买 2. 观察无反应 3. 连接后再次点击 |
| **状态断言点** | ① `isConnectionEstablished=false` ② 仅调用 `startConnection` ③ 不发起 BillingFlow |
| **预期结果** | 未连接时不直接发起购买 |
| **验收标准** | 日志提示，连接后可购买 |

### TC-E6-09: PENDING 状态链路
| 项 | 内容 |
|----|------|
| **用例ID** | TC-E6-09 |
| **优先级** | P2 |
| **链路图** | handlePurchases → purchaseState=PENDING → 等待完成 → 不解锁Pro |
| **前置条件** | 延迟付款方式 |
| **操作步骤** | 1. 选择延迟付款 2. 购买 3. 观察 PENDING |
| **状态断言点** | ① `purchaseState=PENDING` ② 不调用 `updateProState` ③ 等待完成 |
| **预期结果** | PENDING 不立即解锁 |
| **验收标准** | 日志记录 PENDING，Pro 未解锁 |

### TC-E6-10: Pro 状态持久化闭环链路
| 项 | 内容 |
|----|------|
| **用例ID** | TC-E6-10 |
| **优先级** | P1 |
| **链路图** | 购买成功 → updateProState(true) → StoreManager.setProUnlocked(true) → 杀进程 → 重启 → init从DataStore恢复 → _isProUnlocked=true |
| **前置条件** | 已购买 Pro |
| **操作步骤** | 1. 购买 Pro 2. 强杀应用 3. 重启 4. 观察 Pro 状态 |
| **状态断言点** | ① `setProUnlocked(true)` 持久化 ② 重启 init 恢复 ③ `_isProUnlocked=true` |
| **预期结果** | 重启后 Pro 保持解锁 |
| **验收标准** | DataStore 持久化生效，重启恢复 |

---

## E7. 自定义姿势完整链路

### TC-E7-01: 自定义姿势完整链路（保存→应用→删除）
| 项 | 内容 |
|----|------|
| **用例ID** | TC-E7-01 |
| **优先级** | P0 |
| **链路图** | 拍摄页 → openCustomPoseSheet() → customPoseStore.loadAll() → _customPoses显示 → 摆姿势(detectedPointsCount≥3) → 输入名称/描述 → saveCurrentPoseAsCustom → CustomPose(UUID/points归一化) → customPoseStore.save(去重) → 刷新_customPoses → applyCustomPose → _customActivePlan覆盖currentPlan → 拍照 → deleteCustomPose → 清理 |
| **前置条件** | 智能导拍模式，检测到姿势 |
| **操作步骤** | 1. 打开自定义姿势面板 2. 摆姿势 3. 输入名称保存 4. 应用该姿势 5. 拍照 6. 删除姿势 |
| **状态断言点** | ① `_showCustomPoseSheet=true` ② `detectedPointsCount>=3` ③ `customPoseStore.save` 成功 ④ `_customPoses` 刷新 ⑤ `_customActivePlan` 设置 ⑥ `currentPlan` getter 返回自定义 ⑦ 删除后列表更新 |
| **预期结果** | 保存→应用→拍照→删除全链路通畅 |
| **验收标准** | 姿势归一化坐标正确，应用后评分基于该模板，删除后列表更新 |

### TC-E7-02: 关键点不足保护链路
| 项 | 内容 |
|----|------|
| **用例ID** | TC-E7-02 |
| **优先级** | P1 |
| **链路图** | 摆姿势 → detectedPointsCount<3 → saveCurrentPoseAsCustom返回false → 提示"至少需要3个关键点" |
| **前置条件** | 未检测到足够姿势 |
| **操作步骤** | 1. 遮挡身体 2. 尝试保存 3. 观察提示 |
| **状态断言点** | ① `detectedPointsCount<3` ② 返回 false ③ 提示文案 |
| **预期结果** | 关键点不足时拒绝保存 |
| **验收标准** | 提示明确，不保存无效姿势 |

### TC-E7-03: 姿势去重覆盖链路
| 项 | 内容 |
|----|------|
| **用例ID** | TC-E7-03 |
| **优先级** | P2 |
| **链路图** | 保存同名姿势 → loadAll → 按id去重 → 存在则覆盖 → saveAll |
| **前置条件** | 已有自定义姿势 |
| **操作步骤** | 1. 保存姿势 A 2. 用相同 id 保存 3. 观察列表 |
| **状态断言点** | ① `loadAll` 读取 ② 按 id 去重 ③ 覆盖同名 ④ 列表不重复 |
| **预期结果** | 同 id 覆盖，不重复 |
| **验收标准** | 列表唯一，内容更新 |

### TC-E7-04: 应用自定义姿势覆盖方案链路
| 项 | 内容 |
|----|------|
| **用例ID** | TC-E7-04 |
| **优先级** | P1 |
| **链路图** | applyCustomPose → 转ShootingPlan → _customActivePlan=plan → currentPlan getter返回自定义 → 评分基于自定义模板 |
| **前置条件** | 有自定义姿势 |
| **操作步骤** | 1. 应用自定义姿势 2. 摆姿势 3. 观察评分 |
| **状态断言点** | ① `_customActivePlan` 设置 ② `currentPlan` 返回自定义 ③ 评分基于自定义模板 ④ `_activeCustomPoseId` 设置 |
| **预期结果** | 评分基于自定义模板 |
| **验收标准** | 评分逻辑切换正确 |

### TC-E7-05: 删除当前激活姿势链路
| 项 | 内容 |
|----|------|
| **用例ID** | TC-E7-05 |
| **优先级** | P2 |
| **链路图** | 删除激活姿势 → deleteCustomPose → customPoseStore.delete → 刷新列表 → _activeCustomPoseId清空 → 恢复内置方案 |
| **前置条件** | 自定义姿势已激活 |
| **操作步骤** | 1. 应用自定义姿势 2. 删除该姿势 3. 观察方案恢复 |
| **状态断言点** | ① `delete` 成功 ② `_activeCustomPoseId=null` ③ `currentPlan` 恢复内置 |
| **预期结果** | 删除激活姿势后恢复内置 |
| **验收标准** | 无残留激活状态，评分基于内置 |

### TC-E7-06: 清除自定义姿势链路
| 项 | 内容 |
|----|------|
| **用例ID** | TC-E7-06 |
| **优先级** | P2 |
| **链路图** | clearCustomPose → _customActivePlan=null → 恢复内置场景方案 |
| **前置条件** | 自定义姿势激活 |
| **操作步骤** | 1. 点击清除 2. 观察恢复内置 |
| **状态断言点** | ① `_customActivePlan=null` ② `currentPlan` 恢复内置 |
| **预期结果** | 清除后恢复内置方案 |
| **验收标准** | 状态正确切换 |

### TC-E7-07: 场景切换清空自定义链路
| 项 | 内容 |
|----|------|
| **用例ID** | TC-E7-07 |
| **优先级** | P2 |
| **链路图** | setScene → 自动_customActivePlan=null + _activeCustomPoseId=null → 恢复新场景方案 |
| **前置条件** | 自定义姿势激活 |
| **操作步骤** | 1. 应用自定义姿势 2. 切换场景 3. 观察清空 |
| **状态断言点** | ① `setScene` 触发 ② `_customActivePlan=null` ③ `_activeCustomPoseId=null` |
| **预期结果** | 切换场景自动清空自定义 |
| **验收标准** | 无残留，新场景方案生效 |

### TC-E7-08: 持久化闭环链路
| 项 | 内容 |
|----|------|
| **用例ID** | TC-E7-08 |
| **优先级** | P1 |
| **链路图** | 保存姿势 → SharedPreferences(Gson序列化) → 杀进程 → 重启 → loadAll恢复 → _customPoses显示 |
| **前置条件** | 已保存自定义姿势 |
| **操作步骤** | 1. 保存姿势 2. 强杀应用 3. 重启 4. 打开自定义面板 |
| **状态断言点** | ① SharedPreferences 写入 ② 重启 `loadAll` 恢复 ③ `_customPoses` 显示 |
| **预期结果** | 重启后自定义姿势保留 |
| **验收标准** | Gson 序列化正确，PointF→FloatArray DTO 转换无损 |

---

## E8. 设置与主题切换链路

### TC-E8-01: 主题切换完整链路（切换→持久化→全局响应）
| 项 | 内容 |
|----|------|
| **用例ID** | TC-E8-01 |
| **优先级** | P0 |
| **链路图** | 拍摄页 → SettingsDialog → 主题三段切换(自动/暗/亮) → storeManager.setThemeMode → DataStore持久化 → themeMode Flow → PoseAITheme collectAsState → useDarkTheme计算 → Dark/LightColorScheme切换 → 全局重组 |
| **前置条件** | 应用运行中 |
| **操作步骤** | 1. 打开设置 2. 切换主题为暗色 3. 观察全局变化 4. 切换为亮色 5. 切换为自动 |
| **状态断言点** | ① `setThemeMode(value)` 调用 ② DataStore 持久化 ③ `themeMode` Flow 变化 ④ `useDarkTheme` 计算 ⑤ ColorScheme 切换 |
| **预期结果** | 主题切换全局即时生效 |
| **验收标准** | 持久化生效，重启保持，跟随系统模式响应系统变化 |

### TC-E8-02: 主题跟随系统链路
| 项 | 内容 |
|----|------|
| **用例ID** | TC-E8-02 |
| **优先级** | P1 |
| **链路图** | themeMode=0(自动) → isSystemInDarkTheme() → 系统切换暗/亮 → useDarkTheme响应 → 主题实时切换 |
| **前置条件** | 主题设为自动 |
| **操作步骤** | 1. 设置自动主题 2. 切换系统暗色模式 3. 观察应用主题 |
| **状态断言点** | ① `themeMode=0` ② `isSystemInDarkTheme()` 响应 ③ 主题实时切换 |
| **预期结果** | 跟随系统主题变化 |
| **验收标准** | 系统切换时应用即时响应 |

### TC-E8-03: 微笑快门设置链路
| 项 | 内容 |
|----|------|
| **用例ID** | TC-E8-03 |
| **优先级** | P1 |
| **链路图** | 开关微笑快门 → setSmileEnabled → 灵敏度滑块 → setSmileThreshold(0.3-0.95) → smileDetector.triggerThreshold更新 → 持久化 |
| **前置条件** | 设置页 |
| **操作步骤** | 1. 开启微笑快门 2. 调整灵敏度 3. 拍照验证 |
| **状态断言点** | ① `smileEnabled=true` ② `smileThreshold` 0.3-0.95 ③ `smileDetector.triggerThreshold` 更新 ④ 持久化 |
| **预期结果** | 微笑快门按阈值工作 |
| **验收标准** | UI 限制范围，持久化生效 |

### TC-E8-04: 网格线设置链路
| 项 | 内容 |
|----|------|
| **用例ID** | TC-E8-04 |
| **优先级** | P2 |
| **链路图** | 开关网格线 → setGridEnabled → DataStore → gridEnabled Flow → 预览叠加层响应 |
| **前置条件** | 拍摄页 |
| **操作步骤** | 1. 开启网格线 2. 观察预览 3. 关闭 4. 观察 |
| **状态断言点** | ① `gridEnabled` 切换 ② 预览网格叠加响应 |
| **预期结果** | 网格线实时显示/隐藏 |
| **验收标准** | 持久化，重启保持 |

### TC-E8-05: 倒计时设置链路
| 项 | 内容 |
|----|------|
| **用例ID** | TC-E8-05 |
| **优先级** | P1 |
| **链路图** | 选倒计时(0/3/5/10) → setTimerSeconds → coerceIn(0,10) → DataStore → timerSeconds Flow → 拍照应用 |
| **前置条件** | 设置页 |
| **操作步骤** | 1. 设置倒计时 5 秒 2. 拍照 3. 观察倒计时 |
| **状态断言点** | ① `timerSeconds=5` ② `coerceIn(0,10)` ③ 持久化 ④ 拍照时 `startCountdown(5)` |
| **预期结果** | 倒计时按设置工作 |
| **验收标准** | 范围限制，持久化生效 |

### TC-E8-06: JPEG 质量设置链路
| 项 | 内容 |
|----|------|
| **用例ID** | TC-E8-06 |
| **优先级** | P2 |
| **链路图** | 调JPEG质量 → setJpegQuality → coerceIn(50,100) → DataStore → 拍照保存应用 |
| **前置条件** | 设置页 |
| **操作步骤** | 1. 设置 JPEG 质量 80 2. 拍照 3. 检查文件大小 |
| **状态断言点** | ① `jpegQuality=80` ② `coerceIn(50,100)` ③ 保存时应用 |
| **预期结果** | 照片按质量保存 |
| **验收标准** | 文件大小随质量变化 |

### TC-E8-07: 输出格式设置链路
| 项 | 内容 |
|----|------|
| **用例ID** | TC-E8-07 |
| **优先级** | P2 |
| **链路图** | 选格式(JPEG/WEBP) → setOutputFormat → coerceIn(0,1) → DataStore → 拍照保存应用 |
| **前置条件** | 设置页 |
| **操作步骤** | 1. 设置 WEBP 2. 拍照 3. 检查格式 |
| **状态断言点** | ① `outputFormat=1` ② `coerceIn(0,1)` ③ 保存为 .webp |
| **预期结果** | 格式按设置保存 |
| **验收标准** | 文件扩展名正确 |

### TC-E8-08: HDR 设置链路
| 项 | 内容 |
|----|------|
| **用例ID** | TC-E8-08 |
| **优先级** | P2 |
| **链路图** | 开关HDR → setHdrEnabled → DataStore → processAndSavePhoto应用HDR色调映射 |
| **前置条件** | 设置页 |
| **操作步骤** | 1. 开启 HDR 2. 高对比场景拍照 3. 观察效果 |
| **状态断言点** | ① `hdrEnabled=true` ② `processAndSavePhoto` 应用 HDR |
| **预期结果** | HDR 提升高对比场景 |
| **验收标准** | 暗部/亮部细节改善 |

### TC-E8-09: 屏幕补光设置链路
| 项 | 内容 |
|----|------|
| **用例ID** | TC-E8-09 |
| **优先级** | P2 |
| **链路图** | 开屏幕补光 → setScreenFillLightEnabled → 强度滑块 → setScreenFillLightIntensity → DataStore → 暗光时白屏补光 |
| **前置条件** | 设置页 |
| **操作步骤** | 1. 开启屏幕补光 2. 调整强度 3. 暗光环境拍照 |
| **状态断言点** | ① `screenFillLightEnabled=true` ② `screenFillLightIntensity` 设置 ③ 暗光时白屏 |
| **预期结果** | 屏幕补光提亮 |
| **验收标准** | 强度可调，暗光触发 |

### TC-E8-10: 设置持久化闭环链路
| 项 | 内容 |
|----|------|
| **用例ID** | TC-E8-10 |
| **优先级** | P1 |
| **链路图** | 修改多项设置 → DataStore持久化 → 杀进程 → 重启 → initCamera从DataStore.first()加载 → 设置恢复 |
| **前置条件** | 已修改设置 |
| **操作步骤** | 1. 修改主题/网格/倒计时/画质 2. 强杀 3. 重启 4. 验证设置 |
| **状态断言点** | ① DataStore 持久化 ② `initCamera` 加载 `first()` ③ 设置恢复 |
| **预期结果** | 重启后所有设置保持 |
| **验收标准** | 所有偏好项持久化生效 |

---

## E9. 分享完整链路

### TC-E9-01: 分享完整链路（打开→水印→FileProvider→系统面板）
| 项 | 内容 |
|----|------|
| **用例ID** | TC-E9-01 |
| **优先级** | P0 |
| **链路图** | 拍照预览/相册详情 → openShareSheet(photoPath) → _sharePhotoPath设置 → _watermarkStyle初始化(SIGNATURE/NONE) → _showShareSheet=true → ShareBottomSheet显示 → 加载预览图(inSampleSize=4) → 配置水印/话题 → executeShare → ShareEngine.shareToSystem → prepareShareImageFromPath(解码+applyWatermarkAndTopics) → 输出cacheDir/share/share_${ts}.jpg(JPEG95%) → buildShareIntent(FileProvider.getUriForFile + ACTION_SEND + FLAG_GRANT_READ_URI_PERMISSION) → startActivity(createChooser) → closeShareSheet |
| **前置条件** | 有照片 |
| **操作步骤** | 1. 拍照后点分享 2. 选择水印风格 3. 添加话题 4. 点击分享 5. 选择目标应用 6. 返回 |
| **状态断言点** | ① `_sharePhotoPath` 设置 ② `_showShareSheet=true` ③ 预览图加载 ④ 水印实时刷新 ⑤ `share_${timestamp}.jpg` 生成 ⑥ `FileProvider.getUriForFile` 成功 ⑦ Intent 含 `FLAG_GRANT_READ_URI_PERMISSION` ⑧ 系统分享面板弹出 |
| **预期结果** | 系统分享面板弹出，目标应用接收带水印图片 |
| **验收标准** | FileProvider 授权成功，图片含水印和话题，目标应用可访问 |

### TC-E9-02: 水印风格切换链路
| 项 | 内容 |
|----|------|
| **用例ID** | TC-E9-02 |
| **优先级** | P1 |
| **链路图** | 选水印风格(NONE/SIGNATURE/DATE_LOCATION/USERNAME_BRAND/MINIMAL) → setWatermarkStyle → ShareEngine.applyWatermarkAndTopics → 预览实时刷新 |
| **前置条件** | 分享面板 |
| **操作步骤** | 1. 依次选择 5 种水印 2. 观察预览 |
| **状态断言点** | ① `_watermarkStyle` 切换 ② 预览实时刷新 ③ 水印样式正确 |
| **预期结果** | 各水印风格正确渲染 |
| **验收标准** | 5 种风格均可选，预览同步 |

### TC-E9-03: 水印位置切换链路
| 项 | 内容 |
|----|------|
| **用例ID** | TC-E9-03 |
| **优先级** | P2 |
| **链路图** | 选水印位置(四角) → setWatermarkPosition → 预览刷新 |
| **前置条件** | 分享面板，水印非 NONE |
| **操作步骤** | 1. 选择各位置 2. 观察预览 |
| **状态断言点** | ① `_watermarkPosition` 切换 ② 预览刷新 |
| **预期结果** | 水印位置正确 |
| **验收标准** | 四角位置准确 |

### TC-E9-04: 话题添加去重链路
| 项 | 内容 |
|----|------|
| **用例ID** | TC-E9-04 |
| **优先级** | P2 |
| **链路图** | 输入话题 → addTopic → 去重去空 → 最多8个 → _shareTopics更新 → 预览刷新 |
| **前置条件** | 分享面板 |
| **操作步骤** | 1. 添加多个话题 2. 尝试重复添加 3. 尝试空话题 4. 添加超过 8 个 |
| **状态断言点** | ① 重复话题去重 ② 空话题过滤 ③ 最多 8 个 ④ `_shareTopics` 更新 |
| **预期结果** | 话题去重，上限 8 个 |
| **验收标准** | 无重复，无空，上限生效 |

### TC-E9-05: 原文件不存在链路
| 项 | 内容 |
|----|------|
| **用例ID** | TC-E9-05 |
| **优先级** | P1 |
| **链路图** | executeShare → prepareShareImageFromPath → 原文件不存在 → 返回null → shareToSystem返回false |
| **前置条件** | 照片文件已删除 |
| **操作步骤** | 1. 删除照片文件 2. 尝试分享 3. 观察失败 |
| **状态断言点** | ① 原文件不存在 ② `prepareShareImageFromPath` 返回 null ③ `shareToSystem` 返回 false |
| **预期结果** | 分享失败，不崩溃 |
| **验收标准** | 错误处理，无 NPE |

### TC-E9-06: FileProvider 授权失败链路
| 项 | 内容 |
|----|------|
| **用例ID** | TC-E9-06 |
| **优先级** | P2 |
| **链路图** | buildShareIntent → FileProvider.getUriForFile异常 → 返回null → 分享失败 |
| **前置条件** | file_paths.xml 配置错误 |
| **操作步骤** | 1. 模拟 FileProvider 异常 2. 分享 3. 观察失败 |
| **状态断言点** | ① `getUriForFile` 异常 ② `buildShareIntent` 返回 null |
| **预期结果** | 分享失败，不崩溃 |
| **验收标准** | 异常捕获，日志记录 |

### TC-E9-07: 无系统分享目标链路
| 项 | 内容 |
|----|------|
| **用例ID** | TC-E9-07 |
| **优先级** | P3 |
| **链路图** | startActivity(createChooser) → 无目标应用 → 系统提示无应用 |
| **前置条件** | 无可分享应用 |
| **操作步骤** | 1. 分享 2. 观察系统提示 |
| **状态断言点** | ① `startActivity` 调用 ② 系统处理无目标 |
| **预期结果** | 系统提示无应用 |
| **验收标准** | 不崩溃，系统提示友好 |

### TC-E9-08: 分享图片解码失败链路
| 项 | 内容 |
|----|------|
| **用例ID** | TC-E9-08 |
| **优先级** | P2 |
| **链路图** | prepareShareImageFromPath → 解码失败 → 返回null → 分享失败 |
| **前置条件** | 图片损坏 |
| **操作步骤** | 1. 分享损坏图片 2. 观察失败 |
| **状态断言点** | ① 解码失败 ② 返回 null |
| **预期结果** | 分享失败，不崩溃 |
| **验收标准** | 错误处理，无崩溃 |

### TC-E9-09: 分享路径为空保护链路
| 项 | 内容 |
|----|------|
| **用例ID** | TC-E9-09 |
| **优先级** | P2 |
| **链路图** | executeShare → _sharePhotoPath=null → 返回false → 不发起分享 |
| **前置条件** | 无分享路径 |
| **操作步骤** | 1. 无路径时分享 2. 观察无反应 |
| **状态断言点** | ① `_sharePhotoPath=null` ② 返回 false |
| **预期结果** | 无路径时不分享 |
| **验收标准** | 保护生效，无异常 |

### TC-E9-10: 分享关闭闭环链路
| 项 | 内容 |
|----|------|
| **用例ID** | TC-E9-10 |
| **优先级** | P2 |
| **链路图** | 分享完成/取消 → closeShareSheet → _showShareSheet=false → 面板消失 → 恢复拍摄 |
| **前置条件** | 分享面板显示 |
| **操作步骤** | 1. 分享或取消 2. 观察面板关闭 3. 恢复拍摄 |
| **状态断言点** | ① `closeShareSheet` 调用 ② `_showShareSheet=false` ③ 面板消失 |
| **预期结果** | 分享后恢复拍摄 |
| **验收标准** | 状态正确清理，拍摄可用 |

---

## E10. 异常恢复链路

### TC-E10-01: 相机权限运行时撤销链路
| 项 | 内容 |
|----|------|
| **用例ID** | TC-E10-01 |
| **优先级** | P0 |
| **链路图** | 拍摄中 → 切设置撤销相机权限 → 返回应用 → onResume → updatePermissionStates → hasCameraPermission=false → 显示PermissionRequestScreen → 重新授权 → 恢复预览 |
| **前置条件** | 拍摄中 |
| **操作步骤** | 1. 拍摄中切设置 2. 撤销相机权限 3. 返回应用 4. 观察权限页 5. 重新授权 |
| **状态断言点** | ① `onResume` 检测权限 ② `hasCameraPermission=false` ③ 显示 PermissionRequestScreen ④ 授权后恢复 |
| **预期结果** | 权限撤销显示请求页，授权后恢复 |
| **验收标准** | 不崩溃，状态正确切换 |

### TC-E10-02: 相机被占用恢复链路
| 项 | 内容 |
|----|------|
| **用例ID** | TC-E10-02 |
| **优先级** | P1 |
| **链路图** | 其他应用占用相机 → CameraX绑定失败 → 异常捕获 → cameraProvider=null → 用户关闭占用应用 → 恢复预览 |
| **前置条件** | 相机被其他应用占用 |
| **操作步骤** | 1. 用其他相机应用占用 2. 切到 PoseAI 3. 观察失败 4. 关闭占用应用 5. 恢复 |
| **状态断言点** | ① 绑定失败捕获 ② `camera=null` ③ 恢复后重试 |
| **预期结果** | 占用时提示，释放后恢复 |
| **验收标准** | 不崩溃，可恢复 |

### TC-E10-03: 存储空间不足链路
| 项 | 内容 |
|----|------|
| **用例ID** | TC-E10-03 |
| **优先级** | P1 |
| **链路图** | 存储满 → 拍照保存 → 写文件异常 → _photoSaveError="照片保存失败：..." → UI提示 |
| **前置条件** | 存储空间不足 |
| **操作步骤** | 1. 填满存储 2. 拍照 3. 观察错误 4. 清理空间 5. 重试 |
| **状态断言点** | ① 写文件异常 ② `_photoSaveError` 设置 ③ UI 提示 |
| **预期结果** | 存储不足提示错误 |
| **验收标准** | 不崩溃，错误清晰 |

### TC-E10-04: AI 模型激活失败降级链路
| 项 | 内容 |
|----|------|
| **用例ID** | TC-E10-04 |
| **优先级** | P2 |
| **链路图** | AIModelManager.activate → 远程下载失败 → 检测assets iOS模型元数据 → 启用关键词映射降级 → SceneClassifier启发式fallback |
| **前置条件** | 网络不可用，无本地模型 |
| **操作步骤** | 1. 离线启动 2. AI 激活 3. 观察降级 |
| **状态断言点** | ① 下载失败 ② `hasIosModelAssets` 检测 ③ 关键词映射启用 ④ 启发式 fallback |
| **预期结果** | 模型失败降级到启发式 |
| **验收标准** | 场景检测仍工作，不崩溃 |

### TC-E10-05: 数据库损坏降级链路
| 项 | 内容 |
|----|------|
| **用例ID** | TC-E10-05 |
| **优先级** | P1 |
| **链路图** | 数据库文件损坏 → Room初始化失败 → fallbackToDestructiveMigration → 失败 → 降级inMemoryDatabaseBuilder → 应用可用(重启丢失) |
| **前置条件** | 数据库文件损坏 |
| **操作步骤** | 1. 破坏数据库文件 2. 启动应用 3. 观察 |
| **状态断言点** | ① Room 初始化异常 ② 降级到内存数据库 ③ `allowMainThreadQueries` ④ 应用不崩溃 |
| **预期结果** | 数据库损坏降级到内存库 |
| **验收标准** | 应用可用，logcat 提示降级 |

### TC-E10-06: Vlog 合成异常恢复链路
| 项 | 内容 |
|----|------|
| **用例ID** | TC-E10-06 |
| **优先级** | P2 |
| **链路图** | mergeVlog → 合成异常 → _vlogErrorMessage="Vlog合成异常：${e.message}" → VideoMerger清理(空输出删除) → 用户可重试 |
| **前置条件** | Vlog 合成 |
| **操作步骤** | 1. 模拟合成异常 2. 观察错误 3. 重试 |
| **状态断言点** | ① 异常捕获 ② `_vlogErrorMessage` 设置 ③ VideoMerger 清理 |
| **预期结果** | 合成异常提示，可重试 |
| **验收标准** | 无残留文件，状态可恢复 |

### TC-E10-07: Billing 初始化失败降级链路
| 项 | 内容 |
|----|------|
| **用例ID** | TC-E10-07 |
| **优先级** | P2 |
| **链路图** | PoseAIApp.onCreate → BillingManager初始化异常 → 捕获日志 → 应用可用(无IAP) → Pro功能不可购买 |
| **前置条件** | Billing 库异常 |
| **操作步骤** | 1. 模拟 Billing 异常 2. 启动应用 3. 使用应用 |
| **状态断言点** | ① 初始化异常捕获 ② 应用不崩溃 ③ IAP 不可用 |
| **预期结果** | Billing 失败不影响核心功能 |
| **验收标准** | 应用可用，Pro 入口无响应或提示 |

### TC-E10-08: 生命周期资源清理链路
| 项 | 内容 |
|----|------|
| **用例ID** | TC-E10-08 |
| **优先级** | P1 |
| **链路图** | ViewModel.onCleared → 停止自动抓拍/Vlog/倒计时/连拍 → 取消reset Job → 注销传感器 → shutdown相机 → close所有检测器/TTS/toneGenerator |
| **前置条件** | 退出拍摄页 |
| **操作步骤** | 1. 进入拍摄页 2. 开始各类功能 3. 退出 4. 检查资源释放 |
| **状态断言点** | ① `onCleared` 触发 ② 所有协程取消 ③ 传感器注销 ④ 相机 shutdown ⑤ 检测器 close |
| **预期结果** | 退出时资源完整释放 |
| **验收标准** | 无内存泄漏，无残留传感器 |

### TC-E10-09: 过热降帧保护链路
| 项 | 内容 |
|----|------|
| **用例ID** | TC-E10-09 |
| **优先级** | P2 |
| **链路图** | 温度>450 → batteryReceiver → setHeatWarning(true) → 姿势检测间隔100ms→200ms → 降低负载 |
| **前置条件** | 过热环境 |
| **操作步骤** | 1. 模拟过热 2. 观察降帧 3. 恢复常温 4. 观察恢复 |
| **状态断言点** | ① `isHeatWarning=true` ② 检测间隔 `POSE_FRAME_INTERVAL_HOT(200ms)` ③ 恢复后 100ms |
| **预期结果** | 过热时降帧保护 |
| **验收标准** | 帧率降低，恢复后回升 |

### TC-E10-10: 暗光首次震动警告链路
| 项 | 内容 |
|----|------|
| **用例ID** | TC-E10-10 |
| **优先级** | P3 |
| **链路图** | 暗光首次触发 → vibrateWarn() → 重震动反馈 → 后续不重复 |
| **前置条件** | 暗光环境 |
| **操作步骤** | 1. 进入暗光 2. 观察首次震动 3. 保持暗光 4. 观察不重复 |
| **状态断言点** | ① 首次触发 `vibrateWarn()` ② 后续不重复 |
| **预期结果** | 首次暗光震动提醒 |
| **验收标准** | 不频繁打扰 |

### TC-E10-11: 姿势检测每帧异常隔离链路
| 项 | 内容 |
|----|------|
| **用例ID** | TC-E10-11 |
| **优先级** | P2 |
| **链路图** | 帧分析器 → 姿势检测异常 → try-catch日志 → 不影响下一帧 → 继续检测 |
| **前置条件** | 检测中偶发异常 |
| **操作步骤** | 1. 模拟单帧异常 2. 观察后续帧 |
| **状态断言点** | ① 异常捕获 ② 日志记录 ③ 下一帧正常 |
| **预期结果** | 单帧异常不影响整体 |
| **验收标准** | 检测持续，无中断 |

### TC-E10-12: 前台服务启动失败容错链路
| 项 | 内容 |
|----|------|
| **用例ID** | TC-E10-12 |
| **优先级** | P2 |
| **链路图** | startRecordingForegroundService → 启动异常 → 仅日志 → 不影响录制本身 |
| **前置条件** | 前台服务启动失败 |
| **操作步骤** | 1. 模拟服务启动失败 2. 开始录制 3. 观察录制 |
| **状态断言点** | ① 异常捕获 ② 仅日志 ③ 录制继续 |
| **预期结果** | 服务失败不影响录制 |
| **验收标准** | 录制正常，日志记录 |

### TC-E10-13: 全局未捕获异常记录链路
| 项 | 内容 |
|----|------|
| **用例ID** | TC-E10-13 |
| **优先级** | P2 |
| **链路图** | 未捕获异常 → setupUncaughtExceptionHandler → 记录线程/异常/设备/版本 → 转交默认处理器 → 进程终止 |
| **前置条件** | 致命异常 |
| **操作步骤** | 1. 触发未捕获异常 2. 检查日志 3. 观察进程终止 |
| **状态断言点** | ① 异常捕获 ② 日志含完整信息 ③ 转交默认处理器 |
| **预期结果** | 崩溃日志完整 |
| **验收标准** | 日志含设备型号/API/版本，便于定位 |

### TC-E10-14: 多权限分级恢复链路
| 项 | 内容 |
|----|------|
| **用例ID** | TC-E10-14 |
| **优先级** | P1 |
| **链路图** | 多权限被撤销 → onResume → updatePermissionStates → 分级处理(相机阻断/录音降级/存储降级/通知降级) → 各自恢复路径 |
| **前置条件** | 多权限被撤销 |
| **操作步骤** | 1. 撤销相机/录音/存储/通知 2. 返回应用 3. 观察分级处理 |
| **状态断言点** | ① 相机阻断显示请求页 ② 录音降级无音频 ③ 存储降级无法保存 ④ 通知降级无通知 |
| **预期结果** | 各权限分级处理 |
| **验收标准** | 核心权限阻断，次要权限降级，不崩溃 |

---

## E11. 跨链路组合场景

### TC-E11-01: 拍照→编辑→分享完整闭环
| 项 | 内容 |
|----|------|
| **用例ID** | TC-E11-01 |
| **优先级** | P0 |
| **链路图** | 拍照(E2) → 相册 → 编辑(E4) → 保存 → 分享(E9) → 系统面板 → 返回 |
| **前置条件** | 完整功能可用 |
| **操作步骤** | 1. 拍照 2. 进相册 3. 编辑(裁剪+滤镜) 4. 保存 5. 分享(带水印) 6. 返回 |
| **状态断言点** | ① 拍照保存成功 ② 编辑后 DB 更新 ③ 分享图片为编辑后版本 ④ 水印正确 |
| **预期结果** | 跨链路闭环通畅 |
| **验收标准** | 数据流转正确，分享图片含编辑和水印 |

### TC-E11-02: Vlog录制→相册→分享闭环
| 项 | 内容 |
|----|------|
| **用例ID** | TC-E11-02 |
| **优先级** | P1 |
| **链路图** | Vlog录制(E3) → 合成 → 相册 → 视频分享 → 系统面板 |
| **前置条件** | 完整功能可用 |
| **操作步骤** | 1. 录制 Vlog 2. 合成完成 3. 进相册 4. 分享视频 5. 选择目标 |
| **状态断言点** | ① Vlog 合成成功 ② 相册可见视频 ③ 分享 Intent 含视频 URI |
| **预期结果** | Vlog 可分享 |
| **验收标准** | 视频文件可被目标应用接收 |

### TC-E11-03: 设置→拍摄→验证闭环
| 项 | 内容 |
|----|------|
| **用例ID** | TC-E11-03 |
| **优先级** | P1 |
| **链路图** | 设置(E8) → 修改网格/微笑/倒计时/画质 → 拍照(E2) → 验证设置生效 |
| **前置条件** | 设置页 |
| **操作步骤** | 1. 开启网格 2. 开启微笑快门 3. 设置倒计时 4. 设置画质 5. 拍照 6. 验证 |
| **状态断言点** | ① 设置持久化 ② 拍照应用设置 ③ 网格显示/微笑触发/倒计时/画质 |
| **预期结果** | 设置在拍摄中生效 |
| **验收标准** | 各设置项正确应用 |

### TC-E11-04: 自定义姿势→拍照→评分闭环
| 项 | 内容 |
|----|------|
| **用例ID** | TC-E11-04 |
| **优先级** | P1 |
| **链路图** | 自定义姿势(E7) → 保存 → 应用 → 拍照(E2) → 评分基于自定义 |
| **前置条件** | 智能导拍 |
| **操作步骤** | 1. 保存自定义姿势 2. 应用 3. 摆姿势 4. 观察评分 5. 拍照 |
| **状态断言点** | ① 自定义姿势应用 ② 评分基于自定义模板 ③ 拍照保存 |
| **预期结果** | 评分基于自定义模板 |
| **验收标准** | 评分逻辑正确切换 |

### TC-E11-05: 内购→Pro功能解锁闭环
| 项 | 内容 |
|----|------|
| **用例ID** | TC-E11-05 |
| **优先级** | P0 |
| **链路图** | 内购(E6) → Pro解锁 → 高级美颜/AR特效/无水印/自定义导出可用 |
| **前置条件** | 未解锁 Pro |
| **操作步骤** | 1. 购买 Pro 2. 验证高级美颜可用 3. 验证 AR 特效 4. 验证无水印 5. 验证自定义导出 |
| **状态断言点** | ① `isProUnlocked=true` ② `requiresProUnlock` 拦截放行 ③ Pro 功能可用 |
| **预期结果** | Pro 功能全部解锁 |
| **验收标准** | 高级美颜/AR/无水印/自定义导出可用 |

### TC-E11-06: 引导→权限→拍摄→相册首次闭环
| 项 | 内容 |
|----|------|
| **用例ID** | TC-E11-06 |
| **优先级** | P0 |
| **链路图** | 首次启动(E1) → 引导 → AI激活 → 权限授予 → 拍照(E2) → 相册查看 |
| **前置条件** | 首次安装 |
| **操作步骤** | 1. 首次启动 2. 完成引导 3. AI 激活 4. 授予权限 5. 拍照 6. 查看相册 |
| **状态断言点** | ① 引导完成持久化 ② 权限授予 ③ 拍照成功 ④ 相册可见 |
| **预期结果** | 首次完整体验闭环 |
| **验收标准** | 全链路无阻断 |

### TC-E11-07: OOTD→编辑→分享闭环
| 项 | 内容 |
|----|------|
| **用例ID** | TC-E11-07 |
| **优先级** | P2 |
| **链路图** | OOTD分析(E5) → 选图分析 → 进相册编辑(E4) → 分享(E9) |
| **前置条件** | 有穿搭照片 |
| **操作步骤** | 1. OOTD 分析 2. 进相册 3. 编辑该图 4. 分享 |
| **状态断言点** | ① OOTD 分析完成 ② 编辑同一图 ③ 分享编辑后图 |
| **预期结果** | 跨功能闭环 |
| **验收标准** | 数据流转正确 |

### TC-E11-08: 热启动→状态恢复→继续拍摄闭环
| 项 | 内容 |
|----|------|
| **用例ID** | TC-E11-08 |
| **优先级** | P1 |
| **链路图** | 拍摄中 → Home → 后台 → 恢复(E1热启动) → 预览恢复 → 继续拍摄(E2) |
| **前置条件** | 拍摄中 |
| **操作步骤** | 1. 拍照 2. Home 退后台 3. 恢复 4. 继续拍照 |
| **状态断言点** | ① 后台资源暂停 ② 前台恢复 ③ 预览正常 ④ 拍照可用 |
| **预期结果** | 热启动后继续拍摄 |
| **验收标准** | 无状态丢失，预览恢复 |

### TC-E11-09: 多次拍照→相册批量查看闭环
| 项 | 内容 |
|----|------|
| **用例ID** | TC-E11-09 |
| **优先级** | P2 |
| **链路图** | 连续多次拍照(E2) → 相册 → 批量查看 → 多选 → 删除 |
| **前置条件** | 相册为空 |
| **操作步骤** | 1. 连拍 5 张 2. 进相册 3. 查看列表 4. 多选 3 张 5. 删除 |
| **状态断言点** | ① 5 张照片入库 ② 相册列表 5 条 ③ 多选 3 张 ④ 删除后 2 条 |
| **预期结果** | 批量操作正确 |
| **验收标准** | Room Flow 刷新，删除后列表更新 |

### TC-E11-10: 主题切换→全页面响应闭环
| 项 | 内容 |
|----|------|
| **用例ID** | TC-E11-10 |
| **优先级** | P2 |
| **链路图** | 设置主题(E8) → 切换暗/亮 → 拍摄页/相册/穿搭/编辑器全响应 |
| **前置条件** | 应用运行中 |
| **操作步骤** | 1. 切换暗色主题 2. 遍历各页面 3. 切换亮色 4. 遍历验证 |
| **状态断言点** | ① `themeMode` 变化 ② 各页面 ColorScheme 响应 |
| **预期结果** | 全页面主题一致 |
| **验收标准** | 无页面遗漏，主题统一 |

---

## 附录：覆盖率统计

### 链路覆盖率
| 链路 | 用例数 | 覆盖链路 |
|------|--------|----------|
| E1 应用启动与导航 | 10 | 冷启动/引导/AI激活/权限/导航/热启动/付费墙入口 |
| E2 拍照完整 | 14 | 预览/姿势/构图/倒计时/连拍/微笑/保存/相册闭环 |
| E3 Vlog 录制 | 10 | 模板/分镜/合成/BGM/字幕/导出/分享闭环 |
| E4 照片编辑 | 12 | 选图/加载/裁剪/旋转/滤镜/撤销/保存/DB刷新闭环 |
| E5 OOTD 分析 | 8 | 选图/复制/解码/分析/评分/建议/资源释放闭环 |
| E6 内购 | 10 | 触发/连接/购买/确认/解锁/恢复/重连/已拥有闭环 |
| E7 自定义姿势 | 8 | 打开/保存/去重/应用/覆盖/删除/清空/场景切换闭环 |
| E8 设置与主题 | 10 | 主题/网格/微笑/水印/倒计时/画质/HDR/补光/持久化闭环 |
| E9 分享 | 10 | 打开/预览/水印/话题/FileProvider/系统面板/关闭闭环 |
| E10 异常恢复 | 14 | 权限/相机/存储/AI模型/合成/购买/生命周期/电池温度 |
| E11 跨链路组合 | 10 | 拍照→编辑→分享等 10 个组合场景 |
| **合计** | **116** | **11 大链路 100% 覆盖** |

### 优先级分布
| 优先级 | 用例数 | 占比 |
|--------|--------|------|
| P0 | 16 | 13.8% |
| P1 | 47 | 40.5% |
| P2 | 50 | 43.1% |
| P3 | 3 | 2.6% |

### 跨链路组合覆盖
| 组合场景 | 对应用例 |
|----------|----------|
| 拍照→编辑→分享 | TC-E11-01 |
| Vlog→相册→分享 | TC-E11-02 |
| 设置→拍摄→验证 | TC-E11-03 |
| 自定义姿势→拍照→评分 | TC-E11-04 |
| 内购→Pro功能解锁 | TC-E11-05 |
| 引导→权限→拍摄→相册 | TC-E11-06 |
| OOTD→编辑→分享 | TC-E11-07 |
| 热启动→恢复→拍摄 | TC-E11-08 |
| 多拍照→相册批量 | TC-E11-09 |
| 主题→全页面响应 | TC-E11-10 |

### 关键状态流转覆盖
| 状态流转点 | 对应用例 |
|------------|----------|
| onboardingCompleted → 起点destination | TC-E1-01、TC-E1-09 |
| hasCameraPermission → 预览/权限页 | TC-E1-03、TC-E10-01 |
| _isProUnlocked → Pro功能/付费墙关闭 | TC-E6-01、TC-E6-10、TC-E11-05 |
| _isVlogRecording → 拍照互斥 | TC-E2-10、TC-E3-06 |
| themeMode → ColorScheme | TC-E8-01、TC-E8-02、TC-E11-10 |
| currentPlan → 自定义/内置切换 | TC-E7-04、TC-E7-07、TC-E11-04 |
| ShootingRecord imagePath → 编辑后更新 | TC-E4-01、TC-E4-09、TC-E4-12 |
| _showShareSheet → FileProvider授权 | TC-E9-01、TC-E9-06 |
| isHeatWarning → 降帧保护 | TC-E1-06、TC-E10-09 |
| fallbackToDestructiveMigration → 内存库 | TC-E10-05 |

### 异常恢复链路覆盖
| 异常场景 | 恢复路径 | 对应用例 |
|----------|----------|----------|
| 相机权限撤销 | PermissionRequestScreen → 重新授权 | TC-E10-01 |
| 相机被占用 | 异常捕获 → 释放后恢复 | TC-E10-02 |
| 存储不足 | _photoSaveError → 清理重试 | TC-E10-03 |
| AI模型失败 | 关键词映射 → 启发式fallback | TC-E10-04 |
| 数据库损坏 | inMemoryDatabase降级 | TC-E10-05 |
| Vlog合成异常 | _vlogErrorMessage → 重试 | TC-E10-06 |
| Billing失败 | 应用可用无IAP | TC-E10-07 |
| 生命周期退出 | onCleared资源释放 | TC-E10-08 |
| 过热 | 降帧保护 | TC-E10-09 |
| 单帧检测异常 | try-catch隔离 | TC-E10-11 |

---

> **文档版本**: v1.0
> **生成依据**: 代码库 53 个 Kotlin 源文件逐链路梳理，10 大主链路 + 跨链路组合 + 异常恢复链路
> **配套文档**: `android-functional-test-cases-v2.2.1.md`（单点功能验证，与本 E2E 链路测试互补）
