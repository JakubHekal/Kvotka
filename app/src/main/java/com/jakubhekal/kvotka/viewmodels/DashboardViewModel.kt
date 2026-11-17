package com.jakubhekal.kvotka.viewmodels

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.viewModelScope
import com.jakubhekal.kvotka.managers.NetworkUsageManager
import com.jakubhekal.kvotka.managers.netstats.SimUsageProvider
import com.jakubhekal.kvotka.providers.UsageLimitsProvider
import com.jakubhekal.kvotka.utils.LimitsPreferences
import com.jakubhekal.kvotka.utils.PreferencesKeys
import com.jakubhekal.kvotka.utils.bytes.FormattedBytes
import com.jakubhekal.kvotka.utils.bytes.FormattedBytesPair
import com.jakubhekal.kvotka.utils.safePreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import kotlin.math.min

class DashboardViewModel(
    private val dataStore: DataStore<Preferences>,
    private val networkUsageManager: NetworkUsageManager,
    private val simUsageProvider: SimUsageProvider
): GenericViewModel(dataStore) {

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

    var simIds: List<Int> by mutableStateOf(emptyList())
        private set

    var simLoading: Boolean by mutableStateOf(false)
        private set

    var selectedSim: Int? by mutableStateOf(null)
        private set

    private val refresh = MutableStateFlow(0)
    private var started = false
    private var simLoadJob: Job? = null

    fun start() {
        if (started) return
        started = true

        viewModelScope.launch {
            // One collector. getState() only bumps refresh, it never adds collectors.
            dataStore.safePreferences()
                .map { preferences ->
                    LimitsPreferences.from(preferences) to
                        (preferences[PreferencesKeys.Settings.SELECTED_SIM] ?: -1)
                }
                .distinctUntilChanged()
                .combine(refresh) { prefs, _ -> prefs }
                .collectLatest { (limits, sim) ->
                    selectedSim = sim.takeIf { it >= 0 }
                    update(limits)
                }
        }
    }

    fun getState() {
        refresh.value++
        loadSimUsage(force = true)
    }

    fun refreshSimUsage() {
        loadSimUsage(force = false)
    }

    fun selectSim(subId: Int?) {
        viewModelScope.launch {
            dataStore.edit { preferences ->
                preferences[PreferencesKeys.Settings.SELECTED_SIM] = subId ?: -1
            }
        }
    }

    /**
     * Loads netstats off the UI path. The dashboard renders from the system API first,
     * then this bumps [refresh] so selected-SIM totals replace the device totals.
     */
    private fun loadSimUsage(force: Boolean) {
        if (!force && simLoadJob?.isActive == true) return
        simLoadJob?.cancel()
        simLoadJob = viewModelScope.launch {
            simLoading = true
            try {
                // Shizuku's binder can attach a moment after the activity starts.
                var data = simUsageProvider.data(force)
                if (data == null && simUsage == null) {
                    delay(1500)
                    data = simUsageProvider.data(false)
                }
                val netstats = data ?: return@launch
                val preferences = dataStore.safePreferences().first()
                val limits = UsageLimitsProvider(LimitsPreferences.from(preferences))
                simIds = netstats.subIds.sorted()
                simUsage = simUsageProvider.usage(
                    netstats,
                    limits.currentPeriodStart.toInstant().toEpochMilli(),
                    System.currentTimeMillis()
                )
                refresh.value++
            } finally {
                simLoading = false
            }
        }
    }

    private suspend fun update(prefs: LimitsPreferences) {
        val usageLimitsProvider = UsageLimitsProvider(prefs)
        state = if (model == null) State.LOADING else State.UPDATING

        state = try {
            val now = ZonedDateTime.now()
            val periodStartMillis = usageLimitsProvider.currentPeriodStart.toInstant().toEpochMilli()
            val nowMillis = now.toInstant().toEpochMilli()

            // Never block the dashboard on the large netstats dump; use the last parsed copy.
            val netstats = simUsageProvider.cached()
            if (netstats != null) {
                simIds = netstats.subIds.sorted()
                simUsage = simUsageProvider.usage(netstats, periodStartMillis, nowMillis)
            } else {
                loadSimUsage(force = false)
            }

            val selectedSubId = selectedSim
            val simPeriodUsed = if (netstats != null && selectedSubId != null) {
                simUsageProvider.usage(netstats, periodStartMillis, nowMillis)[selectedSubId]
                    ?: FormattedBytesPair.ZERO
            } else {
                null
            }

            val thisPeriodUsed = simPeriodUsed ?: withContext(Dispatchers.Default) {
                networkUsageManager.getDeviceUsageCellular(periodStartMillis, nowMillis)
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
                val dayStartMillis = dayStart.toInstant().toEpochMilli()
                val dayEndMillis = dayStart.plusDays(1).toInstant().toEpochMilli()
                if (netstats != null && selectedSubId != null) {
                    simUsageProvider.usage(netstats, dayStartMillis, dayEndMillis)[selectedSubId]
                        ?: FormattedBytesPair.ZERO
                } else {
                    withContext(Dispatchers.Default) {
                        networkUsageManager.getDeviceUsageCellular(dayStartMillis, dayEndMillis)
                    }
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
