package com.poseai.app.store

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.poseai.app.PoseAIApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "BillingManager"

/**
 * 封装 Google Play BillingClient，负责一次性 Pro 解锁商品的购买、恢复与状态持久化。
 *
 * 商品 ID `poseai_pro_unlock` 为 INAPP（非订阅）一次性购买。
 * 购买成功或恢复后通过 [StoreManager.setProUnlocked] 持久化，并更新 [isProUnlocked]。
 */
class BillingManager(private val context: Context) : PurchasesUpdatedListener {

    companion object {
        /** Pro 解锁一次性购买商品 ID */
        const val PRO_PRODUCT_ID = "poseai_pro_unlock"

        /** 连接断开后的重连延迟 */
        private const val RECONNECT_DELAY_MS = 3000L
        /** 最大重连尝试次数 */
        private const val MAX_RECONNECT_RETRY = 5
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _isProUnlocked = MutableStateFlow(false)
    /** Pro 解锁状态：购买/恢复后为 true，UI 通过 collectAsState 观察 */
    val isProUnlocked: StateFlow<Boolean> = _isProUnlocked.asStateFlow()

    private val billingClient: BillingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases()
        .build()

    private var retryCount = 0
    private var isConnectionEstablished = false

    init {
        // 启动时从 DataStore 恢复已持久化的 Pro 状态
        scope.launch {
            val persisted = PoseAIApp.getStoreManager().proUnlocked.first()
            _isProUnlocked.value = persisted
        }
    }

    /**
     * 建立 BillingClient 连接。连接断开时会自动重连，最多重试 [MAX_RECONNECT_RETRY] 次。
     */
    fun startConnection() {
        if (billingClient.connectionState == BillingClient.ConnectionState.CONNECTED) {
            isConnectionEstablished = true
            return
        }
        if (billingClient.connectionState == BillingClient.ConnectionState.CONNECTING) {
            return
        }
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    Log.i(TAG, "BillingClient connected")
                    isConnectionEstablished = true
                    retryCount = 0
                    // 连接成功后主动查询已有购买，恢复 Pro 状态
                    restorePurchases()
                } else {
                    Log.w(TAG, "BillingClient setup failed: ${billingResult.responseCode}")
                    isConnectionEstablished = false
                    retryReconnect()
                }
            }

