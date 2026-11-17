package com.jakubhekal.kvotka.providers

import com.jakubhekal.kvotka.utils.LimitsPreferences
import com.jakubhekal.kvotka.utils.bytes.FormattedBytes
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZonedDateTime

class UsageLimitsProvider(
    preferences: LimitsPreferences,
    private val now: ZonedDateTime = ZonedDateTime.now()
) {

    private val periodResetDate = preferences.periodResetDate.coerceIn(1, 31)
    val periodLimit = FormattedBytes.from(preferences.periodLimit)
    val dailyLimit = FormattedBytes.from(preferences.dailyLimit)
    val dynamicDailyLimit = preferences.dailyLimitDynamic

    val todayStart: ZonedDateTime = now.toLocalDate().atStartOfDay(now.zone)

    val currentPeriodStart: ZonedDateTime
        get() {
            val thisMonthStart = periodStart(YearMonth.from(now.toLocalDate()))
            return if (thisMonthStart.isAfter(now)) {
                periodStart(YearMonth.from(now.toLocalDate()).minusMonths(1))
            } else {
                thisMonthStart
            }
        }

    val lastPeriodStart: ZonedDateTime
        get() = periodStart(YearMonth.from(currentPeriodStart.toLocalDate()).minusMonths(1))

    val nextPeriodStart: ZonedDateTime
        get() = periodStart(YearMonth.from(currentPeriodStart.toLocalDate()).plusMonths(1))

    private fun periodStart(yearMonth: YearMonth): ZonedDateTime {
        val day = periodResetDate.coerceAtMost(yearMonth.lengthOfMonth())
        return LocalDate.of(yearMonth.year, yearMonth.monthValue, day).atStartOfDay(now.zone)
    }
}
