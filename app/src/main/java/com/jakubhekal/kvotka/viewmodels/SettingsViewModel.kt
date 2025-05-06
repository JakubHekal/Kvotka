package com.jakubhekal.kvotka.viewmodels

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.viewModelScope
import com.jakubhekal.kvotka.managers.PermissionManager
import com.jakubhekal.kvotka.utils.PreferencesKeys
import com.jakubhekal.kvotka.utils.bytes.FormattedBytes
import kotlinx.coroutines.launch

class SettingsViewModel(private val dataStore: DataStore<Preferences>, private val permissionManager: PermissionManager): GenericViewModel(dataStore) {

    data class Model(
        val permissionManager: PermissionManager,
        val periodLimit: FormattedBytes,
        val periodResetDate: Int,
        val dailyLimitDynamic: Boolean,
        val dailyLimit: FormattedBytes
    )

    var model: Model? by mutableStateOf(null)
        private set

    init {
        getState()
    }

    fun getState() {
        viewModelScope.launch {
            dataStore.data.collect { preferences ->
                state = State.LOADING
                state = try {
                    model = Model(
                        permissionManager = permissionManager,
                        periodLimit = FormattedBytes.from(preferences[PreferencesKeys.Settings.PERIOD_LIMIT] ?: 0L),
                        periodResetDate = preferences[PreferencesKeys.Settings.PERIOD_RESET_DATE] ?: 1,
                        dailyLimitDynamic = preferences[PreferencesKeys.Settings.DAILY_LIMIT_DYNAMIC] ?: false,
                        dailyLimit = FormattedBytes.from(preferences[PreferencesKeys.Settings.DAILY_LIMIT] ?: 0L)
                    )
                    State.SUCCESS
                } catch (e: Exception) {
                    e.printStackTrace()
                    State.ERROR
                }
            }
        }
    }

    fun setState(model: Model) {
        viewModelScope.launch {
            dataStore.edit {
                it[PreferencesKeys.Settings.PERIOD_LIMIT] = model.periodLimit.bytes
                it[PreferencesKeys.Settings.PERIOD_RESET_DATE] = model.periodResetDate
                it[PreferencesKeys.Settings.DAILY_LIMIT_DYNAMIC] = model.dailyLimitDynamic
                it[PreferencesKeys.Settings.DAILY_LIMIT] = model.dailyLimit.bytes
            }
        }
    }
}
