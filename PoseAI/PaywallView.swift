import SwiftUI

// MARK: - Paywall View
struct PaywallView: View {
    @Environment(\.dismiss) var dismiss
    @EnvironmentObject var storeManager: StoreManager

    // 购买中状态（本地 UI）
    @State private var isPurchasing = false
    @State private var showError = false

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()

            // 背景渐变（深空黑调）
            LinearGradient(
                colors: [Color(red: 0.05, green: 0.08, blue: 0.07), .black],
                startPoint: .topLeading, endPoint: .bottomTrailing
            )
            .ignoresSafeArea()

            VStack(spacing: 30) {
                // 顶部关闭按钮
                HStack {
                    Spacer()
                    Button {
                        dismiss()
                    } label: {
                        Image(systemName: "xmark.circle.fill")
                            .font(.system(size: 28))
                            .foregroundColor(.white.opacity(0.3))
                    }
                }
                .padding(.horizontal, 24)
                .padding(.top, 20)

                // 图标
                ZStack {
                    Circle()
                        .fill(RadialGradient(
                            colors: [Design.accent.opacity(0.3), .clear],
                            center: .center, startRadius: 10, endRadius: 80
                        ))
                        .frame(width: 140, height: 140)

                    Image(systemName: "crown.fill")
                        .font(.system(size: 60))
                        .foregroundStyle(
                            LinearGradient(
                                colors: [Design.accent, Color(red: 0.08, green: 0.70, blue: 0.55)],
                                startPoint: .top, endPoint: .bottom
                            )
                        )
                        .shadow(color: Design.accent.opacity(0.5), radius: 10, x: 0, y: 5)
                }

                // 标题
                VStack(spacing: 8) {
                    if storeManager.isPro {
                        // 已购买状态
                        Text("已解锁 PoseAI Pro")
                            .font(.system(size: 28, weight: .bold))
                            .foregroundColor(.white)
                        Text("感谢你的支持，尽情享受所有高级功能")
                            .font(.system(size: 15))
                            .foregroundColor(Design.success.opacity(0.8))
                    } else {
                        Text("解锁 PoseAI Pro")
                            .font(.system(size: 28, weight: .bold))
                            .foregroundColor(.white)
                        Text("释放完整拍摄潜力，拍出电影级大片")
                            .font(.system(size: 15))
                            .foregroundColor(.white.opacity(0.6))
                    }
                }

                // 特权列表
                VStack(alignment: .leading, spacing: 20) {
                    ProFeatureRow(icon: "sparkles", title: "全场景方案库", desc: "解锁街道、公园、家居等专属姿势推荐")
                    ProFeatureRow(icon: "camera.burst.fill", title: "阵发无限连拍", desc: "不再局限于单张，高速抓拍不错过任何瞬间")
                    ProFeatureRow(icon: "photo.badge.plus", title: "无水印纯净保存", desc: "解锁取消专属底标的功能配置")
                }
                .padding(.horizontal, 32)
                .padding(.top, 10)

                Spacer()

                // 购买按钮区域
                if storeManager.isPro {
                    // 已购买 — 显示确认按钮
                    purchasedSection
                } else if storeManager.products.isEmpty {
                    // 商品加载中
                    loadingSection
                } else {
                    // 正常购买流程
                    purchaseSection
                }
            }
        }
        .alert("提示", isPresented: $showError) {
            Button("好的", role: .cancel) {
                storeManager.errorMessage = nil
            }
        } message: {
            Text(storeManager.errorMessage ?? "发生未知错误")
        }
        .onChange(of: storeManager.errorMessage) { newValue in
            if newValue != nil { showError = true }
        }
        .onChange(of: storeManager.isPro) { newValue in
            if newValue {
                // 购买成功后自动关闭
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.8) {
                    dismiss()
                }
            }
        }
    }

    // MARK: - 正常购买区域

    private var purchaseSection: some View {
        VStack(spacing: 16) {
            // 价格标签 —— 从 App Store 动态读取
            if let product = storeManager.proProduct {
                Text("限时优惠：\(product.displayPrice) / 终身买断")
                    .font(.system(size: 12, weight: .bold))
                    .foregroundColor(Design.accent)
                    .padding(.horizontal, 12)
                    .padding(.vertical, 6)
                    .background(Design.accent.opacity(0.15), in: Capsule())
            }

            // 购买按钮
            Button {
                Task { await performPurchase() }
            } label: {
                HStack(spacing: 8) {
                    if isPurchasing {
                        ProgressView()
                            .progressViewStyle(CircularProgressViewStyle(tint: .black))
                            .scaleEffect(0.8)
                    }
                    Text(isPurchasing ? "处理中…" : "立即升级")
                        .font(.system(size: 17, weight: .bold))
                }
                .foregroundColor(.black)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 16)
                .background(
                    LinearGradient(
                        colors: [Design.accent, Color(red: 0.08, green: 0.70, blue: 0.55)],
                        startPoint: .topLeading, endPoint: .bottomTrailing
                    ),
                    in: Capsule()
                )
                .shadow(color: Design.accent.opacity(0.4), radius: 12, y: 5)
                .opacity(isPurchasing ? 0.7 : 1.0)
            }
            .disabled(isPurchasing)

            // 底部链接
            HStack(spacing: 20) {
                Button("恢复购买") {
                    Task { await performRestore() }
                }
                Button("服务条款") { }
            }
            .font(.system(size: 11))
            .foregroundColor(.white.opacity(0.4))
        }
        .padding(.horizontal, 32)
        .padding(.bottom, 40)
    }

    // MARK: - 加载中区域

    private var loadingSection: some View {
        VStack(spacing: 16) {
            ProgressView()
                .progressViewStyle(CircularProgressViewStyle(tint: .white))
                .scaleEffect(1.2)

            Text("正在加载商品信息…")
                .font(.system(size: 13))
                .foregroundColor(.white.opacity(0.5))

            // 重试按钮
            Button {
                Task { await storeManager.loadProducts() }
            } label: {
                Text("重试")
                    .font(.system(size: 13, weight: .medium))
                    .foregroundColor(Design.accent)
            }
            .padding(.top, 4)
        }
        .padding(.bottom, 60)
    }

    // MARK: - 已购买区域

    private var purchasedSection: some View {
        VStack(spacing: 16) {
            HStack(spacing: 8) {
                Image(systemName: "checkmark.seal.fill")
                    .font(.system(size: 18))
                    .foregroundColor(Design.success)
                Text("已解锁全部特权")
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundColor(Design.success)
            }
            .padding(.horizontal, 20)
            .padding(.vertical, 12)
            .background(Design.success.opacity(0.15), in: Capsule())

            Button {
                dismiss()
            } label: {
                Text("完成")
                    .font(.system(size: 17, weight: .bold))
                    .foregroundColor(.black)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 16)
                    .background(Design.success, in: Capsule())
            }
        }
        .padding(.horizontal, 32)
        .padding(.bottom, 40)
    }

    // MARK: - 操作

    private func performPurchase() async {
        guard let product = storeManager.proProduct else { return }
        isPurchasing = true
        defer { isPurchasing = false }

        do {
            try await storeManager.purchase(product)
        } catch {
            // 错误已由 StoreManager 设置到 errorMessage
            #if DEBUG
            print("[PaywallView] 购买失败: \(error)")
            #endif
        }
    }

    private func performRestore() async {
        isPurchasing = true
        defer { isPurchasing = false }
        await storeManager.restorePurchases()
    }
}

// MARK: - Pro 特权行
struct ProFeatureRow: View {
    let icon: String
    let title: String
    let desc: String

    var body: some View {
        HStack(alignment: .top, spacing: 16) {
            ZStack {
                Circle()
                    .fill(Design.accent.opacity(0.15))
                    .frame(width: 44, height: 44)
                Image(systemName: icon)
                    .font(.system(size: 18, weight: .semibold))
                    .foregroundColor(Design.accent)
            }

            VStack(alignment: .leading, spacing: 4) {
                Text(title)
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundColor(.white)
                Text(desc)
                    .font(.system(size: 13))
                    .foregroundColor(.white.opacity(0.6))
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
    }
}
