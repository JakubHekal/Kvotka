package com.jakubhekal.kvotka.viewmodels

import android.app.usage.NetworkStats
import android.content.pm.PackageManager
import android.os.Process
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.WifiTethering
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.graphics.drawable.toBitmap
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jakubhekal.kvotka.managers.FormattedBytesPair
import com.jakubhekal.kvotka.managers.NetworkUsageManager
import com.jakubhekal.kvotka.managers.UsageLimitsInterpreter
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
        val uid: Int,
        val name: String,
        val packageName: String? = null,
        val bitmap: ImageBitmap? = null,
        val vector: ImageVector? = null,
        val used: FormattedBytesPair,
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

                    val rawUsages = withContext(Dispatchers.Default) { networkUsageManager.getAllBytesCellular(periodStart.toInstant().toEpochMilli(), now.toInstant().toEpochMilli()) }
                    val usages = mutableMapOf<Int, FormattedBytesPair>()

                    rawUsages.forEach { (uid, bytes) ->
                        usages.merge( when(uid) {
                            Process.ROOT_UID, Process.SYSTEM_UID -> Process.SYSTEM_UID
                            NetworkStats.Bucket.UID_TETHERING -> NetworkStats.Bucket.UID_TETHERING
                            NetworkStats.Bucket.UID_REMOVED -> NetworkStats.Bucket.UID_REMOVED
                            in Process.FIRST_APPLICATION_UID..Process.LAST_APPLICATION_UID -> uid
                            else -> NetworkStats.Bucket.UID_ALL
                        }, bytes, FormattedBytesPair::plus)
                    }

                    AppListUiState.Success(
                        usages.map { (uid, bytes) ->
                            when (uid) {
                                Process.SYSTEM_UID -> AppValues(
                                    uid = uid,
                                    name = "System",
                                    vector = Icons.Outlined.Android,
                                    used = bytes
                                )

                                NetworkStats.Bucket.UID_ALL -> AppValues(
                                    uid = uid,
                                    name = "Other",
                                    vector = Icons.Outlined.MoreHoriz,
                                    used = bytes
                                )

                                NetworkStats.Bucket.UID_TETHERING -> AppValues(
                                    uid = uid,
                                    name = "Tethering and Hotspot",
                                    vector = Icons.Outlined.WifiTethering,
                                    used = bytes
                                )

                                NetworkStats.Bucket.UID_REMOVED -> AppValues(
                                    uid = uid,
                                    name = "Removed apps",
                                    vector = Icons.Outlined.Delete,
                                    used = bytes
                                )

                                in Process.FIRST_APPLICATION_UID..Process.LAST_APPLICATION_UID -> {
                                    val ai = packageManager.getApplicationInfo(
                                        packageManager.getPackagesForUid(uid)!!.first(),
                                        PackageManager.GET_META_DATA
                                    )
                                    AppValues(
                                        uid = uid,
                                        name = ai.loadLabel(packageManager).toString(),
                                        packageName = ai.packageName,
                                        bitmap = ai.loadIcon(packageManager).toBitmap(128, 128)
                                            .asImageBitmap(),
                                        used = bytes
                                    )
                                }

                                else -> {
                                    AppValues(
                                        uid = uid,
                                        name = uid.toString(),
                                        bitmap = ImageBitmap(1, 1),
                                        used = bytes
                                    )
                                }
                            }
                        }
                        .sortedBy {
                            -it.used.total.bytes
                        }
                    )

                } catch (e: Exception) {
                    e.printStackTrace()
                    AppListUiState.Error
                }
            }
        }
    }
}
