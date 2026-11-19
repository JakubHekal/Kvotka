package com.jakubhekal.kvotka.viewmodels

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
import com.jakubhekal.kvotka.managers.netstats.SimUsageProvider
import com.jakubhekal.kvotka.providers.UsageLimitsProvider
import com.jakubhekal.kvotka.utils.LimitsPreferences
import com.jakubhekal.kvotka.utils.PreferencesKeys
import com.jakubhekal.kvotka.utils.adoptUnassignedPlan
import com.jakubhekal.kvotka.utils.bytes.FormattedBytesPair
import com.jakubhekal.kvotka.utils.ensurePlansMigrated
import com.jakubhekal.kvotka.utils.firstPlan
import com.jakubhekal.kvotka.utils.safePreferences
import com.jakubhekal.kvotka.utils.simPlans
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

class AppListViewModel(private val context: Context, val dataStore: DataStore<Preferences>, private val networkUsageManager: NetworkUsageManager, private val packageManager: PackageManager, private val simUsageProvider: SimUsageProvider): GenericViewModel(dataStore) {

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

    private data class AppInfo(
        val name: String,
        val packageName: String? = null,
        val bitmap: ImageBitmap? = null,
        val vector: ImageVector? = null,
    )

    private data class UsageKey(val interval: Filters.Period, val startMillis: Long, val sim: Int)

    private data class PlanFilters(
        val plans: Map<Int, LimitsPreferences>,
        val filters: Filters
    )

    var model: Model? by mutableStateOf(null)
        private set

    var simIds: List<Int> by mutableStateOf(emptyList())
        private set


    data class Filters(
        val interval: Period,
        val orderBy: OrderBy,
        val orderAsc: Boolean,
        val sim: Int = -1
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

        companion object {
            // Unknown names and out-of-range legacy ordinals fall back to a default instead of crashing.
            fun periodFrom(name: String?, legacyOrdinal: Int?): Period =
                Period.entries.firstOrNull { it.name == name }
                    ?: Period.entries.getOrNull(legacyOrdinal ?: -1)
                    ?: Period.TODAY

            fun orderByFrom(name: String?, legacyOrdinal: Int?): OrderBy =
                OrderBy.entries.firstOrNull { it.name == name }
                    ?: OrderBy.entries.getOrNull(legacyOrdinal ?: -1)
                    ?: OrderBy.TOTAL

            fun from(preferences: Preferences) = Filters(
                interval = periodFrom(
                    preferences[PreferencesKeys.Filter.PERIOD_NAME],
                    preferences[PreferencesKeys.Filter.PERIOD]
                ),
                orderBy = orderByFrom(
                    preferences[PreferencesKeys.Filter.ORDER_BY_NAME],
                    preferences[PreferencesKeys.Filter.ORDER_BY]
                ),
                orderAsc = preferences[PreferencesKeys.Filter.ORDER_ASC] ?: false,
                sim = preferences[PreferencesKeys.Filter.SIM] ?: -1
            )
        }
    }

    private val refresh = MutableStateFlow(0)
    private var started = false
    private val appInfoCache = java.util.Collections.synchronizedMap(mutableMapOf<Int, AppInfo>())
    private val usageCache = mutableMapOf<UsageKey, Map<Int, FormattedBytesPair>>()

    // A manual refresh is the only thing that invalidates cached results.
    fun getState() {
        appInfoCache.clear()
        usageCache.clear()
        if (!started) start()
        refresh.value++
    }

