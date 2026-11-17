package com.jakubhekal.kvotka

import com.jakubhekal.kvotka.providers.UsageLimitsProvider
import com.jakubhekal.kvotka.utils.LimitsPreferences
import com.jakubhekal.kvotka.viewmodels.AppListViewModel
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class UsageLimitsProviderTest {

    private val zone = ZoneId.of("Europe/Prague")

    private fun provider(now: ZonedDateTime, resetDate: Int) = UsageLimitsProvider(
        LimitsPreferences(
            periodLimit = 0L,
            periodResetDate = resetDate,
            dailyLimitDynamic = false,
            dailyLimit = 0L
        ),
        now
    )

    @Test
    fun resetOnFirstDay() {
        val now = ZonedDateTime.of(2026, 6, 15, 12, 0, 0, 0, zone)
        val provider = provider(now, 1)

        assertEquals(ZonedDateTime.of(2026, 6, 1, 0, 0, 0, 0, zone), provider.currentPeriodStart)
        assertEquals(ZonedDateTime.of(2026, 5, 1, 0, 0, 0, 0, zone), provider.lastPeriodStart)
        assertEquals(ZonedDateTime.of(2026, 7, 1, 0, 0, 0, 0, zone), provider.nextPeriodStart)
        assertEquals(ZonedDateTime.of(2026, 6, 15, 0, 0, 0, 0, zone), provider.todayStart)
    }

    @Test
    fun resetOnDayThirtyFirstClampsToShortMonths() {
        val now = ZonedDateTime.of(2026, 2, 15, 12, 0, 0, 0, zone)
        val provider = provider(now, 31)

        assertEquals(ZonedDateTime.of(2026, 1, 31, 0, 0, 0, 0, zone), provider.currentPeriodStart)
        assertEquals(ZonedDateTime.of(2025, 12, 31, 0, 0, 0, 0, zone), provider.lastPeriodStart)
        assertEquals(ZonedDateTime.of(2026, 2, 28, 0, 0, 0, 0, zone), provider.nextPeriodStart)
    }

    @Test
    fun resetOnDayThirtyFirstOnClampedDay() {
        val now = ZonedDateTime.of(2026, 4, 30, 10, 0, 0, 0, zone)
        val provider = provider(now, 31)

        assertEquals(ZonedDateTime.of(2026, 4, 30, 0, 0, 0, 0, zone), provider.currentPeriodStart)
        assertEquals(ZonedDateTime.of(2026, 5, 31, 0, 0, 0, 0, zone), provider.nextPeriodStart)
        assertEquals(ZonedDateTime.of(2026, 3, 31, 0, 0, 0, 0, zone), provider.lastPeriodStart)
    }

    @Test
    fun resetOnMiddleOfMonth() {
        val beforeReset = provider(ZonedDateTime.of(2026, 6, 10, 12, 0, 0, 0, zone), 15)

        assertEquals(ZonedDateTime.of(2026, 5, 15, 0, 0, 0, 0, zone), beforeReset.currentPeriodStart)
        assertEquals(ZonedDateTime.of(2026, 6, 15, 0, 0, 0, 0, zone), beforeReset.nextPeriodStart)

        val afterReset = provider(ZonedDateTime.of(2026, 6, 20, 12, 0, 0, 0, zone), 15)

        assertEquals(ZonedDateTime.of(2026, 6, 15, 0, 0, 0, 0, zone), afterReset.currentPeriodStart)
        assertEquals(ZonedDateTime.of(2026, 7, 15, 0, 0, 0, 0, zone), afterReset.nextPeriodStart)
    }
}

class FiltersParsingTest {

    @Test
    fun readsStoredEnumNames() {
        assertEquals(
            AppListViewModel.Filters.Period.LAST_PERIOD,
            AppListViewModel.Filters.periodFrom("LAST_PERIOD", null)
        )
        assertEquals(
            AppListViewModel.Filters.OrderBy.NAME,
            AppListViewModel.Filters.orderByFrom("NAME", null)
        )
    }

    @Test
    fun fallsBackToLegacyOrdinals() {
        assertEquals(
            AppListViewModel.Filters.Period.THIS_YEAR,
            AppListViewModel.Filters.periodFrom(null, AppListViewModel.Filters.Period.THIS_YEAR.ordinal)
        )
        assertEquals(
            AppListViewModel.Filters.OrderBy.SENT,
            AppListViewModel.Filters.orderByFrom(null, AppListViewModel.Filters.OrderBy.SENT.ordinal)
        )
    }

    @Test
    fun unknownValuesFallBackToDefaults() {
        assertEquals(
            AppListViewModel.Filters.Period.TODAY,
            AppListViewModel.Filters.periodFrom("NOT_A_PERIOD", 999)
        )
        assertEquals(
            AppListViewModel.Filters.OrderBy.TOTAL,
            AppListViewModel.Filters.orderByFrom(null, -1)
        )
    }
}
