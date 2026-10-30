package com.example.ui.screens

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AllInclusive
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import com.example.data.billing.BillingRepository
import com.example.data.billing.PremiumPlanType
import com.example.data.billing.SubscriptionUiState
import com.example.ui.auth.launchInteractiveGoogleSignIn
import com.example.ui.components.PremiumCrownIcon

private val DarkNavyBg = Color(0xFF07131E)
private val DarkNavyCard = Color(0xFF0E2133)
private val ForestGreenSurface = Color(0xFF134631)
private val ElectricGreen = Color(0xFF2EE585)
private val ElectricGreenBright = Color(0xFF00E676)
private val CrownGold = Color(0xFFFFCA45)
private val SoftIceText = Color(0xFFEAF6F1)
private val MutedSlateText = Color(0xFF9EB9AD)

private data class PremiumFeatureHighlight(
    val icon: ImageVector,
    val title: String,
    val subtitle: String
)

private val PREMIUM_FEATURES = listOf(
    PremiumFeatureHighlight(
        icon = Icons.Filled.AllInclusive,
        title = "Unlimited Catches, Journals & Locations",
        subtitle = "Go beyond the Free plan limits (30 catches, 10 journals, 3 spots) with unlimited logs."
    ),
    PremiumFeatureHighlight(
        icon = Icons.Filled.BarChart,
        title = "Monthly & Yearly Performance Charts",
        subtitle = "Track multi-year seasonal trends, 12-month catch curves, and trophy weight progression."
    ),
    PremiumFeatureHighlight(
        icon = Icons.AutoMirrored.Filled.CompareArrows,
        title = "Species Comparison & Catch Analytics",
        subtitle = "Compare species head-to-head by max weight, average size, lure efficiency, and water clarity."
    ),
    PremiumFeatureHighlight(
        icon = Icons.Filled.Layers,
        title = "Advanced Fishing Maps",
        subtitle = "Unlock Bathymetric Depth Contours, Catch Heatmaps, and Solunar Structure overlays."
    ),
    PremiumFeatureHighlight(
        icon = Icons.Filled.FileDownload,
        title = "Export Records to PDF & CSV",
        subtitle = "Generate official printable PDF angler reports and full CSV spreadsheets anytime."
    ),
    PremiumFeatureHighlight(
        icon = Icons.Filled.CloudSync,
        title = "Cloud Backup, Sync & Photo Storage",
        subtitle = "Automatic encrypted cloud backup across Android devices with unlimited catch photo entries."
    ),
    PremiumFeatureHighlight(
        icon = Icons.Filled.Palette,
        title = "Custom Themes, Pro Badges & Ad-Free",
        subtitle = "Unlock Electric Emerald, Deep Ocean, and Golden River themes, exclusive badges, and zero ads."
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PremiumUpgradeScreen(
    subscriptionState: SubscriptionUiState,
    isGuestMode: Boolean,
    pendingGuestRecordsCount: Int = 0,
    onBack: () -> Unit,
    onPurchasePlan: (Activity, PremiumPlanType) -> Unit,
    onRestorePurchases: () -> Unit,
    onManageSubscriptions: () -> Unit,
    onRegisterGuestWithEmail: () -> Unit,
    onGuestGoogleAuthCompleted: () -> Unit
) {
    BackHandler { onBack() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    var selectedPlan by remember { mutableStateOf(PremiumPlanType.YEARLY) }
    var guestAuthLoading by remember { mutableStateOf(false) }
    var guestAuthError by remember { mutableStateOf<String?>(null) }
    var showTermsDialog by remember { mutableStateOf(false) }
    var showPrivacyDialog by remember { mutableStateOf(false) }

    val pulseTransition = rememberInfiniteTransition(label = "crownPulse")
    val crownScale by pulseTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "crownScale"
    )

    if (showTermsDialog) {
        LegalInfoDialog(
            title = "FishLog Terms of Service",
            paragraphs = listOf(
                "1. Membership & Billing: FishLog Premium Monthly and Yearly plans are billed through your Google Play account and automatically renew unless canceled at least 24 hours before the end of the current billing period.",
                "2. Lifetime Option: FishLog Lifetime Pro is a one-time non-consumable Google Play purchase granting permanent access to FishLog Premium features tied to your verified Google Play account.",
                "3. Cancellations & Refunds: You can manage or cancel auto-renewing subscriptions anytime in Google Play Store > Payments & Subscriptions. When canceled, Premium access remains active until the end of the paid period.",
                "4. Account & Fair Use: Unlimited photo storage is subject to fair-use cloud document size limits and your account's storage policies."
            ),
            onDismiss = { showTermsDialog = false }
        )
    }

    if (showPrivacyDialog) {
        LegalInfoDialog(
            title = "FishLog Privacy Policy",
            paragraphs = listOf(
                "1. Private Fishing Data: Your catch records, journal entries, and GPS coordinates are strictly private to your account unless you explicitly mark a fishing spot as Shared.",
                "2. Local Guest Storage: In Guest Mode, records remain stored locally on your device until you choose to sign in or create an account to back them up.",
                "3. Payment Security: All subscription and one-time payments are processed directly by Google Play Billing. FishLog never receives or stores your credit card or payment details.",
                "4. Data Portability: Premium members can export their complete fishing history to CSV or PDF at any time."
            ),
            onDismiss = { showPrivacyDialog = false }
        )
    }

    Scaffold(
        containerColor = DarkNavyBg,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PremiumCrownIcon(modifier = Modifier.size(20.dp), tint = CrownGold)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "FishLog Premium",
                            color = SoftIceText,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("premium_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = SoftIceText
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkNavyBg
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(DarkNavyBg)
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier.widthIn(max = 640.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. Hero Upgrade Header Card
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = 1.dp,
                            brush = Brush.linearGradient(
                                colors = listOf(ElectricGreen.copy(alpha = 0.65f), ForestGreenSurface)
                            ),
                            shape = RoundedCornerShape(24.dp)
                        )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        Color(0xFF0B2336),
                                        ForestGreenSurface,
                                        Color(0xFF0A1D2C)
                                    )
                                )
                            )
                            .padding(24.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .scale(crownScale)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.radialGradient(
                                            colors = listOf(
                                                CrownGold.copy(alpha = 0.28f),
                                                ElectricGreen.copy(alpha = 0.10f)
                                            )
                                        )
                                    )
                                    .border(1.5.dp, CrownGold.copy(alpha = 0.6f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                PremiumCrownIcon(
                                    modifier = Modifier.size(38.dp),
                                    tint = CrownGold
                                )
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = ElectricGreen.copy(alpha = 0.16f)
                            ) {
                                Text(
                                    text = if (subscriptionState.isPremiumActive) {
                                        "VERIFIED PREMIUM MEMBER"
                                    } else {
                                        "FISHLOG PRO MEMBERSHIP"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = ElectricGreen,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = "Unlock Your Full Fishing Experience.",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.testTag("premium_headline_text")
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = "Take your fishing journey to the next level with FishLog Premium.",
                                style = MaterialTheme.typography.bodyLarge,
                                color = SoftIceText.copy(alpha = 0.88f),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                // 2. Current Entitlement / Renewal / Cancellation Banner
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (subscriptionState.isPremiumActive) {
                            ForestGreenSurface.copy(alpha = 0.7f)
                        } else {
                            DarkNavyCard
                        }
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (subscriptionState.isPremiumActive) {
                                Icons.Filled.Verified
                            } else {
                                Icons.Filled.WorkspacePremium
                            },
                            contentDescription = null,
                            tint = if (subscriptionState.isPremiumActive) ElectricGreen else CrownGold,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (subscriptionState.isPremiumActive) {
                                    "Membership Active"
                                } else {
                                    "Current Plan: Free Tier"
                                },
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = SoftIceText
                            )
                            Text(
                                text = subscriptionState.entitlementStatusLabel,
                                style = MaterialTheme.typography.bodySmall,
                                color = MutedSlateText
                            )
                        }
                    }
                }

                // 3. Guest Account Requirement Card (When user is in Guest Mode)
                if (isGuestMode && !subscriptionState.isPremiumActive) {
                    GuestAccountRequiredForPremiumCard(
                        pendingGuestRecordsCount = pendingGuestRecordsCount,
                        isLoading = guestAuthLoading,
                        errorMessage = guestAuthError,
                        onContinueWithGoogle = {
                            guestAuthError = null
                            guestAuthLoading = true
                            launchInteractiveGoogleSignIn(
                                context = context,
                                credentialManager = credentialManager,
                                onAuthSuccess = {
                                    guestAuthLoading = false
                                    onGuestGoogleAuthCompleted()
                                },
                                onAuthError = { err ->
                                    guestAuthLoading = false
                                    guestAuthError = err
                                },
                                scope = scope,
                                onAuthCancelled = {
                                    guestAuthLoading = false
                                }
                            )
                        },
                        onRegisterWithEmail = onRegisterGuestWithEmail
                    )
                }

                // 4. Pricing Plan Cards (Yearly Best Value, Monthly, Lifetime)
                Text(
                    text = "Choose Your Membership Plan",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = SoftIceText
                )

                PricingOptionCard(
                    planType = PremiumPlanType.YEARLY,
                    title = "Yearly Plan",
                    priceLabel = subscriptionState.displayYearlyPrice,
                    supportingLabel = "Equivalent to ₱33.25/month • Save 32% vs monthly",
                    badgeText = "BEST VALUE",
                    selected = selectedPlan == PremiumPlanType.YEARLY,
                    onSelect = { selectedPlan = PremiumPlanType.YEARLY },
                    testTag = "plan_card_yearly"
                )

                PricingOptionCard(
                    planType = PremiumPlanType.MONTHLY,
                    title = "Monthly Plan",
                    priceLabel = subscriptionState.displayMonthlyPrice,
                    supportingLabel = "Flexible month-to-month billing • Cancel anytime",
                    badgeText = null,
                    selected = selectedPlan == PremiumPlanType.MONTHLY,
                    onSelect = { selectedPlan = PremiumPlanType.MONTHLY },
                    testTag = "plan_card_monthly"
                )

                PricingOptionCard(
                    planType = PremiumPlanType.LIFETIME,
                    title = "Lifetime Access",
                    priceLabel = subscriptionState.displayLifetimePrice,
                    supportingLabel = "One-time purchase • Unlock FishLog Premium forever",
                    badgeText = "ONE-TIME",
                    selected = selectedPlan == PremiumPlanType.LIFETIME,
                    onSelect = { selectedPlan = PremiumPlanType.LIFETIME },
                    testTag = "plan_card_lifetime"
                )

                if (!subscriptionState.isUsingLivePlayStorePrices) {
                    Text(
                        text = "Prices shown are default Google Play reference tiers (₱49/mo, ₱399/yr, ₱799 lifetime) and automatically display your localized Google Play Store pricing once products are published.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MutedSlateText
                    )
                }

                // 5. Status / Verification Message from Google Play Billing
                if (!subscriptionState.statusMessage.isNullOrBlank()) {
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF132C42)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = subscriptionState.statusMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = SoftIceText,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }

                // 6. Primary Subscribe / Purchase CTA
                if (!subscriptionState.isPremiumActive) {
                    Button(
                        onClick = {
                            if (isGuestMode) {
                                onRegisterGuestWithEmail()
                            } else {
                                val activity = context as? Activity
                                if (activity != null) {
                                    onPurchasePlan(activity, selectedPlan)
                                }
                            }
                        },
                        enabled = !subscriptionState.isProcessing && !guestAuthLoading,
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ElectricGreenBright,
                            contentColor = Color(0xFF042112)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .testTag("subscribe_play_billing_button")
                    ) {
                        if (subscriptionState.isProcessing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = Color(0xFF042112),
                                strokeWidth = 2.5.dp
                            )
                        } else {
                            PremiumCrownIcon(
                                modifier = Modifier.size(20.dp),
                                tint = Color(0xFF042112)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            val ctaLabel = when {
                                isGuestMode -> "Create Account to Subscribe"
                                selectedPlan == PremiumPlanType.LIFETIME ->
                                    "Unlock Lifetime (${subscriptionState.displayLifetimePrice})"
                                selectedPlan == PremiumPlanType.YEARLY ->
                                    "Subscribe Yearly (${subscriptionState.displayYearlyPrice})"
                                else ->
                                    "Subscribe Monthly (${subscriptionState.displayMonthlyPrice})"
                            }
                            Text(
                                text = ctaLabel,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // 7. Restore Purchases & Play Store Subscription Management
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onRestorePurchases,
                        enabled = !subscriptionState.isProcessing,
                        border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(
                            brush = SolidColor(ElectricGreen.copy(alpha = 0.5f))
                        ),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = SoftIceText),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("restore_purchases_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = ElectricGreen
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Restore Purchases")
                    }

                    OutlinedButton(
                        onClick = onManageSubscriptions,
                        border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(
                            brush = SolidColor(MutedSlateText.copy(alpha = 0.45f))
                        ),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = SoftIceText),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("manage_subscription_button")
                    ) {
                        Text("Manage in Play")
                    }
                }

                // 8. Feature Highlights List
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkNavyCard),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Everything Unlocked with FishLog Premium",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = SoftIceText
                        )

                        PREMIUM_FEATURES.forEach { feature ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.Top
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = ForestGreenSurface,
                                    modifier = Modifier.size(42.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = feature.icon,
                                            contentDescription = null,
                                            tint = ElectricGreen,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = feature.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = SoftIceText
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = feature.subtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MutedSlateText
                                    )
                                }
                            }
                        }
                    }
                }

                // 9. Free vs Premium Comparison Matrix
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkNavyCard),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "Plan Comparison",
                                style = MaterialTheme.typography.labelLarge,
                                color = SoftIceText,
                                modifier = Modifier.weight(1.4f)
                            )
                            Text(
                                text = "Free",
                                style = MaterialTheme.typography.labelLarge,
                                color = MutedSlateText,
                                modifier = Modifier.weight(0.8f)
                            )
                            Text(
                                text = "Premium",
                                style = MaterialTheme.typography.labelLarge,
                                color = ElectricGreen,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(0.9f)
                            )
                        }
                        HorizontalDivider(color = MutedSlateText.copy(alpha = 0.2f))
                        PlanComparisonRow("Catch Records", "Up to ${BillingRepository.FREE_MAX_CATCHES}", "Unlimited")
                        PlanComparisonRow("Fishing Journals", "Up to ${BillingRepository.FREE_MAX_SESSIONS}", "Unlimited")
                        PlanComparisonRow("Saved Fishing Spots", "Up to ${BillingRepository.FREE_MAX_SPOTS}", "Unlimited")
                        PlanComparisonRow("Catch Photo Uploads", "Included", "Unlimited*")
                        PlanComparisonRow("Monthly & Yearly Charts", "6-Month", "Full Multi-Year")
                        PlanComparisonRow("Species Comparison", "—", "Included")
                        PlanComparisonRow("PDF & CSV Export", "—", "Included")
                        PlanComparisonRow("Cloud Sync & Backup", "Local / Basic", "Full Multi-Device")
                        PlanComparisonRow("Custom Pro Themes", "—", "All Unlocked")
                        PlanComparisonRow("Exclusive Badges", "Standard", "Pro Crown Badges")
                        PlanComparisonRow("Advertisements", "None", "100% Ad-Free")
                    }
                }

                // 10. Legal Links: Terms of Service & Privacy Policy
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Purchases are verified directly with Google Play Billing. Premium access is granted only after verified payment confirmation.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MutedSlateText,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = { showTermsDialog = true },
                            modifier = Modifier.testTag("terms_of_service_button")
                        ) {
                            Text("Terms of Service", color = ElectricGreen)
                        }
                        Text("•", color = MutedSlateText)
                        TextButton(
                            onClick = { showPrivacyDialog = true },
                            modifier = Modifier.testTag("privacy_policy_button")
                        ) {
                            Text("Privacy Policy", color = ElectricGreen)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun GuestAccountRequiredForPremiumCard(
    pendingGuestRecordsCount: Int,
    isLoading: Boolean,
    errorMessage: String?,
    onContinueWithGoogle: () -> Unit,
    onRegisterWithEmail: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF102A3E)),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, CrownGold.copy(alpha = 0.55f), RoundedCornerShape(18.dp))
            .testTag("guest_premium_account_prompt")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Person,
                    contentDescription = null,
                    tint = CrownGold,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Account Required for FishLog Premium",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            val preservationNote = if (pendingGuestRecordsCount > 0) {
                "You currently have $pendingGuestRecordsCount local guest record(s). Signing in with Google or registering with Email will safely preserve and migrate all your catches, photos, journals, and spots, then return right here so you can complete your purchase."
            } else {
                "Sign in with Google or register with Email first so your FishLog Premium membership and cloud backups stay protected across devices. After signing in, you'll return right here to continue your purchase."
            }

            Text(
                text = preservationNote,
                style = MaterialTheme.typography.bodySmall,
                color = SoftIceText.copy(alpha = 0.9f)
            )

            if (!errorMessage.isNullOrBlank()) {
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFFF8A80)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onContinueWithGoogle,
                    enabled = !isLoading,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ElectricGreen,
                        contentColor = Color(0xFF052414)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("guest_premium_google_button")
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = Color(0xFF052414),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = "Google Sign-In",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                OutlinedButton(
                    onClick = onRegisterWithEmail,
                    enabled = !isLoading,
                    border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(
                        brush = SolidColor(ElectricGreen)
                    ),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = SoftIceText),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("guest_premium_email_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Email,
                        contentDescription = null,
                        tint = ElectricGreen,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Email Sign-Up",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun PricingOptionCard(
    planType: PremiumPlanType,
    title: String,
    priceLabel: String,
    supportingLabel: String,
    badgeText: String?,
    selected: Boolean,
    onSelect: () -> Unit,
    testTag: String
) {
    val borderColor by animateColorAsState(
        targetValue = if (selected) ElectricGreenBright else Color(0xFF1D384F),
        animationSpec = tween(220),
        label = "planBorderColor"
    )
    val containerColor by animateColorAsState(
        targetValue = if (selected) Color(0xFF11352B) else DarkNavyCard,
        animationSpec = tween(220),
        label = "planBgColor"
    )

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(18.dp)
            )
            .clickable { onSelect() }
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = selected,
                onClick = onSelect,
                colors = RadioButtonDefaults.colors(
                    selectedColor = ElectricGreenBright,
                    unselectedColor = MutedSlateText
                )
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    if (badgeText != null) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = if (planType == PremiumPlanType.YEARLY) {
                                ElectricGreenBright
                            } else {
                                CrownGold.copy(alpha = 0.22f)
                            }
                        ) {
                            Text(
                                text = badgeText,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (planType == PremiumPlanType.YEARLY) {
                                    Color(0xFF042112)
                                } else {
                                    CrownGold
                                },
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = supportingLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MutedSlateText
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = priceLabel,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (selected) ElectricGreenBright else SoftIceText
            )
        }
    }
}

@Composable
private fun PlanComparisonRow(
    feature: String,
    freeValue: String,
    premiumValue: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = feature,
            style = MaterialTheme.typography.bodySmall,
            color = SoftIceText,
            modifier = Modifier.weight(1.4f)
        )
        Text(
            text = freeValue,
            style = MaterialTheme.typography.bodySmall,
            color = MutedSlateText,
            modifier = Modifier.weight(0.8f)
        )
        Row(
            modifier = Modifier.weight(0.9f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = ElectricGreen,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = premiumValue,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = ElectricGreen
            )
        }
    }
}

@Composable
fun LegalInfoDialog(
    title: String,
    paragraphs: List<String>,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                paragraphs.forEach { p ->
                    Text(
                        text = p,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