    fun start() {
        if (started) return
        started = true

        viewModelScope.launch {
            try {
                dataStore.ensurePlansMigrated()
                dataStore.safePreferences()
                    .map { preferences ->
                        PlanFilters(
                            plans = preferences.simPlans(),
                            filters = Filters.from(preferences)
                        )
                    }
                    .distinctUntilChanged()
                    .combine(refresh) { prefs, _ -> prefs }
                    .collectLatest { (plans, filters) -> update(plans, filters) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // A dead preferences stream must not leave the screen on a spinner forever.
                e.printStackTrace()
                state = State.ERROR
                started = false
            }
        }
    }

    private suspend fun update(plans: Map<Int, LimitsPreferences>, filters: Filters) {
        state = if (model == null) State.LOADING else State.UPDATING
        state = try {
            val netstats = simUsageProvider.data()
            if (netstats != null) {
                simIds = netstats.subIds.sorted()
                if (plans.containsKey(LimitsPreferences.UNASSIGNED_SIM)) {
                    dataStore.adoptUnassignedPlan(netstats.subIds)
                }
            }

            val planSim = filters.sim.takeIf { netstats?.subIds?.contains(it) == true }
            val usageLimitsProvider = UsageLimitsProvider(
                plans[planSim]
                    ?: plans.firstPlan()
                    ?: LimitsPreferences(0L, 1, false, 0L)
            )

            val intervalStart = when (filters.interval) {
                Filters.Period.TODAY -> usageLimitsProvider.todayStart
                Filters.Period.THIS_PERIOD -> usageLimitsProvider.currentPeriodStart
                Filters.Period.LAST_PERIOD -> usageLimitsProvider.lastPeriodStart
                Filters.Period.THIS_YEAR -> usageLimitsProvider.todayStart.withDayOfMonth(1).withMonth(1)
                Filters.Period.ALL_TIME -> ZonedDateTime.of(2000, 1, 1, 0, 0, 0, 0, ZoneId.systemDefault())
            }

            val intervalEnd = when (filters.interval) {
                Filters.Period.LAST_PERIOD -> usageLimitsProvider.currentPeriodStart
                else -> ZonedDateTime.now()
            }

            val usageKey = UsageKey(filters.interval, intervalStart.toInstant().toEpochMilli(), planSim ?: -1)
            val usages = usageCache[usageKey] ?: withContext(Dispatchers.Default) {
                // A SIM that netstats does not report (Shizuku down, SIM removed) falls back to
                // device totals instead of returning an empty list.
                val sim = planSim
                if (netstats != null && sim != null) {
                    aggregateUids(
                        simUsageProvider.uidUsage(
                            netstats,
                            intervalStart.toInstant().toEpochMilli(),
                            intervalEnd.toInstant().toEpochMilli()
                        )[sim].orEmpty()
                    )
                } else {
                    aggregateUids(
                        networkUsageManager.getUidsUsageCellular(
                            intervalStart.toInstant().toEpochMilli(),
                            intervalEnd.toInstant().toEpochMilli()
                        )
                    )
                }
            }.also { usageCache[usageKey] = it }

            val apps = withContext(Dispatchers.Default) { buildApps(usages, filters) }

            model = Model(apps, filters)
            State.SUCCESS
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            e.printStackTrace()
            State.ERROR
        }
    }

    private fun aggregateUids(rawUsages: Map<Int, FormattedBytesPair>): Map<Int, FormattedBytesPair> {
        val usages = mutableMapOf<Int, FormattedBytesPair>()
        rawUsages.forEach { (uid, bytes) ->
            usages.merge(canonicalUid(uid), bytes, FormattedBytesPair::plus)
        }
        return usages
    }

    private fun canonicalUid(uid: Int): Int = when (uid) {
        Process.ROOT_UID, Process.SYSTEM_UID -> Process.SYSTEM_UID
        NetworkStats.Bucket.UID_TETHERING -> NetworkStats.Bucket.UID_TETHERING
        NetworkStats.Bucket.UID_REMOVED -> NetworkStats.Bucket.UID_REMOVED
        in Process.FIRST_APPLICATION_UID..Process.LAST_APPLICATION_UID -> {
            val packageName = packageManager.getPackagesForUid(uid)?.firstOrNull()
            val appUid = packageName?.let {
                try {
                    packageManager.getApplicationInfo(it, 0).uid
                } catch (_: PackageManager.NameNotFoundException) {
                    null
                }
            }
            appUid ?: NetworkStats.Bucket.UID_REMOVED
        }
        else -> NetworkStats.Bucket.UID_ALL
    }

    private fun buildApps(usages: Map<Int, FormattedBytesPair>, filters: Filters): List<AppValues> {
        val collator = Collator.getInstance(Locale.getDefault()).apply {
            strength = Collator.SECONDARY
        }

        val apps = usages.map { (uid, bytes) ->
            val info = appInfo(uid)
            AppValues(
                uid = uid,
                name = info.name,
                packageName = info.packageName,
                bitmap = info.bitmap,
                vector = info.vector,
                used = bytes
            )
        }.sortedWith(
            when (filters.orderBy) {
                Filters.OrderBy.NAME -> compareBy(collator) { it.name }
                Filters.OrderBy.TOTAL -> compareBy { it.used.total.bytes }
                Filters.OrderBy.RECEIVED -> compareBy { it.used.rx.bytes }
                Filters.OrderBy.SENT -> compareBy { it.used.tx.bytes }
            }
        )

        return if (filters.orderAsc) apps else apps.reversed()
    }

    private fun appInfo(uid: Int): AppInfo = appInfoCache.getOrPut(uid) {
        when (uid) {
            Process.SYSTEM_UID -> AppInfo(
                name = context.getString(R.string.app_name_system),
                vector = Icons.Outlined.Android
            )

            NetworkStats.Bucket.UID_ALL -> AppInfo(
                name = context.getString(R.string.app_name_other),
                vector = Icons.Outlined.MoreHoriz
            )

            NetworkStats.Bucket.UID_TETHERING -> AppInfo(
                name = context.getString(R.string.app_name_tethering_and_hotspot),
                vector = Icons.Outlined.WifiTethering
            )

            NetworkStats.Bucket.UID_REMOVED -> AppInfo(
                name = context.getString(R.string.app_name_removed_apps),
                vector = Icons.Outlined.Delete
            )

            in Process.FIRST_APPLICATION_UID..Process.LAST_APPLICATION_UID -> applicationAppInfo(uid)

            else -> AppInfo(name = uid.toString(), vector = Icons.Outlined.BrokenImage)
        }
    }

    private fun applicationAppInfo(uid: Int): AppInfo {
        val removed = AppInfo(context.getString(R.string.app_name_removed_apps), vector = Icons.Outlined.Delete)
        val packageName = packageManager.getPackagesForUid(uid)?.firstOrNull() ?: return removed
        val applicationInfo = try {
            packageManager.getApplicationInfo(packageName, 0)
        } catch (_: PackageManager.NameNotFoundException) {
            return removed
        }
        val bitmap = try {
            applicationInfo.loadIcon(packageManager).toBitmap(128, 128).asImageBitmap()
        } catch (_: Exception) {
            null
        }
        return AppInfo(
            name = applicationInfo.loadLabel(packageManager).toString(),
            packageName = applicationInfo.packageName,
            bitmap = bitmap
        )
    }

    fun setState(model: Model) {
        viewModelScope.launch {
            dataStore.edit { preferences ->
                preferences[PreferencesKeys.Filter.PERIOD_NAME] = model.filters.interval.name
                preferences[PreferencesKeys.Filter.ORDER_BY_NAME] = model.filters.orderBy.name
                preferences[PreferencesKeys.Filter.ORDER_ASC] = model.filters.orderAsc
                preferences[PreferencesKeys.Filter.SIM] = model.filters.sim
            }
        }
    }
}
