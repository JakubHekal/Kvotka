package com.jakubhekal.kvotka.viewmodels

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.viewModelScope
import com.jakubhekal.kvotka.managers.NetworkUsageManager
import com.jakubhekal.kvotka.managers.ShizukuManager
import com.jakubhekal.kvotka.managers.netstats.SimUsageProvider
import com.jakubhekal.kvotka.providers.UsageLimitsProvider
import com.jakubhekal.kvotka.utils.LimitsPreferences
import com.jakubhekal.kvotka.utils.bytes.FormattedBytes
import com.jakubhekal.kvotka.utils.bytes.FormattedBytesPair
import com.jakubhekal.kvotka.utils.limitsPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import kotlin.math.min

class DashboardViewModel(private val dataStore: DataStore<Preferences>, private val networkUsageManager: NetworkUsageManager, private val shizukuManager: ShizukuManager): GenericViewModel(dataStore) {

    data class UsageValues(
        val used: FormattedBytes,
        val remaining: FormattedBytes,
        val limit: FormattedBytes,
        val date: ZonedDateTime
    )

    data class Model(val period: UsageValues, val daily: List<UsageValues>)

    var model: Model? by mutableStateOf(null)
        private set

    var simUsage: Map<Int, FormattedBytesPair>? by mutableStateOf(null)
        private set

    private val simUsageProvider = SimUsageProvider(shizukuManager)
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
        loadSimUsage(force = false)
    }

    fun getState() {
        refresh.value++
        loadSimUsage(force = true)
    }

    fun refreshSimUsage() {
        loadSimUsage(force = false)
    }

    private fun loadSimUsage(force: Boolean) {
        viewModelScope.launch {
            val prefs = dataStore.limitsPreferences().first()
            val limits = UsageLimitsProvider(prefs)
            simUsage = simUsageProvider.usage(
                limits.currentPeriodStart.toInstant().toEpochMilli(),
                System.currentTimeMillis(),
                force
            )
        }
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

            val daysSincePeriodStart = ChronoUnit.DAYS.between(usageLimitsProvider.currentPeriodStart, now)
            val dayStarts = (min(6, daysSincePeriodStart).toInt() downTo 0)
                .map { usageLimitsProvider.todayStart.minusDays(it.toLong()) }
            val dayUsages = dayStarts.map { dayStart ->
                withContext(Dispatchers.Default) {
                    networkUsageManager.getDeviceUsageCellular(
                        dayStart.toInstant().toEpochMilli(),
                        dayStart.plusDays(1).toInstant().toEpochMilli()
                    )
                }
            }

            val daily = mutableListOf<UsageValues>()
            var usedSinceDayStart = FormattedBytesPair.ZERO

            for (index in dayStarts.indices.reversed()) {
                val dayStart = dayStarts[index]
                val dayUsed = dayUsages[index]
                usedSinceDayStart += dayUsed

                val dayLimit = if (usageLimitsProvider.dynamicDailyLimit) {
                    // Usage before this day = whole period minus everything from this day to now.
                    val periodBeforeDay = (thisPeriodUsed - usedSinceDayStart).total
                    val periodRemaining = usageLimitsProvider.periodLimit - periodBeforeDay
                    val daysRemaining = ChronoUnit.DAYS.between(dayStart, usageLimitsProvider.nextPeriodStart)
                        .coerceAtLeast(1L)

                    periodRemaining / daysRemaining
                } else {
                    usageLimitsProvider.dailyLimit
                }

                daily.add(UsageValues(dayUsed.total, dayLimit - dayUsed.total, dayLimit, dayStart))
            }
            daily.reverse()

            model = Model(thisPeriod, daily)
            State.SUCCESS
        } catch (e: Exception) {
            e.printStackTrace()
            State.ERROR
        }
    }
}
