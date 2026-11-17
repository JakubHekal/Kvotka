package com.jakubhekal.kvotka.utils

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

object PreferencesKeys {
    object Settings {
        val PERIOD_LIMIT = longPreferencesKey("period_limit")
        val PERIOD_RESET_DATE = intPreferencesKey("period_reset_date")
        val DAILY_LIMIT_DYNAMIC = booleanPreferencesKey("daily_limit_dynamic")
        val DAILY_LIMIT = longPreferencesKey("daily_limit")
        val SELECTED_SIM = intPreferencesKey("selected_sim")
    }

    object Filter {
        // Stored as enum names. The int keys are read only as a fallback for values saved by older versions.
        val PERIOD_NAME = stringPreferencesKey("period_name")
        val ORDER_BY_NAME = stringPreferencesKey("order_by_name")
        val PERIOD = intPreferencesKey("period")
        val ORDER_BY = intPreferencesKey("order_by")
        val ORDER_ASC = booleanPreferencesKey("order_asc")
        val SIM = intPreferencesKey("sim")
    }
}