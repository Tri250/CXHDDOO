import StoreKit
import SwiftUI

/// StoreKit 2 内购管理器
/// 职责：商品加载、购买、恢复购买、Transaction 后台监听
/// 替换原有 @AppStorage("isPro") 的模拟逻辑
@MainActor
final class StoreManager: ObservableObject {

    // MARK: - 商品 ID

    /// 终身买断 Pro（非消耗型 Non-Consumable）
    static let proProductID = "com.lucas.poseai.studio1.pro.lifetime"

    private static let allProductIDs: Set<String> = [proProductID]

    // MARK: - 可观察状态

    /// App Store Connect 加载到的商品列表
    @Published var products: [Product] = []

    /// 当前已购买的商品 ID 集合
    @Published var purchasedProductIDs: Set<String> = []

    /// 正在执行购买/恢复操作
    @Published var isLoading: Bool = false

    /// 用户可见的错误消息（nil = 无错误）
    @Published var errorMessage: String? = nil

    // MARK: - Pro 状态判定

    /// 兼容旧版 @AppStorage 标记（S1 阶段的模拟购买会写入此值）
    /// 一旦 StoreKit 2 接管，此值仅作为向后兼容读取
    @AppStorage("isPro") private var isProLegacy: Bool = false

    /// 是否为 Pro 用户
    /// 优先检查 StoreKit 2 的真实购买记录，同时兼容旧版 AppStorage 标记
    var isPro: Bool {
        purchasedProductIDs.contains(Self.proProductID) || isProLegacy
    }

    /// 便捷获取 Pro 商品（用于 PaywallView 显示价格和购买）
    var proProduct: Product? {
        products.first { $0.id == Self.proProductID }
    }

    // MARK: - 内部

    /// 后台 Transaction 监听任务
    private var updateListenerTask: Task<Void, Never>?

    // MARK: - 生命周期

    init() {
        // 启动后台 Transaction 监听（退款、家庭共享、外部购买等）
        updateListenerTask = listenForTransactions()

        // 异步加载商品和购买状态
        Task {
            await loadProducts()
            await updatePurchasedProducts()
        }
    }

    deinit {
        updateListenerTask?.cancel()
    }

    // MARK: - 1. 加载商品

    /// 从 App Store Connect 加载商品信息
    func loadProducts() async {
        do {
            let storeProducts = try await Product.products(for: Self.allProductIDs)
            // 按 price 排序（当前只有一个商品，预留扩展）
            products = storeProducts.sorted { $0.price < $1.price }

            #if DEBUG
            print("[StoreManager] 加载到 \(products.count) 个商品:")
            for p in products {
                print("  - \(p.id): \(p.displayName) \(p.displayPrice)")
            }
            #endif
        } catch {
            #if DEBUG
            print("[StoreManager] 商品加载失败: \(error.localizedDescription)")
            #endif
            // 商品加载失败不阻塞使用，errorMessage 留给购买时设置
        }
    }

    // MARK: - 2. 购买

    /// 执行购买流程
    /// - Parameter product: 要购买的商品
    /// - Returns: 购买结果的 Transaction（成功时）
    @discardableResult
    func purchase(_ product: Product) async throws -> StoreKit.Transaction? {
        isLoading = true
        errorMessage = nil

        defer { isLoading = false }

        do {
            let result = try await product.purchase()

            switch result {
            case .success(let verification):
                // 验证交易签名
                let transaction = try checkVerified(verification)
                // 更新已购买状态
                await updatePurchasedProducts()
                // 标记交易完成
                await transaction.finish()

                #if DEBUG
                print("[StoreManager] 购买成功: \(transaction.productID)")
                #endif

                return transaction

            case .userCancelled:
                #if DEBUG
                print("[StoreManager] 用户取消购买")
                #endif
                return nil

            case .pending:
                #if DEBUG
                print("[StoreManager] 购买等待审批（家长控制/Ask to Buy）")
                #endif
                errorMessage = String(localized: "purchase_pending")
                return nil

            @unknown default:
                return nil
            }
        } catch let error as StoreKitError {
            errorMessage = String(localized: "store_not_available")
            throw error
        } catch {
            errorMessage = String(localized: "purchase_failed_generic")
            #if DEBUG
            print("[StoreManager] 购买异常: \(error)")
            #endif
            throw error
        }
    }

    // MARK: - 3. 恢复购买

    /// 强制刷新购买状态（用于"恢复购买"按钮）
    func restorePurchases() async {
        isLoading = true
        errorMessage = nil

        defer { isLoading = false }

        // StoreKit 2: 同步最新的 App Store 交易记录
        // 这会触发用户输入 Apple ID 密码进行验证
        do {
            try await AppStore.sync()
        } catch {
            #if DEBUG
            print("[StoreManager] AppStore.sync 失败: \(error)")
            #endif
        }

        // 刷新本地购买状态
        await updatePurchasedProducts()

        if purchasedProductIDs.isEmpty {
            errorMessage = String(localized: "no_purchases_to_restore")
        }

        #if DEBUG
        print("[StoreManager] 恢复购买完成, 已购: \(purchasedProductIDs)")
        #endif
    }

    // MARK: - 4. 后台 Transaction 监听

    /// 监听外部触发的 Transaction 变更：
    /// - 退款（Pro 被撤销）
    /// - 家庭共享（新成员获得权限）
    /// - 外部购买（促销码兑换等）
    /// - Ask to Buy 批准
    private func listenForTransactions() -> Task<Void, Never> {
        Task.detached { [weak self] in
            // Transaction.updates 不包括 App 启动时已有的交易
            // 只会收到运行期间发生的新状态变更
            for await result in StoreKit.Transaction.updates {
                guard let self = self else { break }
                do {
                    let transaction = try await MainActor.run {
                        try self.checkVerified(result)
                    }
                    await self.updatePurchasedProducts()
                    await transaction.finish()
                } catch {
                    #if DEBUG
                    print("[StoreManager] Transaction 验证失败: \(error)")
                    #endif
                }
            }
        }
    }

    // MARK: - 5. 刷新已购商品

    /// 遍历当前用户的所有有效 entitlement，更新 purchasedProductIDs
    func updatePurchasedProducts() async {
        var purchased = Set<String>()

        // Transaction.currentEntitlements: 当前有效的所有已购商品
        // 对于非消耗型商品，购买过就永远在这个列表中（除非被退款）
        for await result in Transaction.currentEntitlements {
            do {
                let transaction = try checkVerified(result)
                // 非消耗型 / 活跃订阅才算有效
                if transaction.revocationDate == nil {
                    purchased.insert(transaction.productID)
                }
            } catch {
                #if DEBUG
                print("[StoreManager] entitlement 验证失败: \(error)")
                #endif
            }
        }

        purchasedProductIDs = purchased

        // 如果 StoreKit 2 确认了购买，也同步到旧 AppStorage（单向写入）
        if purchased.contains(Self.proProductID) {
            isProLegacy = true
        }

        #if DEBUG
        print("[StoreManager] 已购商品更新: \(purchased), isPro=\(isPro)")
        #endif
    }

    // MARK: - 交易验证

    /// 解包并验证 VerificationResult
    /// StoreKit 2 内置了设备端签名验证（JWS / App Attest）
    private func checkVerified<T>(_ result: VerificationResult<T>) throws -> T {
        switch result {
        case .unverified(_, let error):
            throw error
        case .verified(let value):
            return value
        }
    }
}
