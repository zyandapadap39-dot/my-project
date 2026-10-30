package com.example.data.billing

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import android.util.Log
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
import com.example.BuildConfig
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class PremiumPlanType(
    val title: String,
    val periodSubtitle: String,
    val configuredReferencePrice: String,
    val isSubscription: Boolean
) {
    YEARLY(
        title = "Yearly Pro",
        periodSubtitle = "₱399/year • Save 32% (Best Value)",
        configuredReferencePrice = "₱399/year",
        isSubscription = true
    ),
    MONTHLY(
        title = "Monthly Pro",
        periodSubtitle = "₱49/month • Flexible monthly billing",
        configuredReferencePrice = "₱49/month",
        isSubscription = true
    ),
    LIFETIME(
        title = "Lifetime Pro",
        periodSubtitle = "₱799 one-time • Pay once, own forever",
        configuredReferencePrice = "₱799",
        isSubscription = false
    )
}

data class SubscriptionUiState(
    val isBillingConnected: Boolean = false,
    val isPremiumActive: Boolean = false,
    val activePlanType: PremiumPlanType? = null,
    val willAutoRenew: Boolean = false,
    val isPendingPurchase: Boolean = false,
    val activePurchaseTimeMillis: Long? = null,
    val monthlyProduct: ProductDetails? = null,
    val yearlyProduct: ProductDetails? = null,
    val lifetimeProduct: ProductDetails? = null,
    val formattedMonthlyPrice: String? = null,
    val formattedYearlyPrice: String? = null,
    val formattedLifetimePrice: String? = null,
    val statusMessage: String? = null,
    val isProcessing: Boolean = false
) {
    val displayMonthlyPrice: String
        get() = formattedMonthlyPrice ?: PremiumPlanType.MONTHLY.configuredReferencePrice

    val displayYearlyPrice: String
        get() = formattedYearlyPrice ?: PremiumPlanType.YEARLY.configuredReferencePrice

    val displayLifetimePrice: String
        get() = formattedLifetimePrice ?: PremiumPlanType.LIFETIME.configuredReferencePrice

    val isUsingLivePlayStorePrices: Boolean
        get() = formattedMonthlyPrice != null || formattedYearlyPrice != null || formattedLifetimePrice != null

    val entitlementStatusLabel: String
        get() = when {
            isPendingPurchase && !isPremiumActive ->
                "Pending Payment Confirmation in Google Play"
            !isPremiumActive ->
                "Free Membership (Up to $FREE_MAX_CATCHES catches • $FREE_MAX_SESSIONS journals • $FREE_MAX_SPOTS spots)"
            activePlanType == PremiumPlanType.LIFETIME ->
                "Lifetime Pro Active • Permanent Verified Access"
            willAutoRenew ->
                "${activePlanType?.title ?: "FishLog Premium"} Active • Auto-renews via Google Play"
            else ->
                "${activePlanType?.title ?: "FishLog Premium"} Canceled • Active until end of current billing period"
        }

    companion object {
        const val FREE_MAX_CATCHES = BillingRepository.FREE_MAX_CATCHES
        const val FREE_MAX_SESSIONS = BillingRepository.FREE_MAX_SESSIONS
        const val FREE_MAX_SPOTS = BillingRepository.FREE_MAX_SPOTS
    }
}

class BillingRepository(context: Context) {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    val monthlyProductId: String = BuildConfig.PLAY_BILLING_MONTHLY_PRODUCT_ID
        .takeIf { it.isNotBlank() } ?: DEFAULT_PRODUCT_MONTHLY

    val yearlyProductId: String = BuildConfig.PLAY_BILLING_YEARLY_PRODUCT_ID
        .takeIf { it.isNotBlank() } ?: DEFAULT_PRODUCT_YEARLY

    val lifetimeProductId: String = BuildConfig.PLAY_BILLING_LIFETIME_PRODUCT_ID
        .takeIf { it.isNotBlank() } ?: DEFAULT_PRODUCT_LIFETIME

