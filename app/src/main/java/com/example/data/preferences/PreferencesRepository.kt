package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.fishLogDataStore: DataStore<Preferences> by preferencesDataStore(name = "fishlog_user_preferences")

enum class ThemeMode {
    DARK, LIGHT, SYSTEM
}

enum class CustomThemePalette(val displayName: String, val isPremium: Boolean) {
    ALPINE_FOREST("Alpine Forest (Navy & Forest Green)", false),
    ELECTRIC_EMERALD("Electric Emerald (Navy & Electric Green)", true),
    DEEP_OCEAN("Deep Ocean (Midnight & Cyan)", true),
    GOLDEN_RIVER("Golden River (Slate & Amber)", true)
}

enum class WeightUnit(val symbol: String) {
    KG("kg"),
    LB("lb")
}

enum class LengthUnit(val symbol: String) {
    CM("cm"),
    IN("in")
}

data class UserPreferences(
    val isGuestModeActive: Boolean = false,
    val guestBackupBannerDismissed: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.DARK,
    val customPalette: CustomThemePalette = CustomThemePalette.ALPINE_FOREST,
    val weightUnit: WeightUnit = WeightUnit.KG,
    val lengthUnit: LengthUnit = LengthUnit.CM,
    val journalRemindersEnabled: Boolean = false,
    val sessionRemindersEnabled: Boolean = false,
    val milestoneNotificationsEnabled: Boolean = true,
    val defaultPrivateSpots: Boolean = true
)

class PreferencesRepository(private val context: Context) {

    private object Keys {
        val IS_GUEST_MODE_ACTIVE = booleanPreferencesKey("is_guest_mode_active")
        val GUEST_BANNER_DISMISSED = booleanPreferencesKey("guest_backup_banner_dismissed")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val CUSTOM_PALETTE = stringPreferencesKey("custom_palette")
        val WEIGHT_UNIT = stringPreferencesKey("weight_unit")
        val LENGTH_UNIT = stringPreferencesKey("length_unit")
        val JOURNAL_REMINDERS = booleanPreferencesKey("journal_reminders")
        val SESSION_REMINDERS = booleanPreferencesKey("session_reminders")
        val MILESTONE_NOTIFICATIONS = booleanPreferencesKey("milestone_notifications")
        val DEFAULT_PRIVATE_SPOTS = booleanPreferencesKey("default_private_spots")
    }

    val preferencesFlow: Flow<UserPreferences> = context.fishLogDataStore.data.map { prefs ->
        val mode = prefs[Keys.THEME_MODE]?.let {
            runCatching { ThemeMode.valueOf(it) }.getOrNull()
        } ?: ThemeMode.DARK

        val palette = prefs[Keys.CUSTOM_PALETTE]?.let {
            runCatching { CustomThemePalette.valueOf(it) }.getOrNull()
        } ?: CustomThemePalette.ALPINE_FOREST

        val wUnit = prefs[Keys.WEIGHT_UNIT]?.let {
            runCatching { WeightUnit.valueOf(it) }.getOrNull()
        } ?: WeightUnit.KG

        val lUnit = prefs[Keys.LENGTH_UNIT]?.let {
            runCatching { LengthUnit.valueOf(it) }.getOrNull()
        } ?: LengthUnit.CM

        UserPreferences(
            isGuestModeActive = prefs[Keys.IS_GUEST_MODE_ACTIVE] ?: false,
            guestBackupBannerDismissed = prefs[Keys.GUEST_BANNER_DISMISSED] ?: false,
            themeMode = mode,
            customPalette = palette,
            weightUnit = wUnit,
            lengthUnit = lUnit,
            journalRemindersEnabled = prefs[Keys.JOURNAL_REMINDERS] ?: false,
            sessionRemindersEnabled = prefs[Keys.SESSION_REMINDERS] ?: false,
            milestoneNotificationsEnabled = prefs[Keys.MILESTONE_NOTIFICATIONS] ?: true,
            defaultPrivateSpots = prefs[Keys.DEFAULT_PRIVATE_SPOTS] ?: true
        )
    }

    suspend fun setGuestModeActive(active: Boolean) {
        context.fishLogDataStore.edit {
            it[Keys.IS_GUEST_MODE_ACTIVE] = active
            if (active) {
                it[Keys.GUEST_BANNER_DISMISSED] = false
            }
        }
    }

    suspend fun setGuestBackupBannerDismissed(dismissed: Boolean) {
        context.fishLogDataStore.edit { it[Keys.GUEST_BANNER_DISMISSED] = dismissed }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.fishLogDataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setCustomPalette(palette: CustomThemePalette) {
        context.fishLogDataStore.edit { it[Keys.CUSTOM_PALETTE] = palette.name }
    }

    suspend fun setWeightUnit(unit: WeightUnit) {
        context.fishLogDataStore.edit { it[Keys.WEIGHT_UNIT] = unit.name }
    }

    suspend fun setLengthUnit(unit: LengthUnit) {
        context.fishLogDataStore.edit { it[Keys.LENGTH_UNIT] = unit.name }
    }

    suspend fun setJournalReminders(enabled: Boolean) {
        context.fishLogDataStore.edit { it[Keys.JOURNAL_REMINDERS] = enabled }
    }

    suspend fun setSessionReminders(enabled: Boolean) {
        context.fishLogDataStore.edit { it[Keys.SESSION_REMINDERS] = enabled }
    }

    suspend fun setMilestoneNotifications(enabled: Boolean) {
        context.fishLogDataStore.edit { it[Keys.MILESTONE_NOTIFICATIONS] = enabled }
    }

    suspend fun setDefaultPrivateSpots(privateByDefault: Boolean) {
        context.fishLogDataStore.edit { it[Keys.DEFAULT_PRIVATE_SPOTS] = privateByDefault }
    }
}
