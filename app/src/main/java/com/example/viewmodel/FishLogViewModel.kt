package com.example.viewmodel

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.billing.BillingRepository
import com.example.data.billing.PremiumPlanType
import com.example.data.billing.SubscriptionUiState
import com.example.data.model.CatchRecord
import com.example.data.model.FishingSession
import com.example.data.model.FishingSpot
import com.example.data.model.UiState
import com.example.data.model.UserProfile
import com.example.data.local.GuestFishLogDao
import com.example.data.local.GuestProfileEntity
import com.example.data.preferences.CustomThemePalette
import com.example.data.preferences.LengthUnit
import com.example.data.preferences.PreferencesRepository
import com.example.data.preferences.ThemeMode
import com.example.data.preferences.UserPreferences
import com.example.data.preferences.WeightUnit
import com.example.data.repository.FishLogDataSource
import com.example.data.repository.FishLogRepository
import com.example.data.weather.LiveWeatherConditions
import com.example.data.weather.WeatherLocationRepository
import com.example.util.FishLogExportManager
import com.example.util.FishLogNotificationManager
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PersonalMilestone(
    val id: String,
    val title: String,
    val subtitle: String,
    val achieved: Boolean
)

data class MonthlyCatchPoint(
    val monthLabel: String,
    val count: Int
)

data class SpeciesBreakdown(
    val species: String,
    val count: Int,
    val percentage: Float
)

data class YearlyCatchPoint(
    val yearLabel: String,
    val count: Int,
    val biggestWeightKg: Double
)

data class SpeciesComparisonMetric(
    val species: String,
    val count: Int,
    val maxWeightKg: Double,
    val averageWeightKg: Double,
    val maxLengthCm: Double,
    val topBait: String
)

data class PremiumAnglerBadge(
    val id: String,
    val title: String,
    val description: String,
    val unlocked: Boolean
)

data class FishingAnalytics(
    val totalCatches: Int = 0,
    val totalSessions: Int = 0,
    val biggestByWeight: CatchRecord? = null,
    val longestFish: CatchRecord? = null,
    val averageWeightKg: Double = 0.0,
    val mostFrequentSpecies: String? = null,
    val bestFishingMonth: String? = null,
    val mostCatchesInOneSession: Int = 0,
    val favoriteLocation: String? = null,
    val favoriteBait: String? = null,
    val monthlyHistory: List<MonthlyCatchPoint> = emptyList(),
    val full12MonthHistory: List<MonthlyCatchPoint> = emptyList(),
    val yearlyHistory: List<YearlyCatchPoint> = emptyList(),
    val speciesBreakdown: List<SpeciesBreakdown> = emptyList(),
    val speciesComparisons: List<SpeciesComparisonMetric> = emptyList(),
    val methodBreakdown: Map<String, Int> = emptyMap(),
    val waterConditionBreakdown: Map<String, Int> = emptyMap(),
    val releaseRatePercent: Int = 0,
    val milestones: List<PersonalMilestone> = emptyList(),
    val premiumBadges: List<PremiumAnglerBadge> = emptyList()
)

