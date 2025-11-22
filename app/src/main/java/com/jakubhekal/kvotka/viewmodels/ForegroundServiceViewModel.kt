package com.jakubhekal.kvotka.viewmodels

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.viewModelScope
import com.jakubhekal.kvotka.managers.NetworkUsageManager
import com.jakubhekal.kvotka.providers.UsageLimitsProvider
import com.jakubhekal.kvotka.utils.bytes.FormattedBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
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

    init {
        getState()
    }

    fun getState() {
        viewModelScope.launch {
            // Use collectLatest so previous running calculations are cancelled when preferences change
            dataStore.data.collectLatest { prefs ->
                 val usageLimitsProvider = UsageLimitsProvider( prefs)

                 state = if (model == null) State.LOADING else State.UPDATING
                 state = try {
                     val now = ZonedDateTime.now()
                     // heavy IO/CPU work off the main thread
                     val thisPeriodUsed = withContext(Dispatchers.Default) {
                         networkUsageManager.getDeviceUsageCellular(
                             usageLimitsProvider.currentPeriodStart.toInstant().toEpochMilli(),
                             usageLimitsProvider.todayStart.toInstant().toEpochMilli()
                         )
                     }
                     val thisPeriodUsedAll = usageLimitsProvider.periodLimit <= thisPeriodUsed.total

                     val todayLimit = if(usageLimitsProvider.dynamicDailyLimit) {
                        val periodRemaining = usageLimitsProvider.periodLimit - thisPeriodUsed.total
                        var daysRemaining = ChronoUnit.DAYS.between(usageLimitsProvider.todayStart, usageLimitsProvider.nextPeriodStart)
                        // avoid division by zero or negative days
                        if (daysRemaining <= 0L) daysRemaining = 1L
                        periodRemaining / daysRemaining
                     } else {
                         usageLimitsProvider.dailyLimit
                     }

                     // also run this on background dispatcher
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
    }
}
