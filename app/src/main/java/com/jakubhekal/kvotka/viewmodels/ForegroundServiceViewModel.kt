package com.jakubhekal.kvotka.viewmodels

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
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
import kotlinx.coroutines.cancel
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

class ForegroundServiceViewModel(
    private val dataStore: DataStore<Preferences>,
    private val networkUsageManager: NetworkUsageManager,
    private val simUsageProvider: SimUsageProvider
) : GenericViewModel(dataStore) {

    data class Model(
        val used: FormattedBytes,
        val remaining: FormattedBytes,
        val limit: FormattedBytes,
        val usedAll: Boolean,
        val periodUsedAll: Boolean,
        val hasPlan: Boolean
    )

    private data class PlanSelection(
        val plans: Map<Int, LimitsPreferences>,
        val selectedSim: Int,
        val shizukuEnabled: Boolean
    )

    var model: Model? by mutableStateOf(null)
        private set

    private val refresh = MutableStateFlow(0)
    private var started = false

    fun start() {
        if (started) return
        started = true

        viewModelScope.launch {
            dataStore.ensurePlansMigrated()
            // One collector. The service timer only bumps refresh.
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
                .collectLatest { selection ->
                    update(selection.plans, selection.selectedSim, selection.shizukuEnabled)
                }
        }
    }

    fun getState() {
        if (!started) start()
        refresh.value++
    }

    // The service creates this view model by hand, so nothing calls onCleared() for us.
    fun stop() {
        viewModelScope.cancel()
    }

    private suspend fun update(
        storedPlans: Map<Int, LimitsPreferences>,
        storedSim: Int,
        shizukuEnabled: Boolean
    ) {
        state = if (model == null) State.LOADING else State.UPDATING

        state = try {
            val now = ZonedDateTime.now()
            val netstats = if (shizukuEnabled) simUsageProvider.data() else null
            val subIds = netstats?.subIds.orEmpty()

            var plans = storedPlans
            if (subIds.isNotEmpty() && plans.containsKey(LimitsPreferences.UNASSIGNED_SIM)) {
                dataStore.adoptUnassignedPlan(subIds)
                plans = dataStore.safePreferences().first().simPlans()
            }

            val simId = storedSim.takeIf { it in subIds }
            // With a single SIM there is nothing to choose, so "All SIMs" uses its plan and totals.
            val activeSim = simId ?: subIds.singleOrNull()
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

            val thisPeriodUsed = if (netstats != null && activeSim != null) {
                withContext(Dispatchers.Default) {
                    simUsageProvider.usage(netstats, periodStartMillis, nowMillis)[activeSim]
                        ?: FormattedBytesPair.ZERO
                }
            } else {
                withContext(Dispatchers.Default) {
                    networkUsageManager.getDeviceUsageCellular(periodStartMillis, nowMillis)
                }
            }
            val thisPeriodUsedAll = hasPlan && usageLimitsProvider.periodLimit <= thisPeriodUsed.total

            val todayLimit = when {
                !hasPlan -> FormattedBytes.ZERO
                usageLimitsProvider.dynamicDailyLimit -> {
                    val periodRemaining = usageLimitsProvider.periodLimit - thisPeriodUsed.total
                    val daysRemaining = ChronoUnit.DAYS.between(usageLimitsProvider.todayStart, usageLimitsProvider.nextPeriodStart)
                        .coerceAtLeast(1L)
                    periodRemaining / daysRemaining
                }
                else -> usageLimitsProvider.dailyLimit
            }

            val todayStartMillis = usageLimitsProvider.todayStart.toInstant().toEpochMilli()
            val todayUsed = if (netstats != null && activeSim != null) {
                withContext(Dispatchers.Default) {
                    simUsageProvider.usage(netstats, todayStartMillis, nowMillis)[activeSim]
                        ?: FormattedBytesPair.ZERO
                }
            } else {
                withContext(Dispatchers.Default) {
                    networkUsageManager.getDeviceUsageCellular(todayStartMillis, nowMillis)
                }
            }

            val todayUsedAll = hasPlan && todayLimit <= todayUsed.total

            model = Model(
                used = todayUsed.total,
                remaining = if (hasPlan) todayLimit - todayUsed.total else FormattedBytes.ZERO,
                limit = todayLimit,
                usedAll = todayUsedAll,
                periodUsedAll = thisPeriodUsedAll,
                hasPlan = hasPlan
            )

            State.SUCCESS
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            e.printStackTrace()
            State.ERROR
        }
    }
}
