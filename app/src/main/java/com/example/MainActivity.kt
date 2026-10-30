package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Phishing
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Phishing
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.billing.BillingRepository
import com.example.data.local.GuestFishLogDatabase
import com.example.data.model.CatchRecord
import com.example.data.model.FishingSession
import com.example.data.model.FishingSpot
import com.example.data.model.UiState
import com.example.data.preferences.PreferencesRepository
import com.example.data.repository.FishLogRepository
import com.example.data.repository.GuestLocalDataSource
import com.example.data.weather.WeatherLocationRepository
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.AuthViewMode
import com.example.ui.auth.signOutUser
import com.example.ui.screens.CatchDetailScreen
import com.example.ui.screens.CatchEditorScreen
import com.example.ui.screens.CatchListScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.FishingMapScreen
import com.example.ui.screens.JournalListScreen
import com.example.ui.screens.PremiumUpgradeScreen
import com.example.ui.screens.SessionDetailScreen
import com.example.ui.screens.SessionEditorScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.StandalonePersonalRecordsScreen
import com.example.ui.screens.StatisticsAndRecordsScreen
import com.example.ui.theme.FishLogTheme
import com.example.util.FishLogNotificationManager
import com.example.viewmodel.FishLogViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        FishLogNotificationManager.ensureChannels(this)
        setContent {
            FishLogRootApp()
        }
    }
}

internal fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

enum class PrimaryTab(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
) {
    DASHBOARD("Home", Icons.Filled.Home, Icons.Outlined.Home, "nav_home"),
    CATCHES("Catches", Icons.Filled.Phishing, Icons.Outlined.Phishing, "nav_catches"),
    JOURNAL("Journal", Icons.Filled.MenuBook, Icons.Outlined.MenuBook, "nav_journal"),
    MAP("Map", Icons.Filled.Map, Icons.Outlined.Map, "nav_map"),
    STATS("Stats", Icons.Filled.BarChart, Icons.Outlined.BarChart, "nav_stats")
}

sealed interface OverlayRoute {
    data class CatchEditor(
        val existingCatch: CatchRecord? = null,
        val preselectedSessionId: String? = null,
        val preselectedSpot: FishingSpot? = null
    ) : OverlayRoute

    data class CatchDetail(val catchId: String) : OverlayRoute
    data class SessionEditor(val existingSession: FishingSession? = null) : OverlayRoute
    data class SessionDetail(val sessionId: String) : OverlayRoute
    data object PersonalRecords : OverlayRoute
    data object Premium : OverlayRoute
    data object Settings : OverlayRoute
}

