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
import com.jakubhekal.kvotka.utils.adoptUnassignedPlan
import com.jakubhekal.kvotka.utils.bytes.FormattedBytes
import com.jakubhekal.kvotka.utils.bytes.FormattedBytesPair
import com.jakubhekal.kvotka.utils.combinedPlan
import com.jakubhekal.kvotka.utils.ensurePlansMigrated
import com.jakubhekal.kvotka.utils.safePreferences
import com.jakubhekal.kvotka.utils.simPlans
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
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

    data class Model(
        val period: UsageValues,
        val daily: List<UsageValues>,
        val hasPlan: Boolean
    )

    private data class PlanSelection(
        val plans: Map<Int, LimitsPreferences>,
        val selectedSim: Int,
        val shizukuEnabled: Boolean
    )

    var model: Model? by mutableStateOf(null)
        private set

    var simUsage: Map<Int, FormattedBytesPair>? by mutableStateOf(null)
        private set

    var simIds: List<Int> by mutableStateOf(emptyList())
        private set

    var selectedSim: Int? by mutableStateOf(null)
        private set

    private val refresh = MutableStateFlow(0)
    private var started = false

    fun start() {
        if (started) return
        started = true

        viewModelScope.launch {
            try {
                // One collector. getState() only bumps refresh, it never adds collectors.
                dataStore.ensurePlansMigrated()
                dataStore.safePreferences()
                    .map { preferences ->
                        PlanSelection(
                            plans = preferences.simPlans(),
                            selectedSim = preferences[PreferencesKeys.Settings.SELECTED_SIM] ?: -1,
                            shizukuEnabled = preferences[PreferencesKeys.Settings.SHIZUKU_ENABLED] ?: true
                        )
                    }
                    .distinctUntilChanged()
                    .combine(refresh) { selection, _ -> selection }
                    .collectLatest { selection -> update(selection) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // A dead preferences stream must not leave the screen on a spinner forever.
                e.printStackTrace()
                state = State.ERROR
                started = false
            }
        }
    }

    fun getState() {
        if (!started) start()
        refresh.value++
    }

    fun refreshSimUsage() {
        refresh.value++
    }

    fun selectSim(subId: Int?) {
        viewModelScope.launch {
            dataStore.edit { preferences ->
                preferences[PreferencesKeys.Settings.SELECTED_SIM] = subId ?: -1
            }
        }
    }

    private suspend fun update(selection: PlanSelection) {
        state = if (model == null) State.LOADING else State.UPDATING

        state = try {
            var plans = selection.plans
            val now = ZonedDateTime.now()

            // Part of the standard dashboard loading state: no dashboard until SIM totals exist.
            // Retry briefly while Shizuku is starting; the binder and grant can settle late.
            val waitsForShizuku = selection.shizukuEnabled && simUsageProvider.isInstalled()
            var netstats = if (selection.shizukuEnabled) simUsageProvider.data() else null
            var attempts = 0
            while (netstats == null && simUsage == null && attempts < 5 && waitsForShizuku) {
                attempts++
                delay(1_000)
                netstats = simUsageProvider.data()
                // Give Shizuku a couple of seconds to attach before giving up.
                if (netstats == null && attempts >= 2 &&
                    !simUsageProvider.isAvailable() && !simUsageProvider.hasPermission()
                ) break
            }

            val subIds = netstats?.subIds.orEmpty()
            if (subIds.isNotEmpty() && plans.containsKey(LimitsPreferences.UNASSIGNED_SIM)) {
                dataStore.adoptUnassignedPlan(subIds)
                plans = dataStore.safePreferences().first().simPlans()
            }

            if (netstats != null) {
                simIds = subIds.sorted()
            } else {
                // No per-SIM data: hide the SIM selector instead of showing a stale list.
                simIds = emptyList()
                simUsage = null
            }

            val selectedSubId = selection.selectedSim.takeIf { it in subIds }
            selectedSim = selectedSubId
            // With a single SIM there is nothing to choose, so "All SIMs" uses its plan and totals.
            val activeSim = selectedSubId ?: subIds.singleOrNull()

            // "All SIMs" adds up the plans of the SIMs that are present, or of all stored plans when none are known.
            val knownPlans = plans.filterKeys {
                it != LimitsPreferences.UNASSIGNED_SIM && (subIds.isEmpty() || it in subIds)
            }
            val plan = activeSim?.let { plans[it] } ?: knownPlans.ifEmpty { plans }.combinedPlan()
            val usageLimitsProvider = UsageLimitsProvider(
                plan ?: LimitsPreferences(0L, 1, false, 0L)
            )
            val hasPlan = plan != null

            val periodStartMillis = usageLimitsProvider.currentPeriodStart.toInstant().toEpochMilli()
            val nowMillis = now.toInstant().toEpochMilli()
            var simPeriodTotals: Map<Int, FormattedBytesPair>? = null

            if (netstats != null) {
                simPeriodTotals = withContext(Dispatchers.Default) {
                    simUsageProvider.usage(netstats, periodStartMillis, nowMillis)
                }
                simUsage = simPeriodTotals
            }

            val simPeriodUsed = if (simPeriodTotals != null && activeSim != null) {
                simPeriodTotals[activeSim] ?: FormattedBytesPair.ZERO
            } else {
                null
            }
            val thisPeriodUsed = simPeriodUsed ?: withContext(Dispatchers.Default) {
                networkUsageManager.getDeviceUsageCellular(periodStartMillis, nowMillis)
            }
            val thisPeriodRemaining = if (hasPlan) {
                usageLimitsProvider.periodLimit - thisPeriodUsed.total
            } else {
                FormattedBytes.ZERO
            }
            val thisPeriod = UsageValues(
                thisPeriodUsed.total,
                thisPeriodRemaining,
                if (hasPlan) usageLimitsProvider.periodLimit else FormattedBytes.ZERO,
                usageLimitsProvider.nextPeriodStart
            )

            val daysSincePeriodStart = ChronoUnit.DAYS.between(usageLimitsProvider.currentPeriodStart, now)
            val dayStarts = (min(6, daysSincePeriodStart).toInt() downTo 0)
                .map { usageLimitsProvider.todayStart.minusDays(it.toLong()) }
            val dayUsages = withContext(Dispatchers.Default) {
                dayStarts.map { dayStart ->
                    val dayStartMillis = dayStart.toInstant().toEpochMilli()
                    val dayEndMillis = dayStart.plusDays(1).toInstant().toEpochMilli()
                    if (netstats != null && activeSim != null) {
                        simUsageProvider.usage(netstats, dayStartMillis, dayEndMillis)[activeSim]
                            ?: FormattedBytesPair.ZERO
                    } else {
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

                val dayLimit = when {
                    !hasPlan -> FormattedBytes.ZERO
                    usageLimitsProvider.dynamicDailyLimit -> {
                        // Usage before this day = whole period minus everything from this day to now.
                        val periodBeforeDay = (thisPeriodUsed - usedSinceDayStart).total
                        val periodRemaining = usageLimitsProvider.periodLimit - periodBeforeDay
                        val daysRemaining = ChronoUnit.DAYS.between(dayStart, usageLimitsProvider.nextPeriodStart)
                            .coerceAtLeast(1L)

                        periodRemaining / daysRemaining
                    }
                    else -> usageLimitsProvider.dailyLimit
                }

                daily.add(UsageValues(dayUsed.total, dayLimit - dayUsed.total, dayLimit, dayStart))
            }
            daily.reverse()

            model = Model(thisPeriod, daily, hasPlan)
            State.SUCCESS
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            e.printStackTrace()
            State.ERROR
        }
    }
}
