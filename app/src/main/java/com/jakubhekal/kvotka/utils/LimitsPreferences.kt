package com.jakubhekal.kvotka.utils

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import java.io.IOException

data class LimitsPreferences(
    val periodLimit: Long,
    val periodResetDate: Int,
    val dailyLimitDynamic: Boolean,
    val dailyLimit: Long
) {

    fun encode(): String = "$periodLimit|$periodResetDate|$dailyLimitDynamic|$dailyLimit"

    companion object {
        /** Holds the single plan created before the first SIM id is known. */
        const val UNASSIGNED_SIM = -1

        private val LEGACY_KEYS = listOf(
            PreferencesKeys.Settings.PERIOD_LIMIT,
            PreferencesKeys.Settings.PERIOD_RESET_DATE,
            PreferencesKeys.Settings.DAILY_LIMIT_DYNAMIC,
            PreferencesKeys.Settings.DAILY_LIMIT
        )

        fun from(preferences: Preferences) = LimitsPreferences(
            periodLimit = preferences[PreferencesKeys.Settings.PERIOD_LIMIT] ?: 0L,
            periodResetDate = preferences[PreferencesKeys.Settings.PERIOD_RESET_DATE] ?: 1,
            dailyLimitDynamic = preferences[PreferencesKeys.Settings.DAILY_LIMIT_DYNAMIC] ?: false,
            dailyLimit = preferences[PreferencesKeys.Settings.DAILY_LIMIT] ?: 0L
        )

        /** Malformed values are ignored so a broken entry falls back to "no plan". */
        fun decode(value: String?): LimitsPreferences? {
            val parts = value?.split('|') ?: return null
            if (parts.size != 4) return null
            return LimitsPreferences(
                periodLimit = parts[0].toLongOrNull() ?: return null,
                periodResetDate = parts[1].toIntOrNull() ?: return null,
                dailyLimitDynamic = parts[2].toBooleanStrictOrNull() ?: return null,
                dailyLimit = parts[3].toLongOrNull() ?: return null
            )
        }

        /** Moves the pre-plans global limit keys into the unassigned plan, once. */
        fun migrateLegacy(preferences: MutablePreferences): Boolean {
            if (preferences[PreferencesKeys.Settings.PLANS_MIGRATED] == true) return false

            if (LEGACY_KEYS.any { preferences.contains(it) }) {
                preferences[PreferencesKeys.Settings.simPlan(UNASSIGNED_SIM)] =
                    from(preferences).encode()
                LEGACY_KEYS.forEach { preferences.remove(it) }
            }
            preferences[PreferencesKeys.Settings.PLANS_MIGRATED] = true
            return true
        }

        /** Gives the unassigned plan to the lowest known SIM id. */
        fun adoptUnassigned(preferences: MutablePreferences, subIds: Collection<Int>): Boolean {
            val unassignedKey = PreferencesKeys.Settings.simPlan(UNASSIGNED_SIM)
            val plan = preferences[unassignedKey] ?: return false
            val subId = subIds.minOrNull() ?: return false
            val simKey = PreferencesKeys.Settings.simPlan(subId)

            if (!preferences.contains(simKey)) preferences[simKey] = plan
            preferences.remove(unassignedKey)
            return true
        }
    }
}

fun Preferences.simPlans(): Map<Int, LimitsPreferences> =
    asMap().entries.mapNotNull { (key, value) ->
        if (!key.name.startsWith(PreferencesKeys.Settings.SIM_PLAN_PREFIX)) return@mapNotNull null
        val subId = key.name
            .removePrefix(PreferencesKeys.Settings.SIM_PLAN_PREFIX)
            .toIntOrNull()
            ?: return@mapNotNull null
        val plan = LimitsPreferences.decode(value as? String) ?: return@mapNotNull null
        subId to plan
    }.toMap()

fun Map<Int, LimitsPreferences>.firstPlan(): LimitsPreferences? =
    entries.minByOrNull { it.key }?.value

fun DataStore<Preferences>.safePreferences(): Flow<Preferences> = data.catch { e ->
    if (e is IOException) emit(emptyPreferences()) else throw e
}

suspend fun DataStore<Preferences>.ensurePlansMigrated() {
    edit { LimitsPreferences.migrateLegacy(it) }
}

suspend fun DataStore<Preferences>.adoptUnassignedPlan(subIds: Collection<Int>) {
    if (subIds.isEmpty()) return
    edit { LimitsPreferences.adoptUnassigned(it, subIds) }
}

fun DataStore<Preferences>.simPlansPreferences(): Flow<Map<Int, LimitsPreferences>> =
    safePreferences()
        .onStart { ensurePlansMigrated() }
        .map { it.simPlans() }
        .distinctUntilChanged()
