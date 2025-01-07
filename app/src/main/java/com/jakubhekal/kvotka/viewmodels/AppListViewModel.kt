package com.jakubhekal.kvotka.viewmodels

import android.content.pm.PackageManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jakubhekal.kvotka.managers.FormattedBytes
import com.jakubhekal.kvotka.managers.UsageLimitsInterpreter
import com.jakubhekal.kvotka.managers.NetworkUsageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.ZonedDateTime

sealed interface AppListUiState {
    data class Success(
        val appList: List<AppListViewModel.AppValues>,
    ) : AppListUiState
    object Error : AppListUiState
    object Loading : AppListUiState
}

class AppListViewModel(private val dataStore: DataStore<Preferences>, private val networkUsageManager: NetworkUsageManager, private val packageManager: PackageManager): ViewModel() {

    data class AppValues(
        val name: String,
        val icon: ImageBitmap,
        val bytes: FormattedBytes
    )

    var uiState: AppListUiState by mutableStateOf(AppListUiState.Loading)
        private set

    init {
        getState()
    }

    fun getState() {
        viewModelScope.launch {
            dataStore.data.collect {
                uiState = AppListUiState.Loading
                uiState = try {

                    val now = ZonedDateTime.now()
                    val periodStart = UsageLimitsInterpreter(it).currentPeriodStart

                    val packages = withContext(Dispatchers.Default) {
                        packageManager.getInstalledPackages(
                            PackageManager.GET_PERMISSIONS
                        ).filter { p ->
                            p.applicationInfo != null && p.requestedPermissions?.contains("android.permission.INTERNET") == true
                        }
                    }

                    AppListUiState.Success(
                        withContext(Dispatchers.Default) {
                            packages
                                .map { p ->
                                    val ai = p.applicationInfo!!
                                    val bytes = networkUsageManager.getPackageBytesCellular(
                                        ai.uid,
                                        periodStart.toInstant().toEpochMilli(),
                                        now.toInstant().toEpochMilli()
                                    )

                                    AppValues(
                                        ai.loadLabel(packageManager).toString(),
                                        ai.loadIcon(packageManager).toBitmap(128, 128)
                                            .asImageBitmap(),
                                        bytes
                                    )
                                }
                                .filter { appInfo ->
                                    appInfo.bytes.bytes > 0
                                }
                                .sortedBy { appInfo ->
                                    -appInfo.bytes.bytes
                                }
                        })

                } catch (e: Exception) {
                    AppListUiState.Error
                }
            }
        }
    }
}
