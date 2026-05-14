package com.jakubhekal.kvotka.viewmodels

import android.app.Application
import android.app.usage.NetworkStats
import android.content.Context
import android.content.pm.PackageManager
import android.icu.text.Collator
import android.os.Process
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.BrokenImage
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
import com.jakubhekal.kvotka.R
import com.jakubhekal.kvotka.managers.NetworkUsageManager
import com.jakubhekal.kvotka.providers.UsageLimitsProvider
import com.jakubhekal.kvotka.utils.PreferencesKeys
import com.jakubhekal.kvotka.utils.bytes.FormattedBytesPair
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

class AppListViewModel(private val context: Context, val dataStore: DataStore<Preferences>, private val networkUsageManager: NetworkUsageManager, private val packageManager: PackageManager): GenericViewModel(dataStore) {

    data class AppValues(
        val uid: Int,
        val name: Any? = null,
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
        enum class Period(val titleRes: Int) {
            TODAY(R.string.label_today),
            THIS_PERIOD(R.string.label_this_month),
            LAST_PERIOD(R.string.label_last_month),
            THIS_YEAR(R.string.label_this_year),
            ALL_TIME(R.string.label_all_time),
        }
        enum class OrderBy(val titleRes: Int) {
            TOTAL(R.string.label_total),
            RECEIVED(R.string.label_received),
            SENT(R.string.label_sent),
            NAME(R.string.label_app_name),
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
                                    in Process.FIRST_APPLICATION_UID..Process.LAST_APPLICATION_UID -> {
                                        try {
                                            packageManager.getPackagesForUid(uid)?.firstOrNull()?.let {
                                                packageManager.getApplicationInfo(
                                                    it,
                                                    PackageManager.GET_META_DATA
                                                )
                                            }
                                        } catch (_: PackageManager.NameNotFoundException) {
                                            null
                                        }?.uid ?: NetworkStats.Bucket.UID_REMOVED
                                    }
                                    else -> NetworkStats.Bucket.UID_ALL
                                }, bytes, FormattedBytesPair::plus
                            )
                        }
                    }

                    model = Model(
                        withContext(Dispatchers.Default) {
                            val collator = Collator.getInstance(Locale.getDefault()).apply {
                                strength = Collator.SECONDARY
                            }

                            usages.map { (uid, bytes) ->
                                when (uid) {
                                    Process.SYSTEM_UID -> AppValues(
                                        uid = uid,
                                        name = context.getString(R.string.app_name_system),
                                        vector = Icons.Outlined.Android,
                                        used = bytes
                                    )

                                    NetworkStats.Bucket.UID_ALL -> AppValues(
                                        uid = uid,
                                        name = context.getString(R.string.app_name_other),
                                        vector = Icons.Outlined.MoreHoriz,
                                        used = bytes
                                    )

                                    NetworkStats.Bucket.UID_TETHERING -> AppValues(
                                        uid = uid,
                                        name = context.getString(R.string.app_name_tethering_and_hotspot),
                                        vector = Icons.Outlined.WifiTethering,
                                        used = bytes
                                    )

                                    NetworkStats.Bucket.UID_REMOVED -> AppValues(
                                        uid = uid,
                                        name = context.getString(R.string.app_name_removed_apps),
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
                                            name = uid,
                                            vector = Icons.Outlined.BrokenImage,
                                            used = bytes
                                        )
                                    }
                                }
                            }.sortedWith(
                                when(filters.orderBy) {
                                    Filters.OrderBy.NAME -> compareBy(collator) { it.name.toString().orEmpty() }
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
