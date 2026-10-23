package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONObject

private val Context.dayFlowDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "dayflow_user_preferences"
)

data class UserSettings(
    val onboardingCompleted: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val use24HourFormat: Boolean = false,
    val weekStartsOnMonday: Boolean = true,
    val defaultPriority: ReminderPriority = ReminderPriority.MEDIUM,
    val defaultCategory: ReminderCategory = ReminderCategory.PERSONAL,
    val defaultAdvanceMinutes: Int = 10,
    val soundEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val defaultSnoozeMinutes: Int = 10,
    val lastReconciledEpochMillis: Long = 0L,
    val lastKnownTimezoneId: String = ZoneId.systemDefault().id
) {
    fun toJsonObject(): JSONObject {
        return JSONObject().apply {
            put("onboardingCompleted", onboardingCompleted)
            put("themeMode", themeMode.name)
            put("use24HourFormat", use24HourFormat)
            put("weekStartsOnMonday", weekStartsOnMonday)
            put("defaultPriority", defaultPriority.name)
            put("defaultCategory", defaultCategory.name)
            put("defaultAdvanceMinutes", defaultAdvanceMinutes)
            put("soundEnabled", soundEnabled)
            put("vibrationEnabled", vibrationEnabled)
            put("defaultSnoozeMinutes", defaultSnoozeMinutes)
        }
    }
}

class UserPreferencesRepository(private val context: Context) {

    private object Keys {
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val USE_24_HOUR = booleanPreferencesKey("use_24_hour")
        val WEEK_STARTS_MONDAY = booleanPreferencesKey("week_starts_monday")
        val DEFAULT_PRIORITY = stringPreferencesKey("default_priority")
        val DEFAULT_CATEGORY = stringPreferencesKey("default_category")
        val DEFAULT_ADVANCE_MINUTES = intPreferencesKey("default_advance_minutes")
        val SOUND_ENABLED = booleanPreferencesKey("sound_enabled")
        val VIBRATION_ENABLED = booleanPreferencesKey("vibration_enabled")
        val DEFAULT_SNOOZE_MINUTES = intPreferencesKey("default_snooze_minutes")
        val LAST_RECONCILED_MILLIS = longPreferencesKey("last_reconciled_millis")
        val LAST_TIMEZONE_ID = stringPreferencesKey("last_timezone_id")
        val INITIAL_SEED_DONE = booleanPreferencesKey("initial_seed_done")
    }

    val settingsFlow: Flow<UserSettings> = context.dayFlowDataStore.data.map { prefs ->
        UserSettings(
            onboardingCompleted = prefs[Keys.ONBOARDING_COMPLETED] ?: false,
            themeMode = prefs[Keys.THEME_MODE]?.let {
                runCatching { ThemeMode.valueOf(it) }.getOrNull()
            } ?: ThemeMode.SYSTEM,
            use24HourFormat = prefs[Keys.USE_24_HOUR] ?: false,
            weekStartsOnMonday = prefs[Keys.WEEK_STARTS_MONDAY] ?: true,
            defaultPriority = prefs[Keys.DEFAULT_PRIORITY]?.let {
                runCatching { ReminderPriority.valueOf(it) }.getOrNull()
            } ?: ReminderPriority.MEDIUM,
            defaultCategory = prefs[Keys.DEFAULT_CATEGORY]?.let {
                runCatching { ReminderCategory.valueOf(it) }.getOrNull()
            } ?: ReminderCategory.PERSONAL,
            defaultAdvanceMinutes = prefs[Keys.DEFAULT_ADVANCE_MINUTES] ?: 10,
            soundEnabled = prefs[Keys.SOUND_ENABLED] ?: true,
            vibrationEnabled = prefs[Keys.VIBRATION_ENABLED] ?: true,
            defaultSnoozeMinutes = prefs[Keys.DEFAULT_SNOOZE_MINUTES] ?: 10,
            lastReconciledEpochMillis = prefs[Keys.LAST_RECONCILED_MILLIS] ?: 0L,
            lastKnownTimezoneId = prefs[Keys.LAST_TIMEZONE_ID] ?: ZoneId.systemDefault().id
        )
    }

    val isInitialSeedDoneFlow: Flow<Boolean> = context.dayFlowDataStore.data.map { prefs ->
        prefs[Keys.INITIAL_SEED_DONE] ?: false
    }

