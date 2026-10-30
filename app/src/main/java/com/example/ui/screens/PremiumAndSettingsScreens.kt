package com.example.ui.screens

import android.Manifest
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.billing.SubscriptionUiState
import com.example.data.model.UserProfile
import com.example.data.preferences.CustomThemePalette
import com.example.data.preferences.LengthUnit
import com.example.data.preferences.ThemeMode
import com.example.data.preferences.UserPreferences
import com.example.data.preferences.WeightUnit
import com.example.ui.components.PremiumCrownIcon
import com.example.ui.components.PremiumFeatureBadge
import com.example.util.FishLogNotificationManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    userEmail: String,
    profile: UserProfile?,
    preferences: UserPreferences,
    subscriptionState: SubscriptionUiState,
    isGuestMode: Boolean = false,
    isSaving: Boolean,
    onBack: () -> Unit,
    onUpdateProfile: (String, String, String, Boolean) -> Unit,
    onSetThemeMode: (ThemeMode) -> Unit,
    onSetCustomPalette: (CustomThemePalette) -> Unit,
    onSetWeightUnit: (WeightUnit) -> Unit,
    onSetLengthUnit: (LengthUnit) -> Unit,
    onToggleJournalReminders: (Boolean) -> Unit,
    onToggleSessionReminders: (Boolean) -> Unit,
    onToggleMilestoneNotifications: (Boolean) -> Unit,
    onToggleDefaultPrivateSpots: (Boolean) -> Unit,
    onExportPdf: (Uri) -> Unit,
    onExportCsv: (Uri) -> Unit,
    onOpenPremium: () -> Unit,
    onManageSubscriptions: () -> Unit,
    onCreateAccountClick: () -> Unit = {},
    onSignOut: () -> Unit
) {
    BackHandler { onBack() }
    val context = LocalContext.current
    val isPremium = subscriptionState.isPremiumActive

    var showProfileDialog by remember { mutableStateOf(false) }
    var showHelpDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showTermsDialog by remember { mutableStateOf(false) }
    var showPrivacyDialog by remember { mutableStateOf(false) }
    var pendingNotificationAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            pendingNotificationAction?.invoke()
        }
        pendingNotificationAction = null
    }

    val pdfExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        if (uri != null) {
            onExportPdf(uri)
        }
    }

    val csvExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            onExportCsv(uri)
        }
    }

    fun runWithNotificationPermission(action: () -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !FishLogNotificationManager.hasNotificationPermission(context)
        ) {
            pendingNotificationAction = action
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            action()
        }
    }

    if (showProfileDialog) {
        EditProfileDialog(
            currentName = profile?.displayName ?: "",
            currentBio = profile?.bio ?: "",
            currentHomeWaters = profile?.homeWaters ?: "",
            currentSharePublic = profile?.sharePublicSpots ?: false,
            isSaving = isSaving,
            onDismiss = { showProfileDialog = false },
            onSave = { name, bio, waters, share ->
                onUpdateProfile(name, bio, waters, share)
                showProfileDialog = false
            }
        )
    }

    if (showHelpDialog) {
        AlertDialog(
            onDismissRequest = { showHelpDialog = false },
            title = { Text("Help & Angler Support") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("• Logging Catches: Tap Quick Add Catch on Home or + in the Catch Logbook. Free users can log up to 30 catches, 10 journals, and 3 map spots.")
                    Text("• Private Fishing Spots: Every spot pinned on the Fishing Map is strictly private to your account unless you explicitly turn on Share with Community.")
                    Text("• FishLog Premium: Unlock unlimited logs, advanced multi-year & species comparison charts, custom themes, and PDF/CSV exports.")
                }
            },
            confirmButton = {
                TextButton(onClick = { showHelpDialog = false }) { Text("Close") }
            }
        )
    }

    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = { Text("About FishLog") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("FishLog v1.0", fontWeight = FontWeight.Bold)
                    Text("Designed for freshwater and saltwater anglers who value clean organization, strict spot privacy, and accurate catch analytics.")
                    Text("Built with Material 3, encrypted cloud synchronization, real-time weather telemetry, and Google Play Billing.")
                }
            },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) { Text("Done") }
            }
        )
    }

    if (showTermsDialog) {
        LegalInfoDialog(
            title = "Terms of Service",
            paragraphs = listOf(
                "Subscriptions renew automatically via Google Play unless canceled at least 24 hours before the renewal date.",
                "Manage or cancel anytime in Google Play Store > Payments & Subscriptions.",
                "Premium access is granted exclusively upon verified Google Play Billing entitlement confirmation."
            ),
            onDismiss = { showTermsDialog = false }
        )
    }

    if (showPrivacyDialog) {
        LegalInfoDialog(
            title = "Privacy Policy",
            paragraphs = listOf(
                "All personal catch logs, photos, and private fishing spots remain strictly accessible only to you.",
                "Guest Mode stores records offline on your device until you explicitly sign in to migrate and sync them.",
                "Billing transactions are handled securely by Google Play without sharing payment details with FishLog."
            ),
            onDismiss = { showPrivacyDialog = false }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings & Profile") },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("settings_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier.widthIn(max = 640.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. Account & Edit Profile Card
                Card(
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(52.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    if (isPremium) {
                                        PremiumCrownIcon(
                                            modifier = Modifier.size(26.dp),
                                            tint = Color(0xFFFFCA45)
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Filled.Person,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = profile?.displayName?.ifBlank {
                                            if (isGuestMode) "Guest Angler" else "Angler"
                                        } ?: if (isGuestMode) "Guest Angler" else "Angler",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (isPremium) {
                                        PremiumFeatureBadge(isLocked = false, label = "PRO")
                                    }
                                }
                                Text(
                                    text = if (isGuestMode) {
                                        "Guest Mode • Stored Locally on This Device"
                                    } else {
                                        userEmail.ifBlank { "Cloud Synced Account" }
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (!profile?.homeWaters.isNullOrBlank()) {
                                    Text(
                                        text = "Home Waters: ${profile?.homeWaters}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }

                        if (isGuestMode) {
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Filled.CloudUpload,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Back Up & Sync Your Records",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                    Text(
                                        text = "Guest records are saved offline on this phone only and may be lost if you uninstall the app. Create an account to automatically transfer all your catches, photos, journals, and saved spots to the cloud.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Button(
                                        onClick = onCreateAccountClick,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(50.dp)
                                            .testTag("settings_create_account_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.PersonAdd,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Create an Account",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }

                        OutlinedButton(
                            onClick = { showProfileDialog = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("edit_profile_button")
                        ) {
                            Text("Edit Angler Profile")
                        }
                    }
                }

                // 2. FishLog Premium Membership & Subscription Status Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenPremium() }
                        .testTag("settings_premium_card"),
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF0E2133)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                PremiumCrownIcon(
                                    modifier = Modifier.size(26.dp),
                                    tint = Color(0xFFFFCA45)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = if (isPremium) "FishLog Premium Active" else "Upgrade to FishLog Premium",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Text(
                                        text = subscriptionState.entitlementStatusLabel,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF9EB9AD)
                                    )
                                }
                            }
                        }

                        if (isPremium && subscriptionState.activePlanType?.isSubscription == true) {
                            OutlinedButton(
                                onClick = onManageSubscriptions,
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF2EE585)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Manage Renewal / Cancellation in Google Play")
                            }
                        }
                    }
                }

                // 3. Export Fishing Records (PDF & CSV) - Premium Feature
                SettingsSectionCard(
                    icon = Icons.Filled.FileDownload,
                    title = "Export Fishing Records",
                    trailingBadge = {
                        PremiumFeatureBadge(
                            isLocked = !isPremium,
                            onClick = if (!isPremium) onOpenPremium else null
                        )
                    }
                ) {
                    Text(
                        text = if (isPremium) {
                            "Download your complete catch logbook and trip summary as a printable PDF angler report or CSV spreadsheet."
                        } else {
                            "Upgrade to FishLog Premium to export your fishing records, coordinates, and personal bests to PDF and CSV."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                if (isPremium) {
                                    pdfExportLauncher.launch("FishLog_Angler_Report.pdf")
                                } else {
                                    onOpenPremium()
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("settings_export_pdf_button")
                        ) {
                            Icon(
                                imageVector = if (isPremium) Icons.Filled.PictureAsPdf else Icons.Filled.Lock,
                                contentDescription = null,
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Export PDF")
                        }

                        OutlinedButton(
                            onClick = {
                                if (isPremium) {
                                    csvExportLauncher.launch("FishLog_Catches.csv")
                                } else {
                                    onOpenPremium()
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("settings_export_csv_button")
                        ) {
                            Icon(
                                imageVector = if (isPremium) Icons.Filled.TableChart else Icons.Filled.Lock,
                                contentDescription = null,
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Export CSV")
                        }
                    }
                }

                // 4. Appearance & Custom Themes
                SettingsSectionCard(icon = Icons.Filled.Palette, title = "Appearance & Theme") {
                    Text(
                        text = "Theme Mode",
                        style = MaterialTheme.typography.labelLarge
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThemeMode.entries.forEach { mode ->
                            FilterChip(
                                selected = preferences.themeMode == mode,
                                onClick = { onSetThemeMode(mode) },
                                label = { Text(mode.name.lowercase().replaceFirstChar { it.uppercase() }) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Color Palette",
                        style = MaterialTheme.typography.labelLarge
                    )
                    CustomThemePalette.entries.forEach { palette ->
                        val locked = palette.isPremium && !isPremium
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (locked) {
                                        onOpenPremium()
                                    } else {
                                        onSetCustomPalette(palette)
                                    }
                                }
                                .padding(vertical = 4.dp)
                                .testTag("theme_palette_${palette.name.lowercase()}"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                RadioButton(
                                    selected = preferences.customPalette == palette && !locked,
                                    onClick = {
                                        if (locked) {
                                            onOpenPremium()
                                        } else {
                                            onSetCustomPalette(palette)
                                        }
                                    }
                                )
                                Text(palette.displayName, style = MaterialTheme.typography.bodyMedium)
                            }
                            if (palette.isPremium) {
                                PremiumFeatureBadge(
                                    isLocked = locked,
                                    label = "PREMIUM",
                                    onClick = if (locked) onOpenPremium else null
                                )
                            }
                        }
                    }
                }

                // 5. Measurement Units (kg/lb and cm/in)
                SettingsSectionCard(icon = Icons.Filled.Straighten, title = "Measurement Units") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Weight Unit", style = MaterialTheme.typography.bodyMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            WeightUnit.entries.forEach { unit ->
                                FilterChip(
                                    selected = preferences.weightUnit == unit,
                                    onClick = { onSetWeightUnit(unit) },
                                    label = { Text(unit.symbol.uppercase()) }
                                )
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Length Unit", style = MaterialTheme.typography.bodyMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            LengthUnit.entries.forEach { unit ->
                                FilterChip(
                                    selected = preferences.lengthUnit == unit,
                                    onClick = { onSetLengthUnit(unit) },
                                    label = { Text(unit.symbol.uppercase()) }
                                )
                            }
                        }
                    }
                }

                // 6. Notifications
                SettingsSectionCard(icon = Icons.Filled.Notifications, title = "Notifications") {
                    ToggleSettingRow(
                        title = "Fishing Journal Reminders",
                        subtitle = "Daily check-in to record trips & catches",
                        checked = preferences.journalRemindersEnabled,
                        onCheckedChange = { enabled ->
                            if (enabled) {
                                runWithNotificationPermission { onToggleJournalReminders(true) }
                            } else {
                                onToggleJournalReminders(false)
                            }
                        }
                    )
                    ToggleSettingRow(
                        title = "Fishing Session Reminders",
                        subtitle = "Weekly reminder ahead of prime weekend waters",
                        checked = preferences.sessionRemindersEnabled,
                        onCheckedChange = { enabled ->
                            if (enabled) {
                                runWithNotificationPermission { onToggleSessionReminders(true) }
                            } else {
                                onToggleSessionReminders(false)
                            }
                        }
                    )
                    ToggleSettingRow(
                        title = "Personal Milestone Notifications",
                        subtitle = "Alerts when you break a personal record",
                        checked = preferences.milestoneNotificationsEnabled,
                        onCheckedChange = { enabled ->
                            if (enabled) {
                                runWithNotificationPermission { onToggleMilestoneNotifications(true) }
                            } else {
                                onToggleMilestoneNotifications(false)
                            }
                        }
                    )
                }

                // 7. Privacy Settings
                SettingsSectionCard(icon = Icons.Filled.Security, title = "Privacy Settings") {
                    ToggleSettingRow(
                        title = "Keep New Fishing Spots Private by Default",
                        subtitle = "Pinned map locations are visible only to you unless explicitly shared",
                        checked = preferences.defaultPrivateSpots,
                        onCheckedChange = onToggleDefaultPrivateSpots
                    )
                }

                // 8. Support, Legal & About
                Card(
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showHelpDialog = true }
                                .padding(18.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = null)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Help & Support", style = MaterialTheme.typography.titleSmall)
                        }
                        HorizontalDivider()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showTermsDialog = true }
                                .padding(18.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Gavel, contentDescription = null)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Terms of Service", style = MaterialTheme.typography.titleSmall)
                        }
                        HorizontalDivider()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showPrivacyDialog = true }
                                .padding(18.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Policy, contentDescription = null)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Privacy Policy", style = MaterialTheme.typography.titleSmall)
                        }
                        HorizontalDivider()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showAboutDialog = true }
                                .padding(18.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Info, contentDescription = null)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("About FishLog", style = MaterialTheme.typography.titleSmall)
                        }
                    }
                }

                // 9. Sign Out or Exit Guest Mode
                Button(
                    onClick = onSignOut,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("sign_out_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (isGuestMode) "Exit Guest Mode (Keep Local Data)" else "Sign Out")
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun SettingsSectionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    trailingBadge: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                trailingBadge?.invoke()
            }
            content()
        }
    }
}

@Composable
private fun ToggleSettingRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun EditProfileDialog(
    currentName: String,
    currentBio: String,
    currentHomeWaters: String,
    currentSharePublic: Boolean,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, String, String, Boolean) -> Unit
) {
    var name by remember { mutableStateOf(currentName) }
    var bio by remember { mutableStateOf(currentBio) }
    var waters by remember { mutableStateOf(currentHomeWaters) }
    var shareSpots by remember { mutableStateOf(currentSharePublic) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Angler Profile") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Display Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = waters,
                    onValueChange = { waters = it },
                    label = { Text("Home Waters / Region") },
                    placeholder = { Text("e.g., Pacific Northwest Rivers") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = bio,
                    onValueChange = { bio = it },
                    label = { Text("Angler Bio") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(name, bio, waters, shareSpots) },
                enabled = !isSaving && name.isNotBlank()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
