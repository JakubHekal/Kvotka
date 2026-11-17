package com.jakubhekal.kvotka.utils

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException

data class LimitsPreferences(
    val periodLimit: Long,
    val periodResetDate: Int,
    val dailyLimitDynamic: Boolean,
    val dailyLimit: Long
) {
    companion object {
        fun from(preferences: Preferences) = LimitsPreferences(
            periodLimit = preferences[PreferencesKeys.Settings.PERIOD_LIMIT] ?: 0L,
            periodResetDate = preferences[PreferencesKeys.Settings.PERIOD_RESET_DATE] ?: 1,
            dailyLimitDynamic = preferences[PreferencesKeys.Settings.DAILY_LIMIT_DYNAMIC] ?: false,
            dailyLimit = preferences[PreferencesKeys.Settings.DAILY_LIMIT] ?: 0L
        )
    }
}

fun DataStore<Preferences>.safePreferences(): Flow<Preferences> = data.catch { e ->
    if (e is IOException) emit(emptyPreferences()) else throw e
}

fun DataStore<Preferences>.limitsPreferences(): Flow<LimitsPreferences> =
    safePreferences().map(LimitsPreferences::from).distinctUntilChanged()