    suspend fun getSettingsSync(): UserSettings = settingsFlow.first()

    suspend fun restoreSettingsFromJson(obj: JSONObject) {
        context.dayFlowDataStore.edit { prefs ->
            if (obj.has("onboardingCompleted")) {
                prefs[Keys.ONBOARDING_COMPLETED] = obj.optBoolean("onboardingCompleted", true)
            }
            if (obj.has("themeMode")) {
                val mode = runCatching { ThemeMode.valueOf(obj.optString("themeMode")) }.getOrNull()
                if (mode != null) prefs[Keys.THEME_MODE] = mode.name
            }
            if (obj.has("use24HourFormat")) {
                prefs[Keys.USE_24_HOUR] = obj.optBoolean("use24HourFormat", false)
            }
            if (obj.has("weekStartsOnMonday")) {
                prefs[Keys.WEEK_STARTS_MONDAY] = obj.optBoolean("weekStartsOnMonday", true)
            }
            if (obj.has("defaultPriority")) {
                val prio = runCatching {
                    ReminderPriority.valueOf(obj.optString("defaultPriority"))
                }.getOrNull()
                if (prio != null) prefs[Keys.DEFAULT_PRIORITY] = prio.name
            }
            if (obj.has("defaultCategory")) {
                val cat = runCatching {
                    ReminderCategory.valueOf(obj.optString("defaultCategory"))
                }.getOrNull()
                if (cat != null) prefs[Keys.DEFAULT_CATEGORY] = cat.name
            }
            if (obj.has("defaultAdvanceMinutes")) {
                prefs[Keys.DEFAULT_ADVANCE_MINUTES] = obj.optInt("defaultAdvanceMinutes", 10).coerceAtLeast(0)
            }
            if (obj.has("soundEnabled")) {
                prefs[Keys.SOUND_ENABLED] = obj.optBoolean("soundEnabled", true)
            }
            if (obj.has("vibrationEnabled")) {
                prefs[Keys.VIBRATION_ENABLED] = obj.optBoolean("vibrationEnabled", true)
            }
            if (obj.has("defaultSnoozeMinutes")) {
                prefs[Keys.DEFAULT_SNOOZE_MINUTES] = obj.optInt("defaultSnoozeMinutes", 10).coerceAtLeast(1)
            }
        }
    }

    suspend fun markInitialSeedDone() {
        context.dayFlowDataStore.edit { it[Keys.INITIAL_SEED_DONE] = true }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.dayFlowDataStore.edit { it[Keys.ONBOARDING_COMPLETED] = completed }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dayFlowDataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setUse24HourFormat(use24Hour: Boolean) {
        context.dayFlowDataStore.edit { it[Keys.USE_24_HOUR] = use24Hour }
    }

    suspend fun setWeekStartsOnMonday(monday: Boolean) {
        context.dayFlowDataStore.edit { it[Keys.WEEK_STARTS_MONDAY] = monday }
    }

    suspend fun setDefaultPriority(priority: ReminderPriority) {
        context.dayFlowDataStore.edit { it[Keys.DEFAULT_PRIORITY] = priority.name }
    }

    suspend fun setDefaultCategory(category: ReminderCategory) {
        context.dayFlowDataStore.edit { it[Keys.DEFAULT_CATEGORY] = category.name }
    }

    suspend fun setDefaultAdvanceMinutes(minutes: Int) {
        context.dayFlowDataStore.edit { it[Keys.DEFAULT_ADVANCE_MINUTES] = minutes }
    }

    suspend fun setSoundEnabled(enabled: Boolean) {
        context.dayFlowDataStore.edit { it[Keys.SOUND_ENABLED] = enabled }
    }

    suspend fun setVibrationEnabled(enabled: Boolean) {
        context.dayFlowDataStore.edit { it[Keys.VIBRATION_ENABLED] = enabled }
    }

    suspend fun setDefaultSnoozeMinutes(minutes: Int) {
        context.dayFlowDataStore.edit { it[Keys.DEFAULT_SNOOZE_MINUTES] = minutes }
    }

    suspend fun recordReconciliation(timestampMillis: Long, timezoneId: String) {
        context.dayFlowDataStore.edit {
            it[Keys.LAST_RECONCILED_MILLIS] = timestampMillis
            it[Keys.LAST_TIMEZONE_ID] = timezoneId
        }
    }
}
