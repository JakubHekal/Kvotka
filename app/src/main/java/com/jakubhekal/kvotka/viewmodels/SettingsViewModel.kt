package com.jakubhekal.kvotka.viewmodels

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jakubhekal.kvotka.managers.PermissionManager
import com.jakubhekal.kvotka.utils.bytes.FormattedBytes
import kotlinx.coroutines.launch

sealed interface SettingsUiState {
    data class Success(
        val permissionManager: PermissionManager,
        val periodLimit: FormattedBytes,
        val periodResetDate: Int,
        val dailyLimitDynamic: Boolean,
        val dailyLimit: FormattedBytes
    ) : SettingsUiState
    object Error : SettingsUiState
    object Loading : SettingsUiState
}

class SettingsViewModel(private val dataStore: DataStore<Preferences>, private val permissionManager: PermissionManager): ViewModel() {

    object SettingsPreferencesKeys {
        val PERIOD_LIMIT = longPreferencesKey("period_limit")
        val PERIOD_RESET_DATE = intPreferencesKey("period_reset_date")
        val DAILY_LIMIT_DYNAMIC = booleanPreferencesKey("daily_limit_dynamic")
        val DAILY_LIMIT = longPreferencesKey("daily_limit")
    }

    var uiState: SettingsUiState by mutableStateOf(SettingsUiState.Loading)
        private set

    init {
        getState()
    }

    fun getState() {
        viewModelScope.launch {
            dataStore.data.collect { preferences ->
                uiState = SettingsUiState.Loading
                uiState = try {
                    SettingsUiState.Success(
                        permissionManager = permissionManager,
                        periodLimit = FormattedBytes.from(preferences[SettingsPreferencesKeys.PERIOD_LIMIT] ?: 0L),
                        periodResetDate = preferences[SettingsPreferencesKeys.PERIOD_RESET_DATE] ?: 1,
                        dailyLimitDynamic = preferences[SettingsPreferencesKeys.DAILY_LIMIT_DYNAMIC] ?: false,
                        dailyLimit = FormattedBytes.from(preferences[SettingsPreferencesKeys.DAILY_LIMIT] ?: 0L)
                    )
                } catch (e: Exception) {
                    SettingsUiState.Error
                }
            }
        }
    }

    fun <T> setState(key: Preferences.Key<T>, value: T) {
        viewModelScope.launch {
            dataStore.edit {
                it[key] = value
            }
        }
    }
}
