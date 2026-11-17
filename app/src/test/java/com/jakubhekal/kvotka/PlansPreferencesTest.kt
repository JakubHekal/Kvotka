package com.jakubhekal.kvotka

import androidx.datastore.preferences.core.mutablePreferencesOf
import com.jakubhekal.kvotka.utils.LimitsPreferences
import com.jakubhekal.kvotka.utils.PreferencesKeys
import com.jakubhekal.kvotka.utils.firstPlan
import com.jakubhekal.kvotka.utils.simPlans
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlansPreferencesTest {

    private val plan = LimitsPreferences(
        periodLimit = 20_000_000_000L,
        periodResetDate = 15,
        dailyLimitDynamic = true,
        dailyLimit = 0L
    )

    @Test
    fun encodeDecodeRoundTrip() {
        assertEquals(plan, LimitsPreferences.decode(plan.encode()))
    }

    @Test
    fun malformedValuesAreIgnored() {
        assertNull(LimitsPreferences.decode(null))
        assertNull(LimitsPreferences.decode(""))
        assertNull(LimitsPreferences.decode("1|2|true"))
        assertNull(LimitsPreferences.decode("x|2|true|4"))
        assertNull(LimitsPreferences.decode("1|2|maybe|4"))
    }

    @Test
    fun readsOnlySimPlanKeys() {
        val preferences = mutablePreferencesOf()
        preferences[PreferencesKeys.Settings.simPlan(3)] = plan.encode()
        preferences[PreferencesKeys.Settings.simPlan(4)] = "broken"
        preferences[PreferencesKeys.Settings.SELECTED_SIM] = 9

        assertEquals(mapOf(3 to plan), preferences.simPlans())
    }

    @Test
    fun firstPlanIsLowestId() {
        val plans = mapOf(
            7 to plan.copy(periodResetDate = 20),
            2 to plan.copy(periodResetDate = 5)
        )

        assertEquals(5, plans.firstPlan()?.periodResetDate)
    }

    @Test
    fun migrationMovesLegacyKeysToUnassigned() {
        val preferences = mutablePreferencesOf()
        preferences[PreferencesKeys.Settings.PERIOD_LIMIT] = 5L
        preferences[PreferencesKeys.Settings.PERIOD_RESET_DATE] = 9
        preferences[PreferencesKeys.Settings.DAILY_LIMIT_DYNAMIC] = true
        preferences[PreferencesKeys.Settings.DAILY_LIMIT] = 1L

        assertTrue(LimitsPreferences.migrateLegacy(preferences))
        assertFalse(preferences.contains(PreferencesKeys.Settings.PERIOD_LIMIT))
        assertEquals(
            LimitsPreferences(5L, 9, true, 1L),
            LimitsPreferences.decode(preferences[PreferencesKeys.Settings.simPlan(LimitsPreferences.UNASSIGNED_SIM)])
        )
        assertFalse(LimitsPreferences.migrateLegacy(preferences))
    }

    @Test
    fun migrationWithoutLegacyKeysOnlyMarksDone() {
        val preferences = mutablePreferencesOf()

        assertTrue(LimitsPreferences.migrateLegacy(preferences))
        assertTrue(preferences.simPlans().isEmpty())
        assertTrue(preferences[PreferencesKeys.Settings.PLANS_MIGRATED] == true)
    }

    @Test
    fun adoptionMovesUnassignedPlanToLowestSim() {
        val preferences = mutablePreferencesOf()
        preferences[PreferencesKeys.Settings.simPlan(LimitsPreferences.UNASSIGNED_SIM)] = plan.encode()

        assertTrue(LimitsPreferences.adoptUnassigned(preferences, listOf(4, 2)))
        assertNull(preferences[PreferencesKeys.Settings.simPlan(LimitsPreferences.UNASSIGNED_SIM)])
        assertEquals(plan.encode(), preferences[PreferencesKeys.Settings.simPlan(2)])
        assertFalse(LimitsPreferences.adoptUnassigned(preferences, listOf(4, 2)))
    }
}
