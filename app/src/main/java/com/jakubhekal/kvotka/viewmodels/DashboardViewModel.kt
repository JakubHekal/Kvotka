package com.jakubhekal.kvotka.viewmodels

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.viewModelScope
import com.jakubhekal.kvotka.managers.NetworkUsageManager
import com.jakubhekal.kvotka.providers.UsageLimitsProvider
import com.jakubhekal.kvotka.utils.LimitsPreferences
import com.jakubhekal.kvotka.utils.bytes.FormattedBytes
import com.jakubhekal.kvotka.utils.limitsPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import kotlin.math.min

class DashboardViewModel(private val dataStore: DataStore<Preferences>, private val networkUsageManager: NetworkUsageManager): GenericViewModel(dataStore) {

    data class UsageValues(
        val used: FormattedBytes,
        val remaining: FormattedBytes,
        val limit: FormattedBytes,
        val date: ZonedDateTime
    )

    data class Model(val period: UsageValues, val daily: List<UsageValues>)

    var model: Model? by mutableStateOf(null)
        private set

    private val refresh = MutableStateFlow(0)
    private var started = false

    fun start() {
        if (started) return
        started = true

        viewModelScope.launch {
            // One collector. getState() only bumps refresh, it never adds collectors.
            dataStore.limitsPreferences()
                .combine(refresh) { prefs, _ -> prefs }
                .collectLatest { prefs -> update(prefs) }
        }
    }

    fun getState() {
        refresh.value++
    }

    private suspend fun update(prefs: LimitsPreferences) {
        val usageLimitsProvider = UsageLimitsProvider(prefs)
        state = if (model == null) State.LOADING else State.UPDATING

        state = try {
            val now = ZonedDateTime.now()
            val thisPeriodUsed = withContext(Dispatchers.Default) {
                networkUsageManager.getDeviceUsageCellular(
                    usageLimitsProvider.currentPeriodStart.toInstant().toEpochMilli(),
                    now.toInstant().toEpochMilli()
                )
            }
            val thisPeriodRemaining = usageLimitsProvider.periodLimit - thisPeriodUsed.total
            val thisPeriod = UsageValues(
                thisPeriodUsed.total,
                thisPeriodRemaining,
                usageLimitsProvider.periodLimit,
                usageLimitsProvider.nextPeriodStart
            )

            val daily = mutableListOf<UsageValues>()

            val daysSincePeriodStart = ChronoUnit.DAYS.between(usageLimitsProvider.currentPeriodStart, now)
            for (i in min(6, daysSincePeriodStart).toInt() downTo 0) {
                daily.add(withContext(Dispatchers.Default) {

                    val dayStart = usageLimitsProvider.todayStart.minusDays(i.toLong())
                    val dayEnd = dayStart.plusDays(1)

                    val dayUsed = networkUsageManager.getDeviceUsageCellular(
                        dayStart.toInstant().toEpochMilli(),
                        dayEnd.toInstant().toEpochMilli()
                    )

                    val dayLimit = if (usageLimitsProvider.dynamicDailyLimit) {

                        val periodUsed = networkUsageManager.getDeviceUsageCellular(
                            usageLimitsProvider.currentPeriodStart.toInstant().toEpochMilli(),
                            dayStart.toInstant().toEpochMilli()
                        )

                        val periodRemaining = usageLimitsProvider.periodLimit - periodUsed.total

                        val daysRemaining = ChronoUnit.DAYS.between(dayStart, usageLimitsProvider.nextPeriodStart)
                            .coerceAtLeast(1L)

                        periodRemaining / daysRemaining
                    } else {
                        usageLimitsProvider.dailyLimit
                    }

                    val dayRemaining = dayLimit - dayUsed.total

                    UsageValues(dayUsed.total, dayRemaining, dayLimit, dayStart)
                })
            }

            model = Model(thisPeriod, daily)
            State.SUCCESS
        } catch (e: Exception) {
            e.printStackTrace()
            State.ERROR
        }
    }
}
