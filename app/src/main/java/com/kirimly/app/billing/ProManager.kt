package com.kirimly.app.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import com.kirimly.app.R
import com.kirimly.app.data.prefs.UserPreferencesRepository
import com.kirimly.app.util.tr
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Kirimly Pro: one Google Play in-app product, bought once and owned forever.
 *
 * The purchase is checked against Google Play on every start and the result is cached in
 * DataStore, so Pro keeps working offline (Kirimly is often used on a hotspot with no internet).
 */
class ProManager(
    context: Context,
    private val scope: CoroutineScope,
    private val preferences: UserPreferencesRepository
) : PurchasesUpdatedListener {

    enum class Status { CONNECTING, READY, UNAVAILABLE, PURCHASING, PENDING }

    private val client: BillingClient = BillingClient.newBuilder(context.applicationContext)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .build()

    private var details: ProductDetails? = null
    private var connecting = false

    private val _status = MutableStateFlow(Status.CONNECTING)
    val status: StateFlow<Status> = _status.asStateFlow()

    /** Localised price from Google Play, e.g. "Rp 39.000". Null until loaded or when unavailable. */
    private val _price = MutableStateFlow<String?>(null)
    val price: StateFlow<String?> = _price.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    fun connect() {
        if (client.isReady) {
            scope.launch { refresh() }
            return
        }
        if (connecting) return
        connecting = true
        _status.value = Status.CONNECTING
        try {
            client.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    connecting = false
                    if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                        scope.launch { refresh() }
                    } else {
                        _status.value = Status.UNAVAILABLE
                    }
                }

                override fun onBillingServiceDisconnected() {
                    connecting = false
                }
            })
        } catch (_: Exception) {
            connecting = false
            _status.value = Status.UNAVAILABLE
        }
    }

    /** Loads the price and re-checks what this Google account owns. */
    private suspend fun refresh() {
        try {
            val params = QueryProductDetailsParams.newBuilder()
                .setProductList(
                    listOf(
                        QueryProductDetailsParams.Product.newBuilder()
                            .setProductId(PRODUCT_ID)
                            .setProductType(BillingClient.ProductType.INAPP)
                            .build()
                    )
                )
                .build()
            val result = client.queryProductDetails(params)
            details = result.productDetailsList?.firstOrNull()
            _price.value = details?.oneTimePurchaseOfferDetails?.formattedPrice
            _status.value = if (details != null) Status.READY else Status.UNAVAILABLE
            restore(silent = true)
        } catch (_: Exception) {
            _status.value = Status.UNAVAILABLE
        }
    }

    /** "Restore purchase": asks Google Play again. Also revokes Pro after a refund. */
    suspend fun restore(silent: Boolean = false) {
        if (!client.isReady) {
            connect()
            if (!silent) _messages.tryEmit(tr(R.string.p_msg_store_unavailable))
            return
        }
        val result = client.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()
        )
        if (result.billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
            if (!silent) _messages.tryEmit(tr(R.string.p_msg_store_unavailable))
            return
        }
        val owned = handlePurchases(result.purchasesList)
        // Only an authoritative answer from Play may switch Pro off (refund / chargeback).
        preferences.setProOwned(owned)
        if (!silent) _messages.tryEmit(tr(if (owned) R.string.p_msg_restored else R.string.p_msg_nothing_to_restore))
    }

    fun purchase(activity: Activity) {
        val product = details
        if (!client.isReady || product == null) {
            connect()
            _messages.tryEmit(tr(R.string.p_msg_store_unavailable))
            return
        }
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(product).build())
            )
            .build()
        _status.value = Status.PURCHASING
        val result = client.launchBillingFlow(activity, params)
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            _status.value = Status.READY
            if (result.responseCode == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED) {
                scope.launch { restore() }
            } else {
                _messages.tryEmit(tr(R.string.p_msg_purchase_failed))
            }
        }
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> scope.launch {
                val owned = handlePurchases(purchases.orEmpty())
                if (owned) {
                    preferences.setProOwned(true)
                    _messages.tryEmit(tr(R.string.p_msg_thanks))
                }
                _status.value = if (_status.value == Status.PENDING) Status.PENDING else Status.READY
            }
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> scope.launch {
                _status.value = Status.READY
                restore()
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> _status.value = Status.READY
            else -> {
                _status.value = Status.READY
                _messages.tryEmit(tr(R.string.p_msg_purchase_failed))
            }
        }
    }

    /** Acknowledges new purchases (Play refunds unacknowledged ones after 3 days). Returns whether Pro is owned. */
    private suspend fun handlePurchases(purchases: List<Purchase>): Boolean {
        var owned = false
        var pending = false
        for (purchase in purchases) {
            if (PRODUCT_ID !in purchase.products) continue
            when (purchase.purchaseState) {
                Purchase.PurchaseState.PURCHASED -> {
                    owned = true
                    if (!purchase.isAcknowledged) {
                        try {
                            client.acknowledgePurchase(
                                AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()
                            )
                        } catch (_: Exception) {
                            // Retried on the next start via restore().
                        }
                    }
                }
                Purchase.PurchaseState.PENDING -> pending = true
            }
        }
        if (pending && !owned) {
            _status.value = Status.PENDING
            _messages.tryEmit(tr(R.string.p_msg_pending))
        }
        return owned
    }

    fun setDebugPro(enabled: Boolean) {
        if (!com.kirimly.app.BuildConfig.DEBUG) return
        scope.launch { preferences.setDebugPro(enabled) }
    }

    companion object {
        /** Create this one-time product in Play Console → Monetize → In-app products. */
        const val PRODUCT_ID = "kirimly_pro"
    }
}