@Composable
fun FishLogRootApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val preferencesRepository = remember(context) { PreferencesRepository(context.applicationContext) }
    val guestDao = remember(context) { GuestFishLogDatabase.getInstance(context.applicationContext).guestDao() }

    val loadedPrefs by preferencesRepository.preferencesFlow.collectAsStateWithLifecycle(
        initialValue = null
    )

    val pendingGuestCountFlow = remember(guestDao) {
        combine(
            guestDao.observeActiveCatches(),
            guestDao.observeActiveSessions(),
            guestDao.observeActiveSpots()
        ) { catches, sessions, spots ->
            catches.size + sessions.size + spots.size
        }
    }
    val pendingGuestRecordsCount by pendingGuestCountFlow.collectAsStateWithLifecycle(initialValue = 0)

    val auth = remember { Firebase.auth }
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser

    var showGuestUpgradeScreen by remember { mutableStateOf(false) }
    var guestUpgradeInitialMode by remember { mutableStateOf(AuthViewMode.WELCOME) }
    var resumePremiumAfterAuth by remember { mutableStateOf(false) }

    LaunchedEffect(user?.uid) {
        if (user != null) {
            showGuestUpgradeScreen = false
        }
    }

    val prefs = loadedPrefs
    if (prefs == null) {
        // Brief cold-start state while DataStore resolves persisted Guest Mode / Theme preference
        FishLogTheme {
            Scaffold { innerPadding ->
                Box(modifier = Modifier.fillMaxSize().padding(innerPadding))
            }
        }
        return
    }

    when {
        user != null -> {
            FishLogAppShell(
                sessionKey = "cloud_${user.uid}",
                isGuestSession = false,
                currentUserId = user.uid,
                defaultDisplayName = user.displayName ?: "Angler",
                userEmail = user.email ?: "",
                pendingGuestRecordsCount = pendingGuestRecordsCount,
                preferencesRepository = preferencesRepository,
                openPremiumImmediately = resumePremiumAfterAuth,
                onConsumedOpenPremiumImmediately = { resumePremiumAfterAuth = false },
                onRequestGuestAccountUpgrade = { _, _ -> },
                onMarkGuestPremiumContinueAfterAuth = {},
                onExitGuestMode = {}
            )
        }

        prefs.isGuestModeActive && !showGuestUpgradeScreen -> {
            FishLogAppShell(
                sessionKey = "guest_local",
                isGuestSession = true,
                currentUserId = "guest_local",
                defaultDisplayName = "Guest Angler",
                userEmail = "",
                pendingGuestRecordsCount = pendingGuestRecordsCount,
                preferencesRepository = preferencesRepository,
                openPremiumImmediately = false,
                onConsumedOpenPremiumImmediately = {},
                onRequestGuestAccountUpgrade = { initialMode, returnToPremium ->
                    guestUpgradeInitialMode = initialMode
                    resumePremiumAfterAuth = returnToPremium
                    showGuestUpgradeScreen = true
                },
                onMarkGuestPremiumContinueAfterAuth = {
                    resumePremiumAfterAuth = true
                },
                onExitGuestMode = {
                    scope.launch {
                        preferencesRepository.setGuestModeActive(false)
                    }
                }
            )
        }

        else -> {
            FishLogTheme(
                themeMode = prefs.themeMode,
                palette = prefs.customPalette,
                isPremiumActive = false
            ) {
                AuthScreen(
                    isUpgradeFromGuest = showGuestUpgradeScreen,
                    initialViewMode = guestUpgradeInitialMode,
                    pendingGuestRecordsCount = pendingGuestRecordsCount,
                    onAuthSuccess = {
                        showGuestUpgradeScreen = false
                    },
                    onContinueAsGuest = {
                        resumePremiumAfterAuth = false
                        scope.launch {
                            preferencesRepository.setGuestModeActive(true)
                        }
                    },
                    onCancelUpgrade = if (showGuestUpgradeScreen) {
                        {
                            showGuestUpgradeScreen = false
                            guestUpgradeInitialMode = AuthViewMode.WELCOME
                        }
                    } else {
                        null
                    }
                )
            }
        }
    }
}