    private val base64PublicKey: String? = BuildConfig.PLAY_BILLING_PUBLIC_KEY
        .takeIf { it.isNotBlank() && it != PLACEHOLDER_PUBLIC_KEY }

    private val _state = MutableStateFlow(SubscriptionUiState())
    val state: StateFlow<SubscriptionUiState> = _state.asStateFlow()

    @Volatile
    private var latestSubsPurchases: List<Purchase> = emptyList()

    @Volatile
    private var latestInAppPurchases: List<Purchase> = emptyList()

    private val purchasesUpdatedListener = PurchasesUpdatedListener { billingResult, purchases ->
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                if (!purchases.isNullOrEmpty()) {
                    processIncomingPurchases(purchases)
                } else {
                    _state.value = _state.value.copy(isProcessing = false)
                }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                _state.value = _state.value.copy(
                    isProcessing = false,
                    statusMessage = "Purchase canceled. No charge was made."
                )
            }
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                _state.value = _state.value.copy(
                    isProcessing = false,
                    statusMessage = "You already own this plan. Syncing verified entitlement..."
                )
                refreshProductsAndEntitlements(isManualRestore = true)
            }
            else -> {
                _state.value = _state.value.copy(
                    isProcessing = false,
                    statusMessage = billingResult.debugMessage.ifBlank {
                        "Google Play Billing unavailable (code ${billingResult.responseCode})."
                    }
                )
            }
        }
    }

    private val billingClient: BillingClient = BillingClient.newBuilder(appContext)
        .setListener(purchasesUpdatedListener)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .build()
        )
        .build()

    fun startConnection() {
        if (billingClient.isReady) {
            refreshProductsAndEntitlements(isManualRestore = false)
            return
        }
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    _state.value = _state.value.copy(
                        isBillingConnected = true
                    )
                    refreshProductsAndEntitlements(isManualRestore = false)
                } else {
                    Log.w(TAG, "Play Billing setup code=${billingResult.responseCode}: ${billingResult.debugMessage}")
                    _state.value = _state.value.copy(
                        isBillingConnected = false,
                        isProcessing = false
                    )
                }
            }

            override fun onBillingServiceDisconnected() {
                _state.value = _state.value.copy(isBillingConnected = false)
            }
        })
    }

    fun restorePurchases() {
        _state.value = _state.value.copy(
            isProcessing = true,
            statusMessage = "Checking Google Play account for active subscriptions & lifetime purchases..."
        )
        if (!billingClient.isReady) {
            billingClient.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(billingResult: BillingResult) {
                    if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                        _state.value = _state.value.copy(isBillingConnected = true)
                        refreshProductsAndEntitlements(isManualRestore = true)
                    } else {
                        _state.value = _state.value.copy(
                            isBillingConnected = false,
                            isProcessing = false,
                            statusMessage = "Google Play Store is unavailable on this device (code ${billingResult.responseCode}). Cannot verify purchases."
                        )
                    }
                }

                override fun onBillingServiceDisconnected() {
                    _state.value = _state.value.copy(isBillingConnected = false, isProcessing = false)
                }
            })
        } else {
            refreshProductsAndEntitlements(isManualRestore = true)
        }
    }

    /**
     * Queries both SUBS (Monthly & Yearly) and INAPP (Lifetime) products and active purchases.
     * Handles subscription expiration automatically: if a previously active subscription has
     * expired in Google Play, queryPurchasesAsync no longer returns it and isPremiumActive becomes false.
     */
    fun refreshProductsAndEntitlements(isManualRestore: Boolean = false) {
        if (!billingClient.isReady) {
            startConnection()
            return
        }

        queryAllProductDetails()

        val completedQueries = AtomicInteger(0)
        var subsOk = false
        var inAppOk = false

        fun evaluateCombinedEntitlements() {
            if (completedQueries.incrementAndGet() < 2) return
            val allPurchases = latestSubsPurchases + latestInAppPurchases
            applyVerifiedPurchases(
                purchases = allPurchases,
                querySucceeded = subsOk || inAppOk,
                isManualRestore = isManualRestore
            )
        }

        val subsPurchaseParams = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()

        billingClient.queryPurchasesAsync(subsPurchaseParams) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                subsOk = true
                latestSubsPurchases = purchases
            }
            evaluateCombinedEntitlements()
        }

        val inAppPurchaseParams = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()

        billingClient.queryPurchasesAsync(inAppPurchaseParams) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                inAppOk = true
                latestInAppPurchases = purchases
            }
            evaluateCombinedEntitlements()
        }
    }

    private fun queryAllProductDetails() {
        // 1. Subscription SKUs (Monthly & Yearly)
        val subsProducts = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(monthlyProductId)
                .setProductType(BillingClient.ProductType.SUBS)
                .build(),
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(yearlyProductId)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        )

        billingClient.queryProductDetailsAsync(
            QueryProductDetailsParams.newBuilder().setProductList(subsProducts).build()
        ) { result, detailsList ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                val monthly = detailsList.firstOrNull { it.productId == monthlyProductId }
                val yearly = detailsList.firstOrNull { it.productId == yearlyProductId }

                val monthlyPrice = monthly?.subscriptionOfferDetails
                    ?.firstOrNull()
                    ?.pricingPhases
                    ?.pricingPhaseList
                    ?.lastOrNull()
                    ?.formattedPrice

                val yearlyPrice = yearly?.subscriptionOfferDetails
                    ?.firstOrNull()
                    ?.pricingPhases
                    ?.pricingPhaseList
                    ?.lastOrNull()
                    ?.formattedPrice

                _state.value = _state.value.copy(
                    monthlyProduct = monthly,
                    yearlyProduct = yearly,
                    formattedMonthlyPrice = monthlyPrice?.let { "$it/month" },
                    formattedYearlyPrice = yearlyPrice?.let { "$it/year" }
                )
            }
        }

        // 2. One-Time Lifetime SKU (INAPP)
        val inAppProducts = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(lifetimeProductId)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        )

        billingClient.queryProductDetailsAsync(
            QueryProductDetailsParams.newBuilder().setProductList(inAppProducts).build()
        ) { result, detailsList ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                val lifetime = detailsList.firstOrNull { it.productId == lifetimeProductId }
                val lifetimePrice = lifetime?.oneTimePurchaseOfferDetails?.formattedPrice
                _state.value = _state.value.copy(
                    lifetimeProduct = lifetime,
                    formattedLifetimePrice = lifetimePrice
                )
            }
        }
    }

    /**
     * Launches real Google Play Billing flow for Monthly, Yearly, or Lifetime.
     * Never simulates a purchase or grants access without verified Google Play response.
     */
    fun launchPlanPurchase(
        activity: Activity,
        planType: PremiumPlanType,
        authenticatedUserId: String
    ) {
        if (authenticatedUserId.isBlank() || authenticatedUserId == "guest_local") {
            _state.value = _state.value.copy(
                statusMessage = "Please sign in or create an account before purchasing FishLog Premium."
            )
            return
        }

        if (!billingClient.isReady) {
            _state.value = _state.value.copy(
                statusMessage = "Connecting to Google Play Billing... Please ensure Google Play Store is signed in on this device."
            )
            startConnection()
            return
        }

        val targetSku = when (planType) {
            PremiumPlanType.MONTHLY -> monthlyProductId
            PremiumPlanType.YEARLY -> yearlyProductId
            PremiumPlanType.LIFETIME -> lifetimeProductId
        }

        val productDetails = when (planType) {
            PremiumPlanType.MONTHLY -> _state.value.monthlyProduct
            PremiumPlanType.YEARLY -> _state.value.yearlyProduct
            PremiumPlanType.LIFETIME -> _state.value.lifetimeProduct
        }

        if (productDetails == null) {
            _state.value = _state.value.copy(
                isProcessing = false,
                statusMessage = "Product \"$targetSku\" (${planType.configuredReferencePrice}) is not yet published in Google Play Console for this build. Publish the product in Google Play Console to complete live checkout."
            )
            return
        }

        val productDetailsParamsBuilder = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(productDetails)

        if (planType.isSubscription) {
            val offerToken = productDetails.subscriptionOfferDetails?.firstOrNull()?.offerToken
            if (offerToken.isNullOrBlank()) {
                _state.value = _state.value.copy(
                    isProcessing = false,
                    statusMessage = "No active base plan offer found for \"$targetSku\" in Google Play Console."
                )
                return
            }
            productDetailsParamsBuilder.setOfferToken(offerToken)
        }

        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productDetailsParamsBuilder.build()))
            .setObfuscatedAccountId(authenticatedUserId.take(64))
            .build()

        _state.value = _state.value.copy(isProcessing = true, statusMessage = null)
        val launchResult = billingClient.launchBillingFlow(activity, flowParams)
        if (launchResult.responseCode != BillingClient.BillingResponseCode.OK) {
            _state.value = _state.value.copy(
                isProcessing = false,
                statusMessage = "Unable to launch Google Play checkout (${launchResult.debugMessage.ifBlank { "code ${launchResult.responseCode}" }})."
            )
        }
    }

    fun openPlayStoreSubscriptionManagement(context: Context) {
        val activeSku = when (_state.value.activePlanType) {
            PremiumPlanType.MONTHLY -> monthlyProductId
            PremiumPlanType.YEARLY -> yearlyProductId
            else -> null
        }
        val url = if (activeSku != null) {
            "https://play.google.com/store/account/subscriptions?sku=$activeSku&package=${context.packageName}"
        } else {
            "https://play.google.com/store/account/subscriptions?package=${context.packageName}"
        }
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }

    fun clearStatusMessage() {
        _state.value = _state.value.copy(statusMessage = null)
    }

    private fun processIncomingPurchases(purchases: List<Purchase>) {
        // Re-verify and merge with latest known purchases
        var sawPending = false
        var sawInvalidSignature = false

        purchases.forEach { purchase ->
            when (purchase.purchaseState) {
                Purchase.PurchaseState.PURCHASED -> {
                    if (verifyPurchaseSignature(purchase)) {
                        if (!purchase.isAcknowledged) {
                            acknowledgePurchase(purchase)
                        }
                    } else {
                        sawInvalidSignature = true
                    }
                }
                Purchase.PurchaseState.PENDING -> {
                    sawPending = true
                }
                else -> Unit
            }
        }

        if (sawInvalidSignature) {
            _state.value = _state.value.copy(
                isProcessing = false,
                statusMessage = "Purchase signature verification failed. Premium access was not granted."
            )
            return
        }

        if (sawPending) {
            _state.value = _state.value.copy(
                isProcessing = false,
                isPendingPurchase = true,
                statusMessage = "Your payment is pending confirmation in Google Play. FishLog Premium will unlock automatically once payment completes."
            )
        }

        // Refresh full authoritative state from Google Play Billing
        refreshProductsAndEntitlements(isManualRestore = false)
    }

    private fun applyVerifiedPurchases(
        purchases: List<Purchase>,
        querySucceeded: Boolean,
        isManualRestore: Boolean
    ) {
        if (!querySucceeded) {
            _state.value = _state.value.copy(
                isProcessing = false,
                statusMessage = if (isManualRestore) {
                    "Could not query Google Play purchases right now. Please check your Play Store connection."
                } else {
                    _state.value.statusMessage
                }
            )
            return
        }

        val validPurchased = purchases.filter { purchase ->
            purchase.purchaseState == Purchase.PurchaseState.PURCHASED &&
                purchase.products.any { it in setOf(monthlyProductId, yearlyProductId, lifetimeProductId) } &&
                verifyPurchaseSignature(purchase)
        }

        val hasPending = purchases.any { purchase ->
            purchase.purchaseState == Purchase.PurchaseState.PENDING &&
                purchase.products.any { it in setOf(monthlyProductId, yearlyProductId, lifetimeProductId) }
        }

        validPurchased.forEach { purchase ->
            if (!purchase.isAcknowledged) {
                acknowledgePurchase(purchase)
            }
        }

        // Priority: Lifetime > Yearly > Monthly
        val chosenPurchase = validPurchased.firstOrNull { lifetimeProductId in it.products }
            ?: validPurchased.firstOrNull { yearlyProductId in it.products }
            ?: validPurchased.firstOrNull { monthlyProductId in it.products }

        val resolvedPlan = when {
            chosenPurchase == null -> null
            lifetimeProductId in chosenPurchase.products -> PremiumPlanType.LIFETIME
            yearlyProductId in chosenPurchase.products -> PremiumPlanType.YEARLY
            monthlyProductId in chosenPurchase.products -> PremiumPlanType.MONTHLY
            else -> null
        }

        val isActive = chosenPurchase != null && resolvedPlan != null
        val autoRenews = when (resolvedPlan) {
            PremiumPlanType.LIFETIME -> true
            PremiumPlanType.YEARLY, PremiumPlanType.MONTHLY -> chosenPurchase?.isAutoRenewing == true
            null -> false
        }

        val restoreMsg = if (isManualRestore) {
            if (isActive) {
                "Verified ${resolvedPlan?.title ?: "FishLog Premium"} purchase restored from Google Play!"
            } else if (hasPending) {
                "Found a pending purchase in Google Play awaiting payment completion."
            } else {
                "No active FishLog Premium subscriptions or lifetime purchases found on your Google Play account."
            }
        } else {
            _state.value.statusMessage
        }

        _state.value = _state.value.copy(
            isPremiumActive = isActive,
            activePlanType = resolvedPlan,
            willAutoRenew = autoRenews,
            isPendingPurchase = hasPending,
            activePurchaseTimeMillis = chosenPurchase?.purchaseTime,
            isProcessing = false,
            statusMessage = restoreMsg
        )
    }

    private fun verifyPurchaseSignature(purchase: Purchase): Boolean {
        val key = base64PublicKey ?: return true // Relies on Play Store IPC verification unless RSA key is configured in Secrets
        if (purchase.originalJson.isBlank() || purchase.signature.isBlank()) return false
        return try {
            val decodedKey = Base64.decode(key, Base64.DEFAULT)
            val keyFactory = KeyFactory.getInstance("RSA")
            val publicKey = keyFactory.generatePublic(X509EncodedKeySpec(decodedKey))
            val sig = Signature.getInstance("SHA1withRSA")
            sig.initVerify(publicKey)
            sig.update(purchase.originalJson.toByteArray(Charsets.UTF_8))
            sig.verify(Base64.decode(purchase.signature, Base64.DEFAULT))
        } catch (e: Exception) {
            Log.e(TAG, "RSA purchase signature verification error", e)
            false
        }
    }

    private fun acknowledgePurchase(purchase: Purchase) {
        scope.launch {
            val params = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()
            billingClient.acknowledgePurchase(params) { result ->
                if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                    Log.w(TAG, "Failed to acknowledge purchase: ${result.debugMessage}")
                }
            }
        }
    }

    companion object {
        private const val TAG = "FishLogBilling"
        const val DEFAULT_PRODUCT_MONTHLY = "fishlog_premium_monthly"
        const val DEFAULT_PRODUCT_YEARLY = "fishlog_premium_yearly"
        const val DEFAULT_PRODUCT_LIFETIME = "fishlog_premium_lifetime"
        private const val PLACEHOLDER_PUBLIC_KEY = "OPTIONAL_PLAY_CONSOLE_BASE64_RSA_KEY"

        // Free tier membership limits
        const val FREE_MAX_CATCHES = 30
        const val FREE_MAX_SESSIONS = 10
        const val FREE_MAX_SPOTS = 3
    }
}
