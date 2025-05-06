package com.jakubhekal.kvotka.providers

import androidx.datastore.preferences.core.Preferences
import com.jakubhekal.kvotka.utils.PreferencesKeys
import com.jakubhekal.kvotka.utils.bytes.FormattedBytes
import java.time.ZonedDateTime

class UsageLimitsProvider(preferences: Preferences) {

    private val periodResetDate = preferences[PreferencesKeys.Settings.PERIOD_RESET_DATE] ?: 1
    val periodLimit = FormattedBytes.from(preferences[PreferencesKeys.Settings.PERIOD_LIMIT] ?: 0L)
    val dailyLimit = FormattedBytes.from(preferences[PreferencesKeys.Settings.DAILY_LIMIT] ?: 0L)
    val dynamicDailyLimit = preferences[PreferencesKeys.Settings.DAILY_LIMIT_DYNAMIC] ?: false

    val todayStart: ZonedDateTime = ZonedDateTime.now().withHour(0).withMinute(0).withSecond(0).withNano(0)

    val currentPeriodStart: ZonedDateTime
        get() {
            var day = todayStart
            while (day.dayOfMonth != periodResetDate) {
                day = day.minusDays(1)
            }
            return day
        }

    val lastPeriodStart: ZonedDateTime
        get() {
            var day = currentPeriodStart.minusDays(1)
            while (day.dayOfMonth != periodResetDate) {
                day = day.minusDays(1)
            }
            return day
        }

    val nextPeriodStart: ZonedDateTime
        get() {
            var day = todayStart.plusDays(1)
            while (day.dayOfMonth != periodResetDate) {
                day = day.plusDays(1)
            }
            return day
        }
}