class FishLogViewModel(
    private val repository: FishLogDataSource,
    private val preferencesRepository: PreferencesRepository,
    private val weatherRepository: WeatherLocationRepository,
    private val billingRepository: BillingRepository,
    val currentUserId: String,
    val defaultDisplayName: String,
    val userEmail: String,
    private val guestDao: GuestFishLogDao? = null
) : ViewModel() {

    val isGuestMode: Boolean = repository.isGuestDataSource
    private val cloudRepository: FishLogRepository? = repository as? FishLogRepository

    val preferences: StateFlow<UserPreferences> = preferencesRepository.preferencesFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UserPreferences()
        )

    val subscriptionState: StateFlow<SubscriptionUiState> = billingRepository.state

    val userProfileState: StateFlow<UiState<UserProfile?>> = repository.observeUserProfile(currentUserId)
        .map<UserProfile?, UiState<UserProfile?>> { UiState.Success(it) }
        .catch { err ->
            Log.w(TAG, "Error observing profile", err)
            emit(UiState.Error(err.localizedMessage ?: "Failed to load profile"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val catchesState: StateFlow<UiState<List<CatchRecord>>> = repository.observeCatches(currentUserId)
        .map<List<CatchRecord>, UiState<List<CatchRecord>>> { UiState.Success(it) }
        .catch { err ->
            Log.w(TAG, "Error observing catches", err)
            emit(UiState.Error(err.localizedMessage ?: "Failed to load catches"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val sessionsState: StateFlow<UiState<List<FishingSession>>> = repository.observeSessions(currentUserId)
        .map<List<FishingSession>, UiState<List<FishingSession>>> { UiState.Success(it) }
        .catch { err ->
            Log.w(TAG, "Error observing sessions", err)
            emit(UiState.Error(err.localizedMessage ?: "Failed to load sessions"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val spotsState: StateFlow<UiState<List<FishingSpot>>> = repository.observeAllVisibleSpots(currentUserId)
        .map<List<FishingSpot>, UiState<List<FishingSpot>>> { UiState.Success(it) }
        .catch { err ->
            Log.w(TAG, "Error observing spots", err)
            emit(UiState.Error(err.localizedMessage ?: "Failed to load spots"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    private val _weatherState = MutableStateFlow<UiState<LiveWeatherConditions>?>(null)
    val weatherState: StateFlow<UiState<LiveWeatherConditions>?> = _weatherState.asStateFlow()

    private val _currentGps = MutableStateFlow<Pair<Double, Double>?>(null)
    val currentGps: StateFlow<Pair<Double, Double>?> = _currentGps.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _isMigratingGuestData = MutableStateFlow(false)
    val isMigratingGuestData: StateFlow<Boolean> = _isMigratingGuestData.asStateFlow()

    val analytics: StateFlow<FishingAnalytics> = combine(
        catchesState,
        sessionsState,
        spotsState
    ) { catchesUi, sessionsUi, spotsUi ->
        val catches = (catchesUi as? UiState.Success)?.data.orEmpty()
        val sessions = (sessionsUi as? UiState.Success)?.data.orEmpty()
        val mySpots = (spotsUi as? UiState.Success)?.data.orEmpty().filter { it.userId == currentUserId }
        computeAnalytics(catches, sessions, mySpots.size)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), FishingAnalytics())

    init {
        // Connect Google Play Billing so product details/prices load dynamically,
        // while purchases remain strictly gated on account authentication + Play verification.
        billingRepository.startConnection()
        initializeSessionAndMigrateIfNeeded()
    }

    private fun initializeSessionAndMigrateIfNeeded() {
        viewModelScope.launch {
            if (isGuestMode) {
                val existingGuestProfile = guestDao?.getProfileOnce()
                if (existingGuestProfile == null) {
                    guestDao?.upsertProfile(
                        GuestProfileEntity(
                            id = "guest_profile",
                            displayName = defaultDisplayName.ifBlank { "Guest Angler" },
                            bio = "Fishing locally in Guest Mode."
                        )
                    )
                }
            } else {
                // 1. Safely migrate any unmigrated local Guest records BEFORE initializing fallback profile
                val dao = guestDao
                val cloud = cloudRepository
                if (dao != null && cloud != null) {
                    _isMigratingGuestData.value = true
                    val migrationRes = cloud.migrateGuestDataToAccount(dao)
                    _isMigratingGuestData.value = false
                    migrationRes.fold(
                        onSuccess = { summary ->
                            val totalMoved = summary.catchesTransferred +
                                summary.sessionsTransferred +
                                summary.spotsTransferred
                            preferencesRepository.setGuestModeActive(false)
                            if (totalMoved > 0) {
                                _snackbarMessage.value =
                                    "Account synced! Safely backed up ${summary.catchesTransferred} catches, ${summary.sessionsTransferred} journals, and ${summary.spotsTransferred} spots."
                            }
                        },
                        onFailure = { err ->
                            Log.e(TAG, "Guest migration encountered an error; local guest records preserved.", err)
                            _snackbarMessage.value =
                                "Could not finish syncing all guest records yet. Your local data is safely preserved and will retry."
                        }
                    )
                }

                // 2. Initialize cloud profile ONLY if none exists yet (never overwrite existing cloud data)
                val latestAuthName = runCatching { Firebase.auth.currentUser?.displayName }.getOrNull()
                    ?.takeIf { it.isNotBlank() }
                    ?: defaultDisplayName.ifBlank { "Angler" }

                cloud?.initializeProfileIfAbsent(
                    UserProfile(
                        userId = currentUserId,
                        displayName = latestAuthName,
                        bio = "Passionate angler tracking catches with FishLog.",
                        homeWaters = ""
                    )
                )
            }
        }
    }

    fun dismissGuestBackupBanner() {
        viewModelScope.launch {
            preferencesRepository.setGuestBackupBannerDismissed(true)
        }
    }

    fun clearSnackbar() {
        _snackbarMessage.value = null
    }

    fun postMessage(msg: String) {
        _snackbarMessage.value = msg
    }

    // --- Weather & GPS ---

    fun refreshWeatherFromDeviceGps() {
        viewModelScope.launch {
            _weatherState.value = UiState.Loading
            val coords = weatherRepository.getCurrentGpsCoordinates()
            if (coords != null) {
                _currentGps.value = coords
                val result = weatherRepository.fetchWeatherForCoordinates(coords.first, coords.second)
                _weatherState.value = result.fold(
                    onSuccess = { UiState.Success(it) },
                    onFailure = { UiState.Error(it.localizedMessage ?: "Unable to fetch weather") }
                )
            } else {
                // Fallback to user's latest saved spot coordinates if device GPS fix isn't available
                val savedSpot = (spotsState.value as? UiState.Success)?.data
                    ?.firstOrNull { it.userId == currentUserId }
                if (savedSpot != null) {
                    val result = weatherRepository.fetchWeatherForCoordinates(
                        savedSpot.latitude,
                        savedSpot.longitude,
                        fallbackLabel = savedSpot.name
                    )
                    _weatherState.value = result.fold(
                        onSuccess = { UiState.Success(it) },
                        onFailure = { UiState.Error(it.localizedMessage ?: "Unable to fetch weather") }
                    )
                } else {
                    _weatherState.value = UiState.Error("Enable location or pin a fishing spot to view live water weather.")
                }
            }
        }
    }

    fun refreshWeatherForSpot(spot: FishingSpot) {
        viewModelScope.launch {
            _weatherState.value = UiState.Loading
            val result = weatherRepository.fetchWeatherForCoordinates(
                spot.latitude,
                spot.longitude,
                fallbackLabel = spot.name
            )
            _weatherState.value = result.fold(
                onSuccess = { UiState.Success(it) },
                onFailure = { UiState.Error(it.localizedMessage ?: "Weather unavailable") }
            )
        }
    }

    // --- CRUD Operations with Tier Enforcement & Milestone Checks ---

    fun canCreateCatch(): Boolean {
        if (subscriptionState.value.isPremiumActive) return true
        val currentCount = (catchesState.value as? UiState.Success)?.data?.size ?: 0
        return currentCount < BillingRepository.FREE_MAX_CATCHES
    }

    fun canCreateSession(): Boolean {
        if (subscriptionState.value.isPremiumActive) return true
        val currentCount = (sessionsState.value as? UiState.Success)?.data?.size ?: 0
        return currentCount < BillingRepository.FREE_MAX_SESSIONS
    }

    fun canCreateSpot(): Boolean {
        if (subscriptionState.value.isPremiumActive) return true
        val mySpotsCount = (spotsState.value as? UiState.Success)?.data
            ?.count { it.userId == currentUserId } ?: 0
        return mySpotsCount < BillingRepository.FREE_MAX_SPOTS
    }

    fun saveCatch(
        context: Context,
        record: CatchRecord,
        previousSessionId: String? = null,
        onSuccess: () -> Unit
    ) {
        val isNew = record.id.isBlank()
        if (isNew && !canCreateCatch()) {
            _snackbarMessage.value = "Free plan limit (${BillingRepository.FREE_MAX_CATCHES} catches) reached. Upgrade to FishLog Premium for unlimited catch logs."
            return
        }

        val previousCatches = (catchesState.value as? UiState.Success)?.data.orEmpty()
        val previousMaxWeight = previousCatches.maxOfOrNull { it.weightKg } ?: 0.0
        val previousCount = previousCatches.size

        viewModelScope.launch {
            _isSaving.value = true
            val result = if (isNew) {
                repository.createCatch(record).map { Unit }
            } else {
                repository.updateCatch(record, previousSessionId)
            }
            _isSaving.value = false

            result.fold(
                onSuccess = {
                    if (isNew && preferences.value.milestoneNotificationsEnabled) {
                        checkAndTriggerMilestones(
                            context = context,
                            newRecord = record,
                            previousCount = previousCount,
                            previousMaxWeight = previousMaxWeight
                        )
                    }
                    _snackbarMessage.value = if (isNew) "Catch logged!" else "Catch updated."
                    onSuccess()
                },
                onFailure = { err ->
                    _snackbarMessage.value = err.localizedMessage ?: "Failed to save catch."
                }
            )
        }
    }

    fun deleteCatch(record: CatchRecord) {
        viewModelScope.launch {
            repository.deleteCatch(record.id, record.sessionId).fold(
                onSuccess = { _snackbarMessage.value = "Catch deleted." },
                onFailure = { _snackbarMessage.value = it.localizedMessage ?: "Failed to delete catch." }
            )
        }
    }

    fun saveSession(session: FishingSession, onSuccess: () -> Unit) {
        val isNew = session.id.isBlank()
        if (isNew && !canCreateSession()) {
            _snackbarMessage.value = "Free plan limit (${BillingRepository.FREE_MAX_SESSIONS} sessions) reached. Upgrade to FishLog Premium for unlimited journal sessions."
            return
        }

        viewModelScope.launch {
            _isSaving.value = true
            val result = if (isNew) {
                repository.createSession(session).map { Unit }
            } else {
                repository.updateSession(session)
            }
            _isSaving.value = false

            result.fold(
                onSuccess = {
                    _snackbarMessage.value = if (isNew) "Fishing session saved!" else "Session updated."
                    onSuccess()
                },
                onFailure = { err ->
                    _snackbarMessage.value = err.localizedMessage ?: "Failed to save session."
                }
            )
        }
    }

    fun deleteSession(session: FishingSession) {
        viewModelScope.launch {
            repository.deleteSession(session.id).fold(
                onSuccess = { _snackbarMessage.value = "Fishing session deleted." },
                onFailure = { _snackbarMessage.value = it.localizedMessage ?: "Failed to delete session." }
            )
        }
    }

    fun saveSpot(spot: FishingSpot, onSuccess: () -> Unit) {
        val isNew = spot.id.isBlank()
        if (isNew && !canCreateSpot()) {
            _snackbarMessage.value = "Free plan limit (${BillingRepository.FREE_MAX_SPOTS} saved spots) reached. Upgrade to FishLog Premium for unlimited spots."
            return
        }

        val profileName = (userProfileState.value as? UiState.Success)?.data?.displayName
            ?: defaultDisplayName.ifBlank { "Angler" }

        viewModelScope.launch {
            _isSaving.value = true
            val enriched = spot.copy(ownerName = profileName)
            val result = if (isNew) {
                repository.createSpot(enriched).map { Unit }
            } else {
                repository.updateSpot(enriched)
            }
            _isSaving.value = false

            result.fold(
                onSuccess = {
                    _snackbarMessage.value = if (isNew) "Fishing spot pinned!" else "Fishing spot updated."
                    refreshWeatherForSpot(enriched)
                    onSuccess()
                },
                onFailure = { err ->
                    _snackbarMessage.value = err.localizedMessage ?: "Failed to save spot."
                }
            )
        }
    }

    fun deleteSpot(spot: FishingSpot) {
        if (spot.userId != currentUserId) {
            _snackbarMessage.value = "You can only delete your own spots."
            return
        }
        viewModelScope.launch {
            repository.deleteSpot(spot.id).fold(
                onSuccess = { _snackbarMessage.value = "Spot removed." },
                onFailure = { _snackbarMessage.value = it.localizedMessage ?: "Failed to remove spot." }
            )
        }
    }

    // --- Profile & Preferences ---

    fun updateProfile(displayName: String, bio: String, homeWaters: String, sharePublicSpots: Boolean) {
        viewModelScope.launch {
            _isSaving.value = true
            val profile = UserProfile(
                userId = currentUserId,
                displayName = displayName.trim().ifBlank { "Angler" },
                bio = bio.trim(),
                homeWaters = homeWaters.trim(),
                sharePublicSpots = sharePublicSpots
            )
            val res = repository.ensureOrSaveUserProfile(profile)
            _isSaving.value = false
            res.fold(
                onSuccess = { _snackbarMessage.value = "Profile updated." },
                onFailure = { _snackbarMessage.value = it.localizedMessage ?: "Failed to update profile." }
            )
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { preferencesRepository.setThemeMode(mode) }
    }

    fun setCustomPalette(palette: CustomThemePalette) {
        if (palette.isPremium && !subscriptionState.value.isPremiumActive) {
            _snackbarMessage.value = "${palette.displayName} is a FishLog Premium theme."
            return
        }
        viewModelScope.launch { preferencesRepository.setCustomPalette(palette) }
    }

    fun setWeightUnit(unit: WeightUnit) {
        viewModelScope.launch { preferencesRepository.setWeightUnit(unit) }
    }

    fun setLengthUnit(unit: LengthUnit) {
        viewModelScope.launch { preferencesRepository.setLengthUnit(unit) }
    }

    fun toggleJournalReminders(context: Context, enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setJournalReminders(enabled)
            FishLogNotificationManager.updateJournalReminderSchedule(context, enabled)
            _snackbarMessage.value = if (enabled) "Daily journal reminders enabled." else "Journal reminders turned off."
        }
    }

    fun toggleSessionReminders(context: Context, enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setSessionReminders(enabled)
            FishLogNotificationManager.updateSessionReminderSchedule(context, enabled)
            _snackbarMessage.value = if (enabled) "Weekly fishing session reminders enabled." else "Session reminders turned off."
        }
    }

    fun toggleMilestoneNotifications(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setMilestoneNotifications(enabled)
        }
    }

    fun toggleDefaultPrivateSpots(privateByDefault: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setDefaultPrivateSpots(privateByDefault)
        }
    }

    // --- Billing & Premium Export ---

    fun purchasePlan(activity: Activity, planType: PremiumPlanType) {
        billingRepository.launchPlanPurchase(
            activity = activity,
            planType = planType,
            authenticatedUserId = currentUserId
        )
    }

    fun restorePurchases() {
        billingRepository.restorePurchases()
    }

    fun openManageSubscriptions(context: Context) {
        billingRepository.openPlayStoreSubscriptionManagement(context)
    }

    fun onAppForegrounded() {
        billingRepository.refreshProductsAndEntitlements(isManualRestore = false)
    }

    fun refreshBilling() {
        billingRepository.refreshProductsAndEntitlements(isManualRestore = false)
    }

    fun clearBillingStatus() {
        billingRepository.clearStatusMessage()
    }

    fun exportRecordsToCsv(context: Context, destinationUri: Uri) {
        if (!subscriptionState.value.isPremiumActive) {
            _snackbarMessage.value = "Exporting records to CSV requires FishLog Premium."
            return
        }
        val catches = (catchesState.value as? UiState.Success)?.data.orEmpty()
        val sessions = (sessionsState.value as? UiState.Success)?.data.orEmpty()
        val spots = (spotsState.value as? UiState.Success)?.data.orEmpty().filter { it.userId == currentUserId }
        viewModelScope.launch {
            _isSaving.value = true
            val res = FishLogExportManager.exportCatchesAndSessionsToCsv(
                context = context,
                destinationUri = destinationUri,
                catches = catches,
                sessions = sessions,
                spots = spots,
                preferences = preferences.value
            )
            _isSaving.value = false
            res.fold(
                onSuccess = { count ->
                    _snackbarMessage.value = "Exported $count catch records to CSV."
                },
                onFailure = { err ->
                    _snackbarMessage.value = err.localizedMessage ?: "Failed to export CSV."
                }
            )
        }
    }

    fun exportRecordsToPdf(context: Context, destinationUri: Uri) {
        if (!subscriptionState.value.isPremiumActive) {
            _snackbarMessage.value = "Exporting angler reports to PDF requires FishLog Premium."
            return
        }
        val catches = (catchesState.value as? UiState.Success)?.data.orEmpty()
        val sessions = (sessionsState.value as? UiState.Success)?.data.orEmpty()
        val anglerName = (userProfileState.value as? UiState.Success)?.data?.displayName
            ?: defaultDisplayName.ifBlank { "Angler" }
        viewModelScope.launch {
            _isSaving.value = true
            val res = FishLogExportManager.exportFishingReportToPdf(
                context = context,
                destinationUri = destinationUri,
                anglerName = anglerName,
                analytics = analytics.value,
                catches = catches,
                sessions = sessions,
                preferences = preferences.value
            )
            _isSaving.value = false
            res.fold(
                onSuccess = { count ->
                    _snackbarMessage.value = "Generated official PDF angler report ($count catches)."
                },
                onFailure = { err ->
                    _snackbarMessage.value = err.localizedMessage ?: "Failed to export PDF."
                }
            )
        }
    }

    // --- Analytics & Milestone Engine ---

    private fun checkAndTriggerMilestones(
        context: Context,
        newRecord: CatchRecord,
        previousCount: Int,
        previousMaxWeight: Double
    ) {
        val newTotal = previousCount + 1
        when {
            newTotal == 1 -> {
                FishLogNotificationManager.sendMilestoneNotification(
                    context,
                    "First Catch Logged!",
                    "Congratulations on logging your first ${newRecord.species} in FishLog!"
                )
            }
            newRecord.weightKg > previousMaxWeight && previousCount > 0 -> {
                FishLogNotificationManager.sendMilestoneNotification(
                    context,
                    "New Personal Best Weight!",
                    "Your ${newRecord.species} sets a new personal record in FishLog!"
                )
            }
            newTotal in listOf(5, 10, 25, 50, 100) -> {
                FishLogNotificationManager.sendMilestoneNotification(
                    context,
                    "$newTotal Catches Milestone!",
                    "You've recorded $newTotal catches in your angler logbook."
                )
            }
        }
    }

    private fun computeAnalytics(
        catches: List<CatchRecord>,
        sessions: List<FishingSession>,
        mySpotsCount: Int
    ): FishingAnalytics {
        val totalCatches = catches.size
        val totalSessions = sessions.size

        val biggestByWeight = catches.maxByOrNull { it.weightKg }
        val longestFish = catches.maxByOrNull { it.lengthCm }
        val avgWeightKg = if (catches.isNotEmpty()) catches.sumOf { it.weightKg } / catches.size else 0.0

        val speciesCounts = catches
            .groupingBy { it.species.trim() }
            .eachCount()
            .entries
            .sortedByDescending { it.value }

        val mostFrequentSpecies = speciesCounts.firstOrNull()?.key

        val speciesBreakdown = speciesCounts.take(6).map { entry ->
            SpeciesBreakdown(
                species = entry.key,
                count = entry.value,
                percentage = if (totalCatches > 0) entry.value.toFloat() / totalCatches else 0f
            )
        }

        val favoriteLocation = catches
            .groupingBy { it.locationName.trim() }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key

        val favoriteBait = catches
            .groupingBy { it.baitOrLure.trim() }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key

        val methodBreakdown = catches
            .groupingBy { it.fishingMethod.trim() }
            .eachCount()

        val monthFormat = SimpleDateFormat("MMMM", Locale.getDefault())
        val bestFishingMonth = catches
            .filter { it.caughtAtEpochMillis > 0L }
            .groupingBy { monthFormat.format(Date(it.caughtAtEpochMillis)) }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key

        // Calculate most catches in one session (either from session.catchCount or linked catches)
        val linkedSessionCounts = catches
            .mapNotNull { it.sessionId?.takeIf { sid -> sid.isNotBlank() } }
            .groupingBy { it }
            .eachCount()
            .values
            .maxOrNull() ?: 0

        val recordedSessionMax = sessions.maxOfOrNull { it.catchCount.toInt() } ?: 0
        val mostInOneSession = maxOf(linkedSessionCounts, recordedSessionMax)

        // Build last 6 months chart points (basic) and last 12 months chart points (Premium)
        val shortMonthFmt = SimpleDateFormat("MMM", Locale.getDefault())
        val keyFmt = SimpleDateFormat("yyyy-MM", Locale.US)
        val catchesByMonthKey = catches
            .filter { it.caughtAtEpochMillis > 0L }
            .groupingBy { keyFmt.format(Date(it.caughtAtEpochMillis)) }
            .eachCount()

        val full12Points = mutableListOf<MonthlyCatchPoint>()
        val cal12 = Calendar.getInstance()
        cal12.set(Calendar.DAY_OF_MONTH, 1)
        cal12.add(Calendar.MONTH, -11)
        repeat(12) {
            val k = keyFmt.format(cal12.time)
            val label = shortMonthFmt.format(cal12.time)
            full12Points.add(MonthlyCatchPoint(label, catchesByMonthKey[k] ?: 0))
            cal12.add(Calendar.MONTH, 1)
        }
        val monthlyPoints = full12Points.takeLast(6)

        // Yearly performance history (last 4 years)
        val yearFmt = SimpleDateFormat("yyyy", Locale.US)
        val catchesByYear = catches
            .filter { it.caughtAtEpochMillis > 0L }
            .groupBy { yearFmt.format(Date(it.caughtAtEpochMillis)) }

        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        val yearlyPoints = ((currentYear - 3)..currentYear).map { yr ->
            val yrStr = yr.toString()
            val listForYear = catchesByYear[yrStr].orEmpty()
            YearlyCatchPoint(
                yearLabel = yrStr,
                count = listForYear.size,
                biggestWeightKg = listForYear.maxOfOrNull { it.weightKg } ?: 0.0
            )
        }

        // Species Comparison metrics (Premium)
        val speciesComparisons = catches
            .filter { it.species.isNotBlank() }
            .groupBy { it.species.trim() }
            .map { (sp, items) ->
                val topBaitForSpecies = items
                    .groupingBy { it.baitOrLure.trim() }
                    .eachCount()
                    .maxByOrNull { it.value }
                    ?.key ?: "—"
                SpeciesComparisonMetric(
                    species = sp,
                    count = items.size,
                    maxWeightKg = items.maxOfOrNull { it.weightKg } ?: 0.0,
                    averageWeightKg = items.sumOf { it.weightKg } / items.size.coerceAtLeast(1),
                    maxLengthCm = items.maxOfOrNull { it.lengthCm } ?: 0.0,
                    topBait = topBaitForSpecies
                )
            }
            .sortedByDescending { it.count }

        val waterConditionBreakdown = catches
            .filter { it.waterConditions.isNotBlank() }
            .groupingBy { it.waterConditions.trim() }
            .eachCount()

        val releaseCount = catches.count { it.released }
        val releaseRatePercent = if (totalCatches > 0) {
            ((releaseCount.toDouble() / totalCatches.toDouble()) * 100.0).toInt()
        } else {
            0
        }

        val distinctSpeciesCount = speciesCounts.size
        val milestones = listOf(
            PersonalMilestone(
                id = "first_catch",
                title = "First Line Wet",
                subtitle = "Log your very first catch in FishLog",
                achieved = totalCatches >= 1
            ),
            PersonalMilestone(
                id = "species_explorer",
                title = "Multi-Species Angler",
                subtitle = "Catch 3 or more distinct fish species ($distinctSpeciesCount/3)",
                achieved = distinctSpeciesCount >= 3
            ),
            PersonalMilestone(
                id = "journal_keeper",
                title = "Dedicated Chronicler",
                subtitle = "Complete 5 fishing journal sessions ($totalSessions/5)",
                achieved = totalSessions >= 5
            ),
            PersonalMilestone(
                id = "cartographer",
                title = "Water Cartographer",
                subtitle = "Pin 3 productive fishing spots ($mySpotsCount/3)",
                achieved = mySpotsCount >= 3
            ),
            PersonalMilestone(
                id = "trophy_hunter",
                title = "Trophy Specimen",
                subtitle = "Land a fish weighing 5 kg (11 lb) or heavier",
                achieved = (biggestByWeight?.weightKg ?: 0.0) >= 5.0
            ),
            PersonalMilestone(
                id = "conservationist",
                title = "Conservation Steward",
                subtitle = "Release 10 catches safely back into the water",
                achieved = releaseCount >= 10
            )
        )

        val premiumBadges = listOf(
            PremiumAnglerBadge(
                id = "pro_crown_member",
                title = "Crown Pro Member",
                description = "Verified FishLog Premium angler emblem",
                unlocked = true
            ),
            PremiumAnglerBadge(
                id = "pro_grand_slam",
                title = "Grand Slam Specialist",
                description = "Record 5+ distinct species across your waters ($distinctSpeciesCount/5)",
                unlocked = distinctSpeciesCount >= 5
            ),
            PremiumAnglerBadge(
                id = "pro_expedition_master",
                title = "Expedition Captain",
                description = "Log 15+ full fishing journal trips ($totalSessions/15)",
                unlocked = totalSessions >= 15
            ),
            PremiumAnglerBadge(
                id = "pro_apex_specimen",
                title = "Apex Trophy Club",
                description = "Land a specimen 8 kg (17.6 lb) or heavier",
                unlocked = (biggestByWeight?.weightKg ?: 0.0) >= 8.0
            )
        )

        return FishingAnalytics(
            totalCatches = totalCatches,
            totalSessions = totalSessions,
            biggestByWeight = biggestByWeight,
            longestFish = longestFish,
            averageWeightKg = avgWeightKg,
            mostFrequentSpecies = mostFrequentSpecies,
            bestFishingMonth = bestFishingMonth,
            mostCatchesInOneSession = mostInOneSession,
            favoriteLocation = favoriteLocation,
            favoriteBait = favoriteBait,
            monthlyHistory = monthlyPoints,
            full12MonthHistory = full12Points,
            yearlyHistory = yearlyPoints,
            speciesBreakdown = speciesBreakdown,
            speciesComparisons = speciesComparisons,
            methodBreakdown = methodBreakdown,
            waterConditionBreakdown = waterConditionBreakdown,
            releaseRatePercent = releaseRatePercent,
            milestones = milestones,
            premiumBadges = premiumBadges
        )
    }

    companion object {
        private const val TAG = "FishLogViewModel"
    }
}
