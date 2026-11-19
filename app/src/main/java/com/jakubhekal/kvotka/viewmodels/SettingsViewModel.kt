package com.jakubhekal.kvotka.viewmodels

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.viewModelScope
import com.jakubhekal.kvotka.managers.PermissionManager
import com.jakubhekal.kvotka.managers.netstats.SimUsageProvider
import com.jakubhekal.kvotka.utils.LimitsPreferences
import com.jakubhekal.kvotka.utils.PreferencesKeys
import com.jakubhekal.kvotka.utils.adoptUnassignedPlan
import com.jakubhekal.kvotka.utils.safePreferences
import com.jakubhekal.kvotka.utils.simPlansPreferences
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val dataStore: DataStore<Preferences>,
    private val permissionManager: PermissionManager,
    private val simUsageProvider: SimUsageProvider
): GenericViewModel(dataStore) {

    data class Model(
        val permissionManager: PermissionManager,
        val plans: Map<Int, LimitsPreferences>
    )

    var model: Model? by mutableStateOf(null)
        private set

    var simIds: List<Int> by mutableStateOf(emptyList())
        private set

    var shizukuEnabled: Boolean by mutableStateOf(true)
        private set

    private var job: Job? = null

    fun start() {
        if (job?.isActive == true) return

        job = viewModelScope.launch {
            combine(
                dataStore.simPlansPreferences(),
                dataStore.safePreferences()
                    .map { it[PreferencesKeys.Settings.SHIZUKU_ENABLED] ?: true }
                    .distinctUntilChanged()
            ) { plans, enabled -> plans to enabled }
                .collectLatest { (plans, enabled) ->
                    state = if (model == null) State.LOADING else State.UPDATING
                    state = try {
                        shizukuEnabled = enabled
                        model = Model(permissionManager = permissionManager, plans = plans)
                        State.SUCCESS
                    } catch (e: Exception) {
                        e.printStackTrace()
                        State.ERROR
                    }
                }
        }
        refreshSims()
    }

    fun getState() {
        start()
        refreshSims()
    }

    fun updateShizukuEnabled(enabled: Boolean) {
        viewModelScope.launch {
            dataStore.edit {
                it[PreferencesKeys.Settings.SHIZUKU_ENABLED] = enabled
            }
            if (enabled) refreshSims() else simIds = emptyList()
        }
    }

    fun refreshSims() {
        viewModelScope.launch {
            val enabled = dataStore.safePreferences().first()[PreferencesKeys.Settings.SHIZUKU_ENABLED] ?: true
            if (!enabled) {
                simIds = emptyList()
                return@launch
            }
            val ids = simUsageProvider.data()?.subIds?.sorted().orEmpty()
            simIds = ids
            if (ids.isEmpty()) return@launch
            dataStore.adoptUnassignedPlan(ids)
        }
    }

    fun setState(subId: Int, plan: LimitsPreferences) {
        viewModelScope.launch {
            dataStore.edit {
                it[PreferencesKeys.Settings.simPlan(subId)] = plan.encode()
            }
        }
    }

    fun clearPlan(subId: Int) {
        viewModelScope.launch {
            dataStore.edit {
                it.remove(PreferencesKeys.Settings.simPlan(subId))
            }
        }
    }
}
