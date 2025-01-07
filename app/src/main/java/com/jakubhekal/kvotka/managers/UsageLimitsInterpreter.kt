package com.jakubhekal.kvotka.managers

import androidx.datastore.preferences.core.Preferences
import com.jakubhekal.kvotka.viewmodels.SettingsViewModel
import java.time.ZonedDateTime

class UsageLimitsInterpreter(private val preferences: Preferences) {

    private val periodResetDate = preferences[SettingsViewModel.SettingsPreferencesKeys.PERIOD_RESET_DATE] ?: 1
    val periodLimit = FormattedBytes.from(preferences[SettingsViewModel.SettingsPreferencesKeys.PERIOD_LIMIT] ?: 0L)
    val dailyLimit = FormattedBytes.from(preferences[SettingsViewModel.SettingsPreferencesKeys.DAILY_LIMIT] ?: 0L)
    val dynamicDailyLimit = preferences[SettingsViewModel.SettingsPreferencesKeys.DAILY_LIMIT_DYNAMIC] ?: false

    val todayStart: ZonedDateTime = ZonedDateTime.now().withHour(0).withMinute(0).withSecond(0).withNano(0)

    val currentPeriodStart: ZonedDateTime
        get() {
            var day = todayStart
            while (day.dayOfMonth != periodResetDate) {
                day = day.minusDays(1)
            }
            return day
        }

    val nextPeriodStart: ZonedDateTime
        get() {
            var day = todayStart
            while (day.dayOfMonth != periodResetDate) {
                day = day.plusDays(1)
            }
            return day
        }
}