@Composable
private fun FishLogAppShell(
    sessionKey: String,
    isGuestSession: Boolean,
    currentUserId: String,
    defaultDisplayName: String,
    userEmail: String,
    pendingGuestRecordsCount: Int,
    preferencesRepository: PreferencesRepository,
    openPremiumImmediately: Boolean,
    onConsumedOpenPremiumImmediately: () -> Unit,
    onRequestGuestAccountUpgrade: (AuthViewMode, Boolean) -> Unit,
    onMarkGuestPremiumContinueAfterAuth: () -> Unit,
    onExitGuestMode: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    val viewModel: FishLogViewModel = viewModel(
        key = sessionKey,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing"
                }
                val guestDao = GuestFishLogDatabase.getInstance(app).guestDao()
                val dataSource = if (isGuestSession) {
                    GuestLocalDataSource(guestDao)
                } else {
                    val databaseId = app.getString(R.string.firestore_database_id)
                    val firestore = FirebaseFirestore.getInstance(databaseId)
                    FishLogRepository(firestore)
                }
                FishLogViewModel(
                    repository = dataSource,
                    preferencesRepository = preferencesRepository,
                    weatherRepository = WeatherLocationRepository(app),
                    billingRepository = BillingRepository(app),
                    currentUserId = currentUserId,
                    defaultDisplayName = defaultDisplayName,
                    userEmail = userEmail,
                    guestDao = guestDao
                )
            }
        }
    )

    // Re-verify subscription status & expiration whenever user returns to app (e.g., from Google Play)
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.onAppForegrounded()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val subscriptionState by viewModel.subscriptionState.collectAsStateWithLifecycle()
    val profileState by viewModel.userProfileState.collectAsStateWithLifecycle()
    val catchesState by viewModel.catchesState.collectAsStateWithLifecycle()
    val sessionsState by viewModel.sessionsState.collectAsStateWithLifecycle()
    val spotsState by viewModel.spotsState.collectAsStateWithLifecycle()
    val weatherState by viewModel.weatherState.collectAsStateWithLifecycle()
    val currentGps by viewModel.currentGps.collectAsStateWithLifecycle()
    val analytics by viewModel.analytics.collectAsStateWithLifecycle()
    val snackbarMessage by viewModel.snackbarMessage.collectAsStateWithLifecycle()
    val isSaving by viewModel.isSaving.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(snackbarMessage) {
        val msg = snackbarMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearSnackbar()
        }
    }

    var currentTab by remember { mutableStateOf(PrimaryTab.DASHBOARD) }
    var activeOverlay by remember {
        mutableStateOf<OverlayRoute?>(if (openPremiumImmediately) OverlayRoute.Premium else null)
    }

    LaunchedEffect(openPremiumImmediately) {
        if (openPremiumImmediately) {
            activeOverlay = OverlayRoute.Premium
            onConsumedOpenPremiumImmediately()
        }
    }

    val allCatches = (catchesState as? UiState.Success)?.data.orEmpty()
    val allSessions = (sessionsState as? UiState.Success)?.data.orEmpty()
    val allSpots = (spotsState as? UiState.Success)?.data.orEmpty()
    val mySpots = allSpots.filter { it.userId == currentUserId }
    val userProfile = (profileState as? UiState.Success)?.data

    FishLogTheme(
        themeMode = preferences.themeMode,
        palette = preferences.customPalette,
        isPremiumActive = subscriptionState.isPremiumActive
    ) {
        val overlay = activeOverlay
        if (overlay != null) {
            Scaffold(
                snackbarHost = { SnackbarHost(snackbarHostState) },
                contentWindowInsets = WindowInsets.safeDrawing
            ) { overlayPadding ->
                Box(modifier = Modifier.padding(overlayPadding)) {
                    when (overlay) {
                        is OverlayRoute.CatchEditor -> {
                            CatchEditorScreen(
                                existingCatch = overlay.existingCatch,
                                preselectedSessionId = overlay.preselectedSessionId,
                                preselectedSpot = overlay.preselectedSpot,
                                availableSpots = mySpots,
                                availableSessions = allSessions,
                                preferences = preferences,
                                isSaving = isSaving,
                                onBack = { activeOverlay = null },
                                onSave = { record, prevSid ->
                                    viewModel.saveCatch(
                                        context = context,
                                        record = record,
                                        previousSessionId = prevSid,
                                        onSuccess = { activeOverlay = null }
                                    )
                                }
                            )
                        }

                        is OverlayRoute.CatchDetail -> {
                            val record = allCatches.firstOrNull { it.id == overlay.catchId }
                            if (record == null) {
                                LaunchedEffect(Unit) { activeOverlay = null }
                            } else {
                                val linkedSessionTitle = allSessions.firstOrNull { it.id == record.sessionId }?.title
                                CatchDetailScreen(
                                    record = record,
                                    preferences = preferences,
                                    linkedSessionTitle = linkedSessionTitle,
                                    onBack = { activeOverlay = null },
                                    onEdit = {
                                        activeOverlay = OverlayRoute.CatchEditor(existingCatch = record)
                                    },
                                    onDelete = {
                                        viewModel.deleteCatch(record)
                                        activeOverlay = null
                                    }
                                )
                            }
                        }

                        is OverlayRoute.SessionEditor -> {
                            val weatherSummary = (weatherState as? UiState.Success)?.data?.let {
                                "${it.conditionDescription}, ${it.pressureHpa.toInt()} hPa"
                            }
                            SessionEditorScreen(
                                existingSession = overlay.existingSession,
                                defaultWeatherSummary = weatherSummary,
                                isSaving = isSaving,
                                onBack = { activeOverlay = null },
                                onSave = { session ->
                                    viewModel.saveSession(session) {
                                        activeOverlay = null
                                    }
                                }
                            )
                        }

                        is OverlayRoute.SessionDetail -> {
                            val session = allSessions.firstOrNull { it.id == overlay.sessionId }
                            if (session == null) {
                                LaunchedEffect(Unit) { activeOverlay = null }
                            } else {
                                val linkedCatches = allCatches.filter { it.sessionId == session.id }
                                SessionDetailScreen(
                                    session = session,
                                    sessionCatches = linkedCatches,
                                    preferences = preferences,
                                    onBack = { activeOverlay = null },
                                    onEditSession = {
                                        activeOverlay = OverlayRoute.SessionEditor(existingSession = session)
                                    },
                                    onDeleteSession = {
                                        viewModel.deleteSession(session)
                                        activeOverlay = null
                                    },
                                    onAddCatchToSession = {
                                        if (viewModel.canCreateCatch()) {
                                            activeOverlay = OverlayRoute.CatchEditor(
                                                preselectedSessionId = session.id
                                            )
                                        } else {
                                            viewModel.postMessage("Free catch limit (${BillingRepository.FREE_MAX_CATCHES}) reached. Upgrade to FishLog Premium.")
                                            activeOverlay = OverlayRoute.Premium
                                        }
                                    },
                                    onSelectCatch = { catchRecord ->
                                        activeOverlay = OverlayRoute.CatchDetail(catchRecord.id)
                                    }
                                )
                            }
                        }

                        OverlayRoute.PersonalRecords -> {
                            StandalonePersonalRecordsScreen(
                                analytics = analytics,
                                preferences = preferences,
                                isPremium = subscriptionState.isPremiumActive,
                                onOpenPremium = { activeOverlay = OverlayRoute.Premium },
                                onBack = { activeOverlay = null }
                            )
                        }

                        OverlayRoute.Premium -> {
                            PremiumUpgradeScreen(
                                subscriptionState = subscriptionState,
                                isGuestMode = viewModel.isGuestMode,
                                pendingGuestRecordsCount = pendingGuestRecordsCount,
                                onBack = { activeOverlay = null },
                                onPurchasePlan = { activity, planType ->
                                    viewModel.purchasePlan(activity, planType)
                                },
                                onRestorePurchases = {
                                    viewModel.restorePurchases()
                                },
                                onManageSubscriptions = {
                                    viewModel.openManageSubscriptions(context)
                                },
                                onRegisterGuestWithEmail = {
                                    activeOverlay = null
                                    onRequestGuestAccountUpgrade(AuthViewMode.EMAIL_CREATE_ACCOUNT, true)
                                },
                                onGuestGoogleAuthCompleted = {
                                    onMarkGuestPremiumContinueAfterAuth()
                                }
                            )
                        }

                        OverlayRoute.Settings -> {
                            SettingsScreen(
                                userEmail = viewModel.userEmail,
                                profile = userProfile,
                                preferences = preferences,
                                subscriptionState = subscriptionState,
                                isGuestMode = viewModel.isGuestMode,
                                isSaving = isSaving,
                                onBack = { activeOverlay = null },
                                onUpdateProfile = { name, bio, waters, share ->
                                    viewModel.updateProfile(name, bio, waters, share)
                                },
                                onSetThemeMode = viewModel::setThemeMode,
                                onSetCustomPalette = viewModel::setCustomPalette,
                                onSetWeightUnit = viewModel::setWeightUnit,
                                onSetLengthUnit = viewModel::setLengthUnit,
                                onToggleJournalReminders = { enabled ->
                                    viewModel.toggleJournalReminders(context, enabled)
                                },
                                onToggleSessionReminders = { enabled ->
                                    viewModel.toggleSessionReminders(context, enabled)
                                },
                                onToggleMilestoneNotifications = viewModel::toggleMilestoneNotifications,
                                onToggleDefaultPrivateSpots = viewModel::toggleDefaultPrivateSpots,
                                onExportPdf = { uri ->
                                    viewModel.exportRecordsToPdf(context, uri)
                                },
                                onExportCsv = { uri ->
                                    viewModel.exportRecordsToCsv(context, uri)
                                },
                                onOpenPremium = { activeOverlay = OverlayRoute.Premium },
                                onManageSubscriptions = {
                                    viewModel.openManageSubscriptions(context)
                                },
                                onCreateAccountClick = {
                                    activeOverlay = null
                                    onRequestGuestAccountUpgrade(AuthViewMode.WELCOME, false)
                                },
                                onSignOut = {
                                    activeOverlay = null
                                    if (viewModel.isGuestMode) {
                                        onExitGuestMode()
                                    } else {
                                        signOutUser(
                                            context = context,
                                            onSignOutComplete = {},
                                            scope = scope
                                        )
                                    }
                                }
                            )
                        }
                    }
                }
            }
        } else {
            // Adaptive Responsive Shell: NavigationBar on phones, NavigationRail on tablets/foldables
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val isWideScreen = maxWidth >= 600.dp

                Scaffold(
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    contentWindowInsets = WindowInsets.statusBars,
                    bottomBar = {
                        if (!isWideScreen) {
                            NavigationBar(
                                containerColor = MaterialTheme.colorScheme.surface,
                                windowInsets = WindowInsets.navigationBars
                            ) {
                                PrimaryTab.entries.forEach { tab ->
                                    val selected = currentTab == tab
                                    NavigationBarItem(
                                        selected = selected,
                                        onClick = { currentTab = tab },
                                        icon = {
                                            Icon(
                                                imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                                contentDescription = tab.label
                                            )
                                        },
                                        label = { Text(tab.label) },
                                        modifier = Modifier.testTag(tab.testTag)
                                    )
                                }
                            }
                        }
                    }
                ) { innerPadding ->
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        if (isWideScreen) {
                            NavigationRail(
                                containerColor = MaterialTheme.colorScheme.surface,
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .windowInsetsPadding(WindowInsets.navigationBars)
                            ) {
                                Spacer(modifier = Modifier.height(12.dp))
                                PrimaryTab.entries.forEach { tab ->
                                    val selected = currentTab == tab
                                    NavigationRailItem(
                                        selected = selected,
                                        onClick = { currentTab = tab },
                                        icon = {
                                            Icon(
                                                imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                                contentDescription = tab.label
                                            )
                                        },
                                        label = { Text(tab.label) },
                                        modifier = Modifier.testTag(tab.testTag)
                                    )
                                }
                                Spacer(modifier = Modifier.weight(1f))
                                IconButton(
                                    onClick = { activeOverlay = OverlayRoute.Settings },
                                    modifier = Modifier.padding(bottom = 16.dp)
                                ) {
                                    Icon(Icons.Filled.Settings, contentDescription = "Settings")
                                }
                            }
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        ) {
                            AnimatedContent(
                                targetState = currentTab,
                                transitionSpec = {
                                    fadeIn(tween(200)) togetherWith fadeOut(tween(150))
                                },
                                label = "FishLogTabTransition"
                            ) { tab ->
                                when (tab) {
                                    PrimaryTab.DASHBOARD -> {
                                        DashboardScreen(
                                            anglerName = userProfile?.displayName ?: viewModel.defaultDisplayName,
                                            isPremium = subscriptionState.isPremiumActive,
                                            isGuestMode = viewModel.isGuestMode,
                                            showGuestBackupReminder = viewModel.isGuestMode && !preferences.guestBackupBannerDismissed,
                                            analytics = analytics,
                                            catchesState = catchesState,
                                            weatherState = weatherState,
                                            preferences = preferences,
                                            onQuickAddCatch = {
                                                if (viewModel.canCreateCatch()) {
                                                    activeOverlay = OverlayRoute.CatchEditor()
                                                } else {
                                                    viewModel.postMessage("Free catch limit (${BillingRepository.FREE_MAX_CATCHES}) reached. Upgrade to FishLog Premium.")
                                                    activeOverlay = OverlayRoute.Premium
                                                }
                                            },
                                            onViewAllCatches = { currentTab = PrimaryTab.CATCHES },
                                            onOpenCatchDetail = { catchRecord ->
                                                activeOverlay = OverlayRoute.CatchDetail(catchRecord.id)
                                            },
                                            onOpenPersonalRecords = {
                                                activeOverlay = OverlayRoute.PersonalRecords
                                            },
                                            onOpenSettings = {
                                                activeOverlay = OverlayRoute.Settings
                                            },
                                            onOpenPremium = {
                                                activeOverlay = OverlayRoute.Premium
                                            },
                                            onRefreshWeather = {
                                                viewModel.refreshWeatherFromDeviceGps()
                                            },
                                            onDismissGuestReminder = {
                                                viewModel.dismissGuestBackupBanner()
                                            },
                                            onCreateAccountFromGuest = {
                                                onRequestGuestAccountUpgrade(AuthViewMode.WELCOME, false)
                                            }
                                        )
                                    }

                                    PrimaryTab.CATCHES -> {
                                        CatchListScreen(
                                            catchesState = catchesState,
                                            preferences = preferences,
                                            isPremium = subscriptionState.isPremiumActive,
                                            onAddCatch = {
                                                if (viewModel.canCreateCatch()) {
                                                    activeOverlay = OverlayRoute.CatchEditor()
                                                } else {
                                                    viewModel.postMessage("Free catch limit (${BillingRepository.FREE_MAX_CATCHES}) reached. Upgrade to FishLog Premium.")
                                                    activeOverlay = OverlayRoute.Premium
                                                }
                                            },
                                            onSelectCatch = { catchRecord ->
                                                activeOverlay = OverlayRoute.CatchDetail(catchRecord.id)
                                            },
                                            onExportPdf = { uri ->
                                                viewModel.exportRecordsToPdf(context, uri)
                                            },
                                            onExportCsv = { uri ->
                                                viewModel.exportRecordsToCsv(context, uri)
                                            },
                                            onOpenPremium = {
                                                activeOverlay = OverlayRoute.Premium
                                            }
                                        )
                                    }

                                    PrimaryTab.JOURNAL -> {
                                        JournalListScreen(
                                            sessionsState = sessionsState,
                                            catches = allCatches,
                                            isPremium = subscriptionState.isPremiumActive,
                                            onNewSession = {
                                                if (viewModel.canCreateSession()) {
                                                    activeOverlay = OverlayRoute.SessionEditor()
                                                } else {
                                                    viewModel.postMessage("Free journal limit (${BillingRepository.FREE_MAX_SESSIONS}) reached. Upgrade to FishLog Premium.")
                                                    activeOverlay = OverlayRoute.Premium
                                                }
                                            },
                                            onSelectSession = { session ->
                                                activeOverlay = OverlayRoute.SessionDetail(session.id)
                                            },
                                            onOpenPremium = {
                                                activeOverlay = OverlayRoute.Premium
                                            }
                                        )
                                    }

                                    PrimaryTab.MAP -> {
                                        FishingMapScreen(
                                            currentUserId = currentUserId,
                                            isGuestMode = viewModel.isGuestMode,
                                            isPremium = subscriptionState.isPremiumActive,
                                            canCreateSpot = viewModel.canCreateSpot(),
                                            spotsState = spotsState,
                                            catches = allCatches,
                                            currentGps = currentGps,
                                            preferences = preferences,
                                            isSaving = isSaving,
                                            onRequestGpsUpdate = { viewModel.refreshWeatherFromDeviceGps() },
                                            onSaveSpot = { spot ->
                                                viewModel.saveSpot(spot) {}
                                            },
                                            onDeleteSpot = { spot ->
                                                viewModel.deleteSpot(spot)
                                            },
                                            onLogCatchAtSpot = { spot ->
                                                if (viewModel.canCreateCatch()) {
                                                    activeOverlay = OverlayRoute.CatchEditor(preselectedSpot = spot)
                                                } else {
                                                    viewModel.postMessage("Free catch limit (${BillingRepository.FREE_MAX_CATCHES}) reached. Upgrade to FishLog Premium.")
                                                    activeOverlay = OverlayRoute.Premium
                                                }
                                            },
                                            onSelectCatch = { catchRecord ->
                                                activeOverlay = OverlayRoute.CatchDetail(catchRecord.id)
                                            },
                                            onOpenPremium = {
                                                activeOverlay = OverlayRoute.Premium
                                            }
                                        )
                                    }

                                    PrimaryTab.STATS -> {
                                        StatisticsAndRecordsScreen(
                                            analytics = analytics,
                                            preferences = preferences,
                                            isPremium = subscriptionState.isPremiumActive,
                                            onExportPdf = { uri ->
                                                viewModel.exportRecordsToPdf(context, uri)
                                            },
                                            onExportCsv = { uri ->
                                                viewModel.exportRecordsToCsv(context, uri)
                                            },
                                            onOpenPremium = { activeOverlay = OverlayRoute.Premium }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