            override fun onBillingServiceDisconnected() {
                Log.w(TAG, "BillingClient disconnected, will retry")
                isConnectionEstablished = false
                retryReconnect()
            }
        })
    }

    private fun retryReconnect() {
        if (retryCount >= MAX_RECONNECT_RETRY) {
            Log.e(TAG, "Reached max reconnect retry ($MAX_RECONNECT_RETRY), give up")
            return
        }
        retryCount++
        scope.launch {
            kotlinx.coroutines.delay(RECONNECT_DELAY_MS)
            if (!isConnectionEstablished) {
                Log.i(TAG, "Reconnecting billing client (attempt $retryCount)")
                startConnection()
            }
        }
    }

    /**
     * 查询指定商品的详情。
     * BillingClient 网络调用在 IO 线程执行，结果在回调中返回。
     *
     * @param productId 商品 ID，默认为 Pro 解锁商品
     * @param onResult 查询结果回调，主线程；成功时返回 ProductDetails，失败时为 null
     */
    fun queryProductDetails(
        productId: String = PRO_PRODUCT_ID,
        onResult: (ProductDetails?) -> Unit
    ) {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(productId)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build()
                )
            )
            .build()

        scope.launch {
            withContext(Dispatchers.IO) {
                billingClient.queryProductDetailsAsync(params) { billingResult, productDetailsList ->
                    if (billingResult.responseCode == BillingClient.BillingResponseCode.OK &&
                        productDetailsList.isNotEmpty()
                    ) {
                        Log.i(TAG, "Product details fetched for $productId")
                        onResult(productDetailsList[0])
                    } else {
                        Log.w(TAG, "queryProductDetails failed: ${billingResult.responseCode}")
                        onResult(null)
                    }
                }
            }
        }
    }

    /**
     * 发起购买流程。先查询商品详情，再启动 BillingFlow。
     *
     * @param activity 发起购买的 Activity
     * @param productId 商品 ID，默认为 Pro 解锁商品
     */
    fun launchBillingFlow(activity: Activity, productId: String = PRO_PRODUCT_ID) {
        if (!isConnectionEstablished) {
            Log.w(TAG, "BillingClient not connected, attempting to connect before purchase")
            // 尝试重连后再次发起购买
            startConnection()
            return
        }
        queryProductDetails(productId) { productDetails ->
            if (productDetails == null) {
                Log.e(TAG, "Cannot launch billing flow: product details unavailable")
                return@queryProductDetails
            }
            val offerToken = productDetails.subscriptionOfferDetails?.firstOrNull()?.offerToken
            val productDetailsParamsBuilder = BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(productDetails)
            // INAPP 商品没有订阅 offer，offerToken 为 null 时不设置
            if (offerToken != null) {
                productDetailsParamsBuilder.setOfferToken(offerToken)
            }
            val flowParams = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(listOf(productDetailsParamsBuilder.build()))
                .build()

            val result = billingClient.launchBillingFlow(activity, flowParams)
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                Log.i(TAG, "Billing flow launched for $productId")
            } else {
                Log.e(TAG, "launchBillingFlow failed: ${result.responseCode}")
            }
        }
    }

    /**
     * 恢复购买：查询已有的 INAPP 购买记录，命中 Pro 商品则更新解锁状态。
     * 调用 [queryPurchasesAsync] 查询本地缓存的购买记录。
     *
     * @param onRestore 完成回调，参数表示是否成功恢复到 Pro 状态
     */
    fun restorePurchases(onRestore: ((Boolean) -> Unit)? = null) {
        if (!isConnectionEstablished) {
            Log.w(TAG, "restorePurchases: BillingClient not connected")
            onRestore?.invoke(false)
            return
        }
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()

        scope.launch {
            withContext(Dispatchers.IO) {
                billingClient.queryPurchasesAsync(params) { billingResult, purchases ->
                    if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
                        Log.w(TAG, "queryPurchasesAsync failed: ${billingResult.responseCode}")
                        onRestore?.invoke(false)
                        return@queryPurchasesAsync
                    }
                    val restored = handlePurchases(purchases, fromRestore = true)
                    Log.i(TAG, "Restore purchases done, pro restored=$restored")
                    onRestore?.invoke(restored)
                }
            }
        }
    }

    /**
     * 确认购买。一次性购买必须在授予权益后调用 acknowledge，否则 3 天内会自动退款。
     */
    fun acknowledgePurchase(purchase: Purchase) {
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) {
            Log.w(TAG, "acknowledgePurchase skipped: state=${purchase.purchaseState}")
            return
        }
        if (purchase.isAcknowledged) {
            Log.i(TAG, "Purchase already acknowledged")
            return
        }
        val ackParams = com.android.billingclient.api.AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()

        scope.launch {
            withContext(Dispatchers.IO) {
                billingClient.acknowledgePurchase(ackParams) { billingResult ->
                    if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                        Log.i(TAG, "Purchase acknowledged")
                    } else {
                        Log.e(TAG, "acknowledgePurchase failed: ${billingResult.responseCode}")
                    }
                }
            }
        }
    }

    /**
     * PurchasesUpdatedListener 回调：购买流程结束时由 BillingClient 触发。
     */
    override fun onPurchasesUpdated(
        billingResult: BillingResult,
        purchases: MutableList<Purchase>?
    ) {
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                Log.i(TAG, "onPurchasesUpdated OK, purchases=${purchases?.size ?: 0}")
                if (purchases != null) {
                    handlePurchases(purchases, fromRestore = false)
                }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                Log.i(TAG, "Purchase canceled by user")
            }
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                Log.i(TAG, "Item already owned, triggering restore")
                restorePurchases()
            }
            else -> {
                Log.e(TAG, "onPurchasesUpdated error: ${billingResult.responseCode} / ${billingResult.debugMessage}")
            }
        }
    }

    /**
     * 处理购买列表：过滤 Pro 商品、处理 PURCHASED 状态、确认购买并持久化状态。
     *
     * @return 是否命中 Pro 商品且已购买
     */
    private fun handlePurchases(purchases: List<Purchase>, fromRestore: Boolean): Boolean {
        var proRestored = false
        for (purchase in purchases) {
            // 仅处理 Pro 商品
            if (PRO_PRODUCT_ID !in purchase.products) {
                continue
            }
            when (purchase.purchaseState) {
                Purchase.PurchaseState.PURCHASED -> {
                    Log.i(TAG, "Pro purchase PURCHASED (restore=$fromRestore)")
                    proRestored = true
                    // 授予权益并确认购买（防止 3 天自动退款）
                    updateProState(true)
                    acknowledgePurchase(purchase)
                }
                Purchase.PurchaseState.PENDING -> {
                    Log.i(TAG, "Pro purchase PENDING, waiting for completion")
                }
                Purchase.PurchaseState.UNSPECIFIED_STATE -> {
                    Log.w(TAG, "Pro purchase UNSPECIFIED_STATE")
                }
            }
        }
        return proRestored
    }

    /**
     * 更新 Pro 状态到内存与 DataStore。
     */
    private fun updateProState(unlocked: Boolean) {
        _isProUnlocked.value = unlocked
        scope.launch {
            try {
                PoseAIApp.getStoreManager().setProUnlocked(unlocked)
                Log.i(TAG, "Pro state persisted: $unlocked")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to persist pro state", e)
            }
        }
    }

    /**
     * 释放 BillingClient 连接（如在 Application.onTerminate 调用）。
     */
    fun endConnection() {
        try {
            if (billingClient.isReady) {
                billingClient.endConnection()
            }
        } catch (e: Exception) {
            Log.w(TAG, "endConnection failed", e)
        }
    }
}

/**
 * 高级功能访问拦截辅助函数。
 *
 * 未解锁 Pro 时调用 [onLocked] 回调（通常用于弹出付费墙）并返回 true（表示已被拦截）；
 * 已解锁则返回 false，调用方可继续执行高级功能。
 *
 * 用法：
 * ```
 * if (requiresProUnlock(isPro, onLocked = { showPaywall = true })) return
 * // 高级功能逻辑
 * ```
 *
 * @param isPro 当前 Pro 解锁状态
 * @param onLocked 未解锁时的回调
 * @return true 表示已被拦截（未解锁），false 表示已解锁可继续
 */
fun requiresProUnlock(isPro: Boolean, onLocked: () -> Unit): Boolean {
    if (!isPro) {
        onLocked()
        return true
    }
    return false
}
