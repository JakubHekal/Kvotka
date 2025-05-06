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
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.viewModelScope
import com.jakubhekal.kvotka.managers.NetworkUsageManager
import com.jakubhekal.kvotka.providers.UsageLimitsProvider
import com.jakubhekal.kvotka.utils.PreferencesKeys
import com.jakubhekal.kvotka.utils.bytes.FormattedBytesPair
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.ZoneId
import java.time.ZonedDateTime

class AppListViewModel(private val dataStore: DataStore<Preferences>, private val networkUsageManager: NetworkUsageManager, private val packageManager: PackageManager): GenericViewModel(dataStore) {

    data class AppValues(
        val uid: Int,
        val name: String,
        val packageName: String? = null,
        val bitmap: ImageBitmap? = null,
        val vector: ImageVector? = null,
        val used: FormattedBytesPair,
    )

    data class Model(
        val apps: List<AppValues>,
        val filters: Filters
    )

    var model: Model? by mutableStateOf(null)
        private set


    data class Filters(
        val interval: Period,
        val orderBy: OrderBy,
        val orderAsc: Boolean
    ) {
        enum class Period(val title: String) {
            TODAY("Today"),
            THIS_PERIOD("This month"),
            LAST_PERIOD("Last month"),
            THIS_YEAR("This year"),
            ALL_TIME("All time"),
        }
        enum class OrderBy(val title: String) {
            TOTAL("Total usage"),
            NAME("App name"),
            RECEIVED("Received"),
            SENT("Sent"),
        }
    }

    init {
        getState()
    }

    fun getState() {
        viewModelScope.launch {
            dataStore.data.collect { preferences ->
                state = if (model == null) State.LOADING else State.UPDATING
                state = try {

                    val usageLimitsProvider = UsageLimitsProvider(preferences)

                    val filters = Filters(
                        interval = Filters.Period.entries[preferences[PreferencesKeys.Filter.PERIOD] ?: 0],
                        orderBy = Filters.OrderBy.entries[preferences[PreferencesKeys.Filter.ORDER_BY] ?: 0],
                        orderAsc = preferences[PreferencesKeys.Filter.ORDER_ASC] ?: false
                    )

                    val intervalStart = when (filters.interval) {
                        Filters.Period.TODAY -> usageLimitsProvider.todayStart
                        Filters.Period.THIS_PERIOD -> usageLimitsProvider.currentPeriodStart
                        Filters.Period.LAST_PERIOD -> usageLimitsProvider.lastPeriodStart
                        Filters.Period.THIS_YEAR -> usageLimitsProvider.todayStart.withMonth(1).withDayOfMonth(1)
                        Filters.Period.ALL_TIME -> ZonedDateTime.of(1, 1, 1, 0, 0, 0, 0, ZoneId.systemDefault())
                        else -> ZonedDateTime.now()
                    }
                    val intervalEnd = when (filters.interval) {
                        Filters.Period.LAST_PERIOD -> usageLimitsProvider.currentPeriodStart
                        else -> ZonedDateTime.now()
                    }

                    val rawUsages = withContext(Dispatchers.Default) {
                        networkUsageManager.getUidsUsageCellular(
                            intervalStart.toInstant().toEpochMilli(),
                            intervalEnd.toInstant().toEpochMilli()
                        )
                    }
                    val usages = mutableMapOf<Int, FormattedBytesPair>()

                    withContext(Dispatchers.Default) {
                        rawUsages.forEach { (uid, bytes) ->
                            usages.merge(
                                when (uid) {
                                    Process.ROOT_UID, Process.SYSTEM_UID -> Process.SYSTEM_UID
                                    NetworkStats.Bucket.UID_TETHERING -> NetworkStats.Bucket.UID_TETHERING
                                    NetworkStats.Bucket.UID_REMOVED -> NetworkStats.Bucket.UID_REMOVED
                                    in Process.FIRST_APPLICATION_UID..Process.LAST_APPLICATION_UID -> uid
                                    else -> NetworkStats.Bucket.UID_ALL
                                }, bytes, FormattedBytesPair::plus
                            )
                        }
                    }

                    model = Model(
                        withContext(Dispatchers.Default) {
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
                                            bitmap = ai.loadIcon(packageManager)
                                                .toBitmap(128, 128)
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
                            }.sortedWith(
                                when(filters.orderBy) {
                                    Filters.OrderBy.NAME -> compareBy{ it.name }
                                    Filters.OrderBy.TOTAL -> compareBy{ it.used.total.bytes }
                                    Filters.OrderBy.RECEIVED -> compareBy { it.used.rx.bytes }
                                    Filters.OrderBy.SENT -> compareBy{ it.used.tx.bytes }
                                }
                            ).let {
                                if(filters.orderAsc) it else it.reversed()
                            }
                        }, filters)
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
            dataStore.edit { preferences ->
                preferences[PreferencesKeys.Filter.PERIOD] = model.filters.interval.ordinal
                preferences[PreferencesKeys.Filter.ORDER_BY] = model.filters.orderBy.ordinal
                preferences[PreferencesKeys.Filter.ORDER_ASC] = model.filters.orderAsc
            }
        }
    }
}
