package com.jakubhekal.kvotka.viewmodels

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jakubhekal.kvotka.managers.NetworkUsageManager
import com.jakubhekal.kvotka.providers.UsageLimitsProvider
import com.jakubhekal.kvotka.utils.bytes.FormattedBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import kotlin.math.min

sealed interface DashboardUiState {
    data class Success(
        val period: DashboardViewModel.UsageValues,
        val daily: List<DashboardViewModel.UsageValues>
    ) : DashboardUiState
    object Error : DashboardUiState
    object Loading : DashboardUiState
}

class DashboardViewModel(private val dataStore: DataStore<Preferences>, private val networkUsageManager: NetworkUsageManager): ViewModel() {

    data class UsageValues(
        val used: FormattedBytes,
        val remaining: FormattedBytes,
        val limit: FormattedBytes,
        val date: ZonedDateTime
    )

    var uiState: DashboardUiState by mutableStateOf(DashboardUiState.Loading)
        private set

    init {
        getState()
    }

    fun getState() {
        viewModelScope.launch {
            dataStore.data.collect {
                val usageLimitsProvider = UsageLimitsProvider(it)
                uiState = DashboardUiState.Loading
                uiState = try {
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

                    for (i in min(6, ChronoUnit.DAYS.between(usageLimitsProvider.currentPeriodStart, now)) downTo 0) {
                        daily.add(withContext(Dispatchers.Default) {

                            val dayStart = usageLimitsProvider.todayStart.minusDays(i)
                            val dayEnd = dayStart.plusDays(1)

                            val dayUsed = networkUsageManager.getDeviceUsageCellular(
                                dayStart.toInstant().toEpochMilli(),
                                dayEnd.toInstant().toEpochMilli()
                            )

                            val dayLimit = if(usageLimitsProvider.dynamicDailyLimit) {

                                val periodUsed = networkUsageManager.getDeviceUsageCellular(
                                    usageLimitsProvider.currentPeriodStart.toInstant().toEpochMilli(),
                                    dayEnd.toInstant().toEpochMilli()
                                )

                                val periodRemaining = usageLimitsProvider.periodLimit - periodUsed.total

                                val daysRemaining = ChronoUnit.DAYS.between(dayStart, usageLimitsProvider.nextPeriodStart) + 1

                                periodRemaining / daysRemaining
                            } else {
                                usageLimitsProvider.dailyLimit
                            }

                            val dayRemaining = dayLimit - dayUsed.total

                            UsageValues(dayUsed.total, dayRemaining, dayLimit, dayStart)
                        })
                    }

                    DashboardUiState.Success(thisPeriod, daily.toList())
                } catch (e: Exception) {
                    DashboardUiState.Error
                }
            }
        }
    }
}
