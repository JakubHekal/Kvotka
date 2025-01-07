package com.jakubhekal.kvotka.viewmodels

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jakubhekal.kvotka.managers.FormattedBytes
import com.jakubhekal.kvotka.managers.NetworkUsageManager
import com.jakubhekal.kvotka.managers.UsageLimitsInterpreter
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
                val usageLimitsInterpreter = UsageLimitsInterpreter(it)
                uiState = DashboardUiState.Loading
                uiState = try {
                    val now = ZonedDateTime.now()
                    val periodUsed = withContext(Dispatchers.Default) {
                        networkUsageManager.getDeviceBytesCellular(
                            usageLimitsInterpreter.currentPeriodStart.toInstant().toEpochMilli(),
                            now.toInstant().toEpochMilli()
                        )
                    }
                    val periodRemaining = usageLimitsInterpreter.periodLimit - periodUsed
                    val period = UsageValues(
                        periodUsed,
                        periodRemaining,
                        usageLimitsInterpreter.periodLimit,
                        usageLimitsInterpreter.nextPeriodStart
                    )

                    val daily = mutableListOf<UsageValues>()

                    for (i in min(6, ChronoUnit.DAYS.between(usageLimitsInterpreter.currentPeriodStart, now)) downTo 0) {
                        daily.add(withContext(Dispatchers.Default) {

                            val dayStart = usageLimitsInterpreter.todayStart.minusDays(i)
                            val dayEnd = dayStart.plusDays(1)

                            val dayUsed = networkUsageManager.getDeviceBytesCellular(
                                dayStart.toInstant().toEpochMilli(),
                                dayEnd.toInstant().toEpochMilli()
                            )

                            val dayLimit = if(usageLimitsInterpreter.dynamicDailyLimit) {

                                val remainingUntilPeriodEnd =
                                    usageLimitsInterpreter.periodLimit - networkUsageManager.getDeviceBytesCellular(
                                        usageLimitsInterpreter.currentPeriodStart.toInstant().toEpochMilli(),
                                        dayEnd.toInstant().toEpochMilli()
                                    )

                                val daysRemaining =
                                    ChronoUnit.DAYS.between(dayStart, usageLimitsInterpreter.nextPeriodStart) + 1

                                remainingUntilPeriodEnd / daysRemaining
                            } else {
                                usageLimitsInterpreter.dailyLimit
                            }

                            val dayRemaining = dayLimit - dayUsed

                            UsageValues(dayUsed, dayRemaining, dayLimit, dayStart)
                        })
                    }

                    DashboardUiState.Success(period, daily.toList())
                } catch (e: Exception) {
                    DashboardUiState.Error
                }
            }
        }
    }
}
