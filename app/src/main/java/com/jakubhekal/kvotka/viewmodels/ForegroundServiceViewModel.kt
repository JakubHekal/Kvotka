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
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

class ForegroundServiceViewModel(private val dataStore: DataStore<Preferences>, private val networkUsageManager: NetworkUsageManager) : GenericViewModel(dataStore) {
    data class Model(
        val used: FormattedBytes,
        val remaining: FormattedBytes,
        val limit: FormattedBytes,
        val usedAll: Boolean,
        val periodUsedAll: Boolean
    )

    var model: Model? by mutableStateOf(null)
        private set

    private val refresh = MutableStateFlow(0)
    private var started = false

    fun start() {
        if (started) return
        started = true

        viewModelScope.launch {
            // One collector. The service timer only bumps refresh.
            dataStore.limitsPreferences()
                .combine(refresh) { prefs, _ -> prefs }
                .collectLatest { prefs -> update(prefs) }
        }
    }

    fun getState() {
        refresh.value++
    }

    // The service creates this view model by hand, so nothing calls onCleared() for us.
    fun stop() {
        viewModelScope.cancel()
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
            val thisPeriodUsedAll = usageLimitsProvider.periodLimit <= thisPeriodUsed.total

            val todayLimit = if (usageLimitsProvider.dynamicDailyLimit) {
                val periodRemaining = usageLimitsProvider.periodLimit - thisPeriodUsed.total
                val daysRemaining = ChronoUnit.DAYS.between(usageLimitsProvider.todayStart, usageLimitsProvider.nextPeriodStart)
                    .coerceAtLeast(1L)
                periodRemaining / daysRemaining
            } else {
                usageLimitsProvider.dailyLimit
            }

            val todayUsed = withContext(Dispatchers.Default) {
                networkUsageManager.getDeviceUsageCellular(
                    usageLimitsProvider.todayStart.toInstant().toEpochMilli(),
                    now.toInstant().toEpochMilli()
                )
            }

            val todayUsedAll = todayLimit <= todayUsed.total

            model = Model(
                used = todayUsed.total,
                remaining = todayLimit - todayUsed.total,
                limit = todayLimit,
                usedAll = todayUsedAll,
                periodUsedAll = thisPeriodUsedAll
            )

            State.SUCCESS
        } catch (e: Exception) {
            e.printStackTrace()
            State.ERROR
        }
    }
}
